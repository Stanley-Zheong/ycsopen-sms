#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { lstat, open, realpath } from 'node:fs/promises';
import { dirname, isAbsolute, relative, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { inflateSync } from 'node:zlib';
import { inspectAndVersionLocalChrome, REQUIRED_VIEWPORT, STANDARD_CHROME_PATH } from './probe-local-chrome.mjs';

const webRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repositoryRoot = resolve(webRoot, '..');
export const DURABLE_SUBJECT_PATH = '.planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json';
export const PNG_RAW_MAX_BYTES = 16 * 1024 * 1024;
export const PNG_CANONICAL_BASE64_MAX_LENGTH = 4 * Math.ceil(PNG_RAW_MAX_BYTES / 3);
export const RUNTIME_JSON_MAX_BYTES = 32 * 1024 * 1024;
export const CONTRACT_JSON_MAX_BYTES = 1024 * 1024;
export const SUBJECT_JSON_MAX_BYTES = 4 * 1024 * 1024;

export function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonicalize(value[key])]));
  return value;
}

export function canonicalDigest(value) {
  return createHash('sha256').update(JSON.stringify(canonicalize(value))).digest('hex');
}

function schemaTypeMatches(value, expected) {
  const types = Array.isArray(expected) ? expected : [expected];
  return types.some((type) => {
    if (type === 'null') return value === null;
    if (type === 'integer') return Number.isInteger(value);
    if (type === 'array') return Array.isArray(value);
    if (type === 'object') return value !== null && typeof value === 'object' && !Array.isArray(value);
    return typeof value === type;
  });
}

function validateSchema(value, schema, path, errors, rootSchema = schema) {
  if (schema.$ref) {
    const segments = schema.$ref.replace(/^#\//, '').split('/');
    const referenced = segments.reduce((current, segment) => current?.[segment], rootSchema);
    if (!referenced) errors.push(`RUNTIME_SCHEMA_REF_INVALID path=${path}`);
    else validateSchema(value, referenced, path, errors, rootSchema);
    return;
  }
  if (Object.hasOwn(schema, 'const') && JSON.stringify(value) !== JSON.stringify(schema.const)) errors.push(`RUNTIME_SCHEMA_CONST path=${path}`);
  if (schema.enum && !schema.enum.some((candidate) => JSON.stringify(candidate) === JSON.stringify(value))) errors.push(`RUNTIME_SCHEMA_ENUM path=${path}`);
  if (schema.type && !schemaTypeMatches(value, schema.type)) {
    errors.push(`RUNTIME_SCHEMA_TYPE path=${path}`);
    return;
  }
  if (typeof value === 'string' && schema.maxLength !== undefined && value.length > schema.maxLength) errors.push(`RUNTIME_SCHEMA_MAX_LENGTH path=${path}`);
  if (typeof value === 'string' && schema.pattern && !new RegExp(schema.pattern).test(value)) errors.push(`RUNTIME_SCHEMA_PATTERN path=${path}`);
  if (typeof value === 'number' && schema.minimum !== undefined && value < schema.minimum) errors.push(`RUNTIME_SCHEMA_MINIMUM path=${path}`);
  if (typeof value === 'number' && schema.maximum !== undefined && value > schema.maximum) errors.push(`RUNTIME_SCHEMA_MAXIMUM path=${path}`);
  if (Array.isArray(value) && schema.minItems !== undefined && value.length < schema.minItems) errors.push(`RUNTIME_SCHEMA_MIN_ITEMS path=${path}`);
  if (Array.isArray(value) && schema.maxItems !== undefined && value.length > schema.maxItems) errors.push(`RUNTIME_SCHEMA_MAX_ITEMS path=${path}`);
  if (Array.isArray(value) && schema.items) value.forEach((child, index) => validateSchema(child, schema.items, `${path}[${index}]`, errors, rootSchema));
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const required of schema.required ?? []) if (!Object.hasOwn(value, required)) errors.push(`RUNTIME_SCHEMA_REQUIRED path=${path}.${required}`);
    if (schema.additionalProperties === false) {
      for (const key of Object.keys(value)) if (!Object.hasOwn(schema.properties ?? {}, key)) errors.push(`RUNTIME_SCHEMA_ADDITIONAL path=${path}.${key}`);
    }
    for (const [key, childSchema] of Object.entries(schema.properties ?? {})) {
      if (Object.hasOwn(value, key)) validateSchema(value[key], childSchema, `${path}.${key}`, errors, rootSchema);
    }
  }
}

