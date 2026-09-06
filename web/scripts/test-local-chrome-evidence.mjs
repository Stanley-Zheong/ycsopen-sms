#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { deflateSync } from 'node:zlib';
import { probeLocalChrome, runBoundedCommand, STANDARD_CHROME_PATH, validatePlaywrightConfigSource } from './probe-local-chrome.mjs';
import { publishRuntimeEvidenceAfterCleanup } from './run-local-chrome-smoke.mjs';
import {
  canonicalDigest,
  DURABLE_SUBJECT_PATH,
  PNG_CANONICAL_BASE64_MAX_LENGTH,
  PNG_RAW_MAX_BYTES,
  readJsonFileBounded,
  validateLocalChromeEvidence,
  validateScreenshotArtifact,
} from './validate-local-chrome-evidence.mjs';

const viewport = { width: 1440, height: 900 };
const fullVersion = '151.0.7922.174';
const scenarioPath = resolve('web/verification/browser-scenarios.json');
const scenarioContract = JSON.parse(await readFile(scenarioPath, 'utf8'));
const schema = JSON.parse(await readFile(resolve('web/verification/local-chrome-runtime.schema.json'), 'utf8'));
const expectedSubject = Object.freeze({
  manifestPath: DURABLE_SUBJECT_PATH,
  manifestDigest: 'a'.repeat(64),
  testedSubjectDigest: 'b'.repeat(64),
});

function assert(condition, diagnostic) {
  if (!condition) throw new Error(diagnostic);
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function fakeAdapters(overrides = {}) {
  return {
    inspect: async () => ({ exists: true, regular: true, symbolicLink: false, executable: true, canonicalPath: STANDARD_CHROME_PATH }),
    version: async () => ({ code: 0, stdout: `Google Chrome ${fullVersion}\n`, stderr: '', timedOut: false, overflow: false }),
    launch: async () => ({ succeeded: true, browserVersion: fullVersion, viewport, closed: true }),
    ...overrides,
  };
}

async function expectProbeBlocked(id, adapters, diagnostic, options = {}) {
  try {
    await probeLocalChrome({ adapters, ...options });
    throw new Error(`EXPECTED_BLOCKED case=${id}`);
  } catch (error) {
    assert(String(error.message).includes(diagnostic), `WRONG_PROBE_DIAGNOSTIC case=${id} expected=${diagnostic} actual=${error.message}`);
  }
}

async function runProbeCases() {
  const positive = await probeLocalChrome({ adapters: fakeAdapters() });
  assert(positive.status === 'PROBED', 'PROBE_KNOWN_GOOD_REJECTED');
  const cases = [
    ['path-override', fakeAdapters(), 'LOCAL_CHROME_PATH_OVERRIDE_FORBIDDEN', { executablePath: '/tmp/chrome' }],
    ['missing', fakeAdapters({ inspect: async () => ({ exists: false }) }), 'LOCAL_CHROME_PATH_MISSING'],
    ['symlink', fakeAdapters({ inspect: async () => ({ exists: true, regular: true, symbolicLink: true, executable: true, canonicalPath: STANDARD_CHROME_PATH }) }), 'LOCAL_CHROME_PATH_SYMLINK_FORBIDDEN'],
    ['non-regular', fakeAdapters({ inspect: async () => ({ exists: true, regular: false, symbolicLink: false, executable: true, canonicalPath: STANDARD_CHROME_PATH }) }), 'LOCAL_CHROME_NOT_REGULAR'],
    ['non-executable', fakeAdapters({ inspect: async () => ({ exists: true, regular: true, symbolicLink: false, executable: false, canonicalPath: STANDARD_CHROME_PATH }) }), 'LOCAL_CHROME_NOT_EXECUTABLE'],
    ['non-canonical', fakeAdapters({ inspect: async () => ({ exists: true, regular: true, symbolicLink: false, executable: true, canonicalPath: '/private/tmp/chrome' }) }), 'LOCAL_CHROME_PATH_NOT_CANONICAL'],
    ['version-exit', fakeAdapters({ version: async () => ({ code: 9, stdout: '', stderr: 'failed', timedOut: false, overflow: false }) }), 'LOCAL_CHROME_VERSION_COMMAND_FAILED'],
    ['version-timeout', fakeAdapters({ version: async () => ({ code: null, stdout: '', stderr: '', timedOut: true, overflow: false }) }), 'LOCAL_CHROME_VERSION_COMMAND_TIMEOUT'],
    ['version-overflow', fakeAdapters({ version: async () => ({ code: 0, stdout: 'x', stderr: '', timedOut: false, overflow: true }) }), 'LOCAL_CHROME_VERSION_OUTPUT_TOO_LARGE'],
    ['wrong-brand', fakeAdapters({ version: async () => ({ code: 0, stdout: `Chromium ${fullVersion}`, stderr: '', timedOut: false, overflow: false }) }), 'LOCAL_CHROME_IDENTITY_INVALID'],
    ['bad-version', fakeAdapters({ version: async () => ({ code: 0, stdout: 'Google Chrome 151', stderr: '', timedOut: false, overflow: false }) }), 'LOCAL_CHROME_IDENTITY_INVALID'],
    ['launch-failed', fakeAdapters({ launch: async () => { throw new Error('synthetic launch failure'); } }), 'LOCAL_CHROME_PLAYWRIGHT_LAUNCH_FAILED'],
    ['launch-version', fakeAdapters({ launch: async () => ({ succeeded: true, browserVersion: '150.0.0.0', viewport, closed: true }) }), 'LOCAL_CHROME_PLAYWRIGHT_VERSION_MISMATCH'],
    ['launch-viewport', fakeAdapters({ launch: async () => ({ succeeded: true, browserVersion: fullVersion, viewport: { width: 1366, height: 768 }, closed: true }) }), 'LOCAL_CHROME_PLAYWRIGHT_VIEWPORT_MISMATCH'],
    ['launch-not-closed', fakeAdapters({ launch: async () => ({ succeeded: true, browserVersion: fullVersion, viewport, closed: false }) }), 'LOCAL_CHROME_PLAYWRIGHT_CLOSE_FAILED'],
  ];
  for (const [id, adapters, diagnostic, options] of cases) await expectProbeBlocked(id, adapters, diagnostic, options);
  const configSource = await readFile(resolve('web/playwright.config.ts'), 'utf8');
  assert(validatePlaywrightConfigSource(configSource), 'LOCAL_CHROME_CONFIG_KNOWN_GOOD_REJECTED');
  const configCases = [
    ['environment-override', configSource.replace('launchOptions: { executablePath: standardChromePath }', 'launchOptions: { executablePath: process.env.CHROME_PATH }'), 'LOCAL_CHROME_CONFIG_LAUNCH_INVALID'],
    ['channel-substitution', configSource.replace('launchOptions: { executablePath: standardChromePath }', "channel: 'chrome'"), 'LOCAL_CHROME_CONFIG_LAUNCH_INVALID'],
    ['another-project', configSource.replace("name: 'local-google-chrome'", "name: 'firefox'"), 'LOCAL_CHROME_CONFIG_SUBSTITUTION_FORBIDDEN'],
    ['wrong-viewport', configSource.replace('width: 1440', 'width: 1366'), 'LOCAL_CHROME_CONFIG_VIEWPORT_INVALID'],
  ];
  for (const [id, source, diagnostic] of configCases) {
    try {
      validatePlaywrightConfigSource(source);
      throw new Error(`EXPECTED_CONFIG_BLOCKED case=${id}`);
    } catch (error) {
      assert(String(error.message).includes(diagnostic), `WRONG_CONFIG_DIAGNOSTIC case=${id} expected=${diagnostic} actual=${error.message}`);
    }
  }
  console.log(`local_chrome_probe_tests=PASS cases=${cases.length + configCases.length + 2}`);
}

function sha256Bytes(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

function artifact(content, locator) {
  return { locator, content, sha256: canonicalDigest(content) };
}

function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) {
    value ^= byte;
    for (let bit = 0; bit < 8; bit += 1) value = (value >>> 1) ^ (0xedb88320 & -(value & 1));
  }
  return (value ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBytes = Buffer.from(type, 'ascii');
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(Buffer.concat([typeBytes, data])));
  return Buffer.concat([length, typeBytes, data, checksum]);
}

