#!/usr/bin/env node

import { evaluateVisualRuleInPage } from './validate-browser-scenario.mjs';
import { contract, validateStructuralContractCases } from './test-browser-scenario-validator.mjs';

const selector = contract.scenario.selectors.card;
const rule = { ...contract.scenario.visualRule, requiredSelectorKeys: ['card'] };

const cases = [
  { id: 'known-good', expected: null, html: fixture() },
  { id: 'viewport-escape', expected: 'VISUAL_VIEWPORT_ESCAPE', html: fixture('target', 'position:fixed;left:-4px;top:100px') },
  {
    id: 'ancestor-overflow-clipping', expected: 'VISUAL_ANCESTOR_CLIP_X',
    html: fixture('target', 'width:120px', 'width:100px;overflow:hidden'),
  },
  { id: 'target-clip-path', expected: 'VISUAL_CLIP_PATH', html: fixture('target', 'clip-path:inset(2px)') },
  { id: 'ancestor-clip-path', expected: 'VISUAL_CLIP_PATH', html: fixture('target', '', 'clip-path:inset(2px)') },
  { id: 'target-mask', expected: 'VISUAL_MASK', html: fixture('target', '-webkit-mask-image:linear-gradient(black,black)') },
  { id: 'ancestor-mask', expected: 'VISUAL_MASK', html: fixture('target', '', '-webkit-mask-image:linear-gradient(black,black)') },
  { id: 'target-opacity-zero', expected: 'VISUAL_ZERO_EFFECTIVE_OPACITY', html: fixture('target', 'opacity:0') },
  { id: 'ancestor-hidden', expected: 'VISUAL_VISIBILITY_HIDDEN', html: fixture('target', '', 'visibility:hidden') },
  { id: 'transparent-overlay', expected: 'VISUAL_HIT_OBSTRUCTED', html: fixture('target', '', '', '<div style="position:absolute;left:0;top:0;width:120px;height:80px;z-index:10;background:transparent"></div>') },
  { id: 'opaque-corner-overlay', expected: 'VISUAL_HIT_OBSTRUCTED', html: fixture('target', '', '', '<div style="position:absolute;left:0;top:0;width:20px;height:20px;z-index:10;background:#000"></div>') },
  {
    id: 'normal-input-internal-clipping', expected: null,
    html: '<main style="padding:100px"><input data-testid="shared-auth-login-card" style="width:100px" value="this synthetic value is intentionally wider than the input border box"></main>',
  },
];

function fixture(tag = 'target', targetStyle = '', ancestorStyle = '', overlay = '') {
  return `<main style="padding:100px;position:relative"><section style="position:relative;${ancestorStyle}"><button data-testid="shared-auth-login-card" style="width:120px;height:80px;${targetStyle}">${tag}</button>${overlay}</section></main>`;
}

function assert(condition, diagnostic) {
  if (!condition) throw new Error(diagnostic);
}

async function validateLocalChromeVisualCases() {
  const [{ chromium: chromeBrowserType }, { observeStandardLocalChrome }] = await Promise.all([
    import('@playwright/test'),
    import('./test-browser-scenario-server.mjs'),
  ]);
  const chrome = await observeStandardLocalChrome();
  const browser = await chromeBrowserType.launch({ executablePath: chrome.path, headless: true });
  assert(browser.version() === chrome.fullVersion, `LOCAL_GOOGLE_CHROME_VERSION_MISMATCH expected=${chrome.fullVersion} actual=${browser.version()}`);
  try {
    for (const testCase of cases) {
      const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
      try {
        await page.setContent(testCase.html);
        const result = await page.evaluate(evaluateVisualRuleInPage, {
          selectors: { card: selector },
          visualRule: rule,
        });
        const diagnostics = result.failures.map((failure) => failure.diagnostic);
        if (testCase.expected === null) {
          assert(diagnostics.length === 0, `VISUAL_CASE_UNEXPECTED_FAILURE case=${testCase.id} diagnostics=${diagnostics.join(',')}`);
        } else {
          assert(diagnostics.includes(testCase.expected), `VISUAL_CASE_EXPECTED_DIAGNOSTIC_MISSING case=${testCase.id} expected=${testCase.expected} actual=${diagnostics.join(',')}`);
        }
        assert(result.observations.length === 1, `VISUAL_OBSERVATION_MISSING case=${testCase.id}`);
        assert(result.observations[0].ancestors.length > 0, `VISUAL_ANCESTOR_OBSERVATION_MISSING case=${testCase.id}`);
        assert(result.observations[0].hitTests.length === 5, `VISUAL_HIT_TEST_OBSERVATION_MISSING case=${testCase.id}`);
      } finally {
        await page.close();
      }
    }
  } finally {
    await browser.close();
  }
  return chrome;
}

async function main() {
  try {
    const argv = process.argv.slice(2);
    assert(argv.length === 1 && argv[0] === '--run-local-chrome', 'LOCAL_GOOGLE_CHROME_ARGUMENT_INVALID expected=--run-local-chrome');
    validateStructuralContractCases();
    const chrome = await validateLocalChromeVisualCases();
    console.log(`browser_scenario_visual_local_chrome_tests=PASS cases=${cases.length + 3} local_google_chrome=${chrome.fullVersion} viewport=1440x900 scenario_digest=${contract.digests.scenarioSha256} visual_rule_digest=${contract.digests.visualRuleSha256}`);
  } catch (error) {
    console.error(`browser_scenario_visual_local_chrome_tests=BLOCKED diagnostic=${String(error.message).replaceAll('\n', ';')}`);
    process.exitCode = 1;
  }
}

await main();
