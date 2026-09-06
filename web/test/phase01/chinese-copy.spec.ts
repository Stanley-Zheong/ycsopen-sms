// @ts-expect-error The frontend project intentionally does not install Node type declarations.
import { spawn } from 'node:child_process';
// @ts-expect-error The frontend project intentionally does not install Node type declarations.
import { mkdtemp, rm } from 'node:fs/promises';
// @ts-expect-error The frontend project intentionally does not install Node type declarations.
import { tmpdir } from 'node:os';
// @ts-expect-error The frontend project intentionally does not install Node type declarations.
import { join, resolve } from 'node:path';
// @ts-expect-error The frontend project intentionally does not install Node type declarations.
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
// @ts-expect-error The versioned copy validator is a repository-owned executable ESM module.
import { validateRuntimeObservation } from '../../scripts/validate-copy-zh-cn.mjs';
// @ts-expect-error The Plan 05 server is a repository-owned executable ESM module.
import { startBrowserScenarioServer } from '../../scripts/serve-browser-scenario.mjs';
import copyRegistry from '../../verification/copy.zh-CN.json' with { type: 'json' };

type ServerHandle = Awaited<ReturnType<typeof startBrowserScenarioServer>>;
declare const process: { env: Record<string, string | undefined> };
const webRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const repositoryRoot = resolve(webRoot, '..');
const scenarioPath = resolve(webRoot, 'verification/browser-scenarios.json');
let running: ServerHandle | undefined;
let temporaryRoot = '';

async function run(command: string, args: string[], cwd = repositoryRoot) {
  return new Promise<{ stdout: string }>((accept, reject) => {
    const child = spawn(command, args, { cwd, shell: false, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk: unknown) => { stdout += String(chunk); });
    child.stderr.on('data', (chunk: unknown) => { stderr += String(chunk); });
    child.once('error', reject);
    child.once('close', (code: number | null) => {
      if (code === 0) accept({ stdout });
      else reject(new Error(`COPY_RUNTIME_COMMAND_FAILED code=${code} output=${`${stdout}${stderr}`.trim().replace(/\n/g, ';')}`));
    });
  });
}

async function buildSubjectManifest(destination: string) {
  const ruby = [
    "require 'json'",
    "require File.join(Dir.pwd, 'scripts/lib/phase-01/run_checks')",
    "require File.join(Dir.pwd, '.planning/tools/verification-evidence')",
    'path = ARGV.fetch(0)',
    'manifest = VerificationEvidence.build_subject_manifest(root: Dir.pwd, registries: Phase01RunChecks.subject_registries, manifest_path: path)',
    "puts JSON.generate({'subject_manifest_digest' => VerificationEvidence.subject_manifest_digest(manifest), 'tested_subject_digest' => VerificationEvidence.tested_subject_digest(manifest.fetch('inputs'))})",
  ].join(';');
  const result = await run('/usr/bin/env', ['ruby', '-e', ruby, destination]);
  const lines = result.stdout.trim().split('\n');
  return JSON.parse(lines[lines.length - 1]) as {
    subject_manifest_digest: string;
    tested_subject_digest: string;
  };
}

