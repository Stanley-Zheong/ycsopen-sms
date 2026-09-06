#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { chmod, lstat, realpath, rename, rm, writeFile } from 'node:fs/promises';
import { basename, dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium as chromeBrowserType } from '@playwright/test';

export const STANDARD_CHROME_PATH = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
export const REQUIRED_VIEWPORT = Object.freeze({ width: 1440, height: 900 });
const VERSION_PATTERN = /^Google Chrome (\d+\.\d+\.\d+\.\d+)$/;
const MAX_OUTPUT_BYTES = 4096;
const COMMAND_TIMEOUT_MS = 10_000;
const COMMAND_TERM_GRACE_MS = 1_000;

function blocked(diagnostic, detail = '') {
  throw new Error(`${diagnostic}${detail ? ` detail=${detail}` : ''}`);
}

async function inspectStandardPath() {
  try {
    const info = await lstat(STANDARD_CHROME_PATH);
    return {
      exists: true,
      regular: info.isFile(),
      symbolicLink: info.isSymbolicLink(),
      executable: (info.mode & 0o111) !== 0,
      canonicalPath: await realpath(STANDARD_CHROME_PATH),
    };
  } catch (error) {
    if (error?.code === 'ENOENT') return { exists: false };
    throw error;
  }
}

function signalProcessGroup(child, signal) {
  if (!Number.isInteger(child.pid) || child.pid <= 0) return;
  try {
    process.kill(-child.pid, signal);
  } catch (error) {
    if (error?.code !== 'ESRCH') {
      try { child.kill(signal); } catch (fallbackError) {
        if (fallbackError?.code !== 'ESRCH') throw fallbackError;
      }
    }
  }
}

/**
 * Execute fixed argv without a shell and contain the complete descendant tree.
 * Timeout and output overflow both use TERM, a bounded grace period, then KILL;
 * resolution happens only after the process group's leader has emitted close.
 */
export async function runBoundedCommand(command, args, options = {}) {
  const maxOutputBytes = options.maxOutputBytes ?? MAX_OUTPUT_BYTES;
  const timeoutMs = options.timeoutMs ?? COMMAND_TIMEOUT_MS;
  const termGraceMs = options.termGraceMs ?? COMMAND_TERM_GRACE_MS;
  if (!Number.isInteger(maxOutputBytes) || maxOutputBytes <= 0
    || !Number.isInteger(timeoutMs) || timeoutMs <= 0
    || !Number.isInteger(termGraceMs) || termGraceMs <= 0) {
    blocked('BOUNDED_COMMAND_LIMIT_INVALID');
  }
  return new Promise((accept, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      shell: false,
      detached: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stdout = Buffer.alloc(0);
    let stderr = Buffer.alloc(0);
    let capturedBytes = 0;
    let overflow = false;
    let timedOut = false;
    let terminationReason = null;
    let killTimer;

    const requestTermination = (reason) => {
      if (terminationReason !== null) return;
      terminationReason = reason;
      signalProcessGroup(child, 'SIGTERM');
      killTimer = setTimeout(() => signalProcessGroup(child, 'SIGKILL'), termGraceMs);
    };
    const capture = (target, chunk) => {
      const remaining = Math.max(0, maxOutputBytes - capturedBytes);
      const kept = chunk.subarray(0, remaining);
      capturedBytes += kept.length;
      if (chunk.length > remaining) {
        overflow = true;
        requestTermination('output-overflow');
      }
      return Buffer.concat([target, kept]);
    };

    child.stdout.on('data', (chunk) => { stdout = capture(stdout, chunk); });
    child.stderr.on('data', (chunk) => { stderr = capture(stderr, chunk); });
    const timeoutTimer = setTimeout(() => {
      timedOut = true;
      requestTermination('timeout');
    }, timeoutMs);
    child.once('error', (error) => {
      clearTimeout(timeoutTimer);
      clearTimeout(killTimer);
      reject(error);
    });
    child.once('close', (code, signal) => {
      clearTimeout(timeoutTimer);
      clearTimeout(killTimer);
      accept({
        pid: child.pid,
        code,
        signal,
        stdout: stdout.toString('utf8'),
        stderr: stderr.toString('utf8'),
        timedOut,
        overflow,
        terminationReason,
        capturedBytes,
      });
    });
  });
}

