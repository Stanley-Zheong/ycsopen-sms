#!/usr/bin/env node

import { access, readFile, realpath } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const scriptPath = fileURLToPath(import.meta.url);
const scriptDir = path.dirname(scriptPath);
const webRoot = path.resolve(scriptDir, '..');

const options = parseArguments(process.argv.slice(2));
const diagnostics = [];

try {
  const manifest = await readJson(options.manifest, 'UI_MANIFEST_JSON_INVALID');
  const manifestSchema = await readJson(options.manifestSchema, 'UI_MANIFEST_SCHEMA_JSON_INVALID');
  const rowRegistry = await readJson(options.rowKeyRegistry, 'UI_ROW_KEY_REGISTRY_JSON_INVALID');
  const rowSchema = await readJson(options.rowKeySchema, 'UI_ROW_KEY_SCHEMA_JSON_INVALID');

  validateSchemaDocument(manifest, manifestSchema, 'UI_MANIFEST_SCHEMA_INVALID', diagnostics);
  validateSchemaDocument(rowRegistry, rowSchema, 'UI_ROW_KEY_SCHEMA_INVALID', diagnostics);
  if (diagnostics.length === 0) validateRowRegistryPolicy(rowRegistry, diagnostics);

  if (diagnostics.length === 0) {
    await validateRelations(manifest, rowRegistry, diagnostics);
  }

  if (diagnostics.length > 0) {
    fail(diagnostics);
  }

  const selectorCount = manifest.pages.reduce((count, page) => count + page.selectors.length, 0);
  console.log(
    `ui_drift=PASS schema_version=${manifest.schemaVersion} pages=${manifest.pages.length} selectors=${selectorCount} mode=${manifest.scope.mode}`,
  );
} catch (error) {
  if (error?.code === 'UI_DRIFT_VALIDATION_FAILED') {
    process.exitCode = 1;
  } else {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`ui_drift=BLOCKED errors=1\n- UI_VALIDATOR_INTERNAL_ERROR ${message}`);
    process.exitCode = 1;
  }
}

function parseArguments(argv) {
  const parsed = {
    manifest: null,
    routes: null,
    manifestSchema: null,
    rowKeyRegistry: null,
    rowKeySchema: null,
    checkStatic: false,
  };
  const valueOptions = new Map([
    ['--manifest', 'manifest'],
    ['--routes', 'routes'],
    ['--manifest-schema', 'manifestSchema'],
    ['--row-key-registry', 'rowKeyRegistry'],
    ['--row-key-schema', 'rowKeySchema'],
  ]);
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--check-static') {
      parsed.checkStatic = true;
      continue;
    }
    const key = valueOptions.get(argument);
    if (!key || !argv[index + 1] || argv[index + 1].startsWith('--')) {
      usage(`UI_OPTION_INVALID argument=${argument}`);
    }
    parsed[key] = path.resolve(argv[index + 1]);
    index += 1;
  }
  if (!parsed.manifest || !parsed.routes || !parsed.checkStatic) {
    usage('UI_OPTION_REQUIRED expected=--manifest,--routes,--check-static');
  }
  const manifestDirectory = path.dirname(parsed.manifest);
  parsed.manifestSchema ??= path.join(manifestDirectory, 'ui-manifest.schema.json');
  parsed.rowKeyRegistry ??= path.join(manifestDirectory, 'row-key-registry.json');
  parsed.rowKeySchema ??= path.join(manifestDirectory, 'row-key-registry.schema.json');
  return parsed;
}

function usage(message) {
  console.error(`ui_drift=BLOCKED errors=1\n- ${message}`);
  process.exit(2);
}

async function readJson(filePath, diagnosticId) {
  try {
    return JSON.parse(await readFile(filePath, 'utf8'));
  } catch (error) {
    throw new Error(`${diagnosticId} path=${filePath} detail=${error.message}`);
  }
}

function fail(errors) {
  console.error(`ui_drift=BLOCKED errors=${errors.length}`);
  for (const diagnostic of errors) console.error(`- ${diagnostic}`);
  const error = new Error('UI relation validation failed');
  error.code = 'UI_DRIFT_VALIDATION_FAILED';
  throw error;
}

function validateSchemaDocument(value, schema, diagnosticId, errors) {
  const localErrors = [];
  validateJsonSchema(value, schema, schema, '$', localErrors);
  for (const detail of localErrors) errors.push(`${diagnosticId} ${detail}`);
}