function realPng(width = viewport.width, height = viewport.height, options = {}) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = options.bitDepth ?? 8;
  ihdr[9] = options.colorType ?? 6;
  ihdr[10] = options.compression ?? 0;
  ihdr[11] = options.filterMethod ?? 0;
  ihdr[12] = options.interlace ?? 0;
  const channels = new Map([[0, 1], [2, 3], [3, 1], [4, 2], [6, 4]]).get(ihdr[9]) ?? 4;
  const rowLength = Math.ceil((width * channels * ihdr[8]) / 8) + 1;
  const compressed = options.compressed ?? deflateSync(options.pixels ?? Buffer.alloc(rowLength * height));
  const bodyChunks = options.bodyChunks ?? [
    ...(options.beforeIdat ?? []),
    ...(options.omitIdat ? [] : [pngChunk('IDAT', compressed)]),
    ...(options.afterIdat ?? []),
    ...(options.omitIend ? [] : (options.iendChunks ?? [pngChunk('IEND', Buffer.alloc(0))])),
  ];
  return Buffer.concat([
    signature,
    pngChunk('IHDR', ihdr),
    ...bodyChunks,
    options.trailing ?? Buffer.alloc(0),
  ]);
}

function pngChunkData(bytes, targetType) {
  let offset = 8;
  while (offset + 12 <= bytes.length) {
    const length = bytes.readUInt32BE(offset);
    if (bytes.subarray(offset + 4, offset + 8).toString('ascii') === targetType) {
      return bytes.subarray(offset + 8, offset + 8 + length);
    }
    offset += length + 12;
  }
  throw new Error(`PNG_CHUNK_NOT_FOUND type=${targetType}`);
}

function corruptChunkCrc(bytes, type) {
  const mutated = Buffer.from(bytes);
  let offset = 8;
  while (offset + 12 <= mutated.length) {
    const length = mutated.readUInt32BE(offset);
    if (mutated.subarray(offset + 4, offset + 8).toString('ascii') === type) {
      mutated[offset + 8 + length] ^= 0xff;
      return mutated;
    }
    offset += 12 + length;
  }
  throw new Error(`PNG_CHUNK_NOT_FOUND type=${type}`);
}

function replaceScreenshot(evidence, bytes, contentBase64 = bytes.toString('base64')) {
  evidence.run.artifacts.screenshot.contentBase64 = contentBase64;
  evidence.run.artifacts.screenshot.byteLength = bytes.length;
  evidence.run.artifacts.screenshot.sha256 = sha256Bytes(bytes);
}