function same(value, expected) {
  return JSON.stringify(value) === JSON.stringify(expected);
}

function exactKeys(value, expected, diagnostic, errors) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    errors.push(`${diagnostic}_TYPE_INVALID`);
    return false;
  }
  if (!same(Object.keys(value).sort(), [...expected].sort())) errors.push(`${diagnostic}_FIELDS_INVALID`);
  return true;
}

const PNG_COLOR_DEPTHS = new Map([
  [0, new Set([1, 2, 4, 8, 16])],
  [2, new Set([8, 16])],
  [3, new Set([1, 2, 4, 8])],
  [4, new Set([8, 16])],
  [6, new Set([8, 16])],
]);
const PNG_CHANNELS = new Map([[0, 1], [2, 3], [3, 1], [4, 2], [6, 4]]);
const PNG_CRITICAL_CHUNKS = new Set(['IHDR', 'PLTE', 'IDAT', 'IEND']);

function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) {
    value ^= byte;
    for (let bit = 0; bit < 8; bit += 1) value = (value >>> 1) ^ (0xedb88320 & -(value & 1));
  }
  return (value ^ 0xffffffff) >>> 0;
}

function pngPasses(width, height, interlace) {
  if (interlace === 0) return [[width, height]];
  const starts = [[0, 0], [4, 0], [0, 4], [2, 0], [0, 2], [1, 0], [0, 1]];
  const steps = [[8, 8], [8, 8], [4, 8], [4, 4], [2, 4], [2, 2], [1, 2]];
  return starts.map(([x, y], index) => {
    const [dx, dy] = steps[index];
    return [width <= x ? 0 : Math.ceil((width - x) / dx), height <= y ? 0 : Math.ceil((height - y) / dy)];
  });
}

function validatePngPixels(compressed, header, errors) {
  const { width, height, bitDepth, colorType, interlace } = header;
  const bitsPerPixel = PNG_CHANNELS.get(colorType) * bitDepth;
  const passes = pngPasses(width, height, interlace);
  const expectedLength = passes.reduce((total, [passWidth, passHeight]) => (
    total + (passWidth === 0 || passHeight === 0 ? 0 : passHeight * (1 + Math.ceil((passWidth * bitsPerPixel) / 8)))
  ), 0);
  let inflated;
  try {
    const result = inflateSync(compressed, { info: true, maxOutputLength: expectedLength + 1 });
    inflated = result.buffer;
    if (result.engine.bytesWritten !== compressed.length) errors.push('RUNTIME_SCREENSHOT_IDAT_TRAILING_DATA');
  } catch {
    errors.push('RUNTIME_SCREENSHOT_IDAT_DEFLATE_INVALID');
    return;
  }
  if (inflated.length !== expectedLength) {
    errors.push('RUNTIME_SCREENSHOT_SCANLINE_LENGTH_INVALID');
    return;
  }
  let offset = 0;
  for (const [passWidth, passHeight] of passes) {
    if (passWidth === 0 || passHeight === 0) continue;
    const rowLength = Math.ceil((passWidth * bitsPerPixel) / 8);
    for (let row = 0; row < passHeight; row += 1) {
      if (inflated[offset] > 4) errors.push('RUNTIME_SCREENSHOT_FILTER_INVALID');
      offset += rowLength + 1;
    }
  }
}

