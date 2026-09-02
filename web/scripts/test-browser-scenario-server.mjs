#!/usr/bin/env node

import { lstat, mkdir, mkdtemp, readFile, realpath, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { runBoundedCommand } from './probe-local-chrome.mjs';
import { closeBrowserScenarioServer, prepareServer, startBrowserScenarioServer } from './serve-browser-scenario.mjs';

const webRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repositoryRoot = resolve(webRoot, '..');
const contractPath = resolve(webRoot, 'verification/browser-scenarios.json');
const standardChromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

async function run(command, args, options = {}) {
  const result = await runBoundedCommand(command, args, {
    cwd: options.cwd ?? repositoryRoot,
    env: options.env ?? process.env,
    timeoutMs: options.timeoutMs ?? 120_000,
    maxOutputBytes: 256 * 1024,
  });
  if (result.timedOut) throw new Error(`COMMAND_TIMEOUT command=${basename(command)}`);
  if (result.overflow) throw new Error(`COMMAND_OUTPUT_TOO_LARGE command=${basename(command)}`);
  if (result.code !== 0 && !options.allowFailure) {
    throw new Error(`COMMAND_FAILED command=${basename(command)} code=${result.code} output=${`${result.stdout}${result.stderr}`.trim().replaceAll('\n', ';')}`);
  }
  return result;
}

async function buildSubjectManifest(destination) {
  const ruby = [
    "require 'json'",
    "require File.join(Dir.pwd, 'scripts/lib/phase-01/run_checks')",
    "require File.join(Dir.pwd, '.planning/tools/verification-evidence')",
    'path = ARGV.fetch(0)',
    'manifest = VerificationEvidence.build_subject_manifest(root: Dir.pwd, registries: Phase01RunChecks.subject_registries, manifest_path: path)',
    "puts JSON.generate({'subject_manifest_digest' => VerificationEvidence.subject_manifest_digest(manifest), 'tested_subject_digest' => VerificationEvidence.tested_subject_digest(manifest.fetch('inputs'))})",
  ].join(';');
  const result = await run('/usr/bin/env', ['ruby', '-e', ruby, destination]);
  return JSON.parse(result.stdout.trim().split('\n').at(-1));
}

function serverArgv(subjectPath, digests, port = 0, dist = resolve(webRoot, 'dist')) {
  return [
    '--host', '127.0.0.1', '--port', String(port), '--dist', dist,
    '--contract', contractPath, '--subject-manifest', subjectPath,
    '--subject-manifest-digest', digests.subject_manifest_digest,
    '--tested-subject-digest', digests.tested_subject_digest,
  ];
}

async function testServerContracts(tempRoot) {
  const subjectPath = join(tempRoot, 'tested-inputs.json');
  const digests = await buildSubjectManifest(subjectPath);
  const running = await startBrowserScenarioServer(serverArgv(subjectPath, digests));
  const baseURL = `http://127.0.0.1:${running.address.port}`;
  try {
    const health = await (await fetch(`${baseURL}/__phase01/health`)).json();
    const scenario = JSON.parse(await readFile(contractPath, 'utf8'));
    const expectedHealth = {
      scenario_id: 'LOGIN-SMOKE-V1',
      subject_manifest_digest: digests.subject_manifest_digest,
      tested_subject_digest: digests.tested_subject_digest,
      scenario_contract_digest: (await import('./validate-browser-scenario.mjs')).canonicalDigest(scenario.scenario),
    };
    assert(JSON.stringify(health) === JSON.stringify(expectedHealth), 'SERVER_HEALTH_CONTRACT_MISMATCH');
    for (const path of ['/console/auth/login', '/api/v1/console/auth/login']) {
      const response = await fetch(`${baseURL}${path}`, {
        method: 'POST',
        body: JSON.stringify({ username: 'phase01.synthetic.user', password: 'redacted-synthetic-value' }),
      });
      assert(response.status === 401, `SERVER_RESPONDER_STATUS_MISMATCH path=${path}`);
      assert(response.headers.get('content-type') === 'application/json', `SERVER_RESPONDER_CONTENT_TYPE_MISMATCH path=${path}`);
      assert(response.headers.get('x-ycs-scenario') === 'LOGIN-SMOKE-V1', `SERVER_RESPONDER_MARKER_MISMATCH path=${path}`);
      assert(JSON.stringify(await response.json()) === JSON.stringify({ code: 'AUTH_INVALID_CREDENTIALS', message: '用户名或密码错误' }), `SERVER_RESPONDER_BODY_MISMATCH path=${path}`);
    }
  } finally {
    await closeBrowserScenarioServer(running);
  }
  await expectBlocked(serverArgv(subjectPath, { ...digests, subject_manifest_digest: '0'.repeat(64) }), 'SERVER_SUBJECT_MANIFEST_DIGEST_MISMATCH');
  await expectBlocked(serverArgv(subjectPath, { ...digests, tested_subject_digest: '0'.repeat(64) }), 'SERVER_TESTED_SUBJECT_DIGEST_MISMATCH');
  return { subjectPath, digests };
}

async function expectNotServed(subjectPath, digests, dist, requestPath, secret) {
  const running = await startBrowserScenarioServer(serverArgv(subjectPath, digests, 0, dist));
  try {
    const response = await fetch(`http://127.0.0.1:${running.address.port}${requestPath}`);
    const body = await response.text();
    assert(response.status === 404, `SERVER_UNSAFE_STATIC_STATUS actual=${response.status} path=${requestPath}`);
    assert(!body.includes(secret), `SERVER_UNSAFE_STATIC_BYTES_EXPOSED path=${requestPath}`);
  } finally {
    await closeBrowserScenarioServer(running);
  }
}

async function testStaticSymlinkBoundaries(tempRoot, subjectPath, digests) {
  const secret = 'PHASE01-STATIC-SECRET-CANARY';
  const outsideFile = join(tempRoot, 'outside-secret.txt');
  const outsideDirectory = join(tempRoot, 'outside-directory');
  await writeFile(outsideFile, secret);
  await mkdir(outsideDirectory);
  await writeFile(join(outsideDirectory, 'nested-secret.txt'), secret);

  const fileDist = join(tempRoot, 'file-symlink-dist');
  await mkdir(fileDist);
  await writeFile(join(fileDist, 'index.html'), '<!doctype html><title>safe</title>');
  await symlink(outsideFile, join(fileDist, 'leak.txt'));
  await expectNotServed(subjectPath, digests, fileDist, '/leak.txt', secret);

  const directoryDist = join(tempRoot, 'directory-symlink-dist');
  await mkdir(directoryDist);
  await writeFile(join(directoryDist, 'index.html'), '<!doctype html><title>safe</title>');
  await symlink(outsideDirectory, join(directoryDist, 'outside'));
  await expectNotServed(subjectPath, digests, directoryDist, '/outside/nested-secret.txt', secret);

  const fallbackDist = join(tempRoot, 'fallback-symlink-dist');
  await mkdir(fallbackDist);
  await symlink(outsideFile, join(fallbackDist, 'index.html'));
  await expectNotServed(subjectPath, digests, fallbackDist, '/missing-route', secret);
  console.log('browser_scenario_symlink_tests=PASS cases=3');
}

async function expectBlocked(argv, diagnostic) {
  try {
    await prepareServer(argv);
    throw new Error(`EXPECTED_BLOCKED diagnostic=${diagnostic}`);
  } catch (error) {
    assert(String(error.message).includes(diagnostic), `WRONG_BLOCKED_DIAGNOSTIC expected=${diagnostic} actual=${error.message}`);
  }
}

export async function observeStandardLocalChrome() {
  const info = await lstat(standardChromePath);
  assert(info.isFile() && !info.isSymbolicLink(), 'LOCAL_GOOGLE_CHROME_NOT_REGULAR');
  assert(await realpath(standardChromePath) === standardChromePath, 'LOCAL_GOOGLE_CHROME_PATH_NOT_CANONICAL');
  const identity = await run(standardChromePath, ['--version'], { allowFailure: true });
  const output = `${identity.stdout}${identity.stderr}`.trim();
  const match = output.match(/^Google Chrome (\d+\.\d+\.\d+\.\d+)$/);
  assert(identity.code === 0 && match, `LOCAL_GOOGLE_CHROME_IDENTITY_INVALID actual=${output}`);
  return { path: standardChromePath, fullVersion: match[1], major: Number(match[1].split('.')[0]) };
}

async function runPlaywright(subject) {
  const chrome = await observeStandardLocalChrome();
  const running = await startBrowserScenarioServer(serverArgv(subject.subjectPath, subject.digests));
  const baseURL = `http://127.0.0.1:${running.address.port}`;
  try {
    const result = await run(process.execPath, [
      resolve(webRoot, 'node_modules/@playwright/test/cli.js'),
      'test', '--config', resolve(webRoot, 'playwright.config.ts'),
    ], {
      cwd: webRoot,
      env: { ...process.env, PHASE01_BASE_URL: baseURL },
      allowFailure: true,
    });
    process.stdout.write(result.stdout);
    process.stderr.write(result.stderr);
    assert(result.code === 0, 'PLAYWRIGHT_LOCAL_GOOGLE_CHROME_SCENARIO_FAILED');
  } finally {
    await closeBrowserScenarioServer(running);
  }
  return chrome;
}

function assert(condition, diagnostic) {
  if (!condition) throw new Error(diagnostic);
}

function parseRunPlaywrightArgument(argv) {
  if (argv.length === 0) return false;
  assert(
    argv.length === 1 && argv[0] === '--run-playwright',
    'BROWSER_SCENARIO_SERVER_ARGUMENT_INVALID expected=<none>|--run-playwright',
  );
  return true;
}

async function main() {
  let tempRoot = null;
  try {
    // Parse before creating runtime state or entering any local-browser path.
    const runPlaywrightRequested = parseRunPlaywrightArgument(process.argv.slice(2));
    tempRoot = await mkdtemp(join(tmpdir(), 'ycsopen-sms-plan05-'));
    const subject = await testServerContracts(tempRoot);
    await testStaticSymlinkBoundaries(tempRoot, subject.subjectPath, subject.digests);
    const chrome = runPlaywrightRequested ? await runPlaywright(subject) : null;
    console.log(`browser_scenario_server_tests=PASS real_http=true local_google_chrome=${chrome?.fullVersion ?? 'not-run'} viewport=${chrome ? '1440x900' : 'not-run'}`);
  } catch (error) {
    console.error(`browser_scenario_server_tests=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  } finally {
    if (tempRoot) await rm(tempRoot, { recursive: true, force: true });
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main();
