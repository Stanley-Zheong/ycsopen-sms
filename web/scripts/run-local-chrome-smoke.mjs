#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFile, rm } from 'node:fs/promises';
import { basename, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium as chromeBrowserType } from '@playwright/test';
import { probeLocalChrome, REQUIRED_VIEWPORT, runBoundedCommand, STANDARD_CHROME_PATH, writeJsonAtomic } from './probe-local-chrome.mjs';
import { closeBrowserScenarioServer, startBrowserScenarioServer } from './serve-browser-scenario.mjs';
import { canonicalDigest, evaluateVisualRuleInPage } from './validate-browser-scenario.mjs';
import { loadExpectedSubject, validateLocalChromeEvidence } from './validate-local-chrome-evidence.mjs';

const webRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repositoryRoot = resolve(webRoot, '..');
const contractPath = resolve(webRoot, 'verification/browser-scenarios.json');
const schemaPath = resolve(webRoot, 'verification/local-chrome-runtime.schema.json');
const expected401ConsoleText = 'Failed to load resource: the server responded with a status of 401 (Unauthorized)';
const MAX_COMMAND_OUTPUT = 64 * 1024;
const COMMAND_TIMEOUT_MS = 120_000;
const CLEANUP_TIMEOUT_MS = 10_000;

function assert(condition, diagnostic) {
  if (!condition) throw new Error(diagnostic);
}

async function runFixed(command, args, options = {}) {
  const result = await runBoundedCommand(command, args, {
    cwd: options.cwd ?? repositoryRoot,
    env: options.env ?? { ...process.env },
    maxOutputBytes: MAX_COMMAND_OUTPUT,
    timeoutMs: options.timeoutMs ?? COMMAND_TIMEOUT_MS,
  });
  if (result.timedOut) throw new Error(`COMMAND_TIMEOUT command=${basename(command)}`);
  if (result.overflow) throw new Error(`COMMAND_OUTPUT_TOO_LARGE command=${basename(command)}`);
  if (result.code !== 0) {
    throw new Error(`COMMAND_FAILED command=${basename(command)} code=${result.code} output=${`${result.stdout}${result.stderr}`.trim().replaceAll('\n', ';')}`);
  }
  return result;
}

function serverArguments(subjectPath, digests) {
  return [
    '--host', '127.0.0.1', '--port', '0', '--dist', resolve(webRoot, 'dist'),
    '--contract', contractPath, '--subject-manifest', subjectPath,
    '--subject-manifest-digest', digests.subject_manifest_digest,
    '--tested-subject-digest', digests.tested_subject_digest,
  ];
}

function sha256Bytes(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function embeddedArtifact(locator, content) {
  return { locator, content, sha256: canonicalDigest(content) };
}

async function visibleSelectorFacts(page, selectors) {
  const facts = [];
  for (const [key, testId] of Object.entries(selectors)) {
    facts.push({ key, testId, visible: await page.locator(`[data-testid="${testId}"]`).isVisible() });
  }
  return facts;
}

async function withDeadline(label, action, timeoutMs = CLEANUP_TIMEOUT_MS) {
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(action),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(`${label}_TIMEOUT`)), timeoutMs); }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

function processIsAlive(child) {
  if (!child || !Number.isInteger(child.pid) || child.pid <= 0 || child.exitCode !== null) return false;
  try {
    process.kill(child.pid, 0);
    return true;
  } catch (error) {
    if (error?.code === 'ESRCH') return false;
    throw error;
  }
}

/** Close every necessary runtime resource, while still attempting later cleanup
 * after an earlier close failure. Any failure blocks evidence publication. */