function inspectPng(bytes, errors) {
  const pngSignature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (bytes.length < 45 || bytes.length > 16 * 1024 * 1024 || !bytes.subarray(0, 8).equals(pngSignature)) {
    errors.push('RUNTIME_SCREENSHOT_PNG_INVALID');
    return;
  }
  let offset = 8;
  let chunkIndex = 0;
  let ihdrCount = 0;
  let plteCount = 0;
  let idatCount = 0;
  let iendCount = 0;
  let phase = 'EXPECT_IHDR';
  let paletteEntries = null;
  let header = null;
  let headerValid = false;
  const idatChunks = [];
  while (offset < bytes.length) {
    if (offset + 12 > bytes.length) {
      errors.push('RUNTIME_SCREENSHOT_PNG_TRUNCATED');
      return;
    }
    const length = bytes.readUInt32BE(offset);
    const typeBytes = bytes.subarray(offset + 4, offset + 8);
    const type = typeBytes.toString('ascii');
    const dataStart = offset + 8;
    const dataEnd = dataStart + length;
    const end = dataEnd + 4;
    if (end > bytes.length) {
      errors.push('RUNTIME_SCREENSHOT_PNG_TRUNCATED');
      return;
    }
    if (![...typeBytes].every((byte) => (byte >= 0x41 && byte <= 0x5a) || (byte >= 0x61 && byte <= 0x7a))) {
      errors.push('RUNTIME_SCREENSHOT_CHUNK_TYPE_INVALID');
      return;
    }
    if ((typeBytes[2] & 0x20) !== 0) errors.push('RUNTIME_SCREENSHOT_CHUNK_RESERVED_BIT_INVALID');
    const critical = (typeBytes[0] & 0x20) === 0;
    if (critical && !PNG_CRITICAL_CHUNKS.has(type)) errors.push(`RUNTIME_SCREENSHOT_UNKNOWN_CRITICAL_CHUNK type=${type}`);
    const expectedCrc = bytes.readUInt32BE(dataEnd);
    const actualCrc = crc32(bytes.subarray(offset + 4, dataEnd));
    const chunkCrcValid = actualCrc === expectedCrc;
    if (!chunkCrcValid) errors.push(`RUNTIME_SCREENSHOT_${type}_CRC_INVALID`);
    if (type === 'IHDR') {
      ihdrCount += 1;
      if (chunkIndex !== 0 || ihdrCount !== 1 || length !== 13) {
        errors.push('RUNTIME_SCREENSHOT_IHDR_INVALID');
        headerValid = false;
      } else {
        const dimensionsValid = bytes.readUInt32BE(offset + 8) === REQUIRED_VIEWPORT.width
          && bytes.readUInt32BE(offset + 12) === REQUIRED_VIEWPORT.height;
        if (!dimensionsValid) {
          errors.push('RUNTIME_SCREENSHOT_DIMENSIONS_INVALID');
        }
        header = {
          width: bytes.readUInt32BE(dataStart),
          height: bytes.readUInt32BE(dataStart + 4),
          bitDepth: bytes[dataStart + 8],
          colorType: bytes[dataStart + 9],
          compression: bytes[dataStart + 10],
          filter: bytes[dataStart + 11],
          interlace: bytes[dataStart + 12],
        };
        const validDepths = PNG_COLOR_DEPTHS.get(header.colorType);
        const formatValid = header.width > 0 && header.height > 0 && validDepths?.has(header.bitDepth)
          && header.compression === 0 && header.filter === 0 && [0, 1].includes(header.interlace);
        if (!formatValid) {
          errors.push('RUNTIME_SCREENSHOT_IHDR_INVALID');
        }
        headerValid = chunkCrcValid && dimensionsValid && Boolean(formatValid);
        phase = 'BEFORE_IDAT';
      }
    } else if (chunkIndex === 0) {
      errors.push('RUNTIME_SCREENSHOT_IHDR_INVALID');
      return;
    }
    if (type === 'PLTE') {
      plteCount += 1;
      if (plteCount > 1) errors.push('RUNTIME_SCREENSHOT_PLTE_COUNT_INVALID');
      if (idatCount > 0) errors.push('RUNTIME_SCREENSHOT_PLTE_ORDER_INVALID');
      const entries = length / 3;
      if (length === 0 || length % 3 !== 0 || entries > 256) errors.push('RUNTIME_SCREENSHOT_PLTE_INVALID');
      if (plteCount === 1 && Number.isInteger(entries) && entries >= 1 && entries <= 256) paletteEntries = entries;
    }
    if (type === 'IDAT') {
      if (phase === 'AFTER_IDAT') errors.push('RUNTIME_SCREENSHOT_IDAT_ORDER_INVALID');
      idatCount += 1;
      phase = 'IN_IDAT';
      idatChunks.push(bytes.subarray(dataStart, dataEnd));
    } else if (phase === 'IN_IDAT' && type !== 'IEND') {
      phase = 'AFTER_IDAT';
    }
    if (type === 'IEND') {
      iendCount += 1;
      if (length !== 0) errors.push('RUNTIME_SCREENSHOT_IEND_INVALID');
      if (idatCount === 0) errors.push('RUNTIME_SCREENSHOT_IEND_ORDER_INVALID');
      if (end !== bytes.length) errors.push('RUNTIME_SCREENSHOT_PNG_TRAILING_DATA');
      phase = 'ENDED';
      break;
    }
    offset = end;
    chunkIndex += 1;
  }
  if (ihdrCount !== 1 || !header) errors.push('RUNTIME_SCREENSHOT_IHDR_COUNT_INVALID');
  if (idatCount === 0) errors.push('RUNTIME_SCREENSHOT_IDAT_MISSING');
  if (iendCount !== 1 || phase !== 'ENDED') errors.push('RUNTIME_SCREENSHOT_IEND_COUNT_INVALID');
  if (ihdrCount !== 1 || !header || idatCount === 0 || iendCount !== 1) {
    errors.push('RUNTIME_SCREENSHOT_PNG_TRUNCATED');
    return;
  }
  if ((header.colorType === 3 && (paletteEntries === null || paletteEntries > 2 ** header.bitDepth))
    || ([0, 4].includes(header.colorType) && paletteEntries !== null)) errors.push('RUNTIME_SCREENSHOT_PLTE_INVALID');
  if (!headerValid) return;
  validatePngPixels(Buffer.concat(idatChunks), header, errors);
}

