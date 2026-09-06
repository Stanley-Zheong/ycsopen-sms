#!/usr/bin/env node

import { createServer } from 'node:http';
import { lstat, readFile, realpath } from 'node:fs/promises';
import { extname, isAbsolute, join, normalize, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { runBoundedCommand } from './probe-local-chrome.mjs';
import { canonicalDigest, loadScenarioContract } from './validate-browser-scenario.mjs';

const webRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repositoryRoot = resolve(webRoot, '..');
const SUBJECT_VALIDATOR_TIMEOUT_MS = 30_000;
const SUBJECT_VALIDATOR_MAX_OUTPUT_BYTES = 64 * 1024;
const mimeTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.png', 'image/png'],
]);

function parseArguments(argv) {
  const options = {};
  const allowed = new Map([
    ['--host', 'host'], ['--port', 'port'], ['--dist', 'dist'], ['--contract', 'contract'],
    ['--subject-manifest', 'subjectManifest'], ['--subject-manifest-digest', 'subjectManifestDigest'],
    ['--tested-subject-digest', 'testedSubjectDigest'],
  ]);
  for (let index = 0; index < argv.length; index += 2) {
    const key = allowed.get(argv[index]);
    const value = argv[index + 1];
    if (!key || !value || value.startsWith('--')) throw new Error(`SERVER_OPTION_INVALID argument=${argv[index] ?? '<missing>'}`);
    options[key] = value;
  }
  const required = [...allowed.values()];
  for (const key of required) if (!options[key]) throw new Error(`SERVER_OPTION_REQUIRED option=${key}`);
  if (options.host !== '127.0.0.1') throw new Error('SERVER_HOST_NOT_LOCAL_DIRECT');
  options.port = Number(options.port);
  if (!Number.isInteger(options.port) || options.port < 0 || options.port > 65_535) throw new Error('SERVER_PORT_INVALID');
  if (!/^[a-f0-9]{64}$/.test(options.subjectManifestDigest) || !/^[a-f0-9]{64}$/.test(options.testedSubjectDigest)) {
    throw new Error('SERVER_SUBJECT_DIGEST_INVALID');
  }
  return options;
}

async function requireRegularFile(path, diagnostic) {
  const info = await lstat(path);
  if (!info.isFile() || info.isSymbolicLink()) throw new Error(diagnostic);
  return realpath(path);
}

async function requireDirectory(path, diagnostic) {
  const info = await lstat(path);
  if (!info.isDirectory() || info.isSymbolicLink()) throw new Error(diagnostic);
  return realpath(path);
}

async function runSubjectValidator(subjectManifest) {
  const validator = resolve(repositoryRoot, '.planning/tools/validate-verification-evidence.rb');
  const result = await runBoundedCommand('/usr/bin/env', ['ruby', validator, '--root', repositoryRoot, '--subject', subjectManifest], {
    cwd: repositoryRoot,
    env: {
      PATH: process.env.PATH ?? '/usr/bin:/bin',
      LANG: 'en_US.UTF-8',
      LC_ALL: 'en_US.UTF-8',
    },
    timeoutMs: SUBJECT_VALIDATOR_TIMEOUT_MS,
    maxOutputBytes: SUBJECT_VALIDATOR_MAX_OUTPUT_BYTES,
  });
  if (result.timedOut) throw new Error('SERVER_SUBJECT_VALIDATION_TIMEOUT');
  if (result.overflow) throw new Error('SERVER_SUBJECT_VALIDATION_OUTPUT_TOO_LARGE');
  if (result.code !== 0) {
    const output = `${result.stdout}${result.stderr}`.trim().replaceAll('\n', ';');
    throw new Error(`SERVER_SUBJECT_VALIDATION_BLOCKED output=${output}`);
  }
}

function isContained(root, candidate) {
  const relation = relative(root, candidate);
  return relation === '' || (relation !== '..' && !relation.startsWith('../') && !isAbsolute(relation));
}

async function requireContainedStaticFile(dist, requested) {
  const candidate = resolve(dist, requested);
  if (!isContained(dist, candidate)) throw new Error('SERVER_STATIC_PATH_ESCAPE');
  const relation = relative(dist, candidate);
  let cursor = dist;
  for (const segment of relation.split('/').filter(Boolean)) {
    cursor = join(cursor, segment);
    const info = await lstat(cursor);
    if (info.isSymbolicLink()) throw new Error('SERVER_STATIC_SYMLINK_FORBIDDEN');
  }
  const info = await lstat(candidate);
  if (!info.isFile() || info.isSymbolicLink()) throw new Error('SERVER_STATIC_NOT_REGULAR');
  const canonical = await realpath(candidate);
  if (!isContained(dist, canonical)) throw new Error('SERVER_STATIC_CANONICAL_ESCAPE');
  return canonical;
}