async function fixedVersionCommand() {
  return runBoundedCommand(STANDARD_CHROME_PATH, ['--version'], {
    env: { PATH: '/usr/bin:/bin', LANG: 'en_US.UTF-8', LC_ALL: 'en_US.UTF-8' },
    maxOutputBytes: MAX_OUTPUT_BYTES,
    timeoutMs: COMMAND_TIMEOUT_MS,
    termGraceMs: COMMAND_TERM_GRACE_MS,
  });
}

async function fixedPlaywrightLaunch() {
  let browserServer;
  let browser;
  let context;
  let result;
  let executionError;
  const cleanupFailures = [];
  const closeWithin = async (label, action) => {
    let timer;
    try {
      await Promise.race([
        Promise.resolve().then(action),
        new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(`${label}_TIMEOUT`)), COMMAND_TIMEOUT_MS); }),
      ]);
    } catch (error) {
      cleanupFailures.push(`${label}:${error.message}`);
    } finally {
      clearTimeout(timer);
    }
  };
  try {
    browserServer = await chromeBrowserType.launchServer({ executablePath: STANDARD_CHROME_PATH, headless: true });
    browser = await chromeBrowserType.connect(browserServer.wsEndpoint());
    context = await browser.newContext({ viewport: REQUIRED_VIEWPORT });
    const page = await context.newPage();
    await page.setContent('<!doctype html><title>phase01 local Chrome launch</title>');
    const actualViewport = await page.evaluate(() => ({ width: globalThis.innerWidth, height: globalThis.innerHeight }));
    const browserVersion = browser.version();
    result = { succeeded: true, browserVersion, viewport: actualViewport, closed: true };
  } catch (error) {
    executionError = error;
  }
  if (context) await closeWithin('LOCAL_CHROME_PROBE_CONTEXT_CLOSE', () => context.close());
  if (browser) await closeWithin('LOCAL_CHROME_PROBE_BROWSER_CLOSE', () => browser.close());
  if (browserServer) {
    const child = browserServer.process();
    await closeWithin('LOCAL_CHROME_PROBE_BROWSER_SERVER_CLOSE', () => browserServer.close());
    if (child?.exitCode === null) await closeWithin('LOCAL_CHROME_PROBE_BROWSER_SERVER_KILL', () => browserServer.kill());
    if (child?.exitCode === null) cleanupFailures.push('LOCAL_CHROME_PROBE_BROWSER_PROCESS_SURVIVED');
  }
  if (cleanupFailures.length > 0) blocked('LOCAL_CHROME_PLAYWRIGHT_CLOSE_FAILED', cleanupFailures.join(','));
  if (executionError) throw executionError;
  return result;
}

export async function inspectAndVersionLocalChrome({ adapters = {} } = {}) {
  const active = {
    inspect: adapters.inspect ?? inspectStandardPath,
    version: adapters.version ?? fixedVersionCommand,
  };
  const inspected = await active.inspect(STANDARD_CHROME_PATH);
  if (!inspected.exists) blocked('LOCAL_CHROME_PATH_MISSING');
  if (inspected.symbolicLink) blocked('LOCAL_CHROME_PATH_SYMLINK_FORBIDDEN');
  if (!inspected.regular) blocked('LOCAL_CHROME_NOT_REGULAR');
  if (!inspected.executable) blocked('LOCAL_CHROME_NOT_EXECUTABLE');
  if (inspected.canonicalPath !== STANDARD_CHROME_PATH) blocked('LOCAL_CHROME_PATH_NOT_CANONICAL');

  const identity = await active.version(STANDARD_CHROME_PATH, ['--version'], { shell: false, maxOutputBytes: MAX_OUTPUT_BYTES, timeoutMs: COMMAND_TIMEOUT_MS });
  if (identity.timedOut) blocked('LOCAL_CHROME_VERSION_COMMAND_TIMEOUT');
  if (identity.overflow) blocked('LOCAL_CHROME_VERSION_OUTPUT_TOO_LARGE');
  if (identity.code !== 0) blocked('LOCAL_CHROME_VERSION_COMMAND_FAILED', String(identity.code));
  const output = `${identity.stdout}${identity.stderr}`.trim();
  const match = output.match(VERSION_PATTERN);
  if (!match) blocked('LOCAL_CHROME_IDENTITY_INVALID', output.slice(0, 160));
  return {
    inspected,
    brand: 'Google Chrome',
    fullVersion: match[1],
    major: Number.parseInt(match[1].split('.')[0], 10),
  };
}