function knownGoodEvidence() {
  const visual = {
    ruleId: scenarioContract.scenario.visualRule.id,
    viewport: { ...viewport },
    failures: [],
    observations: scenarioContract.scenario.visualRule.requiredSelectorKeys.map((key, index) => ({
      key,
      testId: scenarioContract.scenario.selectors[key],
      rectangle: { x: 100, y: 100 + index * 20, top: 100 + index * 20, left: 100, right: 300, bottom: 118 + index * 20, width: 200, height: 18 },
      ancestors: [{ testId: scenarioContract.scenario.selectors[key], tag: 'div', rectangle: { x: 100, y: 100, top: 100, left: 100, right: 300, bottom: 300, width: 200, height: 200 }, display: 'block', visibility: 'visible', contentVisibility: 'visible', opacity: '1', clipPath: 'none', maskImage: 'none', overflowX: 'visible', overflowY: 'visible' }],
      hitTests: ['center', 'top-left', 'top-right', 'bottom-left', 'bottom-right'].map((name) => ({ name, x: 150, y: 150, hitTestId: scenarioContract.scenario.selectors[key], hitTag: 'DIV', accepted: true })),
    })),
  };
  const transcript = [
    { event: 'navigate', target: '/login', status: 'PASS' },
    { event: 'native-required-validation', target: 'username', status: 'PASS' },
    { event: 'fill', target: 'username', status: 'PASS' },
    { event: 'fill', target: 'password', status: 'PASS' },
    { event: 'check', target: 'remember', status: 'PASS' },
    { event: 'browser-response', target: '/api/v1/console/auth/login', status: 'PASS' },
    { event: 'ui-error', target: 'shared-auth-login-error', status: 'PASS' },
    { event: 'visual-rule', target: 'LOGIN-CARD-IN-VIEWPORT-V2', status: 'PASS' },
    { event: 'screenshot', target: 'viewport', status: 'PASS' },
  ];
  const consoleArtifact = { consoleEvents: [{ type: 'error', text: 'Failed to load resource: the server responded with a status of 401 (Unauthorized)' }], pageErrors: [], requestFailures: [] };
  const screenshotBytes = realPng();
  return {
    schemaVersion: 'phase01-local-chrome-runtime-v1',
    generatedAt: '2026-08-31T00:00:00.000Z',
    status: 'PASS',
    diagnostic: null,
    run: {
      id: 'LOGIN-SMOKE-V1-local-google-chrome-1440x900', count: 1,
      command: {
        version: { executable: STANDARD_CHROME_PATH, argv: ['--version'], shell: false },
        playwright: { package: '@playwright/test', api: 'chromium.launch', executable: STANDARD_CHROME_PATH, project: 'local-google-chrome', shell: false },
      },
      runtime: {
        path: STANDARD_CHROME_PATH, canonicalPath: STANDARD_CHROME_PATH, fileType: 'regular', executable: true,
        brand: 'Google Chrome', fullVersion, major: 151, playwrightVersion: fullVersion, viewport: { ...viewport },
        launch: { attempted: true, succeeded: true, headless: true, closed: true },
      },
      scenario: {
        subject: {
          manifestPath: DURABLE_SUBJECT_PATH, manifestDigest: expectedSubject.manifestDigest, testedSubjectDigest: expectedSubject.testedSubjectDigest,
          health: { scenario_id: 'LOGIN-SMOKE-V1', subject_manifest_digest: 'a'.repeat(64), tested_subject_digest: 'b'.repeat(64), scenario_contract_digest: scenarioContract.digests.scenarioSha256 },
        },
        contract: { path: 'web/verification/browser-scenarios.json', scenarioId: 'LOGIN-SMOKE-V1', scenarioDigest: scenarioContract.digests.scenarioSha256, visualRuleId: 'LOGIN-CARD-IN-VIEWPORT-V2', visualRuleDigest: scenarioContract.digests.visualRuleSha256 },
        server: { origin: 'http://127.0.0.1:43123', healthObservedByRunner: true },
        route: '/login',
        selectors: Object.entries(scenarioContract.scenario.selectors).map(([key, testId]) => ({ key, testId, visible: true })),
        actions: scenarioContract.scenario.actions.map(({ kind, target }) => ({ kind, target, status: 'PASS' })),
        response: { browserObserved: true, method: 'POST', path: '/api/v1/console/auth/login', status: 401, contentType: 'application/json', marker: { name: 'X-YCS-Scenario', value: 'LOGIN-SMOKE-V1' }, body: clone(scenarioContract.scenario.responder.body), bodySha256: canonicalDigest(scenarioContract.scenario.responder.body) },
        uiError: { testId: 'shared-auth-login-error', text: scenarioContract.scenario.responder.uiErrorText, visible: true },
      },
      artifacts: {
        screenshot: { locator: 'embedded:screenshot', mediaType: 'image/png', contentBase64: screenshotBytes.toString('base64'), byteLength: screenshotBytes.length, sha256: sha256Bytes(screenshotBytes) },
        dom: artifact(visual, 'embedded:dom-observations'),
        transcript: artifact(transcript, 'embedded:browser-transcript'),
        console: artifact(consoleArtifact, 'embedded:console-and-page-errors'),
      },
    },
  };
}

async function expectEvidenceBlocked(id, mutate, diagnostic, forbiddenDiagnostics = []) {
  const evidence = knownGoodEvidence();
  mutate(evidence);
  const errors = await validateLocalChromeEvidence(evidence, scenarioContract, schema, { observedVersion: fullVersion, expectedSubject });
  assert(errors.some((entry) => entry.includes(diagnostic)), `MUTATION_NOT_REJECTED case=${id} expected=${diagnostic} errors=${errors.join(';')}`);
  for (const forbidden of forbiddenDiagnostics) {
    assert(!errors.some((entry) => entry.includes(forbidden)), `MUTATION_DID_NOT_SHORT_CIRCUIT case=${id} forbidden=${forbidden} errors=${errors.join(';')}`);
  }
}