export async function cleanupSmokeResources(resources, options = {}) {
  const timeoutMs = options.timeoutMs ?? CLEANUP_TIMEOUT_MS;
  const failures = [];
  const attempt = async (label, action) => {
    if (!action) return;
    try { await withDeadline(label, action, timeoutMs); } catch (error) { failures.push(`${label}:${error.message}`); }
  };

  await attempt('LOCAL_CHROME_CONTEXT_CLOSE', resources.context && (() => resources.context.close()));
  await attempt('LOCAL_CHROME_BROWSER_CLOSE', resources.browser && (() => resources.browser.close()));
  if (resources.browserServer) {
    const child = resources.browserServer.process?.();
    let closeFailed = false;
    try {
      await withDeadline('LOCAL_CHROME_BROWSER_SERVER_CLOSE', () => resources.browserServer.close(), timeoutMs);
    } catch (error) {
      closeFailed = true;
      failures.push(`LOCAL_CHROME_BROWSER_SERVER_CLOSE:${error.message}`);
    }
    if (closeFailed || processIsAlive(child)) {
      await attempt('LOCAL_CHROME_BROWSER_SERVER_KILL', () => resources.browserServer.kill());
    }
    if (processIsAlive(child)) failures.push('LOCAL_CHROME_BROWSER_PROCESS_SURVIVED');
  }
  await attempt('LOCAL_CHROME_HTTP_SERVER_CLOSE', resources.running && (() => closeBrowserScenarioServer(resources.running, { timeoutMs })));
  if (failures.length > 0) throw new Error(`LOCAL_CHROME_CLEANUP_FAILED diagnostics=${failures.join(',')}`);
}

export async function publishRuntimeEvidenceAfterCleanup(outputPath, evidence, resources, options = {}) {
  await cleanupSmokeResources(resources, options);
  await (options.writeEvidence ?? writeJsonAtomic)(outputPath, evidence);
}