export function validateScreenshotEncodingBounds(screenshot, errors, limits = {}) {
  const maxRawBytes = limits.maxRawBytes ?? PNG_RAW_MAX_BYTES;
  const maxBase64Length = limits.maxBase64Length ?? PNG_CANONICAL_BASE64_MAX_LENGTH;
  if (!screenshot || typeof screenshot !== 'object' || Array.isArray(screenshot)) {
    errors.push('RUNTIME_SCREENSHOT_TYPE_INVALID');
    return false;
  }
  if (typeof screenshot.locator !== 'string' || typeof screenshot.mediaType !== 'string'
    || typeof screenshot.contentBase64 !== 'string' || !Number.isInteger(screenshot.byteLength)
    || typeof screenshot.sha256 !== 'string') {
    errors.push('RUNTIME_SCREENSHOT_FIELD_TYPE_INVALID');
    return false;
  }
  if (screenshot.contentBase64.length > maxBase64Length) {
    errors.push('RUNTIME_SCREENSHOT_BASE64_LIMIT_EXCEEDED');
    return false;
  }
  if (screenshot.byteLength < 1 || screenshot.byteLength > maxRawBytes) {
    errors.push(screenshot.byteLength > maxRawBytes
      ? 'RUNTIME_SCREENSHOT_BYTE_LENGTH_LIMIT_EXCEEDED'
      : 'RUNTIME_SCREENSHOT_INVALID');
    return false;
  }
  return true;
}

export function validateScreenshotArtifact(screenshot, errors, options = {}) {
  const limits = options.limits ?? {};
  if (!validateScreenshotEncodingBounds(screenshot, errors, limits)) return;
  const maxRawBytes = limits.maxRawBytes ?? PNG_RAW_MAX_BYTES;
  const adapters = {
    decode: (value) => Buffer.from(value, 'base64'),
    encode: (bytes) => bytes.toString('base64'),
    digest: (bytes) => createHash('sha256').update(bytes).digest('hex'),
    inspect: inspectPng,
    ...options.adapters,
  };
  let bytes;
  try {
    bytes = adapters.decode(screenshot.contentBase64);
  } catch {
    errors.push('RUNTIME_SCREENSHOT_BASE64_INVALID');
    return;
  }
  if (!Buffer.isBuffer(bytes)) {
    errors.push('RUNTIME_SCREENSHOT_BASE64_INVALID');
    return;
  }
  if (bytes.length > maxRawBytes) {
    errors.push('RUNTIME_SCREENSHOT_RAW_LIMIT_EXCEEDED');
    return;
  }
  if (adapters.encode(bytes) !== screenshot.contentBase64) errors.push('RUNTIME_SCREENSHOT_BASE64_NON_CANONICAL');
  if (screenshot.locator !== 'embedded:screenshot' || screenshot.mediaType !== 'image/png'
    || bytes.length <= 0 || bytes.length !== screenshot.byteLength) errors.push('RUNTIME_SCREENSHOT_INVALID');
  if (adapters.digest(bytes) !== screenshot.sha256) errors.push('RUNTIME_SCREENSHOT_DIGEST_MISMATCH');
  adapters.inspect(bytes, errors);
}

