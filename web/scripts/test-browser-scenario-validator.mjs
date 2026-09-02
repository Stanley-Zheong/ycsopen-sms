#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { validateScenarioContract } from './validate-browser-scenario.mjs';

const webRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
export const contract = JSON.parse(await readFile(resolve(webRoot, 'verification/browser-scenarios.json'), 'utf8'));

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function assert(condition, diagnostic) {
  if (!condition) throw new Error(diagnostic);
}

export function validateStructuralContractCases() {
  assert(validateScenarioContract(contract).length === 0, 'SCENARIO_KNOWN_GOOD_REJECTED');
  const staleScenario = clone(contract);
  staleScenario.scenario.route = '/stale';
  assert(validateScenarioContract(staleScenario).some((entry) => entry.startsWith('SCENARIO_ROUTE_INVALID')), 'SCENARIO_STALE_ROUTE_NOT_REJECTED');
  assert(validateScenarioContract(staleScenario).some((entry) => entry.startsWith('SCENARIO_DIGEST_MISMATCH')), 'SCENARIO_STALE_DIGEST_NOT_REJECTED');
  const staleRule = clone(contract);
  staleRule.scenario.visualRule.hitTest.insetPixels = 3;
  assert(validateScenarioContract(staleRule).some((entry) => entry.startsWith('VISUAL_RULE_DIGEST_MISMATCH')), 'VISUAL_RULE_STALE_DIGEST_NOT_REJECTED');
}

function main() {
  try {
    validateStructuralContractCases();
    console.log(`browser_scenario_validator_tests=PASS cases=3 local_google_chrome=not-run viewport=not-run scenario_digest=${contract.digests.scenarioSha256} visual_rule_digest=${contract.digests.visualRuleSha256}`);
  } catch (error) {
    console.error(`browser_scenario_validator_tests=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();
