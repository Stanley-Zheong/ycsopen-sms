#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';

const CLASSIFICATION_FIXTURE = 'foundation-fixture';
const CLASSIFICATION_PRODUCTION = 'production-surface';
const SOURCE_KINDS = new Set(['jsx-text', 'aria-label', 'placeholder', 'title', 'alt', 'error']);
const ATTRIBUTE_KINDS = new Set(['aria-label', 'placeholder', 'title', 'alt']);
const BROAD_TOKEN_CHARACTERS = new Set('*?[]{}()^$|\\');
const LATIN_TOKEN_PATTERN = /[A-Za-z][A-Za-z0-9._:/+-]*/g;
const TRADITIONAL_ONLY_CHARACTERS = new Set([
  '帳', '號', '碼', '錯', '誤', '鎖', '輸', '記', '憶', '導', '匯', '發', '狀', '態', '間',
]);

export class CopyContractError extends Error {
  constructor(diagnostic, detail = '') {
    super(`${diagnostic}${detail ? ` ${detail}` : ''}`);
    this.name = 'CopyContractError';
    this.diagnostic = diagnostic;
  }
}

function fail(diagnostic, detail = '') {
  throw new CopyContractError(diagnostic, detail);
}

function assert(condition, diagnostic, detail = '') {
  if (!condition) fail(diagnostic, detail);
}

export function normalizeVisibleText(value) {
  return String(value).replace(/\s+/g, ' ').trim();
}

function requirePlainObject(value, diagnostic) {
  assert(value !== null && typeof value === 'object' && !Array.isArray(value), diagnostic);
  return value;
}

function entryKey(entry) {
  return `${entry.kind}\u0000${entry.value}`;
}

function sortedKeys(entries) {
  return entries.map(entryKey).sort();
}