test.beforeAll(async () => {
  const configuredBaseURL = process.env.PHASE01_BASE_URL;
  if (!configuredBaseURL) throw new Error('COPY_RUNTIME_BASE_URL_REQUIRED');
  const origin = new URL(configuredBaseURL);
  if (origin.protocol !== 'http:' || origin.hostname !== '127.0.0.1' || origin.pathname !== '/') {
    throw new Error('COPY_RUNTIME_BASE_URL_NOT_LOOPBACK');
  }
  const port = Number(origin.port);
  if (!Number.isInteger(port) || port <= 0 || port > 65_535) throw new Error('COPY_RUNTIME_BASE_URL_PORT_INVALID');
  try {
    const healthResponse = await fetch(`${origin.href}__phase01/health`);
    if (!healthResponse.ok) throw new Error(`COPY_RUNTIME_EXISTING_SERVER_INVALID status=${healthResponse.status}`);
    const health = await healthResponse.json() as { scenario_id?: string };
    if (health.scenario_id !== 'LOGIN-SMOKE-V1') throw new Error('COPY_RUNTIME_EXISTING_SERVER_SCENARIO_INVALID');
    return;
  } catch (error) {
    if (!(error instanceof TypeError)) throw error;
  }
  await run('/usr/bin/env', ['npm', '--prefix', 'web', 'run', 'build']);
  temporaryRoot = await mkdtemp(join(tmpdir(), 'ycsopen-sms-copy-runtime-'));
  const subjectPath = join(temporaryRoot, 'tested-inputs.json');
  const digests = await buildSubjectManifest(subjectPath);
  running = await startBrowserScenarioServer([
    '--host', '127.0.0.1',
    '--port', String(port),
    '--dist', resolve(webRoot, 'dist'),
    '--contract', scenarioPath,
    '--subject-manifest', subjectPath,
    '--subject-manifest-digest', digests.subject_manifest_digest,
    '--tested-subject-digest', digests.tested_subject_digest,
  ]);
});

test.afterAll(async () => {
  if (running) await new Promise<void>((accept) => running!.server.close(() => accept()));
  if (temporaryRoot) await rm(temporaryRoot, { recursive: true, force: true });
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-TRACE-003 PW-FOUND-LOGIN-SMOKE COPY-ZH-CN-V1', async ({ page }) => {
  const registry = structuredClone(copyRegistry);
  await page.goto(registry.runtime.route, { waitUntil: 'networkidle' });
  await expect(page.getByTestId(registry.runtime.rootSelector)).toBeVisible();
  const username = page.getByTestId('shared-auth-login-username');
  const password = page.getByTestId('shared-auth-login-password');
  const submit = page.getByTestId('shared-auth-login-submit');
  await username.fill('phase01.synthetic.copy.user');
  await password.fill('Phase01-Synthetic-Copy-Only!');

  const responsePromise = page.waitForResponse((response) => (
    response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/console/auth/login'
  ));
  await submit.click();
  const response = await responsePromise;
  const responseBody = await response.json();
  const error = page.getByTestId(registry.runtime.errorSelector);
  await expect(error).toHaveText('用户名或密码错误，或账号已被锁定');

  const observation = await page.getByTestId(registry.runtime.rootSelector).evaluate((root, requiredSelectors) => {
    const entries: Array<{ kind: string; value: string }> = [];
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const parent = node.parentElement;
      const value = node.textContent?.replace(/\s+/g, ' ').trim() ?? '';
      if (value && parent && !['SCRIPT', 'STYLE'].includes(parent.tagName)) entries.push({ kind: 'text', value });
    }
    for (const element of root.querySelectorAll('*')) {
      for (const kind of ['aria-label', 'placeholder', 'title', 'alt']) {
        const value = element.getAttribute(kind)?.replace(/\s+/g, ' ').trim();
        if (value) entries.push({ kind, value });
      }
    }
    return {
      route: location.pathname,
      selectors: requiredSelectors.filter((selector) => root.ownerDocument.querySelector(`[data-testid="${selector}"]`)),
      entries,
    };
  }, registry.runtime.requiredSelectors);

  const result = validateRuntimeObservation(registry, {
    ...observation,
    errorSelector: await error.getAttribute('data-testid'),
    responder: {
      status: response.status(),
      marker: {
        name: registry.runtime.responder.marker.name,
        value: response.headers()[registry.runtime.responder.marker.name.toLowerCase()],
      },
      body: responseBody,
    },
  });
  expect(result).toEqual(expect.objectContaining({
    contractId: 'COPY-ZH-CN-V1',
    classification: 'foundation-fixture',
    productAcceptance: false,
    route: '/login',
  }));
});