function validateArtifacts(artifacts, scenarioContract, errors) {
  if (!exactKeys(artifacts, ['screenshot', 'dom', 'transcript', 'console'], 'RUNTIME_ARTIFACT_SET', errors)) return;
  const screenshot = artifacts.screenshot;
  if (!exactKeys(screenshot, ['locator', 'mediaType', 'contentBase64', 'byteLength', 'sha256'], 'RUNTIME_SCREENSHOT', errors)) return;
  validateScreenshotArtifact(screenshot, errors);

  for (const key of ['dom', 'transcript', 'console']) {
    const artifact = artifacts[key];
    if (!exactKeys(artifact, ['locator', 'content', 'sha256'], `RUNTIME_${key.toUpperCase()}_ARTIFACT`, errors)) continue;
    if (canonicalDigest(artifact.content) !== artifact.sha256) errors.push(`RUNTIME_ARTIFACT_DIGEST_MISMATCH artifact=${key}`);
  }

  const visual = artifacts.dom?.content;
  if (visual?.ruleId !== scenarioContract.scenario.visualRule.id || !same(visual?.viewport, REQUIRED_VIEWPORT)) errors.push('RUNTIME_VISUAL_RULE_INVALID');
  if (!Array.isArray(visual?.failures) || visual.failures.length > 0) errors.push('RUNTIME_VISUAL_FAILURE');
  const required = scenarioContract.scenario.visualRule.requiredSelectorKeys;
  if (!Array.isArray(visual?.observations) || !same(visual.observations.map((entry) => entry.key), required)) {
    errors.push('RUNTIME_VISUAL_OBSERVATIONS_INCOMPLETE');
  }
  for (const observation of visual?.observations ?? []) {
    if (observation.testId !== scenarioContract.scenario.selectors[observation.key]) errors.push('RUNTIME_VISUAL_SELECTOR_MISMATCH');
    const rect = observation.rectangle ?? {};
    if (![rect.left, rect.top, rect.right, rect.bottom, rect.width, rect.height].every(Number.isFinite)
      || rect.width <= 0 || rect.height <= 0 || rect.left < 0 || rect.top < 0 || rect.right > 1440 || rect.bottom > 900) {
      errors.push('RUNTIME_VISUAL_RECT_INVALID');
    }
    if (!Array.isArray(observation.ancestors) || observation.ancestors.length === 0) errors.push('RUNTIME_ANCESTOR_OBSERVATION_MISSING');
    for (const ancestor of observation.ancestors ?? []) {
      if (ancestor.display === 'none' || ['hidden', 'collapse'].includes(ancestor.visibility) || ancestor.contentVisibility === 'hidden'
        || Number.parseFloat(ancestor.opacity) <= 0 || (ancestor.clipPath && ancestor.clipPath !== 'none') || (ancestor.maskImage && ancestor.maskImage !== 'none')) {
        errors.push('RUNTIME_ANCESTOR_STYLE_REJECTED');
      }
    }
    const points = observation.hitTests?.map((entry) => entry.name);
    if (!same(points, scenarioContract.scenario.visualRule.hitTest.points)) errors.push('RUNTIME_HIT_TEST_SET_INVALID');
    if (observation.hitTests?.some((entry) => entry.accepted !== true)) errors.push('RUNTIME_HIT_TEST_REJECTED');
  }

  const expectedTranscript = [
    ['navigate', '/login'], ['native-required-validation', 'username'], ['fill', 'username'], ['fill', 'password'],
    ['check', 'remember'], ['browser-response', '/api/v1/console/auth/login'], ['ui-error', 'shared-auth-login-error'],
    ['visual-rule', 'LOGIN-CARD-IN-VIEWPORT-V2'], ['screenshot', 'viewport'],
  ];
  const actualTranscript = artifacts.transcript?.content?.map((entry) => [entry.event, entry.target]);
  if (!same(actualTranscript, expectedTranscript) || artifacts.transcript?.content?.some((entry) => entry.status !== 'PASS')) errors.push('RUNTIME_TRANSCRIPT_INCOMPLETE');

  const consoleFacts = artifacts.console?.content;
  const expected401 = 'Failed to load resource: the server responded with a status of 401 (Unauthorized)';
  if (!Array.isArray(consoleFacts?.consoleEvents) || consoleFacts.consoleEvents.length > 1
    || consoleFacts.consoleEvents.some((entry) => entry.type !== 'error' || entry.text !== expected401)) errors.push('RUNTIME_CONSOLE_POLICY_FAILED');
  if (!Array.isArray(consoleFacts?.pageErrors) || consoleFacts.pageErrors.length > 0) errors.push('RUNTIME_PAGE_ERROR_POLICY_FAILED');
  if (!Array.isArray(consoleFacts?.requestFailures) || consoleFacts.requestFailures.length > 0) errors.push('RUNTIME_REQUEST_FAILURE_POLICY_FAILED');
}