function arraysEqual(left, right) {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function validateTextPolicy(value, technicalTokens, diagnosticPrefix) {
  const normalized = normalizeVisibleText(value);
  assert(normalized.length > 0, `${diagnosticPrefix}_EMPTY`);
  for (const character of normalized) {
    if (TRADITIONAL_ONLY_CHARACTERS.has(character)) {
      fail('COPY_TRADITIONAL_ONLY_VARIANT', `value=${JSON.stringify(normalized)} character=${character}`);
    }
  }
  const latinTokens = normalized.match(LATIN_TOKEN_PATTERN) ?? [];
  for (const token of latinTokens) {
    if (!technicalTokens.has(token)) {
      fail('COPY_TECHNICAL_TOKEN_UNREGISTERED', `token=${token} value=${JSON.stringify(normalized)}`);
    }
  }
  return normalized;
}

export function validateRegistryDocument(rawRegistry) {
  const registry = requirePlainObject(rawRegistry, 'COPY_REGISTRY_INVALID');
  assert(registry.schemaVersion === '1.0.0', 'COPY_REGISTRY_VERSION_INVALID');
  assert(registry.contractId === 'COPY-ZH-CN-V1', 'COPY_REGISTRY_ID_INVALID');
  assert(registry.locale === 'zh-CN', 'COPY_REGISTRY_LOCALE_INVALID');
  const acceptance = requirePlainObject(registry.acceptance, 'COPY_ACCEPTANCE_SCOPE_MISSING');
  assert(acceptance.classification === CLASSIFICATION_FIXTURE, 'COPY_ACCEPTANCE_SCOPE_INVALID');
  assert(acceptance.closesProductAcceptance === false, 'COPY_PRODUCT_ACCEPTANCE_CLAIM_FORBIDDEN');
  assert(acceptance.productAcceptanceOwner === 'final-release-acceptance', 'COPY_PRODUCT_OWNER_INVALID');

  assert(Array.isArray(registry.technicalTokens), 'COPY_TECHNICAL_ALLOWLIST_INVALID');
  const tokenValues = new Set();
  for (const entry of registry.technicalTokens) {
    requirePlainObject(entry, 'COPY_TECHNICAL_TOKEN_INVALID');
    assert(typeof entry.value === 'string' && entry.value.length > 0, 'COPY_TECHNICAL_TOKEN_INVALID');
    assert(![...entry.value].some((character) => BROAD_TOKEN_CHARACTERS.has(character)), 'COPY_TECHNICAL_ALLOWLIST_BROAD', `token=${entry.value}`);
    assert(/^[A-Za-z][A-Za-z0-9._:/+-]*$/.test(entry.value), 'COPY_TECHNICAL_TOKEN_INVALID', `token=${entry.value}`);
    assert(entry.classification === CLASSIFICATION_PRODUCTION, 'COPY_TECHNICAL_TOKEN_SCOPE_INVALID');
    assert(typeof entry.reason === 'string' && entry.reason.length > 0, 'COPY_TECHNICAL_TOKEN_REASON_MISSING');
    assert(!tokenValues.has(entry.value), 'COPY_TECHNICAL_TOKEN_DUPLICATE', `token=${entry.value}`);
    tokenValues.add(entry.value);
  }

  const source = requirePlainObject(registry.source, 'COPY_SOURCE_SURFACE_MISSING');
  assert(source.path === 'web/src/pages/LoginPage.tsx', 'COPY_SOURCE_PATH_INVALID');
  assert(source.classification === CLASSIFICATION_PRODUCTION, 'COPY_SOURCE_SCOPE_INVALID');
  assert(Array.isArray(source.entries) && source.entries.length > 0, 'COPY_SOURCE_ENTRIES_MISSING');
  for (const entry of source.entries) {
    requirePlainObject(entry, 'COPY_SOURCE_ENTRY_INVALID');
    assert(SOURCE_KINDS.has(entry.kind), 'COPY_SOURCE_KIND_INVALID', `kind=${entry.kind}`);
    assert(entry.classification === CLASSIFICATION_PRODUCTION, 'COPY_SOURCE_ENTRY_SCOPE_INVALID');
    entry.value = validateTextPolicy(entry.value, tokenValues, 'COPY_SOURCE_VALUE');
  }
  assert(new Set(source.entries.map(entryKey)).size === source.entries.length, 'COPY_SOURCE_ENTRY_DUPLICATE');

  const runtime = requirePlainObject(registry.runtime, 'COPY_RUNTIME_SURFACE_MISSING');
  assert(runtime.route === '/login', 'COPY_RUNTIME_ROUTE_INVALID');
  assert(runtime.classification === CLASSIFICATION_PRODUCTION, 'COPY_RUNTIME_SCOPE_INVALID');
  assert(runtime.rootSelector === 'shared-auth-login-page', 'COPY_RUNTIME_ROOT_SELECTOR_INVALID');
  assert(Array.isArray(runtime.requiredSelectors) && runtime.requiredSelectors.length > 0, 'COPY_RUNTIME_SELECTORS_MISSING');
  assert(new Set(runtime.requiredSelectors).size === runtime.requiredSelectors.length, 'COPY_RUNTIME_SELECTOR_DUPLICATE');
  for (const selector of runtime.requiredSelectors) {
    assert(typeof selector === 'string' && /^shared-auth-login-[a-z-]+$/.test(selector), 'COPY_RUNTIME_SELECTOR_INVALID');
  }
  assert(runtime.errorSelector === 'shared-auth-login-error', 'COPY_RUNTIME_ERROR_SELECTOR_INVALID');
  assert(runtime.requiredSelectors.includes(runtime.errorSelector), 'COPY_RUNTIME_ERROR_SELECTOR_MISSING');
  const responder = requirePlainObject(runtime.responder, 'COPY_RUNTIME_RESPONDER_MISSING');
  assert(responder.status === 401, 'COPY_RUNTIME_RESPONDER_STATUS_INVALID');
  assert(responder.marker?.name === 'X-YCS-Scenario' && responder.marker?.value === 'LOGIN-SMOKE-V1', 'COPY_RUNTIME_RESPONDER_MARKER_INVALID');
  assert(responder.body?.code === 'AUTH_INVALID_CREDENTIALS' && responder.body?.message === '用户名或密码错误', 'COPY_RUNTIME_RESPONDER_BODY_INVALID');

  const exportContract = requirePlainObject(registry.export, 'COPY_EXPORT_SURFACE_MISSING');
  assert(exportContract.contractId === 'ZH-CN-EXPORT-FIXTURE-V1', 'COPY_EXPORT_ID_INVALID');
  assert(exportContract.classification === CLASSIFICATION_FIXTURE, 'COPY_EXPORT_SCOPE_INVALID');
  assert(exportContract.productAcceptance === false, 'COPY_EXPORT_PRODUCT_ACCEPTANCE_FORBIDDEN');
  assert(Array.isArray(exportContract.headers) && exportContract.headers.length > 0, 'COPY_EXPORT_HEADERS_MISSING');
  for (const header of exportContract.headers) {
    requirePlainObject(header, 'COPY_EXPORT_HEADER_INVALID');
    assert(header.classification === CLASSIFICATION_FIXTURE, 'COPY_EXPORT_HEADER_SCOPE_INVALID');
    header.value = validateTextPolicy(header.value, tokenValues, 'COPY_EXPORT_HEADER');
  }
  assert(new Set(exportContract.headers.map((entry) => entry.value)).size === exportContract.headers.length, 'COPY_EXPORT_HEADER_DUPLICATE');
  assert(Array.isArray(exportContract.syntheticRows) && exportContract.syntheticRows.length > 0, 'COPY_EXPORT_ROWS_MISSING');
  for (const row of exportContract.syntheticRows) {
    assert(Array.isArray(row) && row.every((cell) => typeof cell === 'string'), 'COPY_EXPORT_ROW_INVALID');
  }
  return registry;
}

function literalAttributeValue(attribute) {
  if (!attribute.initializer) return null;
  if (ts.isStringLiteral(attribute.initializer)) return attribute.initializer.text;
  if (ts.isJsxExpression(attribute.initializer) && attribute.initializer.expression && ts.isStringLiteralLike(attribute.initializer.expression)) {
    return attribute.initializer.expression.text;
  }
  return null;
}

export function extractSourceEntries(sourceText, sourcePath = 'web/src/pages/LoginPage.tsx') {
  const sourceFile = ts.createSourceFile(sourcePath, sourceText, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const parseDiagnostics = sourceFile.parseDiagnostics ?? [];
  assert(parseDiagnostics.length === 0, 'COPY_SOURCE_PARSE_FAILED', `count=${parseDiagnostics.length}`);
  const entries = [];
  function visit(node) {
    if (ts.isJsxText(node)) {
      const value = normalizeVisibleText(node.text);
      if (value) entries.push({ kind: 'jsx-text', value });
    } else if (ts.isJsxAttribute(node)) {
      const kind = node.name.getText(sourceFile);
      if (ATTRIBUTE_KINDS.has(kind)) {
        const value = literalAttributeValue(node);
        assert(value !== null, 'COPY_SOURCE_ATTRIBUTE_DYNAMIC', `kind=${kind}`);
        entries.push({ kind, value: normalizeVisibleText(value) });
      }
    } else if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'setError') {
      const argument = node.arguments[0];
      if (argument && ts.isStringLiteralLike(argument)) entries.push({ kind: 'error', value: normalizeVisibleText(argument.text) });
    }
    ts.forEachChild(node, visit);
  }
  visit(sourceFile);
  return entries;
}

export function parseCsv(csvText) {
  assert(typeof csvText === 'string' && csvText.length > 0, 'COPY_CSV_EMPTY');
  const rows = [];
  let row = [];
  let field = '';
  let inQuotes = false;
  let afterQuote = false;
  for (let index = 0; index < csvText.length; index += 1) {
    const character = csvText[index];
    if (inQuotes) {
      if (character === '"') {
        if (csvText[index + 1] === '"') {
          field += '"';
          index += 1;
        } else {
          inQuotes = false;
          afterQuote = true;
        }
      } else {
        field += character;
      }
      continue;
    }
    if (afterQuote) {
      if (character === ',') {
        row.push(field);
        field = '';
        afterQuote = false;
      } else if (character === '\n' || character === '\r') {
        row.push(field);
        rows.push(row);
        row = [];
        field = '';
        afterQuote = false;
        if (character === '\r' && csvText[index + 1] === '\n') index += 1;
      } else {
        fail('COPY_CSV_CHARACTER_AFTER_QUOTE', `index=${index}`);
      }
      continue;
    }
    if (character === '"') {
      assert(field.length === 0, 'COPY_CSV_QUOTE_IN_UNQUOTED_FIELD', `index=${index}`);
      inQuotes = true;
    } else if (character === ',') {
      row.push(field);
      field = '';
    } else if (character === '\n' || character === '\r') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
      if (character === '\r' && csvText[index + 1] === '\n') index += 1;
    } else {
      field += character;
    }
  }
  assert(!inQuotes, 'COPY_CSV_UNCLOSED_QUOTE');
  if (afterQuote || field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  assert(rows.length >= 2, 'COPY_CSV_ROWS_MISSING');
  const width = rows[0].length;
  assert(width > 0, 'COPY_CSV_HEADERS_MISSING');
  for (let index = 0; index < rows.length; index += 1) {
    assert(rows[index].length === width, 'COPY_CSV_COLUMN_COUNT_INVALID', `row=${index + 1} expected=${width} actual=${rows[index].length}`);
  }
  return rows;
}

export function validateRuntimeObservation(registryInput, observation) {
  const registry = validateRegistryDocument(structuredClone(registryInput));
  requirePlainObject(observation, 'COPY_RUNTIME_OBSERVATION_MISSING');
  assert(observation.route === registry.runtime.route, 'COPY_RUNTIME_ROUTE_MISMATCH');
  assert(Array.isArray(observation.selectors), 'COPY_RUNTIME_SELECTORS_NOT_OBSERVED');
  for (const selector of registry.runtime.requiredSelectors) {
    assert(observation.selectors.includes(selector), 'COPY_RUNTIME_SELECTOR_MISSING', `selector=${selector}`);
  }
  assert(Array.isArray(observation.entries), 'COPY_RUNTIME_ENTRIES_NOT_OBSERVED');
  const expectedEntries = registry.source.entries.map((entry) => ({
    kind: entry.kind === 'jsx-text' || entry.kind === 'error' ? 'text' : entry.kind,
    value: entry.value,
  }));
  const actualEntries = observation.entries.map((entry) => ({
    kind: entry.kind,
    value: normalizeVisibleText(entry.value),
  }));
  const technicalTokens = new Set(registry.technicalTokens.map((entry) => entry.value));
  for (const entry of actualEntries) validateTextPolicy(entry.value, technicalTokens, 'COPY_RUNTIME_VALUE');
  const expectedKeys = sortedKeys(expectedEntries);
  const actualKeys = sortedKeys(actualEntries);
  if (!arraysEqual(actualKeys, expectedKeys)) {
    const expectedCounts = new Map();
    for (const key of expectedKeys) expectedCounts.set(key, (expectedCounts.get(key) ?? 0) + 1);
    for (const key of actualKeys) {
      const remaining = expectedCounts.get(key) ?? 0;
      if (remaining === 0) fail('COPY_RUNTIME_ENTRY_UNREGISTERED', `entry=${JSON.stringify(key)}`);
      expectedCounts.set(key, remaining - 1);
    }
    fail('COPY_RUNTIME_ENTRY_MISSING');
  }
  assert(observation.errorSelector === registry.runtime.errorSelector, 'COPY_RUNTIME_ERROR_SELECTOR_MISSING');
  assert(observation.responder?.status === registry.runtime.responder.status, 'COPY_RUNTIME_RESPONDER_STATUS_MISMATCH');
  assert(observation.responder?.marker?.name === registry.runtime.responder.marker.name
    && observation.responder?.marker?.value === registry.runtime.responder.marker.value, 'COPY_RUNTIME_RESPONDER_MARKER_MISSING');
  assert(JSON.stringify(observation.responder?.body) === JSON.stringify(registry.runtime.responder.body), 'COPY_RUNTIME_RESPONDER_BODY_MISMATCH');
  return {
    contractId: registry.contractId,
    classification: CLASSIFICATION_FIXTURE,
    productAcceptance: false,
    route: observation.route,
    entryCount: actualEntries.length,
    selectorCount: observation.selectors.length,
  };
}

export async function validateCopyContract({ registryPath, exportFixturePath, sourcePath }) {
  const rawRegistry = JSON.parse(await readFile(registryPath, 'utf8'));
  const registry = validateRegistryDocument(rawRegistry);
  assert(resolve(sourcePath).endsWith(registry.source.path.replaceAll('/', String.raw`/`)), 'COPY_SOURCE_ARGUMENT_MISMATCH');
  const sourceText = await readFile(sourcePath, 'utf8');
  const sourceEntries = extractSourceEntries(sourceText, registry.source.path);
  const tokenValues = new Set(registry.technicalTokens.map((entry) => entry.value));
  for (const entry of sourceEntries) validateTextPolicy(entry.value, tokenValues, 'COPY_SOURCE_VALUE');
  const expectedSource = sortedKeys(registry.source.entries);
  const actualSource = sortedKeys(sourceEntries);
  if (!arraysEqual(actualSource, expectedSource)) {
    const expectedCounts = new Map();
    for (const key of expectedSource) expectedCounts.set(key, (expectedCounts.get(key) ?? 0) + 1);
    for (const key of actualSource) {
      const remaining = expectedCounts.get(key) ?? 0;
      if (remaining === 0) fail('COPY_SOURCE_ENTRY_UNREGISTERED', `entry=${JSON.stringify(key)}`);
      expectedCounts.set(key, remaining - 1);
    }
    fail('COPY_SOURCE_ENTRY_MISSING');
  }

  const csvRows = parseCsv(await readFile(exportFixturePath, 'utf8'));
  const expectedHeaders = registry.export.headers.map((entry) => entry.value);
  assert(arraysEqual(csvRows[0], expectedHeaders), 'COPY_EXPORT_HEADER_MISMATCH', `actual=${JSON.stringify(csvRows[0])}`);
  assert(JSON.stringify(csvRows.slice(1)) === JSON.stringify(registry.export.syntheticRows), 'COPY_EXPORT_ROWS_MISMATCH');
  return {
    contractId: registry.contractId,
    schemaVersion: registry.schemaVersion,
    locale: registry.locale,
    classification: CLASSIFICATION_FIXTURE,
    productAcceptance: false,
    productAcceptanceOwner: registry.acceptance.productAcceptanceOwner,
    checks: {
      source: { status: 'PASS', classification: CLASSIFICATION_PRODUCTION, entries: sourceEntries.length },
      export: { status: 'PASS', classification: CLASSIFICATION_FIXTURE, headers: expectedHeaders.length, rows: csvRows.length - 1 },
      policy: { status: 'PASS', exactTechnicalTokens: registry.technicalTokens.length },
    },
  };
}

function parseArguments(argv) {
  const options = {};
  const allowed = new Map([
    ['--registry', 'registryPath'],
    ['--export-fixture', 'exportFixturePath'],
    ['--source', 'sourcePath'],
  ]);
  for (let index = 0; index < argv.length; index += 2) {
    const key = allowed.get(argv[index]);
    const value = argv[index + 1];
    assert(key && value && !value.startsWith('--'), 'COPY_OPTION_INVALID', `argument=${argv[index] ?? '<missing>'}`);
    options[key] = resolve(value);
  }
  for (const key of allowed.values()) assert(options[key], 'COPY_OPTION_REQUIRED', `option=${key}`);
  return options;
}

async function main() {
  try {
    const result = await validateCopyContract(parseArguments(process.argv.slice(2)));
    console.log(`copy_zh_cn_contract=PASS contract_id=${result.contractId} scope=${result.classification} product_acceptance=${result.productAcceptance} source_entries=${result.checks.source.entries} export_headers=${result.checks.export.headers} export_rows=${result.checks.export.rows}`);
  } catch (error) {
    const diagnostic = error instanceof CopyContractError ? error.message : `COPY_INTERNAL_ERROR detail=${String(error?.message ?? error)}`;
    console.error(`copy_zh_cn_contract=BLOCKED diagnostic=${diagnostic.replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main();