export async function runLocalChromeSmoke(options) {
  const outputPath = resolve(options.output);
  await rm(outputPath, { force: true });
  const expectedSubject = await loadExpectedSubject(options.subjectManifest, options.subjectManifestDigest, options.testedSubjectDigest);
  const probeEvidence = await probeLocalChrome();
  await runFixed('/usr/bin/env', ['npm', '--prefix', 'web', 'run', 'build']);
  const resources = { running: null, browserServer: null, browser: null, context: null };
  let evidence;
  let executionError;
  try {
    const digests = {
      subject_manifest_digest: expectedSubject.manifestDigest,
      tested_subject_digest: expectedSubject.testedSubjectDigest,
    };
    resources.running = await startBrowserScenarioServer(serverArguments(resolve(options.subjectManifest), digests));
    const running = resources.running;
    const port = typeof running.address === 'object' ? running.address.port : 0;
    assert(Number.isInteger(port) && port > 0, 'LOCAL_CHROME_SERVER_PORT_INVALID');
    const origin = `http://127.0.0.1:${port}`;
    const scenarioContract = JSON.parse(await readFile(contractPath, 'utf8'));
    const schema = JSON.parse(await readFile(schemaPath, 'utf8'));
    const healthResponse = await fetch(`${origin}/__phase01/health`);
    assert(healthResponse.status === 200, 'LOCAL_CHROME_HEALTH_STATUS_INVALID');
    const health = await healthResponse.json();
    const expectedHealth = {
      scenario_id: scenarioContract.scenario.id,
      subject_manifest_digest: digests.subject_manifest_digest,
      tested_subject_digest: digests.tested_subject_digest,
      scenario_contract_digest: scenarioContract.digests.scenarioSha256,
    };
    assert(JSON.stringify(health) === JSON.stringify(expectedHealth), 'LOCAL_CHROME_HEALTH_IDENTITY_MISMATCH');

    resources.browserServer = await chromeBrowserType.launchServer({ executablePath: STANDARD_CHROME_PATH, headless: true });
    resources.browser = await chromeBrowserType.connect(resources.browserServer.wsEndpoint());
    const browser = resources.browser;
    assert(browser.version() === probeEvidence.run.runtime.fullVersion, 'LOCAL_CHROME_SCENARIO_VERSION_MISMATCH');
    resources.context = await browser.newContext({ viewport: REQUIRED_VIEWPORT });
    const context = resources.context;
    const page = await context.newPage();
    const consoleEvents = [];
    const pageErrors = [];
    const requestFailures = [];
    page.on('console', (message) => {
      if (['warning', 'error'].includes(message.type())) consoleEvents.push({ type: message.type(), text: message.text() });
    });
    page.on('pageerror', (error) => pageErrors.push(error.message));
    page.on('requestfailed', (request) => requestFailures.push(`${request.method()} ${new URL(request.url()).pathname} ${request.failure()?.errorText ?? ''}`.trim()));

    await page.goto(`${origin}/login`, { waitUntil: 'networkidle' });
    assert(new URL(page.url()).pathname === '/login', 'LOCAL_CHROME_ROUTE_INVALID');
    const selectors = scenarioContract.scenario.selectors;
    const locator = (key) => page.locator(`[data-testid="${selectors[key]}"]`);
    for (const key of ['page', 'card', 'username', 'password', 'remember', 'submit']) {
      assert(await locator(key).isVisible(), `LOCAL_CHROME_SELECTOR_NOT_VISIBLE key=${key}`);
    }

    await locator('submit').click();
    assert(await locator('username').evaluate((element) => !element.checkValidity()), 'LOCAL_CHROME_NATIVE_VALIDATION_MISSING');
    assert(new URL(page.url()).pathname === '/login', 'LOCAL_CHROME_NATIVE_VALIDATION_NAVIGATED');
    const action = (kind, target) => scenarioContract.scenario.actions.find((entry) => entry.kind === kind && entry.target === target);
    await locator('username').fill(action('fill', 'username').value);
    await locator('password').fill(action('fill', 'password').value);
    await locator('remember').check();
    assert(await locator('remember').isChecked(), 'LOCAL_CHROME_REMEMBER_NOT_CHECKED');

    const responsePromise = page.waitForResponse((response) => {
      const path = new URL(response.url()).pathname;
      return response.request().method() === 'POST' && scenarioContract.scenario.responder.mappedPaths.includes(path);
    });
    await locator('submit').click();
    const response = await responsePromise;
    const responsePath = new URL(response.url()).pathname;
    const responseBody = await response.json();
    const responseFacts = {
      browserObserved: true,
      method: response.request().method(),
      path: responsePath,
      status: response.status(),
      contentType: response.headers()['content-type'],
      marker: { name: scenarioContract.scenario.responder.marker.name, value: response.headers()['x-ycs-scenario'] },
      body: responseBody,
      bodySha256: canonicalDigest(responseBody),
    };
    assert(responsePath === '/api/v1/console/auth/login', 'LOCAL_CHROME_RESPONSE_PATH_INVALID');
    assert(responseFacts.status === 401 && responseFacts.contentType === 'application/json', 'LOCAL_CHROME_RESPONSE_STATUS_INVALID');
    assert(responseFacts.marker.value === scenarioContract.scenario.id, 'LOCAL_CHROME_RESPONSE_MARKER_INVALID');
    assert(JSON.stringify(responseBody) === JSON.stringify(scenarioContract.scenario.responder.body), 'LOCAL_CHROME_RESPONSE_BODY_INVALID');
    await locator('error').waitFor({ state: 'visible' });
    assert((await locator('error').textContent())?.trim() === scenarioContract.scenario.responder.uiErrorText, 'LOCAL_CHROME_UI_ERROR_INVALID');

    const visual = await page.evaluate(evaluateVisualRuleInPage, {
      selectors,
      visualRule: scenarioContract.scenario.visualRule,
    });
    assert(visual.failures.length === 0, `LOCAL_CHROME_VISUAL_FAILURE diagnostics=${visual.failures.map((entry) => entry.diagnostic).join(',')}`);
    const screenshot = await page.screenshot({ fullPage: false, type: 'png' });
    const selectorFacts = await visibleSelectorFacts(page, selectors);
    assert(selectorFacts.every((entry) => entry.visible), 'LOCAL_CHROME_SELECTOR_FACTS_INCOMPLETE');
    const unexpectedConsole = consoleEvents.filter((entry) => entry.type !== 'error' || entry.text !== expected401ConsoleText);
    assert(consoleEvents.length <= 1 && unexpectedConsole.length === 0, 'LOCAL_CHROME_CONSOLE_POLICY_FAILED');
    assert(pageErrors.length === 0, 'LOCAL_CHROME_PAGE_ERROR_POLICY_FAILED');
    assert(requestFailures.length === 0, 'LOCAL_CHROME_REQUEST_FAILURE_POLICY_FAILED');

    const transcript = [
      { event: 'navigate', target: '/login', status: 'PASS' },
      { event: 'native-required-validation', target: 'username', status: 'PASS' },
      { event: 'fill', target: 'username', status: 'PASS' },
      { event: 'fill', target: 'password', status: 'PASS' },
      { event: 'check', target: 'remember', status: 'PASS' },
      { event: 'browser-response', target: responsePath, status: 'PASS' },
      { event: 'ui-error', target: selectors.error, status: 'PASS' },
      { event: 'visual-rule', target: scenarioContract.scenario.visualRule.id, status: 'PASS' },
      { event: 'screenshot', target: 'viewport', status: 'PASS' },
    ];
    const consoleFacts = { consoleEvents, pageErrors, requestFailures };
    evidence = {
      ...probeEvidence,
      generatedAt: new Date().toISOString(),
      status: 'PASS',
      run: {
        ...probeEvidence.run,
        scenario: {
          subject: {
            manifestPath: expectedSubject.manifestPath,
            manifestDigest: digests.subject_manifest_digest,
            testedSubjectDigest: digests.tested_subject_digest,
            health,
          },
          contract: {
            path: 'web/verification/browser-scenarios.json',
            scenarioId: scenarioContract.scenario.id,
            scenarioDigest: scenarioContract.digests.scenarioSha256,
            visualRuleId: scenarioContract.scenario.visualRule.id,
            visualRuleDigest: scenarioContract.digests.visualRuleSha256,
          },
          server: { origin, healthObservedByRunner: true },
          route: scenarioContract.scenario.route,
          selectors: selectorFacts,
          actions: scenarioContract.scenario.actions.map(({ kind, target }) => ({ kind, target, status: 'PASS' })),
          response: responseFacts,
          uiError: { testId: selectors.error, text: scenarioContract.scenario.responder.uiErrorText, visible: true },
        },
        artifacts: {
          screenshot: { locator: 'embedded:screenshot', mediaType: 'image/png', contentBase64: screenshot.toString('base64'), byteLength: screenshot.length, sha256: sha256Bytes(screenshot) },
          dom: embeddedArtifact('embedded:dom-observations', visual),
          transcript: embeddedArtifact('embedded:browser-transcript', transcript),
          console: embeddedArtifact('embedded:console-and-page-errors', consoleFacts),
        },
      },
    };
    const errors = await validateLocalChromeEvidence(evidence, scenarioContract, schema, {
      observedVersion: probeEvidence.run.runtime.fullVersion,
      expectedSubject,
    });
    assert(errors.length === 0, `LOCAL_CHROME_RUNTIME_SELF_VALIDATION_BLOCKED errors=${errors.join(';')}`);
  } catch (error) {
    executionError = error;
  }
  try {
    await cleanupSmokeResources(resources);
  } catch (error) {
    if (executionError) throw new Error(`${executionError.message};${error.message}`);
    throw error;
  }
  if (executionError) throw executionError;
  await writeJsonAtomic(outputPath, evidence);
  return evidence;
}

