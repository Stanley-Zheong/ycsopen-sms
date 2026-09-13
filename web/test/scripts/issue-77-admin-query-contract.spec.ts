import { expect, test, type Locator, type Page } from '@playwright/test';
import { loginAs, mockEmptyDashboard } from './helpers';

const ADMIN_ROUTES = [
  '/admin/dashboard',
  '/admin/dashboard/configuration',
  '/admin/api/status',
  '/admin/tenants',
  '/admin/channel/configuration',
  '/admin/channel/health',
  '/admin/channel/pools',
  '/admin/routing-policy',
  '/admin/signatures/review',
  '/admin/templates/review',
  '/admin/exemption/policy',
  '/admin/review-history',
  '/admin/riskcontrol',
  '/admin/content-safety',
  '/admin/frequency/rules',
  '/admin/number-attribution',
  '/admin/number-portability',
  '/admin/prefixes',
  '/admin/status-codes',
  '/admin/tenant-trial-contracts',
  '/admin/balance-audit',
  '/admin/tenant-recharge-review',
  '/admin/reconciliation',
  '/admin/settlements',
  '/admin/invoices',
  '/admin/submission/details',
  '/admin/send/details',
  '/admin/receipt/details',
  '/admin/error/details',
  '/admin/push/failures',
  '/admin/bulk/details',
  '/admin/send/jobs',
  '/admin/complaints',
  '/admin/complaint/analytics',
  '/admin/tenant-risk',
  '/admin/uplink',
  '/admin/unsubscribes',
  '/admin/records',
  '/admin/statistics',
  '/admin/statistics/resources',
  '/admin/custom/reports',
  '/admin/export-center',
  '/admin/archive',
  '/admin/shortlinks/review',
  '/admin/tenant/terminations',
  '/admin/finance',
  '/admin/fee/warning',
  '/admin/alerts',
  '/admin/tools',
  '/admin/system/users',
  '/admin/system/roles',
  '/admin/system/login-history',
  '/admin/system/logs',
  '/admin/system/security-events',
  '/admin/system/configuration',
  '/admin/account-overview',
] as const;

const ADMIN_QUERY_PANEL_COUNTS = new Map<string, number>([
  ['/admin/dashboard', 2],
  ['/admin/dashboard/configuration', 1],
  ['/admin/tenants', 1],
  ['/admin/signatures/review', 1],
  ['/admin/templates/review', 1],
  ['/admin/review-history', 1],
  ['/admin/riskcontrol', 1],
  ['/admin/content-safety', 1],
  ['/admin/frequency/rules', 1],
  ['/admin/tenant-trial-contracts', 1],
  ['/admin/balance-audit', 1],
  ['/admin/tenant-recharge-review', 1],
  ['/admin/submission/details', 1],
  ['/admin/send/details', 1],
  ['/admin/receipt/details', 1],
  ['/admin/error/details', 1],
  ['/admin/push/failures', 1],
  ['/admin/bulk/details', 1],
  ['/admin/send/jobs', 1],
  ['/admin/tenant-risk', 1],
  ['/admin/uplink', 2],
  ['/admin/unsubscribes', 1],
  ['/admin/records', 1],
  ['/admin/number-attribution', 1],
  ['/admin/number-portability', 1],
  ['/admin/prefixes', 1],
  ['/admin/status-codes', 1],
  ['/admin/export-center', 1],
  ['/admin/archive', 1],
  ['/admin/tenant/terminations', 1],
  ['/admin/finance', 1],
  ['/admin/fee/warning', 1],
  ['/admin/alerts', 1],
  ['/admin/system/login-history', 1],
  ['/admin/system/logs', 1],
  ['/admin/system/security-events', 1],
]);

const TENANT_QUERY_PANEL_COUNTS = new Map<string, number>([
  ['/tenant/consumption-ledger', 1],
  ['/tenant/uplink', 1],
  ['/tenant/unsubscribes', 1],
  ['/tenant/help/guide', 1],
]);