export async function validateLocalChromeEvidence(evidence, scenarioContract, schema, options = {}) {
  const errors = [];
  const screenshot = evidence?.run?.artifacts?.screenshot;
  if (screenshot !== undefined && !validateScreenshotEncodingBounds(screenshot, errors)) return [...new Set(errors)];
  validateSchema(evidence, schema, '$', errors);
  if (evidence?.status !== 'PASS' || evidence?.diagnostic !== null) errors.push('RUNTIME_STATUS_NOT_PASS');
  const run = evidence?.run;
  if (run?.count !== 1) errors.push('RUNTIME_RUN_COUNT_INVALID');
  if (run?.matrix !== undefined || evidence?.matrix !== undefined) errors.push('RUNTIME_MATRIX_FORBIDDEN');
  const runtime = run?.runtime;
  if (runtime?.path !== STANDARD_CHROME_PATH || runtime?.canonicalPath !== STANDARD_CHROME_PATH || runtime?.fileType !== 'regular' || runtime?.executable !== true) errors.push('RUNTIME_PATH_INVALID');
  if (runtime?.brand !== 'Google Chrome') errors.push('RUNTIME_BRAND_INVALID');
  if (!/^\d+\.\d+\.\d+\.\d+$/.test(runtime?.fullVersion ?? '')) errors.push('RUNTIME_VERSION_INVALID');
  if (runtime?.major !== Number.parseInt(runtime?.fullVersion?.split('.')[0] ?? '', 10)) errors.push('RUNTIME_MAJOR_MISMATCH');
  if (runtime?.playwrightVersion !== runtime?.fullVersion) errors.push('RUNTIME_PLAYWRIGHT_VERSION_MISMATCH');
  if (!same(runtime?.viewport, REQUIRED_VIEWPORT)) errors.push('RUNTIME_VIEWPORT_INVALID');
  if (!same(runtime?.launch, { attempted: true, succeeded: true, headless: true, closed: true })) errors.push('RUNTIME_LAUNCH_INVALID');
  if (options.observedVersion && runtime?.fullVersion !== options.observedVersion) errors.push('RUNTIME_LIVE_VERSION_MISMATCH');

  const scenario = run?.scenario;
  if (scenario?.requestInterception !== undefined) errors.push('RUNTIME_REQUEST_INTERCEPTION_FORBIDDEN');
  const contract = scenario?.contract;
  if (contract?.scenarioId !== scenarioContract.scenario.id || contract?.scenarioDigest !== scenarioContract.digests.scenarioSha256) errors.push('RUNTIME_SCENARIO_DIGEST_MISMATCH');
  if (contract?.visualRuleId !== scenarioContract.scenario.visualRule.id || contract?.visualRuleDigest !== scenarioContract.digests.visualRuleSha256) errors.push('RUNTIME_VISUAL_RULE_DIGEST_MISMATCH');
  const subject = scenario?.subject;
  if (!/^[a-f0-9]{64}$/.test(subject?.manifestDigest ?? '') || !/^[a-f0-9]{64}$/.test(subject?.testedSubjectDigest ?? '')) errors.push('RUNTIME_SUBJECT_DIGEST_INVALID');
  const expectedSubject = options.expectedSubject;
  if (!expectedSubject
    || expectedSubject.manifestPath !== DURABLE_SUBJECT_PATH
    || !/^[a-f0-9]{64}$/.test(expectedSubject.manifestDigest ?? '')
    || !/^[a-f0-9]{64}$/.test(expectedSubject.testedSubjectDigest ?? '')) {
    errors.push('RUNTIME_EXPECTED_SUBJECT_INVALID');
  } else {
    if (subject?.manifestPath !== expectedSubject.manifestPath) errors.push('RUNTIME_SUBJECT_MANIFEST_PATH_MISMATCH');
    if (subject?.manifestDigest !== expectedSubject.manifestDigest) errors.push('RUNTIME_SUBJECT_MANIFEST_DIGEST_MISMATCH');
    if (subject?.testedSubjectDigest !== expectedSubject.testedSubjectDigest) errors.push('RUNTIME_TESTED_SUBJECT_DIGEST_MISMATCH');
  }
  if (subject?.health?.scenario_id !== scenarioContract.scenario.id || subject?.health?.scenario_contract_digest !== scenarioContract.digests.scenarioSha256
    || subject?.health?.subject_manifest_digest !== subject?.manifestDigest || subject?.health?.tested_subject_digest !== subject?.testedSubjectDigest) errors.push('RUNTIME_HEALTH_SUBJECT_MISMATCH');
  if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(scenario?.server?.origin ?? '') || scenario?.server?.healthObservedByRunner !== true) errors.push('RUNTIME_SERVER_NOT_LOOPBACK');
  if (scenario?.route !== '/login') errors.push('RUNTIME_ROUTE_INVALID');
  const selectors = Object.entries(scenarioContract.scenario.selectors).map(([key, testId]) => ({ key, testId, visible: true }));
  if (!same(scenario?.selectors, selectors)) errors.push('RUNTIME_SELECTOR_NOT_VISIBLE');
  const actions = scenarioContract.scenario.actions.map(({ kind, target }) => ({ kind, target, status: 'PASS' }));
  if (!same(scenario?.actions, actions)) errors.push('RUNTIME_ACTION_SEQUENCE_MISMATCH');
  const response = scenario?.response;
  if (response?.browserObserved !== true) errors.push('RUNTIME_RESPONSE_NOT_BROWSER_OBSERVED');
  if (response?.method !== 'POST' || response?.path !== '/api/v1/console/auth/login' || response?.status !== 401
    || response?.contentType !== 'application/json' || !same(response?.marker, scenarioContract.scenario.responder.marker)) errors.push('RUNTIME_RESPONSE_INVALID');
  if (!same(response?.body, scenarioContract.scenario.responder.body)) errors.push('RUNTIME_RESPONSE_BODY_INVALID');
  if (response?.bodySha256 !== canonicalDigest(response?.body)) errors.push('RUNTIME_RESPONSE_BODY_DIGEST_MISMATCH');
  if (!same(scenario?.uiError, { testId: scenarioContract.scenario.selectors.error, text: scenarioContract.scenario.responder.uiErrorText, visible: true })) errors.push('RUNTIME_UI_ERROR_INVALID');
  validateArtifacts(run?.artifacts, scenarioContract, errors);
  return [...new Set(errors)];
}