function validateJsonSchema(value, schema, rootSchema, valuePath, errors) {
  if (schema.$ref) {
    const target = resolveJsonPointer(rootSchema, schema.$ref);
    if (!target) {
      errors.push(`path=${valuePath} unresolved_ref=${schema.$ref}`);
      return;
    }
    validateJsonSchema(value, target, rootSchema, valuePath, errors);
    return;
  }
  if (schema.anyOf) {
    const branches = schema.anyOf.map((candidate) => {
      const branchErrors = [];
      validateJsonSchema(value, candidate, rootSchema, valuePath, branchErrors);
      return branchErrors;
    });
    if (!branches.some((branch) => branch.length === 0)) {
      errors.push(`path=${valuePath} any_of_mismatch`);
    }
    return;
  }
  if (Object.hasOwn(schema, 'const') && !deepEqual(value, schema.const)) {
    errors.push(`path=${valuePath} expected_const=${JSON.stringify(schema.const)}`);
  }
  if (schema.enum && !schema.enum.some((candidate) => deepEqual(value, candidate))) {
    errors.push(`path=${valuePath} expected_enum=${schema.enum.join(',')}`);
  }
  if (schema.type && !matchesType(value, schema.type)) {
    errors.push(`path=${valuePath} expected_type=${schema.type}`);
    return;
  }
  if (typeof value === 'string') {
    if (schema.minLength && value.length < schema.minLength) errors.push(`path=${valuePath} min_length=${schema.minLength}`);
    if (schema.pattern && !new RegExp(schema.pattern).test(value)) errors.push(`path=${valuePath} pattern=${schema.pattern}`);
  }
  if (Array.isArray(value)) {
    if (schema.minItems && value.length < schema.minItems) errors.push(`path=${valuePath} min_items=${schema.minItems}`);
    if (schema.uniqueItems) {
      const keys = value.map((entry) => JSON.stringify(entry));
      if (new Set(keys).size !== keys.length) errors.push(`path=${valuePath} duplicate_items`);
    }
    if (schema.items) {
      value.forEach((entry, index) => validateJsonSchema(entry, schema.items, rootSchema, `${valuePath}[${index}]`, errors));
    }
  }
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const required of schema.required ?? []) {
      if (!Object.hasOwn(value, required)) errors.push(`path=${valuePath}.${required} required`);
    }
    if (schema.additionalProperties === false) {
      for (const key of Object.keys(value)) {
        if (!Object.hasOwn(schema.properties ?? {}, key) && key !== '$defs') errors.push(`path=${valuePath}.${key} additional_property`);
      }
    }
    for (const [key, childSchema] of Object.entries(schema.properties ?? {})) {
      if (Object.hasOwn(value, key)) validateJsonSchema(value[key], childSchema, rootSchema, `${valuePath}.${key}`, errors);
    }
  }
}

function resolveJsonPointer(root, reference) {
  if (!reference.startsWith('#/')) return null;
  return reference.slice(2).split('/').reduce((current, token) => current?.[token.replaceAll('~1', '/').replaceAll('~0', '~')], root);
}

function matchesType(value, type) {
  if (type === 'null') return value === null;
  if (type === 'array') return Array.isArray(value);
  if (type === 'object') return value !== null && typeof value === 'object' && !Array.isArray(value);
  return typeof value === type;
}