async function openPanel(panel: Locator) {
  const toggle = panel.getByTestId('query-panel-toggle');
  if (await toggle.count() && await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
  await expect(panel.getByTestId('query-fields')).toBeVisible();
  await expect(panel.getByTestId('query-actions')).toBeVisible();
}

async function expectPanelContract(panel: Locator, route: string, pageDataRequests: string[]) {
  await openPanel(panel);
  const fields = panel.getByTestId('query-fields');
  const actions = panel.getByTestId('query-actions');
  const resultBeforeSubmit = await panel.getByTestId('query-result-table').count()
    ? await panel.getByTestId('query-result-table').innerText()
    : null;
  const controls = fields.locator('input:not([type="checkbox"]):not([type="radio"]):not([type="file"]), select:not([multiple]), textarea');
  expect(await controls.count(), `${route} has query controls`).toBeGreaterThan(0);
  expect(
    await panel.locator('input, select, textarea').count(),
    `${route} keeps mutation and operation controls outside the query panel`,
  ).toBe(await fields.locator('input, select, textarea').count());
  const baselineValues: string[] = [];

  for (let index = 0; index < await controls.count(); index += 1) {
    const control = controls.nth(index);
    baselineValues.push(await control.inputValue());
    const testId = await control.getAttribute('data-testid');
    expect(testId, `${route} query control ${index + 1} has a page-owned test id`).toMatch(/^(admin|tenant)-/);
    const controlId = await control.getAttribute('id');
    expect(controlId, `${route} query control ${testId} has an id`).toBeTruthy();
    await expect(panel.locator(`label[for="${controlId}"]`), `${route} query control ${testId} has a visible label`).toBeVisible();

    const geometry = await control.evaluate((element) => {
      const style = getComputedStyle(element);
      const box = element.getBoundingClientRect();
      return {
        height: box.height,
        paddingTop: Number.parseFloat(style.paddingTop),
        paddingBottom: Number.parseFloat(style.paddingBottom),
      };
    });
    expect(geometry.height, `${route} ${testId} height`).toBeGreaterThanOrEqual(38);
    expect(geometry.height, `${route} ${testId} height`).toBeLessThanOrEqual(42);
    if ((await control.evaluate((element) => element.tagName)) === 'SELECT') {
      expect(Math.abs(geometry.paddingTop - geometry.paddingBottom), `${route} ${testId} select vertical padding`).toBeLessThanOrEqual(1);
    }
  }

  for (let index = 0; index < await controls.count(); index += 1) {
    const control = controls.nth(index);
    const tagName = await control.evaluate((element) => element.tagName);
    if (tagName === 'SELECT') {
      const value = await control.locator('option').evaluateAll((options) =>
        options.map((option) => (option as HTMLOptionElement).value));
      const nextValue = value.find((candidate) => candidate !== baselineValues[index]) ?? baselineValues[index];
      await control.selectOption(nextValue);
      continue;
    }
    const type = await control.getAttribute('type');
    const testId = await control.getAttribute('data-testid') ?? '';
    const pattern = await control.getAttribute('pattern') ?? '';
    const inputMode = await control.getAttribute('inputmode') ?? '';
    const numericSemantic = pattern.includes('0-9')
      || inputMode === 'numeric'
      || /^\d+$/.test(baselineValues[index])
      || (/(tenant|channel|user|task|job|signature|template)/.test(testId) && /(id|filter)/.test(testId));
    const value = type === 'date'
      ? '2026-09-01'
      : type === 'datetime-local'
        ? '2026-09-01T12:00'
        : type === 'month'
          ? '2025-01'
          : type === 'number'
            ? '77'
            : numericSemantic
              ? '77'
              : /(mobile|phone)/.test(testId)
                ? '13912345678'
                : 'issue-77';
    await control.fill(value);
  }
  const requestCount = pageDataRequests.length;
  await expect(panel.getByTestId('query-submit')).toBeEnabled();
  await panel.getByTestId('query-submit').click();
  if (route === '/tenant/help/guide') {
    await expect.poll(
      () => panel.getByTestId('query-result-table').innerText(),
      { message: `${route} query submission changes the local result`, timeout: 5_000 },
    ).not.toBe(resultBeforeSubmit);
  } else {
    await expect.poll(
      () => pageDataRequests.length,
      { message: `${route} query submission triggers a page-data request`, timeout: 5_000 },
    ).toBeGreaterThan(requestCount);
  }
  expect(await pageUrl(panel), `${route} query submission keeps the current route`).toContain(route);

  const reset = panel.getByTestId('query-reset');
  if (await reset.count()) {
    await reset.click();
    for (let index = 0; index < await controls.count(); index += 1) {
      await expect(controls.nth(index), `${route} reset restores query control ${index + 1}`).toHaveValue(baselineValues[index]);
    }
  }

  for (const button of await panel.getByRole('button', { name: /^(搜索|查询|重置|刷新)$/ }).all()) {
    expect(await button.evaluate((element) => element.closest('[data-testid="query-actions"]') !== null), `${route} query action is scoped`).toBe(true);
  }

  const actionBox = await actions.boundingBox();
  const panelBox = await panel.boundingBox();
  expect(actionBox, `${route} action area has geometry`).not.toBeNull();
  expect(panelBox, `${route} query panel has geometry`).not.toBeNull();
  expect(actionBox!.y + actionBox!.height, `${route} actions stay inside the query panel`).toBeLessThanOrEqual(panelBox!.y + panelBox!.height + 1);

  const result = panel.getByTestId('query-result-table');
  if (await result.count()) {
    const resultBox = await result.boundingBox();
    expect(resultBox, `${route} result area has geometry`).not.toBeNull();
    expect(actionBox!.y + actionBox!.height, `${route} actions stay above the result`).toBeLessThanOrEqual(resultBox!.y + 1);
  } else {
    const followingHeader = panel.locator('xpath=following::thead[1]');
    if (await followingHeader.count()) {
      const headerBox = await followingHeader.boundingBox();
      if (headerBox) expect(actionBox!.y + actionBox!.height, `${route} actions stay above the following table header`).toBeLessThanOrEqual(headerBox.y + 1);
    }
  }
}

async function pageUrl(locator: Locator) {
  return locator.evaluate(() => window.location.pathname);
}

async function abortPageDataRequests(page: Page) {
  await page.route('**/api/v1/**', (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (pathname.endsWith('/account-overview') || pathname.endsWith('/auth/login')) return route.fallback();
    return route.abort();
  });
}

test.describe.configure({ mode: 'serial' });
test.use({ viewport: { width: 1440, height: 900 } });

test('pw-issue-77-admin-query-controls C-ISSUE-77-ADMIN-QUERY-CONTROLS OBL-ISSUE-77-QUERY-CONTROLS', async ({ browser }, testInfo) => {
  test.setTimeout(600_000);
  expect(testInfo.project.name).toBe('local-google-chrome');
  const loginContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const loginPage = await loginContext.newPage();
  await mockEmptyDashboard(loginPage);
  await loginAs(loginPage, 'ADMIN');
  const authSession = await loginPage.evaluate(() => window.sessionStorage.getItem('ycsopen.console.auth-session'));
  await loginContext.close();
  expect(authSession, 'admin login stores an authenticated console session').toBeTruthy();

  for (const route of ADMIN_ROUTES) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await context.addInitScript((session) => {
      window.sessionStorage.setItem('ycsopen.console.auth-session', session);
    }, authSession!);
    const page = await context.newPage();
    const pageDataRequests: string[] = [];
    page.on('request', (request) => {
      const pathname = new URL(request.url()).pathname;
      if (pathname.startsWith('/api/v1/') && !pathname.endsWith('/account-overview')) pageDataRequests.push(request.url());
    });
    try {
      await mockEmptyDashboard(page);
      await page.route('**/api/v1/console/account-overview', (request) => request.fulfill({
        json: {
          code: 0,
          message: 'OK',
          data: { id: 7, username: 'admin-user', userType: 'ADMIN', roleNames: ['系统管理员'], permissions: [], lastLoginAt: null, lastLoginIp: null },
          timestamp: '2026-08-30T00:00:00Z',
          traceId: 'e2e-trace',
        },
      }));
      await abortPageDataRequests(page);
      await page.goto(route);
      await expect(page.locator('.layout > main.content')).toBeVisible();
      const expectedPanels = ADMIN_QUERY_PANEL_COUNTS.get(route) ?? 0;
      const panels = page.getByTestId('query-panel');
      await expect(panels, `${route} query panel count`).toHaveCount(expectedPanels);
      for (let index = 0; index < expectedPanels; index += 1) await expectPanelContract(panels.nth(index), route, pageDataRequests);

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${route} has no page-level horizontal overflow`).toBeLessThanOrEqual(1);
    } catch (error) {
      throw new Error(`Issue #77 admin route acceptance failed at ${route}: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      await context.close();
    }
  }
});