function parseArguments(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = new Map([
      ['--runtime', 'runtime'],
      ['--scenario', 'scenario'],
      ['--subject-manifest', 'subjectManifest'],
      ['--subject-manifest-digest', 'subjectManifestDigest'],
      ['--tested-subject-digest', 'testedSubjectDigest'],
    ]).get(argv[index]);
    if (!key || !argv[index + 1] || argv[index + 1].startsWith('--')) throw new Error('RUNTIME_VALIDATOR_ARGUMENT_INVALID');
    options[key] = argv[index + 1];
  }
  if (!options.runtime || !options.scenario || !options.subjectManifest || !options.subjectManifestDigest || !options.testedSubjectDigest) {
    throw new Error('RUNTIME_VALIDATOR_ARGUMENT_REQUIRED');
  }
  if (!/^[a-f0-9]{64}$/.test(options.subjectManifestDigest) || !/^[a-f0-9]{64}$/.test(options.testedSubjectDigest)) {
    throw new Error('RUNTIME_VALIDATOR_SUBJECT_DIGEST_INVALID');
  }
  return options;
}

function contained(root, candidate) {
  const relation = relative(root, candidate);
  return relation === '' || (relation !== '..' && !relation.startsWith('../') && !isAbsolute(relation));
}

export async function loadExpectedSubject(subjectManifestPath, subjectManifestDigest, testedSubjectDigest) {
  const root = await realpath(repositoryRoot);
  const requested = resolve(subjectManifestPath);
  const info = await lstat(requested);
  if (!info.isFile() || info.isSymbolicLink()) throw new Error('RUNTIME_SUBJECT_MANIFEST_NOT_REGULAR');
  const canonical = await realpath(requested);
  if (!contained(root, canonical)) throw new Error('RUNTIME_SUBJECT_MANIFEST_OUTSIDE_REPOSITORY');
  const manifestPath = relative(root, canonical).split('\\').join('/');
  if (manifestPath !== DURABLE_SUBJECT_PATH) throw new Error('RUNTIME_SUBJECT_MANIFEST_PATH_NOT_DURABLE');
  const manifest = await readJsonFileBounded(canonical, SUBJECT_JSON_MAX_BYTES, 'RUNTIME_SUBJECT_MANIFEST_SIZE_INVALID');
  if (canonicalDigest(manifest) !== subjectManifestDigest) throw new Error('RUNTIME_EXPECTED_MANIFEST_DIGEST_MISMATCH');
  if (canonicalDigest(manifest.inputs) !== testedSubjectDigest) throw new Error('RUNTIME_EXPECTED_TESTED_SUBJECT_DIGEST_MISMATCH');
  return { manifestPath, manifestDigest: subjectManifestDigest, testedSubjectDigest };
}

