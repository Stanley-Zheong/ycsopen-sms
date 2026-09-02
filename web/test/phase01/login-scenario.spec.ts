import { expect, test } from '@playwright/test';
// @ts-expect-error The canonical evaluator is an executable repository-owned ESM validator.
import { evaluateVisualRuleInPage } from '../../scripts/validate-browser-scenario.mjs';
import scenarioDocument from '../../verification/browser-scenarios.json' with { type: 'json' };

type ScenarioContract = {
  scenario: {
    id: string;
    route: string;
    selectors: Record<string, string>;
    actions: Array<{ kind: string; target: string; value?: string }>;
    responder: {
      mappedPaths: string[];
      status: number;
      marker: { name: string; value: string };
      body: { code: string; message: string };
      uiErrorText: string;
    };
    visualRule: Record<string, unknown>;
  };
  digests: { scenarioSha256: string; visualRuleSha256: string };
};

const contract = scenarioDocument as ScenarioContract;

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-PAGE PW-FOUND-LOGIN-PAGE', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-page')).toBeVisible();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-CARD PW-FOUND-LOGIN-CARD', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-card')).toBeVisible();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-USERNAME PW-FOUND-LOGIN-USERNAME', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-username')).toBeVisible();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-PASSWORD PW-FOUND-LOGIN-PASSWORD', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-password')).toBeVisible();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-REMEMBER PW-FOUND-LOGIN-REMEMBER', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-remember')).toBeVisible();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-SUBMIT PW-FOUND-LOGIN-SUBMIT', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-submit')).toBeEnabled();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-LOGIN-ERROR PW-FOUND-LOGIN-ERROR', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByTestId('shared-auth-login-error')).toBeHidden();
});

test('OBL-FOUND-TRACE-003 CASE-FOUND-TRACE-003 PW-FOUND-LOGIN-SMOKE LOGIN-SMOKE-V1 LOGIN-CARD-IN-VIEWPORT-V2', async ({ page, browser }, testInfo) => {
  const consoleEvents: Array<{ type: string; text: string }> = [];
  const pageFailures: string[] = [];
  const requestFailures: string[] = [];
  page.on('console', (message) => {
    if (['warning', 'error'].includes(message.type())) consoleEvents.push({ type: message.type(), text: message.text() });
  });
  page.on('pageerror', (error) => pageFailures.push(error.message));
  page.on('requestfailed', (request) => requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? ''}`));

  const observedBrowserVersion = browser.version();
  expect(observedBrowserVersion).toMatch(/^\d+\.\d+\.\d+\.\d+$/);
  expect(contract.scenario.route).toBe('/login');
  await page.goto('/login', { waitUntil: 'networkidle' });
  const byScenarioKey = (key: string) => page.locator(`[data-testid="${contract.scenario.selectors[key]}"]`);
  const pageElement = byScenarioKey('page');
  const username = byScenarioKey('username');
  const password = byScenarioKey('password');
  const remember = byScenarioKey('remember');
  const submit = byScenarioKey('submit');
  await expect(pageElement).toBeVisible();

  await submit.click();
  expect(await username.evaluate((element) => (element as HTMLInputElement).checkValidity())).toBe(false);
  expect(await page.evaluate(() => location.pathname)).toBe('/login');

  const usernameAction = contract.scenario.actions.find((action) => action.kind === 'fill' && action.target === 'username');
  const passwordAction = contract.scenario.actions.find((action) => action.kind === 'fill' && action.target === 'password');
  expect(usernameAction?.value).toBeTruthy();
  expect(passwordAction?.value).toBeTruthy();
  await username.fill(usernameAction!.value!);
  await password.fill(passwordAction!.value!);
  await remember.check();
  await expect(remember).toBeChecked();

  const responsePromise = page.waitForResponse((response) => {
    const path = new URL(response.url()).pathname;
    return contract.scenario.responder.mappedPaths.includes(path) && response.request().method() === 'POST';
  });
  await submit.click();
  const response = await responsePromise;
  expect(response.status()).toBe(contract.scenario.responder.status);
  expect(response.headers()[contract.scenario.responder.marker.name.toLowerCase()]).toBe(contract.scenario.responder.marker.value);
  expect(await response.json()).toEqual(contract.scenario.responder.body);
  await expect(byScenarioKey('error')).toHaveText(contract.scenario.responder.uiErrorText);

  const visual = await page.evaluate(evaluateVisualRuleInPage, {
    selectors: contract.scenario.selectors,
    visualRule: contract.scenario.visualRule,
  }) as { ruleId: string; failures: unknown[]; observations: unknown[] };
  expect(visual.ruleId).toBe('LOGIN-CARD-IN-VIEWPORT-V2');
  expect(visual.failures).toEqual([]);
  const screenshot = await page.screenshot({ fullPage: false });
  const screenshotDigest = await crypto.subtle.digest('SHA-256', screenshot);
  const screenshotSha256 = [...new Uint8Array(screenshotDigest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  await testInfo.attach('LOGIN-SMOKE-V1-observations.json', {
    body: JSON.stringify({
      scenarioId: contract.scenario.id,
      scenarioDigest: contract.digests.scenarioSha256,
      visualRuleDigest: contract.digests.visualRuleSha256,
      browser: { brand: 'Google Chrome', observedVersion: observedBrowserVersion, viewport: '1440x900' },
      consoleEvents,
      screenshotSha256,
      visual,
    }),
    contentType: 'application/json',
  });

  const expected401ConsoleText = 'Failed to load resource: the server responded with a status of 401 (Unauthorized)';
  const expected401ConsoleEvents = consoleEvents.filter((event) => event.type === 'error' && event.text === expected401ConsoleText);
  const unexpectedConsoleEvents = consoleEvents.filter((event) => !expected401ConsoleEvents.includes(event));
  expect(expected401ConsoleEvents.length).toBeLessThanOrEqual(1);
  expect(unexpectedConsoleEvents).toEqual([]);
  expect(pageFailures).toEqual([]);
  expect(requestFailures).toEqual([]);
});