test('pw-issue-77-tenant-query-controls C-ISSUE-77-TENANT-QUERY-CONTROLS OBL-ISSUE-77-QUERY-CONTROLS', async ({ browser }) => {
  test.setTimeout(90_000);
  const loginContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const loginPage = await loginContext.newPage();
  await loginAs(loginPage, 'TENANT_ADMIN');
  const authSession = await loginPage.evaluate(() => window.sessionStorage.getItem('ycsopen.console.auth-session'));
  await loginContext.close();
  expect(authSession, 'tenant login stores an authenticated console session').toBeTruthy();

  for (const [route, expectedPanels] of TENANT_QUERY_PANEL_COUNTS) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await context.addInitScript((session) => {
      window.sessionStorage.setItem('ycsopen.console.auth-session', session);
    }, authSession!);
    const page = await context.newPage();
    const pageDataRequests: string[] = [];
    page.on('request', (request) => {
      const pathname = new URL(request.url()).pathname;
      if (pathname.startsWith('/api/v1/')) pageDataRequests.push(request.url());
    });
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    try {
      await abortPageDataRequests(page);
      await page.goto(route);
      await expect(page).toHaveURL(route);
      await expect(page.locator('.layout > main.content')).toBeVisible();
      expect(pageErrors, `${route} has no browser runtime errors`).toEqual([]);
      const panels = page.getByTestId('query-panel');
      await expect(panels, `${route} query panel count`).toHaveCount(expectedPanels);
      for (let index = 0; index < expectedPanels; index += 1) await expectPanelContract(panels.nth(index), route, pageDataRequests);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${route} has no page-level horizontal overflow`).toBeLessThanOrEqual(1);
    } finally {
      await context.close();
    }
  }
});
