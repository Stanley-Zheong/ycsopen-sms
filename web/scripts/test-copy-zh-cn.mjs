#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { validateRegistryDocument, validateRuntimeObservation } from './validate-copy-zh-cn.mjs';

const webRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const validatorPath = resolve(webRoot, 'scripts/validate-copy-zh-cn.mjs');
const registryPath = resolve(webRoot, 'verification/copy.zh-CN.json');
const csvPath = resolve(webRoot, 'verification/fixtures/zh-cn-export.csv');
const sourcePath = resolve(webRoot, 'src/pages/LoginPage.tsx');
const specPath = resolve(webRoot, 'test/phase01/chinese-copy.spec.ts');
const playwrightConfigPath = resolve(webRoot, 'playwright.config.ts');
const playwrightCliPath = resolve(webRoot, 'node_modules/@playwright/test/cli.js');

function assert(condition, diagnostic) {
  if (!condition) throw new Error(diagnostic);
}

async function runValidator(paths) {
  return new Promise((accept, reject) => {
    const child = spawn(process.execPath, [
      validatorPath,
      '--registry', paths.registry,
      '--export-fixture', paths.csv,
      '--source', paths.source,
    ], {
      cwd: webRoot,
      shell: false,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    child.stdout.on('data', (chunk) => { output += chunk; });
    child.stderr.on('data', (chunk) => { output += chunk; });
    child.once('error', reject);
    child.once('close', (code) => accept({ code, output }));
  });
}

async function runPlaywrightConfig(baseURL) {
  const env = { ...process.env };
  if (baseURL === undefined) delete env.PHASE01_BASE_URL;
  else env.PHASE01_BASE_URL = baseURL;
  return new Promise((accept, reject) => {
    const child = spawn(process.execPath, [
      playwrightCliPath,
      'test',
      'test/phase01/chinese-copy.spec.ts',
      '--config', playwrightConfigPath,
      '--list',
    ], {
      cwd: webRoot,
      env,
      shell: false,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    child.stdout.on('data', (chunk) => { output += chunk; });
    child.stderr.on('data', (chunk) => { output += chunk; });
    child.once('error', reject);
    child.once('close', (code) => accept({ code, output }));
  });
}

async function expectConfigCase(name, baseURL, expectedCode, diagnostic) {
  const result = await runPlaywrightConfig(baseURL);
  assert(result.code === expectedCode, `COPY_CONFIG_CASE_EXIT_MISMATCH case=${name} expected=${expectedCode} actual=${result.code} output=${result.output.trim()}`);
  if (diagnostic) {
    assert(result.output.includes(diagnostic), `COPY_CONFIG_CASE_DIAGNOSTIC_MISMATCH case=${name} expected=${diagnostic} actual=${result.output.trim()}`);
  } else {
    assert(result.output.includes('COPY-ZH-CN-V1'), `COPY_CONFIG_CASE_LIST_MISSING case=${name} output=${result.output.trim()}`);
  }
  return name;
}

async function writeFixture(root, mutation = {}) {
  const sourceDestination = join(root, 'web/src/pages/LoginPage.tsx');
  const registryDestination = join(root, 'copy.zh-CN.json');
  const csvDestination = join(root, 'zh-cn-export.csv');
  await mkdir(resolve(sourceDestination, '..'), { recursive: true });
  const registry = JSON.parse(await readFile(registryPath, 'utf8'));
  const source = await readFile(sourcePath, 'utf8');
  const csv = await readFile(csvPath, 'utf8');
  await writeFile(registryDestination, JSON.stringify(mutation.registry ? mutation.registry(registry) : registry, null, 2));
  await writeFile(sourceDestination, mutation.source ? mutation.source(source) : source);
  await writeFile(csvDestination, mutation.csv ? mutation.csv(csv) : csv);
  return { registry: registryDestination, source: sourceDestination, csv: csvDestination };
}

async function expectValidatorMutation(tempRoot, testCase) {
  const root = await mkdtemp(join(tempRoot, 'case-'));
  const result = await runValidator(await writeFixture(root, testCase.mutation));
  assert(result.code !== 0, `COPY_MUTATION_UNEXPECTED_PASS case=${testCase.name}`);
  assert(result.output.includes(testCase.diagnostic), `COPY_MUTATION_DIAGNOSTIC_MISMATCH case=${testCase.name} expected=${testCase.diagnostic} actual=${result.output.trim()}`);
  return testCase.name;
}

function baselineRuntimeObservation(registry) {
  return {
    route: registry.runtime.route,
    selectors: [...registry.runtime.requiredSelectors],
    entries: registry.source.entries.map((entry) => ({
      kind: entry.kind === 'jsx-text' || entry.kind === 'error' ? 'text' : entry.kind,
      value: entry.value,
    })),
    errorSelector: registry.runtime.errorSelector,
    responder: structuredClone(registry.runtime.responder),
  };
}

function expectRuntimeMutation(registry, name, diagnostic, mutate) {
  const observation = baselineRuntimeObservation(registry);
  mutate(observation);
  try {
    validateRuntimeObservation(registry, observation);
    throw new Error(`COPY_RUNTIME_MUTATION_UNEXPECTED_PASS case=${name}`);
  } catch (error) {
    assert(String(error.message).includes(diagnostic), `COPY_RUNTIME_MUTATION_DIAGNOSTIC_MISMATCH case=${name} expected=${diagnostic} actual=${error.message}`);
  }
  return name;
}

export async function runCopyContractMutationSuite() {
  const tempRoot = await mkdtemp(join(tmpdir(), 'ycsopen-sms-copy-zh-cn-'));
  try {
    const knownGood = await runValidator(await writeFixture(await mkdtemp(join(tempRoot, 'known-good-'))));
    assert(knownGood.code === 0 && knownGood.output.includes('copy_zh_cn_contract=PASS'), `COPY_KNOWN_GOOD_FAILED output=${knownGood.output.trim()}`);

    const sourceCases = [
      {
        name: 'unregistered-English-source-copy',
        diagnostic: 'COPY_TECHNICAL_TOKEN_UNREGISTERED',
        mutation: { source: (source) => source.replace('<h2>', '<h2>Welcome ' ) },
      },
      {
        name: 'traditional-only-source-copy',
        diagnostic: 'COPY_TRADITIONAL_ONLY_VARIANT',
        mutation: { source: (source) => source.replace('aria-label="密码"', 'aria-label="密碼"') },
      },
      {
        name: 'hidden-unregistered-source-copy',
        diagnostic: 'COPY_TECHNICAL_TOKEN_UNREGISTERED',
        mutation: { source: (source) => source.replace('<h2>', '<span hidden>Secret status</span><h2>') },
      },
      {
        name: 'missing-source-surface',
        diagnostic: 'COPY_SOURCE_SURFACE_MISSING',
        mutation: { registry: (registry) => { delete registry.source; return registry; } },
      },
      {
        name: 'overbroad-technical-allowlist',
        diagnostic: 'COPY_TECHNICAL_ALLOWLIST_BROAD',
        mutation: { registry: (registry) => { registry.technicalTokens.push({ value: '*', classification: 'production-surface', reason: 'invalid-test' }); return registry; } },
      },
      {
        name: 'missing-export-header',
        diagnostic: 'COPY_EXPORT_HEADER_MISMATCH',
        mutation: { registry: (registry) => { registry.export.headers.pop(); return registry; } },
      },
      {
        name: 'malformed-csv-unclosed-quote',
        diagnostic: 'COPY_CSV_UNCLOSED_QUOTE',
        mutation: { csv: (csv) => `${csv}\n14000000000,失败,"未闭合` },
      },
      {
        name: 'export-mislabeled-production',
        diagnostic: 'COPY_EXPORT_SCOPE_INVALID',
        mutation: { registry: (registry) => { registry.export.classification = 'production-surface'; return registry; } },
      },
    ];
    const passed = ['known-good'];
    for (const testCase of sourceCases) passed.push(await expectValidatorMutation(tempRoot, testCase));

    passed.push(await expectConfigCase('default-fixed-loopback', undefined, 0));
    passed.push(await expectConfigCase('explicit-free-port-loopback', 'http://127.0.0.1:43123', 0));
    passed.push(await expectConfigCase('remote-origin-rejected', 'https://example.com:443', 1, 'PHASE01_BASE_URL_LOOPBACK_REQUIRED'));
    passed.push(await expectConfigCase('credential-url-rejected', 'http://user:secret@127.0.0.1:41737', 1, 'PHASE01_BASE_URL_COMPONENTS_FORBIDDEN'));
    passed.push(await expectConfigCase('path-query-fragment-rejected', 'http://127.0.0.1:41737/evil?origin=remote#fragment', 1, 'PHASE01_BASE_URL_COMPONENTS_FORBIDDEN'));
    passed.push(await expectConfigCase('malformed-url-rejected', 'not-a-valid-url', 1, 'PHASE01_BASE_URL_INVALID'));

    const registry = validateRegistryDocument(JSON.parse(await readFile(registryPath, 'utf8')));
    passed.push(expectRuntimeMutation(registry, 'hidden-unregistered-runtime-copy', 'COPY_TECHNICAL_TOKEN_UNREGISTERED', (observation) => {
      observation.entries.push({ kind: 'text', value: 'Hidden status' });
    }));
    passed.push(expectRuntimeMutation(registry, 'english-aria-label', 'COPY_TECHNICAL_TOKEN_UNREGISTERED', (observation) => {
      const entry = observation.entries.find((candidate) => candidate.kind === 'aria-label' && candidate.value === '用户名');
      entry.value = 'Username';
    }));
    passed.push(expectRuntimeMutation(registry, 'missing-error-selector', 'COPY_RUNTIME_SELECTOR_MISSING', (observation) => {
      observation.selectors = observation.selectors.filter((selector) => selector !== registry.runtime.errorSelector);
    }));
    passed.push(expectRuntimeMutation(registry, 'missing-responder-marker', 'COPY_RUNTIME_RESPONDER_MARKER_MISSING', (observation) => {
      observation.responder.marker.value = '';
    }));

    const specSource = await readFile(specPath, 'utf8');
    assert(specSource.includes('CASE-FOUND-TRACE-003') && specSource.includes('PW-FOUND-LOGIN-SMOKE'), 'COPY_PLAYWRIGHT_TRACE_LINK_MISSING');
    assert(!specSource.includes('page.route(') && !specSource.includes('context.route('), 'COPY_PLAYWRIGHT_INTERCEPTION_FORBIDDEN');
    passed.push('playwright-trace-and-no-interception-contract');
    console.log(`copy_zh_cn_mutations=PASS cases=${passed.length} names=${passed.join(',')}`);
    return passed;
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
}

async function main() {
  try {
    await runCopyContractMutationSuite();
  } catch (error) {
    console.error(`copy_zh_cn_mutations=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main();
