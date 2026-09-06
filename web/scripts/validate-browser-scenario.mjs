#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonicalize(value[key])]));
  }
  return value;
}

export function canonicalDigest(value) {
  return createHash('sha256').update(JSON.stringify(canonicalize(value))).digest('hex');
}

export async function loadScenarioContract(contractPath) {
  const contract = JSON.parse(await readFile(contractPath, 'utf8'));
  const schema = JSON.parse(await readFile(resolve(dirname(contractPath), 'browser-scenarios.schema.json'), 'utf8'));
  const errors = validateScenarioContract(contract, schema);
  if (errors.length > 0) throw new Error(errors.join('\n'));
  return contract;
}

export function validateScenarioContract(contract, schema = null) {
  const errors = [];
  if (schema) validateJsonSchema(contract, schema, '$', errors);
  const exactKeys = (value, expected, diagnostic) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      errors.push(`${diagnostic}_TYPE_INVALID`);
      return;
    }
    const actual = Object.keys(value).sort();
    const wanted = [...expected].sort();
    if (JSON.stringify(actual) !== JSON.stringify(wanted)) {
      errors.push(`${diagnostic}_FIELDS_INVALID expected=${wanted.join(',')} actual=${actual.join(',')}`);
    }
  };

  exactKeys(contract, ['schemaVersion', 'scenario', 'digests'], 'SCENARIO_ROOT');
  if (contract?.schemaVersion !== '1.0.0') errors.push('SCENARIO_SCHEMA_VERSION_UNSUPPORTED');
  const scenario = contract?.scenario;
  exactKeys(scenario, ['id', 'route', 'selectors', 'actions', 'responder', 'errorPolicy', 'visualRule'], 'SCENARIO');
  if (scenario?.id !== 'LOGIN-SMOKE-V1') errors.push('SCENARIO_ID_INVALID');
  if (scenario?.route !== '/login') errors.push('SCENARIO_ROUTE_INVALID');

  const selectorKeys = ['page', 'card', 'username', 'password', 'remember', 'submit', 'error'];
  exactKeys(scenario?.selectors, selectorKeys, 'SCENARIO_SELECTORS');
  for (const key of selectorKeys) {
    if (!/^shared-auth-login-[a-z0-9-]+$/.test(scenario?.selectors?.[key] ?? '')) {
      errors.push(`SCENARIO_SELECTOR_INVALID key=${key}`);
    }
  }

  const allowedActions = new Set(['submit-empty', 'fill', 'check', 'assert-checked', 'click', 'await-response', 'assert-visible']);
  if (!Array.isArray(scenario?.actions) || scenario.actions.length < 8) errors.push('SCENARIO_ACTIONS_INCOMPLETE');
  for (const action of scenario?.actions ?? []) {
    if (!allowedActions.has(action.kind)) errors.push(`SCENARIO_ACTION_KIND_INVALID kind=${action.kind}`);
    if (action.target !== 'mapped-responder' && !selectorKeys.includes(action.target)) {
      errors.push(`SCENARIO_ACTION_TARGET_INVALID target=${action.target}`);
    }
  }
  const exactActionSequence = [
    ['submit-empty', 'submit'], ['fill', 'username'], ['fill', 'password'], ['check', 'remember'],
    ['assert-checked', 'remember'], ['click', 'submit'], ['await-response', 'mapped-responder'], ['assert-visible', 'error'],
  ];
  if (JSON.stringify((scenario?.actions ?? []).map(({ kind, target }) => [kind, target])) !== JSON.stringify(exactActionSequence)) {
    errors.push('SCENARIO_ACTION_SEQUENCE_INVALID');
  }

  const responder = scenario?.responder;
  if (responder?.canonicalPath !== '/console/auth/login') errors.push('SCENARIO_RESPONDER_CANONICAL_PATH_INVALID');
  if (JSON.stringify(responder?.mappedPaths) !== JSON.stringify(['/console/auth/login', '/api/v1/console/auth/login'])) {
    errors.push('SCENARIO_RESPONDER_MAPPING_INVALID');
  }
  if (responder?.method !== 'POST' || responder?.status !== 401) errors.push('SCENARIO_RESPONDER_STATUS_INVALID');
  if (responder?.marker?.name !== 'X-YCS-Scenario' || responder?.marker?.value !== scenario?.id) {
    errors.push('SCENARIO_RESPONDER_MARKER_INVALID');
  }
  if (JSON.stringify(responder?.body) !== JSON.stringify({ code: 'AUTH_INVALID_CREDENTIALS', message: '用户名或密码错误' })) {
    errors.push('SCENARIO_RESPONDER_BODY_INVALID');
  }

  const rule = scenario?.visualRule;
  if (rule?.id !== 'LOGIN-CARD-IN-VIEWPORT-V2') errors.push('VISUAL_RULE_ID_INVALID');
  if (JSON.stringify(rule?.requiredSelectorKeys) !== JSON.stringify(['page', 'card', 'username', 'password', 'remember', 'submit'])) {
    errors.push('VISUAL_RULE_REQUIRED_SELECTORS_INVALID');
  }
  if (rule?.ancestorClipping?.requiredCoverage !== 'full-target-border-box') errors.push('VISUAL_RULE_CLIPPING_INVALID');
  if (JSON.stringify(rule?.hitTest?.points) !== JSON.stringify(['center', 'top-left', 'top-right', 'bottom-left', 'bottom-right'])) {
    errors.push('VISUAL_RULE_POINTS_INVALID');
  }
  const scenarioDigest = scenario ? canonicalDigest(scenario) : null;
  const ruleDigest = rule ? canonicalDigest(rule) : null;
  if (contract?.digests?.scenarioSha256 !== scenarioDigest) errors.push(`SCENARIO_DIGEST_MISMATCH expected=${scenarioDigest}`);
  if (contract?.digests?.visualRuleSha256 !== ruleDigest) errors.push(`VISUAL_RULE_DIGEST_MISMATCH expected=${ruleDigest}`);
  return errors;
}