function deepEqual(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function validateRowRegistryPolicy(registry, errors) {
  const allowedIds = registry.allowedClasses.map((entry) => entry.id);
  const deniedIds = registry.deniedClasses.map((entry) => entry.id);
  reportDuplicates(allowedIds, 'UI_ROW_KEY_CLASS_DUPLICATE_ALLOWED', errors);
  reportDuplicates(deniedIds, 'UI_ROW_KEY_CLASS_DUPLICATE_DENIED', errors);
  for (const id of allowedIds.filter((value) => deniedIds.includes(value))) {
    errors.push(`UI_ROW_KEY_CLASS_CONTRADICTORY class=${id}`);
  }
  const requiredDenied = [
    'phone-number', 'database-id', 'localized-label', 'mutable-name',
    'mutable-value', 'credential', 'message-content',
  ];
  for (const id of requiredDenied.filter((value) => !deniedIds.includes(value))) {
    errors.push(`UI_ROW_KEY_DENY_CLASS_MISSING class=${id}`);
  }
}

async function validateRelations(manifest, rowRegistry, errors) {
  // The manifest declares an explicit route prefix boundary so unfinished legacy
  // placeholders are not silently promoted into verified production pages.
  const routeSource = await readFile(options.routes, 'utf8');
  const sourceRoutes = await extractRoutes(routeSource, options.routes, errors);
  const scopedSourceRoutes = sourceRoutes.filter(
    (relation) => relation.kind === 'page' && inScope(relation.route, manifest.scope.routePrefixes),
  );
  const scopedSourceRedirects = sourceRoutes.filter(
    (relation) => relation.kind === 'redirect'
      && (inScope(relation.route, manifest.scope.routePrefixes) || inScope(relation.target, manifest.scope.routePrefixes)),
  );
  const scopedManifestRedirects = manifest.redirects.filter(
    (relation) => inScope(relation.from, manifest.scope.routePrefixes) || inScope(relation.to, manifest.scope.routePrefixes),
  );
  const manifestPages = manifest.pages.filter((page) => inScope(page.route, manifest.scope.routePrefixes));

  reportDuplicates(manifest.pages.map((page) => page.pageId), 'UI_PAGE_DUPLICATE', errors);
  reportDuplicates(manifestPages.map((page) => page.route), 'UI_ROUTE_DUPLICATE_MANIFEST', errors);
  reportDuplicates(scopedSourceRoutes.map((route) => route.route), 'UI_ROUTE_DUPLICATE_SOURCE', errors);
  reportDuplicates(scopedManifestRedirects.map(redirectKey), 'UI_REDIRECT_DUPLICATE_MANIFEST', errors);
  reportDuplicates(scopedSourceRedirects.map((redirect) => redirectKey({ from: redirect.route, to: redirect.target, index: redirect.index })), 'UI_REDIRECT_DUPLICATE_SOURCE', errors);

  const sourceByRoute = new Map(scopedSourceRoutes.map((route) => [route.route, route]));
  const manifestByRoute = new Map(manifestPages.map((page) => [page.route, page]));
  for (const page of manifestPages) {
    const actual = sourceByRoute.get(page.route);
    if (!actual) {
      errors.push(`UI_ROUTE_MISSING_FROM_SOURCE route=${page.route}`);
      errors.push(`UI_PAGE_STALE_IN_MANIFEST page=${page.pageId} route=${page.route}`);
      continue;
    }
    if (actual.kind !== 'page') {
      errors.push(`UI_ROUTE_KIND_MISMATCH route=${page.route} expected=page actual=${actual.kind} location=${actual.location}`);
      continue;
    }
    if (actual.component.symbol !== page.component.symbol || !sameDeclaredPath(actual.component.source, page.component.source)) {
      errors.push(
        `UI_COMPONENT_MISSING_FROM_SOURCE route=${page.route} expected=${page.component.symbol}@${page.component.source} actual=${actual.component.symbol}@${actual.component.source} location=${actual.location}`,
      );
      errors.push(
        `UI_COMPONENT_STALE_IN_SOURCE route=${page.route} source=${actual.component.symbol}@${actual.component.source} location=${actual.location}`,
      );
    }
  }
  for (const actual of scopedSourceRoutes) {
    if (!manifestByRoute.has(actual.route)) {
      errors.push(`UI_ROUTE_STALE_IN_SOURCE route=${actual.route} location=${actual.location}`);
      errors.push(`UI_PAGE_MISSING_FROM_MANIFEST route=${actual.route} component=${actual.component.symbol} location=${actual.location}`);
    }
  }

  const sourceRedirectKeys = new Set(scopedSourceRedirects.map((redirect) => redirectKey({
    from: redirect.route, to: redirect.target, index: redirect.index,
  })));
  const manifestRedirectKeys = new Set(scopedManifestRedirects.map(redirectKey));
  for (const redirect of scopedManifestRedirects) {
    if (!sourceRedirectKeys.has(redirectKey(redirect))) {
      errors.push(`UI_REDIRECT_MISSING_FROM_SOURCE from=${redirect.from} to=${redirect.to} index=${redirect.index}`);
    }
  }
  for (const redirect of scopedSourceRedirects) {
    const normalized = { from: redirect.route, to: redirect.target, index: redirect.index };
    if (!manifestRedirectKeys.has(redirectKey(normalized))) {
      errors.push(`UI_REDIRECT_STALE_IN_SOURCE from=${normalized.from} to=${normalized.to} index=${normalized.index} location=${redirect.location}`);
    }
  }

  const allActualSelectors = [];
  for (const page of manifestPages) {
    const actualRoute = sourceByRoute.get(page.route);
    if (!actualRoute || actualRoute.kind !== 'page') continue;
    const declaredComponentPath = await resolveDeclaredPath(page.component.source, options.manifest, errors, 'component');
    if (!declaredComponentPath) {
      errors.push(`UI_COMPONENT_SOURCE_MISSING route=${page.route} source=${page.component.source}`);
      continue;
    }
    const componentSource = await readFile(declaredComponentPath, 'utf8');
    const actualSelectors = extractJsxSelectors(componentSource, declaredComponentPath, errors);
    allActualSelectors.push(...actualSelectors.map((selector) => ({ ...selector, route: page.route })));
    reportDuplicates(actualSelectors.map((selector) => selector.dataTestId), `UI_SELECTOR_DUPLICATE_SOURCE route=${page.route}`, errors);

    const expectedById = new Map(page.selectors.map((selector) => [selector.dataTestId, selector]));
    const actualById = new Map(actualSelectors.map((selector) => [selector.dataTestId, selector]));
    reportDuplicates(page.selectors.map((selector) => selector.dataTestId), `UI_SELECTOR_DUPLICATE_MANIFEST route=${page.route}`, errors);
    if (page.relationKind === 'verified-selector-closure' && page.selectors.length === 0) {
      errors.push(`UI_ROUTE_SELECTOR_CLOSURE_MISSING route=${page.route}`);
    }
    for (const selector of page.selectors) {
      const actual = actualById.get(selector.dataTestId);
      if (!actual) {
        errors.push(`UI_SELECTOR_MISSING_FROM_DOM route=${page.route} selector=${selector.dataTestId}`);
        continue;
      }
      if (!sameDeclaredPath(declaredComponentPath, selector.source)) {
        errors.push(`UI_SELECTOR_SOURCE_MISMATCH route=${page.route} selector=${selector.dataTestId} source=${selector.source}`);
      }
      validateRowContract(selector, actual, rowRegistry, page.route, errors);
    }
    for (const actual of actualSelectors) {
      if (!expectedById.has(actual.dataTestId)) {
        errors.push(`UI_SELECTOR_STALE_IN_DOM route=${page.route} selector=${actual.dataTestId} location=${actual.location}`);
      }
    }
  }

  const manifestSelectors = manifestPages.flatMap((page) => page.selectors.map((selector) => ({ ...selector, route: page.route })));
  reportDuplicates(manifestSelectors.map((selector) => relationKey(selector.route, selector.dataTestId)), 'UI_RELATION_DUPLICATE_MANIFEST', errors);
  reportDuplicates(manifestSelectors.map((selector) => selector.caseId), 'UI_CASE_ID_DUPLICATE_MANIFEST', errors);
  reportDuplicates(manifestSelectors.map((selector) => selector.playwrightId), 'UI_PLAYWRIGHT_ID_DUPLICATE_MANIFEST', errors);

  const playwrightRelations = [];
  for (const declaredSource of manifest.playwrightSources) {
    const sourcePath = await resolveDeclaredPath(declaredSource, options.manifest, errors, 'playwright');
    if (!sourcePath) {
      errors.push(`UI_PLAYWRIGHT_SOURCE_MISSING source=${declaredSource}`);
      continue;
    }
    const source = await readFile(sourcePath, 'utf8');
    playwrightRelations.push(...extractPlaywrightRelations(source, sourcePath, errors));
  }
  comparePlaywrightRelations(manifestSelectors, playwrightRelations, manifest.scope.routePrefixes, errors);
}

async function extractRoutes(source, sourcePath, errors) {
  const sourceFile = parseTypeScriptSource(sourcePath, source, ts.ScriptKind.TSX, errors);
  const importMap = extractImports(sourceFile, sourcePath);
  let routerArray = null;
  const visit = (node) => {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'createBrowserRouter') {
      const [argument] = node.arguments;
      if (ts.isArrayLiteralExpression(argument)) routerArray = argument;
      else unsupported(argument ?? node, sourceFile, sourcePath, 'router-array', errors);
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  if (!routerArray) {
    errors.push(`UI_ROUTER_CALL_MISSING path=${displayPath(sourcePath)}`);
    return [];
  }
  const relations = [];
  walkRouteArray(routerArray, '/', sourceFile, sourcePath, importMap, relations, errors);
  return relations;
}

function extractImports(sourceFile, sourcePath) {
  const imports = new Map();
  for (const statement of sourceFile.statements) {
    if (!ts.isImportDeclaration(statement) || !ts.isStringLiteral(statement.moduleSpecifier)) continue;
    const clause = statement.importClause;
    if (!clause) continue;
    const modulePath = resolveImportSource(statement.moduleSpecifier.text, sourcePath);
    if (clause.name) imports.set(clause.name.text, modulePath);
    if (clause.namedBindings && ts.isNamedImports(clause.namedBindings)) {
      for (const element of clause.namedBindings.elements) imports.set(element.name.text, modulePath);
    }
  }
  return imports;
}

function walkRouteArray(arrayNode, parentRoute, sourceFile, sourcePath, importMap, relations, errors) {
  for (const element of arrayNode.elements) {
    if (!ts.isObjectLiteralExpression(element)) {
      unsupported(element, sourceFile, sourcePath, 'route-object', errors);
      continue;
    }
    const properties = objectProperties(element, sourceFile, sourcePath, errors);
    const indexNode = properties.get('index');
    const isIndex = indexNode?.kind === ts.SyntaxKind.TrueKeyword;
    const pathNode = properties.get('path');
    let route = parentRoute;
    if (!isIndex) {
      if (!pathNode) {
        unsupported(element, sourceFile, sourcePath, 'route-path-missing', errors);
        continue;
      }
      const literalPath = literalString(pathNode, sourceFile, sourcePath, 'route-path', errors);
      if (literalPath === null) continue;
      route = normalizeRoute(parentRoute, literalPath);
    }
    const elementNode = properties.get('element');
    const component = jsxComponent(elementNode, sourceFile, sourcePath, importMap, errors);
    const childrenNode = properties.get('children');
    const location = sourceLocation(element, sourceFile, sourcePath);

    if (component?.symbol === 'Navigate') {
      const target = jsxAttributeLiteral(elementNode, 'to', sourceFile, sourcePath, errors);
      if (target !== null) {
        const targetBase = isIndex ? route : routeParent(route);
        relations.push({
          route,
          kind: 'redirect',
          target: normalizeRoute(targetBase, target),
          index: isIndex,
          component,
          location,
        });
      }
    } else if (!childrenNode) {
      if (component) relations.push({ route, kind: 'page', component, location });
    }

    if (childrenNode) {
      if (ts.isArrayLiteralExpression(childrenNode)) {
        walkRouteArray(childrenNode, route, sourceFile, sourcePath, importMap, relations, errors);
      } else {
        unsupported(childrenNode, sourceFile, sourcePath, 'route-children', errors);
      }
    }
  }
}

function objectProperties(node, sourceFile, sourcePath, errors) {
  const result = new Map();
  for (const property of node.properties) {
    if (!ts.isPropertyAssignment(property)) {
      unsupported(property, sourceFile, sourcePath, 'route-property', errors);
      continue;
    }
    const name = propertyName(property.name);
    if (name) result.set(name, property.initializer);
    else unsupported(property.name, sourceFile, sourcePath, 'route-property-name', errors);
  }
  return result;
}

function propertyName(node) {
  if (ts.isIdentifier(node) || ts.isStringLiteral(node)) return node.text;
  return null;
}

function jsxComponent(node, sourceFile, sourcePath, importMap, errors) {
  if (!node) return null;
  if (!ts.isJsxSelfClosingElement(node) && !ts.isJsxElement(node)) {
    unsupported(node, sourceFile, sourcePath, 'route-element', errors);
    return null;
  }
  const opening = ts.isJsxElement(node) ? node.openingElement : node;
  if (!ts.isIdentifier(opening.tagName)) {
    unsupported(opening.tagName, sourceFile, sourcePath, 'route-component', errors);
    return null;
  }
  const symbol = opening.tagName.text;
  return { symbol, source: importMap.get(symbol) ?? displayPath(sourcePath) };
}

function jsxAttributeLiteral(node, attributeName, sourceFile, sourcePath, errors) {
  const opening = ts.isJsxElement(node) ? node.openingElement : node;
  const attribute = opening.attributes.properties.find((candidate) => ts.isJsxAttribute(candidate) && candidate.name.text === attributeName);
  if (!attribute?.initializer) {
    unsupported(opening, sourceFile, sourcePath, `jsx-${attributeName}`, errors);
    return null;
  }
  return literalString(attribute.initializer, sourceFile, sourcePath, `jsx-${attributeName}`, errors);
}

function literalString(node, sourceFile, sourcePath, kind, errors) {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) return node.text;
  if (ts.isJsxExpression(node) && node.expression && (ts.isStringLiteral(node.expression) || ts.isNoSubstitutionTemplateLiteral(node.expression))) {
    return node.expression.text;
  }
  unsupported(node, sourceFile, sourcePath, kind, errors);
  return null;
}

function normalizeRoute(parent, child) {
  if (child.startsWith('/')) return normalizeSlashes(child);
  return normalizeSlashes(`${parent === '/' ? '' : parent}/${child}`);
}

function normalizeSlashes(route) {
  const normalized = `/${route.split('/').filter(Boolean).join('/')}`;
  return normalized === '' ? '/' : normalized;
}

function routeParent(route) {
  const segments = route.split('/').filter(Boolean);
  segments.pop();
  return segments.length ? `/${segments.join('/')}` : '/';
}

function resolveImportSource(specifier, importerPath) {
  if (specifier.startsWith('@/')) return displayPath(path.join(webRoot, 'src', specifier.slice(2)));
  if (specifier.startsWith('.')) return displayPath(path.resolve(path.dirname(importerPath), specifier));
  return specifier;
}

function extractJsxSelectors(source, sourcePath, errors) {
  const sourceFile = parseTypeScriptSource(sourcePath, source, ts.ScriptKind.TSX, errors);
  const selectors = [];
  const visit = (node) => {
    if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
      for (const property of node.attributes.properties) {
        if (ts.isJsxSpreadAttribute(property)) unsupported(property, sourceFile, sourcePath, 'jsx-spread-attribute', errors);
      }
      const testIdAttribute = findJsxAttribute(node, 'data-testid');
      if (testIdAttribute) {
        const dataTestId = jsxStaticAttribute(testIdAttribute, sourceFile, sourcePath, 'data-testid', errors);
        if (dataTestId !== null) {
          const rowKeyAttribute = findJsxAttribute(node, 'data-row-key');
          selectors.push({
            dataTestId,
            hasRowKey: Boolean(rowKeyAttribute),
            repeatedInSource: isWithinMapCallback(node),
            rowKeyExpression: rowKeyAttribute
              ? jsxRowKeyExpression(rowKeyAttribute, sourceFile, sourcePath, errors)
              : null,
            location: sourceLocation(testIdAttribute, sourceFile, sourcePath),
          });
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return selectors;
}

function findJsxAttribute(node, name) {
  return node.attributes.properties.find((property) => ts.isJsxAttribute(property) && property.name.text === name);
}

function jsxStaticAttribute(attribute, sourceFile, sourcePath, kind, errors) {
  if (!attribute.initializer) {
    unsupported(attribute, sourceFile, sourcePath, kind, errors);
    return null;
  }
  if (ts.isStringLiteral(attribute.initializer)) return attribute.initializer.text;
  if (ts.isJsxExpression(attribute.initializer) && attribute.initializer.expression) {
    const expression = attribute.initializer.expression;
    if (ts.isStringLiteral(expression) || ts.isNoSubstitutionTemplateLiteral(expression)) return expression.text;
  }
  unsupported(attribute.initializer, sourceFile, sourcePath, kind, errors);
  return null;
}

function jsxRowKeyExpression(attribute, sourceFile, sourcePath, errors) {
  const initializer = attribute.initializer;
  if (
    initializer
    && ts.isJsxExpression(initializer)
    && initializer.expression
    && ts.isPropertyAccessExpression(initializer.expression)
    && ts.isIdentifier(initializer.expression.expression)
    && ts.isIdentifier(initializer.expression.name)
  ) {
    return `${initializer.expression.expression.text}.${initializer.expression.name.text}`;
  }
  unsupported(initializer ?? attribute, sourceFile, sourcePath, 'data-row-key', errors);
  return null;
}

function isWithinMapCallback(node) {
  let current = node;
  while (current?.parent) {
    if (ts.isArrowFunction(current) || ts.isFunctionExpression(current)) {
      const call = current.parent;
      if (
        ts.isCallExpression(call)
        && call.arguments.includes(current)
        && ts.isPropertyAccessExpression(call.expression)
        && call.expression.name.text === 'map'
      ) return true;
    }
    current = current.parent;
  }
  return false;
}

function validateRowContract(selector, actual, registry, route, errors) {
  // Row identity is a separate relation: the semantic selector stays constant,
  // while the reviewed property expression supplies a synthetic immutable key.
  if (selector.repeated && !actual.repeatedInSource) {
    errors.push(`UI_ROW_NOT_REPEATED_SOURCE route=${route} selector=${selector.dataTestId}`);
  } else if (!selector.repeated && actual.repeatedInSource) {
    errors.push(`UI_ROW_REPETITION_MISMATCH route=${route} selector=${selector.dataTestId} expected=false actual=true`);
  }
  if (selector.repeated && !selector.rowContract) {
    errors.push(`UI_ROW_CONTRACT_MISSING route=${route} selector=${selector.dataTestId}`);
    return;
  }
  if (!selector.repeated && selector.rowContract) {
    errors.push(`UI_ROW_CONTRACT_UNEXPECTED route=${route} selector=${selector.dataTestId}`);
  }
  if (!selector.rowContract) return;
  const allowed = registry.allowedClasses.find((entry) => entry.id === selector.rowContract.keyClass);
  const denied = registry.deniedClasses.find((entry) => entry.id === selector.rowContract.keyClass);
  if (denied) {
    errors.push(`UI_ROW_KEY_CLASS_FORBIDDEN route=${route} selector=${selector.dataTestId} class=${denied.id}`);
  } else if (!allowed) {
    errors.push(`UI_ROW_KEY_CLASS_UNREVIEWED route=${route} selector=${selector.dataTestId} class=${selector.rowContract.keyClass}`);
  }
  if (selector.rowContract.keyAttribute !== 'data-row-key' || !actual.hasRowKey) {
    errors.push(`UI_ROW_KEY_ATTRIBUTE_MISSING route=${route} selector=${selector.dataTestId} attribute=data-row-key`);
  }
  if (actual.hasRowKey && actual.rowKeyExpression !== selector.rowContract.keyExpression) {
    errors.push(
      `UI_ROW_KEY_EXPRESSION_MISMATCH route=${route} selector=${selector.dataTestId} expected=${selector.rowContract.keyExpression} actual=${actual.rowKeyExpression ?? '-'}`,
    );
  }
  if (actual.rowKeyExpression) {
    const property = actual.rowKeyExpression.split('.').at(-1);
    if (!allowed?.allowedProperties.includes(property)) {
      errors.push(`UI_ROW_KEY_PROPERTY_FORBIDDEN route=${route} selector=${selector.dataTestId} property=${property}`);
    }
  }
}

function extractPlaywrightRelations(source, sourcePath, errors) {
  const sourceFile = parseTypeScriptSource(sourcePath, source, ts.ScriptKind.TS, errors);
  const relations = [];
  const visit = (node) => {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && ['test', 'it'].includes(node.expression.text)) {
      const title = literalString(node.arguments[0], sourceFile, sourcePath, 'playwright-title', errors);
      const callback = node.arguments[1];
      if (title === null || (!ts.isArrowFunction(callback) && !ts.isFunctionExpression(callback))) {
        if (callback) unsupported(callback, sourceFile, sourcePath, 'playwright-callback', errors);
        return;
      }
      const block = extractPlaywrightBlock(callback.body, title, sourceFile, sourcePath, errors);
      relations.push(...block);
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return relations;
}

function extractPlaywrightBlock(body, title, sourceFile, sourcePath, errors) {
  const routes = [];
  const locators = [];
  const visit = (node) => {
    if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)) {
      const method = node.expression.name.text;
      if (method === 'goto' && isPageExpression(node.expression.expression)) {
        const route = literalString(node.arguments[0], sourceFile, sourcePath, 'playwright-goto', errors);
        if (route !== null) routes.push({ route, location: sourceLocation(node, sourceFile, sourcePath) });
      }
      if (method === 'getByTestId') {
        const selector = literalString(node.arguments[0], sourceFile, sourcePath, 'playwright-selector', errors);
        if (selector !== null) {
          locators.push({ selector, active: locatorIsActioned(node), location: sourceLocation(node, sourceFile, sourcePath) });
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(body);
  if (routes.length > 1) errors.push(`UI_PLAYWRIGHT_ROUTE_AMBIGUOUS location=${sourceLocation(body, sourceFile, sourcePath)}`);
  const route = routes[0]?.route ?? null;
  const metadata = {
    obligationId: exactMetadata(title, /OBL-[A-Z0-9-]+/g),
    caseId: exactMetadata(title, /CASE-[A-Z0-9-]+/g),
    playwrightId: exactMetadata(title, /PW-[A-Z0-9-]+/g),
  };
  return locators.map((locator) => ({ ...locator, route, title, ...metadata }));
}

function exactMetadata(title, pattern) {
  const matches = title.match(pattern) ?? [];
  return matches.length === 1 ? matches[0] : null;
}

function isPageExpression(node) {
  return ts.isIdentifier(node) && node.text === 'page';
}

function locatorIsActioned(locatorCall) {
  const actions = new Set(['click', 'fill', 'check', 'uncheck', 'selectOption', 'press', 'hover', 'focus']);
  const assertions = new Set([
    'toBeVisible', 'toBeHidden', 'toHaveText', 'toContainText', 'toBeEnabled',
    'toBeDisabled', 'toHaveValue', 'toHaveAttribute', 'toHaveCount', 'toBeChecked',
  ]);
  const property = locatorCall.parent;
  const actionCall = ts.isPropertyAccessExpression(property) ? property.parent : null;
  if (actionCall && ts.isCallExpression(actionCall) && actions.has(property.name.text) && ts.isAwaitExpression(actionCall.parent)) return true;

  const expectCall = locatorCall.parent;
  if (expectCall && ts.isCallExpression(expectCall) && ts.isIdentifier(expectCall.expression) && expectCall.expression.text === 'expect') {
    const assertionProperty = expectCall.parent;
    const assertionCall = ts.isPropertyAccessExpression(assertionProperty) ? assertionProperty.parent : null;
    return Boolean(
      assertionCall && ts.isCallExpression(assertionCall)
      && assertions.has(assertionProperty.name.text)
      && ts.isAwaitExpression(assertionCall.parent),
    );
  }
  return false;
}

function comparePlaywrightRelations(expected, actual, routePrefixes, errors) {
  // Set membership is insufficient. The same test block must bind metadata,
  // route, selector, and an awaited action/assertion into one exact tuple.
  const expectedKeys = new Set(expected.map(playwrightRelationKey));
  const actualActive = actual.filter((relation) => relation.active && relation.route && inScope(relation.route, routePrefixes));
  reportDuplicates(actualActive.map(playwrightRelationKey), 'UI_PLAYWRIGHT_RELATION_DUPLICATE_SOURCE', errors);
  for (const relation of actual.filter((candidate) => !candidate.active)) {
    if (relation.route && inScope(relation.route, routePrefixes)) {
      errors.push(`UI_PLAYWRIGHT_DEAD_LOCATOR route=${relation.route} selector=${relation.selector} location=${relation.location}`);
    }
  }
  for (const relation of expected) {
    const key = playwrightRelationKey(relation);
    if (!actualActive.some((candidate) => playwrightRelationKey(candidate) === key)) {
      errors.push(
        `UI_PLAYWRIGHT_RELATION_MISSING route=${relation.route} selector=${relation.dataTestId} playwright=${relation.playwrightId} case=${relation.caseId}`,
      );
    }
  }
  for (const relation of actualActive) {
    const key = playwrightRelationKey(relation);
    if (!expectedKeys.has(key)) {
      errors.push(
        `UI_PLAYWRIGHT_RELATION_STALE route=${relation.route} selector=${relation.selector} playwright=${relation.playwrightId ?? '-'} case=${relation.caseId ?? '-'} location=${relation.location}`,
      );
    }
  }
}

function playwrightRelationKey(relation) {
  return [
    relation.route,
    relation.dataTestId ?? relation.selector,
    relation.playwrightId,
    relation.caseId,
    relation.obligationIds?.[0] ?? relation.obligationId,
  ].join('|');
}

function relationKey(route, selector) {
  return `${route}|${selector}`;
}

function redirectKey(redirect) {
  return `${redirect.from}|${redirect.to}|${redirect.index}`;
}

function inScope(route, prefixes) {
  return prefixes.some((prefix) => route === prefix || route.startsWith(`${prefix}/`));
}

function reportDuplicates(values, diagnosticId, errors) {
  const counts = new Map();
  for (const value of values) counts.set(value, (counts.get(value) ?? 0) + 1);
  for (const [value, count] of counts) {
    if (count > 1) errors.push(`${diagnosticId} value=${value} count=${count}`);
  }
}

async function resolveDeclaredPath(declared, manifestPath, errors, label) {
  const root = path.resolve(process.cwd());
  const canonicalRoot = await realpath(root);
  if (path.isAbsolute(declared)) {
    if (!insideRoot(declared, root) || (await exists(declared) && !insideRoot(await realpath(declared), canonicalRoot))) {
      errors.push(`UI_SOURCE_OUTSIDE_ROOT section=${label} source=${declared}`);
      return null;
    }
    return await exists(declared) ? declared : null;
  }
  const candidates = [
    path.resolve(process.cwd(), declared),
    path.resolve(path.dirname(manifestPath), declared),
  ];
  for (const candidate of candidates) {
    if (!insideRoot(candidate, root)) continue;
    for (const extension of ['', '.tsx', '.ts', '.jsx', '.js', '/index.tsx', '/index.ts']) {
      const resolved = `${candidate}${extension}`;
      if (await exists(resolved)) {
        const canonical = await realpath(resolved);
        if (!insideRoot(canonical, canonicalRoot)) {
          errors.push(`UI_SOURCE_OUTSIDE_ROOT section=${label} source=${declared}`);
          return null;
        }
        return resolved;
      }
    }
  }
  return null;
}

function insideRoot(candidate, root) {
  const absolute = path.resolve(candidate);
  return absolute === root || absolute.startsWith(`${root}${path.sep}`);
}

async function exists(candidate) {
  try {
    await access(candidate);
    return true;
  } catch {
    return false;
  }
}

function sameDeclaredPath(actual, declared) {
  const normalize = (value) => displayPath(value).replace(/\.(?:tsx?|jsx?)$/, '');
  return normalize(actual) === normalize(declared) || normalize(actual).endsWith(`/${normalize(declared)}`);
}

function unsupported(node, sourceFile, sourcePath, kind, errors) {
  errors.push(`UI_UNSUPPORTED_SYNTAX kind=${kind} location=${sourceLocation(node, sourceFile, sourcePath)}`);
}

function parseTypeScriptSource(sourcePath, source, scriptKind, errors) {
  const sourceFile = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, scriptKind);
  for (const diagnostic of sourceFile.parseDiagnostics) {
    const position = sourceFile.getLineAndCharacterOfPosition(diagnostic.start ?? 0);
    const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, ' ');
    errors.push(
      `UI_SOURCE_PARSE_ERROR location=${displayPath(sourcePath)}:${position.line + 1}:${position.character + 1} code=TS${diagnostic.code} detail=${message}`,
    );
  }
  return sourceFile;
}

function sourceLocation(node, sourceFile, sourcePath) {
  const position = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
  return `${displayPath(sourcePath)}:${position.line + 1}:${position.character + 1}`;
}

function displayPath(value) {
  const absolute = path.resolve(value);
  const relative = path.relative(process.cwd(), absolute);
  return relative && !relative.startsWith('..') ? relative.split(path.sep).join('/') : absolute.split(path.sep).join('/');
}