async function runEvidenceCases() {
  const validIdat = pngChunkData(realPng(), 'IDAT');
  const idatSplit = Math.floor(validIdat.length / 2);
  const positivePngs = [
    ['known-good-playwright-shape', realPng()],
    ['unknown-ancillary-before-idat', realPng(viewport.width, viewport.height, {
      beforeIdat: [pngChunk('aaAa', Buffer.from('safe unknown ancillary fixture'))],
    })],
    ['unknown-ancillary-after-idat', realPng(viewport.width, viewport.height, {
      afterIdat: [pngChunk('aaAa', Buffer.from('safe unknown ancillary fixture'))],
    })],
    ['legal-indexed-palette', realPng(viewport.width, viewport.height, {
      colorType: 3, bitDepth: 1, beforeIdat: [pngChunk('PLTE', Buffer.alloc(6))],
    })],
    ['legal-indexed-max-palette', realPng(viewport.width, viewport.height, {
      colorType: 3, bitDepth: 8, beforeIdat: [pngChunk('PLTE', Buffer.alloc(768))],
    })],
    ['legal-truecolor-suggested-palette', realPng(viewport.width, viewport.height, {
      colorType: 6, bitDepth: 8, beforeIdat: [pngChunk('PLTE', Buffer.alloc(6))],
    })],
    ['legal-contiguous-multiple-idat', realPng(viewport.width, viewport.height, {
      bodyChunks: [pngChunk('IDAT', validIdat.subarray(0, idatSplit)), pngChunk('IDAT', validIdat.subarray(idatSplit)), pngChunk('IEND', Buffer.alloc(0))],
    })],
  ];
  for (const [id, png] of positivePngs) {
    const evidence = knownGoodEvidence();
    replaceScreenshot(evidence, png);
    const errors = await validateLocalChromeEvidence(evidence, scenarioContract, schema, { observedVersion: fullVersion, expectedSubject });
    assert(errors.length === 0, `RUNTIME_POSITIVE_PNG_REJECTED case=${id} errors=${errors.join(';')}`);
  }
  const palette = (entries) => pngChunk('PLTE', Buffer.alloc(entries * 3));
  const cases = [
    ['schema-version', (e) => { e.schemaVersion = 'old'; }, 'RUNTIME_SCHEMA'],
    ['status', (e) => { e.status = 'PROBED'; }, 'RUNTIME_STATUS_NOT_PASS'],
    ['duplicate-run', (e) => { e.runs = [clone(e.run), clone(e.run)]; }, 'RUNTIME_SCHEMA_ADDITIONAL'],
    ['count', (e) => { e.run.count = 2; }, 'RUNTIME_RUN_COUNT_INVALID'],
    ['path', (e) => { e.run.runtime.path = '/tmp/chrome'; }, 'RUNTIME_PATH_INVALID'],
    ['brand', (e) => { e.run.runtime.brand = 'Chromium'; }, 'RUNTIME_BRAND_INVALID'],
    ['version', (e) => { e.run.runtime.fullVersion = '151'; }, 'RUNTIME_VERSION_INVALID'],
    ['major', (e) => { e.run.runtime.major = 150; }, 'RUNTIME_MAJOR_MISMATCH'],
    ['live-version', (e) => { e.run.runtime.fullVersion = '152.0.0.0'; e.run.runtime.major = 152; e.run.runtime.playwrightVersion = '152.0.0.0'; }, 'RUNTIME_LIVE_VERSION_MISMATCH'],
    ['playwright-version', (e) => { e.run.runtime.playwrightVersion = '150.0.0.0'; }, 'RUNTIME_PLAYWRIGHT_VERSION_MISMATCH'],
    ['viewport', (e) => { e.run.runtime.viewport.width = 1366; }, 'RUNTIME_VIEWPORT_INVALID'],
    ['launch', (e) => { e.run.runtime.launch.succeeded = false; }, 'RUNTIME_LAUNCH_INVALID'],
    ['scenario-digest', (e) => { e.run.scenario.contract.scenarioDigest = '0'.repeat(64); }, 'RUNTIME_SCENARIO_DIGEST_MISMATCH'],
    ['rule-digest', (e) => { e.run.scenario.contract.visualRuleDigest = '0'.repeat(64); }, 'RUNTIME_VISUAL_RULE_DIGEST_MISMATCH'],
    ['health-subject', (e) => { e.run.scenario.subject.health.tested_subject_digest = '0'.repeat(64); }, 'RUNTIME_HEALTH_SUBJECT_MISMATCH'],
    ['manifest-subject-rebound', (e) => { e.run.scenario.subject.manifestDigest = 'c'.repeat(64); e.run.scenario.subject.health.subject_manifest_digest = 'c'.repeat(64); }, 'RUNTIME_SUBJECT_MANIFEST_DIGEST_MISMATCH'],
    ['tested-subject-rebound', (e) => { e.run.scenario.subject.testedSubjectDigest = 'c'.repeat(64); e.run.scenario.subject.health.tested_subject_digest = 'c'.repeat(64); }, 'RUNTIME_TESTED_SUBJECT_DIGEST_MISMATCH'],
    ['subject-path', (e) => { e.run.scenario.subject.manifestPath = 'ephemeral-runtime/tested-inputs.json'; }, 'RUNTIME_SUBJECT_MANIFEST_PATH_MISMATCH'],
    ['remote-origin', (e) => { e.run.scenario.server.origin = 'https://example.com'; }, 'RUNTIME_SERVER_NOT_LOOPBACK'],
    ['selector', (e) => { e.run.scenario.selectors[0].visible = false; }, 'RUNTIME_SELECTOR_NOT_VISIBLE'],
    ['action', (e) => { e.run.scenario.actions.pop(); }, 'RUNTIME_ACTION_SEQUENCE_MISMATCH'],
    ['runner-only-response', (e) => { e.run.scenario.response.browserObserved = false; }, 'RUNTIME_RESPONSE_NOT_BROWSER_OBSERVED'],
    ['response-status', (e) => { e.run.scenario.response.status = 200; }, 'RUNTIME_RESPONSE_INVALID'],
    ['response-body', (e) => { e.run.scenario.response.body.message = 'wrong'; }, 'RUNTIME_RESPONSE_BODY_INVALID'],
    ['response-body-digest', (e) => { e.run.scenario.response.bodySha256 = '0'.repeat(64); }, 'RUNTIME_RESPONSE_BODY_DIGEST_MISMATCH'],
    ['ui-error', (e) => { e.run.scenario.uiError.visible = false; }, 'RUNTIME_UI_ERROR_INVALID'],
    ['screenshot-only', (e) => { delete e.run.artifacts.dom; }, 'RUNTIME_ARTIFACT_SET'],
    ['screenshot-digest', (e) => { e.run.artifacts.screenshot.sha256 = '0'.repeat(64); }, 'RUNTIME_SCREENSHOT_DIGEST_MISMATCH'],
    ['screenshot-text', (e) => { replaceScreenshot(e, Buffer.from('not a png')); }, 'RUNTIME_SCREENSHOT_PNG_INVALID'],
    ['screenshot-truncated', (e) => { replaceScreenshot(e, realPng().subarray(0, 24)); }, 'RUNTIME_SCREENSHOT_PNG_INVALID'],
    ['screenshot-header-only', (e) => { replaceScreenshot(e, realPng().subarray(0, 33)); }, 'RUNTIME_SCREENSHOT_PNG_INVALID'],
    ['screenshot-ihdr-crc', (e) => { replaceScreenshot(e, corruptChunkCrc(realPng(), 'IHDR')); }, 'RUNTIME_SCREENSHOT_IHDR_CRC_INVALID'],
    ['screenshot-idat-crc', (e) => { replaceScreenshot(e, corruptChunkCrc(realPng(), 'IDAT')); }, 'RUNTIME_SCREENSHOT_IDAT_CRC_INVALID'],
    ['screenshot-iend-crc', (e) => { replaceScreenshot(e, corruptChunkCrc(realPng(), 'IEND')); }, 'RUNTIME_SCREENSHOT_IEND_CRC_INVALID'],
    ['screenshot-unknown-critical', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [pngChunk('ABCD', Buffer.from('unknown critical'))] })); }, 'RUNTIME_SCREENSHOT_UNKNOWN_CRITICAL_CHUNK'],
    ['screenshot-illegal-chunk-type', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [pngChunk('A1CD', Buffer.alloc(0))] })); }, 'RUNTIME_SCREENSHOT_CHUNK_TYPE_INVALID'],
    ['screenshot-reserved-bit', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [pngChunk('aaad', Buffer.alloc(0))] })); }, 'RUNTIME_SCREENSHOT_CHUNK_RESERVED_BIT_INVALID'],
    ['screenshot-duplicate-plte', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [palette(2), palette(2)] })); }, 'RUNTIME_SCREENSHOT_PLTE_COUNT_INVALID'],
    ['screenshot-empty-plte', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [palette(0)] })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-partial-plte-entry', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [pngChunk('PLTE', Buffer.alloc(4))] })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-plte-over-256', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [palette(257)] })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-indexed-plte-too-large', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { colorType: 3, bitDepth: 1, beforeIdat: [palette(3)] })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-indexed-plte-missing', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { colorType: 3, bitDepth: 1 })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-grayscale-plte', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { colorType: 0, bitDepth: 8, beforeIdat: [palette(2)] })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-grayscale-alpha-plte', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { colorType: 4, bitDepth: 8, beforeIdat: [palette(2)] })); }, 'RUNTIME_SCREENSHOT_PLTE_INVALID'],
    ['screenshot-plte-after-idat', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { afterIdat: [palette(2)] })); }, 'RUNTIME_SCREENSHOT_PLTE_ORDER_INVALID'],
    ['screenshot-duplicate-ihdr', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { beforeIdat: [pngChunk('IHDR', Buffer.alloc(13))] })); }, 'RUNTIME_SCREENSHOT_IHDR_INVALID'],
    ['screenshot-ihdr-after-idat', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { afterIdat: [pngChunk('IHDR', Buffer.alloc(13))] })); }, 'RUNTIME_SCREENSHOT_IHDR_INVALID'],
    ['screenshot-idat-missing', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { omitIdat: true })); }, 'RUNTIME_SCREENSHOT_IDAT_MISSING'],
    ['screenshot-idat-noncontiguous', (e) => { const split = Math.floor(validIdat.length / 2); replaceScreenshot(e, realPng(viewport.width, viewport.height, { bodyChunks: [pngChunk('IDAT', validIdat.subarray(0, split)), pngChunk('aaAa', Buffer.alloc(0)), pngChunk('IDAT', validIdat.subarray(split)), pngChunk('IEND', Buffer.alloc(0))] })); }, 'RUNTIME_SCREENSHOT_IDAT_ORDER_INVALID'],
    ['screenshot-iend-nonzero', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { iendChunks: [pngChunk('IEND', Buffer.from([0]))] })); }, 'RUNTIME_SCREENSHOT_IEND_INVALID'],
    ['screenshot-iend-missing', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { omitIend: true })); }, 'RUNTIME_SCREENSHOT_IEND_COUNT_INVALID'],
    ['screenshot-duplicate-iend', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { iendChunks: [pngChunk('IEND', Buffer.alloc(0)), pngChunk('IEND', Buffer.alloc(0))] })); }, 'RUNTIME_SCREENSHOT_PNG_TRAILING_DATA'],
    ['screenshot-iend-before-idat', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { bodyChunks: [pngChunk('IEND', Buffer.alloc(0)), pngChunk('IDAT', validIdat)] })); }, 'RUNTIME_SCREENSHOT_IEND_ORDER_INVALID'],
    ['screenshot-bad-deflate', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { compressed: Buffer.from('not-zlib') })); }, 'RUNTIME_SCREENSHOT_IDAT_DEFLATE_INVALID'],
    ['screenshot-illegal-color-depth', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { colorType: 6, bitDepth: 4 })); }, 'RUNTIME_SCREENSHOT_IHDR_INVALID'],
    ['screenshot-illegal-header-invalid-deflate', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { colorType: 6, bitDepth: 4, compressed: Buffer.from('invalid-zlib-with-correct-idat-crc') })); }, 'RUNTIME_SCREENSHOT_IHDR_INVALID', ['RUNTIME_SCREENSHOT_IDAT_DEFLATE_INVALID']],
    ['screenshot-truncated-scanline', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { pixels: Buffer.alloc((viewport.width * 4 + 1) * viewport.height - 1) })); }, 'RUNTIME_SCREENSHOT_SCANLINE_LENGTH_INVALID'],
    ['screenshot-filter-invalid', (e) => { const pixels = Buffer.alloc((viewport.width * 4 + 1) * viewport.height); pixels[0] = 5; replaceScreenshot(e, realPng(viewport.width, viewport.height, { pixels })); }, 'RUNTIME_SCREENSHOT_FILTER_INVALID'],
    ['screenshot-trailing-data', (e) => { replaceScreenshot(e, realPng(viewport.width, viewport.height, { trailing: Buffer.from('trailing') })); }, 'RUNTIME_SCREENSHOT_PNG_TRAILING_DATA'],
    ['screenshot-wrong-dimensions', (e) => { replaceScreenshot(e, realPng(1366, 768)); }, 'RUNTIME_SCREENSHOT_DIMENSIONS_INVALID'],
    ['screenshot-wrong-dimensions-invalid-deflate', (e) => { replaceScreenshot(e, realPng(1366, 768, { compressed: Buffer.from('invalid-zlib-with-correct-idat-crc') })); }, 'RUNTIME_SCREENSHOT_DIMENSIONS_INVALID', ['RUNTIME_SCREENSHOT_IDAT_DEFLATE_INVALID']],
    ['screenshot-noncanonical-base64', (e) => { const bytes = Buffer.from(e.run.artifacts.screenshot.contentBase64, 'base64'); replaceScreenshot(e, bytes, `${bytes.toString('base64')}\n`); }, 'RUNTIME_SCREENSHOT_BASE64_NON_CANONICAL'],
    ['screenshot-byte-length-lie', (e) => { e.run.artifacts.screenshot.byteLength += 1; }, 'RUNTIME_SCREENSHOT_INVALID'],
    ['dom-digest', (e) => { e.run.artifacts.dom.sha256 = '0'.repeat(64); }, 'RUNTIME_ARTIFACT_DIGEST_MISMATCH'],
    ['visual-failure', (e) => { e.run.artifacts.dom.content.failures.push({ diagnostic: 'VISUAL_HIT_OBSTRUCTED' }); e.run.artifacts.dom.sha256 = canonicalDigest(e.run.artifacts.dom.content); }, 'RUNTIME_VISUAL_FAILURE'],
    ['visual-observation', (e) => { e.run.artifacts.dom.content.observations.pop(); e.run.artifacts.dom.sha256 = canonicalDigest(e.run.artifacts.dom.content); }, 'RUNTIME_VISUAL_OBSERVATIONS_INCOMPLETE'],
    ['hit-test', (e) => { e.run.artifacts.dom.content.observations[0].hitTests[0].accepted = false; e.run.artifacts.dom.sha256 = canonicalDigest(e.run.artifacts.dom.content); }, 'RUNTIME_HIT_TEST_REJECTED'],
    ['console', (e) => { e.run.artifacts.console.content.consoleEvents.push({ type: 'warning', text: 'unexpected' }); e.run.artifacts.console.sha256 = canonicalDigest(e.run.artifacts.console.content); }, 'RUNTIME_CONSOLE_POLICY_FAILED'],
    ['page-error', (e) => { e.run.artifacts.console.content.pageErrors.push('boom'); e.run.artifacts.console.sha256 = canonicalDigest(e.run.artifacts.console.content); }, 'RUNTIME_PAGE_ERROR_POLICY_FAILED'],
    ['request-failure', (e) => { e.run.artifacts.console.content.requestFailures.push('POST failed'); e.run.artifacts.console.sha256 = canonicalDigest(e.run.artifacts.console.content); }, 'RUNTIME_REQUEST_FAILURE_POLICY_FAILED'],
    ['transcript', (e) => { e.run.artifacts.transcript.content.pop(); e.run.artifacts.transcript.sha256 = canonicalDigest(e.run.artifacts.transcript.content); }, 'RUNTIME_TRANSCRIPT_INCOMPLETE'],
    ['request-interception', (e) => { e.run.scenario.requestInterception = true; }, 'RUNTIME_REQUEST_INTERCEPTION_FORBIDDEN'],
    ['matrix', (e) => { e.run.matrix = [{ browser: 'chrome' }]; }, 'RUNTIME_MATRIX_FORBIDDEN'],
  ];
  for (const [id, mutate, diagnostic, forbiddenDiagnostics] of cases) {
    await expectEvidenceBlocked(id, mutate, diagnostic, forbiddenDiagnostics);
  }
  console.log(`local_chrome_evidence_tests=PASS cases=${cases.length + positivePngs.length}`);
}