function validateJsonSchema(value, schema, valuePath, errors) {
  if (Object.hasOwn(schema, 'const') && JSON.stringify(value) !== JSON.stringify(schema.const)) {
    errors.push(`SCENARIO_SCHEMA_CONST path=${valuePath}`);
  }
  if (schema.enum && !schema.enum.some((candidate) => JSON.stringify(candidate) === JSON.stringify(value))) {
    errors.push(`SCENARIO_SCHEMA_ENUM path=${valuePath}`);
  }
  if (schema.type && !matchesType(value, schema.type)) {
    errors.push(`SCENARIO_SCHEMA_TYPE path=${valuePath} expected=${schema.type}`);
    return;
  }
  if (typeof value === 'string' && schema.pattern && !new RegExp(schema.pattern).test(value)) {
    errors.push(`SCENARIO_SCHEMA_PATTERN path=${valuePath}`);
  }
  if (Array.isArray(value)) {
    if (schema.minItems && value.length < schema.minItems) errors.push(`SCENARIO_SCHEMA_MIN_ITEMS path=${valuePath}`);
    if (schema.maxItems && value.length > schema.maxItems) errors.push(`SCENARIO_SCHEMA_MAX_ITEMS path=${valuePath}`);
    if (schema.items) value.forEach((entry, index) => validateJsonSchema(entry, schema.items, `${valuePath}[${index}]`, errors));
  }
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const required of schema.required ?? []) {
      if (!Object.hasOwn(value, required)) errors.push(`SCENARIO_SCHEMA_REQUIRED path=${valuePath}.${required}`);
    }
    if (schema.additionalProperties === false) {
      for (const key of Object.keys(value)) {
        if (!Object.hasOwn(schema.properties ?? {}, key)) errors.push(`SCENARIO_SCHEMA_ADDITIONAL path=${valuePath}.${key}`);
      }
    }
    for (const [key, child] of Object.entries(schema.properties ?? {})) {
      if (Object.hasOwn(value, key)) validateJsonSchema(value[key], child, `${valuePath}.${key}`, errors);
    }
  }
}

function matchesType(value, type) {
  if (type === 'array') return Array.isArray(value);
  if (type === 'object') return value !== null && typeof value === 'object' && !Array.isArray(value);
  return typeof value === type;
}