export async function readJsonFileBounded(path, maxBytes, diagnostic = 'RUNTIME_JSON_FILE_SIZE_INVALID') {
  if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0) throw new Error('RUNTIME_JSON_FILE_LIMIT_INVALID');
  const handle = await open(path, 'r');
  try {
    const info = await handle.stat();
    if (!info.isFile() || info.size < 1 || info.size > maxBytes) throw new Error(diagnostic);
    const bytes = Buffer.alloc(info.size);
    let offset = 0;
    while (offset < bytes.length) {
      const result = await handle.read(bytes, offset, bytes.length - offset, offset);
      if (result.bytesRead === 0) break;
      offset += result.bytesRead;
    }
    const extra = Buffer.alloc(1);
    const extraRead = await handle.read(extra, 0, 1, offset);
    if (offset !== bytes.length || extraRead.bytesRead !== 0) throw new Error(diagnostic);
    return JSON.parse(bytes.toString('utf8'));
  } finally {
    await handle.close();
  }
}

async function main(argv) {
  try {
    const options = parseArguments(argv);
    const runtimePath = resolve(options.runtime);
    const scenarioPath = resolve(options.scenario);
    const [evidence, scenarioContract, schema] = await Promise.all([
      readJsonFileBounded(runtimePath, RUNTIME_JSON_MAX_BYTES, 'RUNTIME_EVIDENCE_FILE_SIZE_INVALID'),
      readJsonFileBounded(scenarioPath, CONTRACT_JSON_MAX_BYTES, 'RUNTIME_SCENARIO_FILE_SIZE_INVALID'),
      readJsonFileBounded(resolve(dirname(scenarioPath), 'local-chrome-runtime.schema.json'), CONTRACT_JSON_MAX_BYTES,
        'RUNTIME_SCHEMA_FILE_SIZE_INVALID'),
    ]);
    const expectedSubject = await loadExpectedSubject(options.subjectManifest, options.subjectManifestDigest, options.testedSubjectDigest);
    const identity = await inspectAndVersionLocalChrome();
    const errors = await validateLocalChromeEvidence(evidence, scenarioContract, schema, { observedVersion: identity.fullVersion, expectedSubject });
    if (errors.length > 0) throw new Error(errors.join(';'));
    console.log(`local_chrome_runtime=PASS path=${STANDARD_CHROME_PATH} version=${evidence.run.runtime.fullVersion} viewport=1440x900 scenario=${evidence.run.scenario.contract.scenarioId} visual_rule=${evidence.run.scenario.contract.visualRuleId} screenshot_sha256=${evidence.run.artifacts.screenshot.sha256}`);
  } catch (error) {
    console.error(`local_chrome_runtime=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main(process.argv.slice(2));