async function runPredecodeBoundsCases() {
  const screenshot = {
    locator: 'embedded:screenshot',
    mediaType: 'image/png',
    contentBase64: 'AAAA',
    byteLength: 3,
    sha256: '0'.repeat(64),
  };
  const calls = [];
  const neverDecodeOrInspect = {
    decode: () => { calls.push('decode'); throw new Error('DECODE_MUST_NOT_RUN'); },
    encode: () => { calls.push('encode'); throw new Error('ENCODE_MUST_NOT_RUN'); },
    digest: () => { calls.push('digest'); throw new Error('DIGEST_MUST_NOT_RUN'); },
    inspect: () => { calls.push('inspect'); throw new Error('INSPECT_MUST_NOT_RUN'); },
  };

  const base64Errors = [];
  validateScreenshotArtifact({ ...screenshot, contentBase64: 'A'.repeat(13) }, base64Errors, {
    limits: { maxRawBytes: 8, maxBase64Length: 12 }, adapters: neverDecodeOrInspect,
  });
  assert(base64Errors.includes('RUNTIME_SCREENSHOT_BASE64_LIMIT_EXCEEDED'), 'BASE64_PREDECODE_LIMIT_MISSING');
  assert(calls.length === 0, `BASE64_LIMIT_RAN_EXPENSIVE_ADAPTER calls=${calls.join(',')}`);

  const byteLengthErrors = [];
  validateScreenshotArtifact({ ...screenshot, byteLength: 9 }, byteLengthErrors, {
    limits: { maxRawBytes: 8, maxBase64Length: 12 }, adapters: neverDecodeOrInspect,
  });
  assert(byteLengthErrors.includes('RUNTIME_SCREENSHOT_BYTE_LENGTH_LIMIT_EXCEEDED'), 'BYTE_LENGTH_PREDECODE_LIMIT_MISSING');
  assert(calls.length === 0, `BYTE_LENGTH_LIMIT_RAN_EXPENSIVE_ADAPTER calls=${calls.join(',')}`);

  const postDecodeCalls = [];
  const rawErrors = [];
  validateScreenshotArtifact({ ...screenshot, byteLength: 8 }, rawErrors, {
    limits: { maxRawBytes: 8, maxBase64Length: 12 },
    adapters: {
      decode: () => { postDecodeCalls.push('decode'); return Buffer.alloc(9); },
      encode: () => { postDecodeCalls.push('encode'); throw new Error('ENCODE_MUST_NOT_RUN'); },
      digest: () => { postDecodeCalls.push('digest'); throw new Error('DIGEST_MUST_NOT_RUN'); },
      inspect: () => { postDecodeCalls.push('inspect'); throw new Error('INSPECT_MUST_NOT_RUN'); },
    },
  });
  assert(rawErrors.includes('RUNTIME_SCREENSHOT_RAW_LIMIT_EXCEEDED'), 'RAW_POSTDECODE_LIMIT_MISSING');
  assert(postDecodeCalls.join(',') === 'decode', `RAW_LIMIT_RAN_POSTDECODE_WORK calls=${postDecodeCalls.join(',')}`);

  const screenshotSchema = schema.properties.run.properties.artifacts.properties.screenshot.properties;
  assert(screenshotSchema.contentBase64.maxLength === PNG_CANONICAL_BASE64_MAX_LENGTH, 'SCHEMA_BASE64_MAX_LENGTH_MISMATCH');
  assert(screenshotSchema.byteLength.maximum === PNG_RAW_MAX_BYTES, 'SCHEMA_BYTE_LENGTH_MAXIMUM_MISMATCH');

  const tempRoot = await mkdtemp(join(tmpdir(), 'phase01-json-bound-test-'));
  try {
    const jsonPath = join(tempRoot, 'bounded.json');
    await writeFile(jsonPath, '{"status":"PASS"}', 'utf8');
    try {
      await readJsonFileBounded(jsonPath, 4, 'SYNTHETIC_JSON_SIZE_LIMIT');
      throw new Error('EXPECTED_JSON_SIZE_BLOCKED');
    } catch (error) {
      assert(String(error.message).includes('SYNTHETIC_JSON_SIZE_LIMIT'), `JSON_SIZE_DIAGNOSTIC_INVALID actual=${error.message}`);
    }
    const parsed = await readJsonFileBounded(jsonPath, 64);
    assert(parsed.status === 'PASS', 'BOUNDED_JSON_KNOWN_GOOD_REJECTED');
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
  console.log('local_chrome_predecode_bounds_tests=PASS cases=5');
}

function pidAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    if (error?.code === 'ESRCH') return false;
    throw error;
  }
}