/** Runs inside the browser. Keep all helpers nested so Playwright can serialize it. */
export function evaluateVisualRuleInPage({ selectors, visualRule }) {
  const observations = [];
  const failures = [];
  const clippingValues = new Set(visualRule.ancestorClipping.overflowValues);
  const inset = visualRule.hitTest.insetPixels;

  const selectorFor = (testId) => `[data-testid="${CSS.escape(testId)}"]`;
  const number = (value) => Number.parseFloat(value) || 0;
  const rectObject = (rect) => ({
    x: rect.x, y: rect.y, top: rect.top, left: rect.left,
    right: rect.right, bottom: rect.bottom, width: rect.width, height: rect.height,
  });
  const covers = (outer, inner, axis) => {
    if (axis === 'x') return outer.left <= inner.left && outer.right >= inner.right;
    return outer.top <= inner.top && outer.bottom >= inner.bottom;
  };

  for (const key of visualRule.requiredSelectorKeys) {
    const testId = selectors[key];
    const target = document.querySelector(selectorFor(testId));
    if (!(target instanceof HTMLElement)) {
      failures.push({ diagnostic: 'VISUAL_ELEMENT_MISSING', key, testId });
      continue;
    }
    const targetRect = target.getBoundingClientRect();
    const elementObservation = { key, testId, rectangle: rectObject(targetRect), ancestors: [], hitTests: [] };
    observations.push(elementObservation);
    if (targetRect.width <= 0 || targetRect.height <= 0) failures.push({ diagnostic: 'VISUAL_RECT_NON_POSITIVE', key });
    if (targetRect.left < 0 || targetRect.top < 0 || targetRect.right > innerWidth || targetRect.bottom > innerHeight) {
      failures.push({ diagnostic: 'VISUAL_VIEWPORT_ESCAPE', key });
    }

    let current = target;
    while (current instanceof HTMLElement) {
      const style = getComputedStyle(current);
      const rect = current.getBoundingClientRect();
      const ancestor = {
        testId: current.dataset.testid ?? null,
        tag: current.tagName.toLowerCase(),
        rectangle: rectObject(rect),
        display: style.display,
        visibility: style.visibility,
        contentVisibility: style.contentVisibility,
        opacity: style.opacity,
        clipPath: style.clipPath,
        maskImage: style.maskImage,
        overflowX: style.overflowX,
        overflowY: style.overflowY,
      };
      elementObservation.ancestors.push(ancestor);
      if (style.display === 'none') failures.push({ diagnostic: 'VISUAL_DISPLAY_NONE', key, tag: ancestor.tag });
      if (['hidden', 'collapse'].includes(style.visibility)) failures.push({ diagnostic: 'VISUAL_VISIBILITY_HIDDEN', key, tag: ancestor.tag });
      if (style.contentVisibility === 'hidden') failures.push({ diagnostic: 'VISUAL_CONTENT_VISIBILITY_HIDDEN', key, tag: ancestor.tag });
      if (number(style.opacity) <= visualRule.computedStyleFailures.effectiveOpacityAtMost) {
        failures.push({ diagnostic: 'VISUAL_ZERO_EFFECTIVE_OPACITY', key, tag: ancestor.tag });
      }
      if (style.clipPath && style.clipPath !== 'none') failures.push({ diagnostic: 'VISUAL_CLIP_PATH', key, tag: ancestor.tag });
      if (style.maskImage && style.maskImage !== 'none') failures.push({ diagnostic: 'VISUAL_MASK', key, tag: ancestor.tag });

      if (current !== target) {
        const paddingBox = {
          left: rect.left + number(style.borderLeftWidth),
          top: rect.top + number(style.borderTopWidth),
          right: rect.right - number(style.borderRightWidth),
          bottom: rect.bottom - number(style.borderBottomWidth),
        };
        ancestor.paddingBox = paddingBox;
        if (clippingValues.has(style.overflowX) && !covers(paddingBox, targetRect, 'x')) {
          failures.push({ diagnostic: 'VISUAL_ANCESTOR_CLIP_X', key, tag: ancestor.tag });
        }
        if (clippingValues.has(style.overflowY) && !covers(paddingBox, targetRect, 'y')) {
          failures.push({ diagnostic: 'VISUAL_ANCESTOR_CLIP_Y', key, tag: ancestor.tag });
        }
      }
      current = current.parentElement;
    }

    const points = [
      { name: 'center', x: targetRect.left + targetRect.width / 2, y: targetRect.top + targetRect.height / 2 },
      { name: 'top-left', x: targetRect.left + inset, y: targetRect.top + inset },
      { name: 'top-right', x: targetRect.right - inset, y: targetRect.top + inset },
      { name: 'bottom-left', x: targetRect.left + inset, y: targetRect.bottom - inset },
      { name: 'bottom-right', x: targetRect.right - inset, y: targetRect.bottom - inset },
    ];
    for (const point of points) {
      const hit = document.elementFromPoint(point.x, point.y);
      const accepted = hit === target || (hit instanceof Node && target.contains(hit));
      elementObservation.hitTests.push({ ...point, hitTestId: hit instanceof HTMLElement ? hit.dataset.testid ?? null : null, hitTag: hit?.nodeName ?? null, accepted });
      if (!accepted) failures.push({ diagnostic: 'VISUAL_HIT_OBSTRUCTED', key, point: point.name });
    }
  }
  return { ruleId: visualRule.id, viewport: { width: innerWidth, height: innerHeight }, observations, failures };
}

async function main(argv) {
  const contractIndex = argv.indexOf('--contract');
  if (contractIndex < 0 || !argv[contractIndex + 1]) {
    console.error('browser_scenario=BLOCKED diagnostic=SCENARIO_CONTRACT_REQUIRED');
    process.exitCode = 2;
    return;
  }
  try {
    const contract = await loadScenarioContract(argv[contractIndex + 1]);
    console.log(`browser_scenario=PASS scenario_id=${contract.scenario.id} scenario_digest=${contract.digests.scenarioSha256} visual_rule_id=${contract.scenario.visualRule.id} visual_rule_digest=${contract.digests.visualRuleSha256}`);
  } catch (error) {
    console.error(`browser_scenario=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main(process.argv.slice(2));