async function selectStaticFile(dist, pathname) {
  let decoded;
  try { decoded = decodeURIComponent(pathname); } catch { throw new Error('SERVER_STATIC_PATH_ENCODING_INVALID'); }
  if (decoded.includes('\0') || decoded.includes('\\')) throw new Error('SERVER_STATIC_PATH_INVALID');
  const requested = decoded === '/' ? 'index.html' : normalize(decoded).replace(/^[/\\]+/, '');
  try {
    return await requireContainedStaticFile(dist, requested);
  } catch (error) {
    if (!['ENOENT', 'ENOTDIR', 'SERVER_STATIC_NOT_REGULAR'].includes(error?.code ?? error?.message)) throw error;
    return requireContainedStaticFile(dist, 'index.html');
  }
}

export async function prepareServer(argv) {
  const options = parseArguments(argv);
  const dist = await requireDirectory(resolve(options.dist), 'SERVER_DIST_INVALID');
  const contractPath = await requireRegularFile(resolve(options.contract), 'SERVER_CONTRACT_INVALID');
  const subjectManifest = await requireRegularFile(resolve(options.subjectManifest), 'SERVER_SUBJECT_MANIFEST_INVALID');
  if (relative(webRoot, contractPath).startsWith('..')) throw new Error('SERVER_CONTRACT_OUTSIDE_WEB_ROOT');
  await runSubjectValidator(subjectManifest);
  const subject = JSON.parse(await readFile(subjectManifest, 'utf8'));
  const actualManifestDigest = canonicalDigest(subject);
  const actualSubjectDigest = canonicalDigest(subject.inputs);
  if (actualManifestDigest !== options.subjectManifestDigest) throw new Error('SERVER_SUBJECT_MANIFEST_DIGEST_MISMATCH');
  if (actualSubjectDigest !== options.testedSubjectDigest) throw new Error('SERVER_TESTED_SUBJECT_DIGEST_MISMATCH');
  const contract = await loadScenarioContract(contractPath);
  return { ...options, dist, contractPath, subjectManifest, contract };
}

export async function startBrowserScenarioServer(argv) {
  const prepared = await prepareServer(argv);
  const { contract } = prepared;
  const responder = contract.scenario.responder;
  const health = {
    scenario_id: contract.scenario.id,
    subject_manifest_digest: prepared.subjectManifestDigest,
    tested_subject_digest: prepared.testedSubjectDigest,
    scenario_contract_digest: contract.digests.scenarioSha256,
  };

  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? '/', `http://${prepared.host}`);
      if (request.method === 'GET' && url.pathname === '/__phase01/health') {
        respondJson(response, 200, health);
        return;
      }
      if (request.method === responder.method && responder.mappedPaths.includes(url.pathname)) {
        request.resume();
        response.writeHead(responder.status, {
          'Content-Type': responder.contentType,
          [responder.marker.name]: responder.marker.value,
          'Cache-Control': 'no-store',
        });
        response.end(JSON.stringify(responder.body));
        return;
      }
      if (!['GET', 'HEAD'].includes(request.method ?? '')) {
        respondJson(response, 405, { code: 'METHOD_NOT_ALLOWED' });
        return;
      }
      const file = await selectStaticFile(prepared.dist, url.pathname);
      const body = await readFile(file);
      response.writeHead(200, {
        'Content-Type': mimeTypes.get(extname(file)) ?? 'application/octet-stream',
        'Cache-Control': 'no-store',
        'X-YCS-Scenario': contract.scenario.id,
      });
      response.end(request.method === 'HEAD' ? undefined : body);
    } catch {
      respondJson(response, 404, { code: 'NOT_FOUND' });
    }
  });
  await new Promise((accept, reject) => {
    server.once('error', reject);
    server.listen(prepared.port, prepared.host, accept);
  });
  return { server, prepared, address: server.address() };
}

export async function closeBrowserScenarioServer(running, options = {}) {
  const timeoutMs = options.timeoutMs ?? 5_000;
  const server = running?.server ?? running;
  if (!server || typeof server.close !== 'function') throw new Error('SERVER_CLEANUP_HANDLE_INVALID');
  const closing = new Promise((accept, reject) => {
    server.close((error) => error ? reject(error) : accept());
    server.closeIdleConnections?.();
  });
  let timer;
  try {
    await Promise.race([
      closing,
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('SERVER_CLEANUP_TIMEOUT')), timeoutMs); }),
    ]);
  } catch (error) {
    server.closeAllConnections?.();
    await Promise.race([
      closing.catch(() => {}),
      new Promise((accept) => setTimeout(accept, Math.min(timeoutMs, 1_000))),
    ]);
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

function respondJson(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(body));
}

async function main() {
  try {
    const running = await startBrowserScenarioServer(process.argv.slice(2));
    const port = typeof running.address === 'object' ? running.address.port : running.prepared.port;
    console.log(`browser_scenario_server=READY host=${running.prepared.host} port=${port} scenario_id=${running.prepared.contract.scenario.id}`);
  } catch (error) {
    console.error(`browser_scenario_server=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main();