async function assertProcessGone(pid, diagnostic) {
  for (let attempt = 0; attempt < 20 && pidAlive(pid); attempt += 1) {
    await new Promise((accept) => setTimeout(accept, 20));
  }
  assert(!pidAlive(pid), diagnostic);
}

async function runBoundedSpawnCases() {
  const grandchild = "process.on('SIGTERM',()=>{});setInterval(()=>{},1000)";
  const parent = [
    "const {spawn}=require('node:child_process')",
    "process.on('SIGTERM',()=>{})",
    `const child=spawn(process.execPath,['-e',${JSON.stringify(grandchild)}],{stdio:['ignore','inherit','inherit']})`,
    "console.log(`grandchild=${child.pid}`)",
    "setInterval(()=>{},1000)",
  ].join(';');
  const timedOut = await runBoundedCommand(process.execPath, ['-e', parent], {
    timeoutMs: 200,
    termGraceMs: 100,
    maxOutputBytes: 4096,
  });
  assert(timedOut.timedOut && timedOut.terminationReason === 'timeout' && timedOut.signal === 'SIGKILL', 'BOUNDED_TIMEOUT_NOT_KILLED');
  const grandchildPid = Number(timedOut.stdout.match(/grandchild=(\d+)/)?.[1]);
  assert(Number.isInteger(grandchildPid), 'BOUNDED_GRANDCHILD_PID_MISSING');
  await assertProcessGone(timedOut.pid, 'BOUNDED_PARENT_SURVIVED');
  await assertProcessGone(grandchildPid, 'BOUNDED_GRANDCHILD_SURVIVED');

  const flood = await runBoundedCommand(process.execPath, ['-e', "process.on('SIGTERM',()=>{});const x='x'.repeat(8192);setInterval(()=>process.stdout.write(x),0)"], {
    timeoutMs: 5_000,
    termGraceMs: 100,
    maxOutputBytes: 1024,
  });
  assert(flood.overflow && flood.terminationReason === 'output-overflow' && flood.capturedBytes === 1024, 'BOUNDED_OUTPUT_CAP_FAILED');
  await assertProcessGone(flood.pid, 'BOUNDED_FLOOD_PROCESS_SURVIVED');
  console.log('local_chrome_bounded_spawn_tests=PASS cases=2 descendants=contained');
}