function parseArguments(argv) {
  const allowed = new Map([
    ['--output', 'output'],
    ['--subject-manifest', 'subjectManifest'],
    ['--subject-manifest-digest', 'subjectManifestDigest'],
    ['--tested-subject-digest', 'testedSubjectDigest'],
  ]);
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = allowed.get(argv[index]);
    const value = argv[index + 1];
    if (!key || !value || value.startsWith('--')) throw new Error('LOCAL_CHROME_SMOKE_ARGUMENT_INVALID');
    options[key] = value;
  }
  if (Object.values(Object.fromEntries([...allowed.values()].map((key) => [key, options[key]]))).some((value) => !value)) {
    throw new Error('LOCAL_CHROME_SMOKE_ARGUMENT_REQUIRED');
  }
  if (!/^[a-f0-9]{64}$/.test(options.subjectManifestDigest) || !/^[a-f0-9]{64}$/.test(options.testedSubjectDigest)) {
    throw new Error('LOCAL_CHROME_SMOKE_SUBJECT_DIGEST_INVALID');
  }
  return options;
}

async function main(argv) {
  try {
    const options = parseArguments(argv);
    const evidence = await runLocalChromeSmoke(options);
    console.log(`local_chrome_smoke=PASS path=${STANDARD_CHROME_PATH} version=${evidence.run.runtime.fullVersion} viewport=1440x900 scenario=LOGIN-SMOKE-V1 visual_rule=LOGIN-CARD-IN-VIEWPORT-V2 output=${options.output}`);
  } catch (error) {
    console.error(`local_chrome_smoke=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main(process.argv.slice(2));