export async function probeLocalChrome(options = {}) {
  if (options.executablePath !== undefined) blocked('LOCAL_CHROME_PATH_OVERRIDE_FORBIDDEN');
  const adapters = options.adapters ?? {};
  const identity = await inspectAndVersionLocalChrome({ adapters });
  let launch;
  try {
    launch = await (adapters.launch ?? fixedPlaywrightLaunch)({ executablePath: STANDARD_CHROME_PATH, viewport: REQUIRED_VIEWPORT, headless: true });
  } catch (error) {
    blocked('LOCAL_CHROME_PLAYWRIGHT_LAUNCH_FAILED', String(error.message).slice(0, 160));
  }
  if (!launch?.succeeded) blocked('LOCAL_CHROME_PLAYWRIGHT_LAUNCH_FAILED');
  if (launch.browserVersion !== identity.fullVersion) blocked('LOCAL_CHROME_PLAYWRIGHT_VERSION_MISMATCH');
  if (launch.viewport?.width !== REQUIRED_VIEWPORT.width || launch.viewport?.height !== REQUIRED_VIEWPORT.height) {
    blocked('LOCAL_CHROME_PLAYWRIGHT_VIEWPORT_MISMATCH');
  }
  if (!launch.closed) blocked('LOCAL_CHROME_PLAYWRIGHT_CLOSE_FAILED');

  return {
    schemaVersion: 'phase01-local-chrome-runtime-v1',
    generatedAt: new Date().toISOString(),
    status: 'PROBED',
    diagnostic: null,
    run: {
      id: 'LOGIN-SMOKE-V1-local-google-chrome-1440x900',
      count: 1,
      command: {
        version: { executable: STANDARD_CHROME_PATH, argv: ['--version'], shell: false },
        playwright: { package: '@playwright/test', api: 'chromium.launch', executable: STANDARD_CHROME_PATH, project: 'local-google-chrome', shell: false },
      },
      runtime: {
        path: STANDARD_CHROME_PATH,
        canonicalPath: identity.inspected.canonicalPath,
        fileType: 'regular',
        executable: true,
        brand: identity.brand,
        fullVersion: identity.fullVersion,
        major: identity.major,
        playwrightVersion: launch.browserVersion,
        viewport: { ...REQUIRED_VIEWPORT },
        launch: { attempted: true, succeeded: true, headless: true, closed: true },
      },
      scenario: null,
      artifacts: null,
    },
  };
}

export function validatePlaywrightConfigSource(source) {
  if (!source.includes(`const standardChromePath = '${STANDARD_CHROME_PATH}';`)) blocked('LOCAL_CHROME_CONFIG_PATH_INVALID');
  if (!source.includes('launchOptions: { executablePath: standardChromePath }')) blocked('LOCAL_CHROME_CONFIG_LAUNCH_INVALID');
  if (!source.includes('viewport: { width: 1440, height: 900 }')) blocked('LOCAL_CHROME_CONFIG_VIEWPORT_INVALID');
  if (/process\.env\.[A-Z0-9_]*(?:CHROME|BROWSER)|channel\s*:|browserName\s*:|\b(?:firefox|webkit)\b/i.test(source)) {
    blocked('LOCAL_CHROME_CONFIG_SUBSTITUTION_FORBIDDEN');
  }
  const projects = [...source.matchAll(/name:\s*['"]([^'"]+)['"]/g)].map((match) => match[1]);
  if (projects.length !== 1 || projects[0] !== 'local-google-chrome') blocked('LOCAL_CHROME_CONFIG_PROJECT_INVALID');
  return true;
}

export async function writeJsonAtomic(outputPath, value) {
  const destination = resolve(outputPath);
  const temporary = resolve(dirname(destination), `.${basename(destination)}.${process.pid}.${Date.now()}.tmp`);
  try {
    await writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, { encoding: 'utf8', mode: 0o600, flag: 'wx' });
    await chmod(temporary, 0o600);
    await rename(temporary, destination);
  } catch (error) {
    await rm(temporary, { force: true });
    throw error;
  }
}

function parseOutput(argv) {
  if (argv.length !== 2 || argv[0] !== '--output' || !argv[1] || argv[1].startsWith('--')) blocked('LOCAL_CHROME_OUTPUT_ARGUMENT_INVALID');
  return argv[1];
}

async function main(argv) {
  try {
    const output = parseOutput(argv);
    const evidence = await probeLocalChrome();
    await writeJsonAtomic(output, evidence);
    console.log(`local_chrome_probe=PASS path=${STANDARD_CHROME_PATH} version=${evidence.run.runtime.fullVersion} viewport=1440x900 launch=PASS output=${output}`);
  } catch (error) {
    console.error(`local_chrome_probe=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main(process.argv.slice(2));