async function runCleanupPublicationCases() {
  const tempRoot = await mkdtemp(join(tmpdir(), 'phase01-cleanup-test-'));
  try {
    const output = join(tempRoot, 'runtime.json');
    const calls = [];
    const resources = {
      context: { close: async () => { calls.push('context'); throw new Error('synthetic cleanup failure'); } },
      browser: { close: async () => { calls.push('browser'); } },
    };
    try {
      await publishRuntimeEvidenceAfterCleanup(output, knownGoodEvidence(), resources);
      throw new Error('EXPECTED_CLEANUP_BLOCKED');
    } catch (error) {
      assert(String(error.message).includes('LOCAL_CHROME_CLEANUP_FAILED'), `CLEANUP_DIAGNOSTIC_INVALID actual=${error.message}`);
    }
    assert(calls.join(',') === 'context,browser', 'CLEANUP_DID_NOT_CONTINUE_AFTER_FAILURE');
    await assert(readFile(output, 'utf8').then(() => false, (error) => error?.code === 'ENOENT'), 'CLEANUP_FAILURE_LEFT_PASS_ARTIFACT');

    const stubborn = spawn(process.execPath, ['-e', "process.on('SIGTERM',()=>{});console.log('ready');setInterval(()=>{},1000)"], {
      detached: true,
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    await new Promise((accept, reject) => {
      stubborn.stdout.once('data', accept);
      stubborn.once('error', reject);
    });
    const browserServer = {
      process: () => stubborn,
      close: async () => { throw new Error('synthetic browser server close failure'); },
      kill: () => new Promise((accept) => {
        stubborn.once('close', accept);
        process.kill(-stubborn.pid, 'SIGKILL');
      }),
    };
    try {
      await publishRuntimeEvidenceAfterCleanup(output, knownGoodEvidence(), { browserServer });
      throw new Error('EXPECTED_BROWSER_SERVER_CLEANUP_BLOCKED');
    } catch (error) {
      assert(String(error.message).includes('LOCAL_CHROME_CLEANUP_FAILED'), `BROWSER_SERVER_CLEANUP_DIAGNOSTIC_INVALID actual=${error.message}`);
    }
    await assertProcessGone(stubborn.pid, 'CLEANUP_FAILURE_LEFT_BROWSER_PROCESS');
    await assert(readFile(output, 'utf8').then(() => false, (error) => error?.code === 'ENOENT'), 'BROWSER_SERVER_FAILURE_LEFT_PASS_ARTIFACT');

    const order = [];
    await publishRuntimeEvidenceAfterCleanup(output, knownGoodEvidence(), {
      context: { close: async () => { order.push('cleanup'); } },
    }, {
      writeEvidence: async () => { order.push('publish'); },
    });
    assert(order.join(',') === 'cleanup,publish', 'PASS_PUBLISHED_BEFORE_CLEANUP');
    console.log('local_chrome_cleanup_publication_tests=PASS cases=3');
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
}

try {
  if (process.argv.includes('--case') && process.argv[process.argv.indexOf('--case') + 1] === 'probe') await runProbeCases();
  else {
    await runProbeCases();
    await runEvidenceCases();
    await runPredecodeBoundsCases();
    await runBoundedSpawnCases();
    await runCleanupPublicationCases();
  }
} catch (error) {
  console.error(`local_chrome_evidence_tests=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
  process.exitCode = 1;
}
