import { expect, test, type Browser, type Locator, type Page } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

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

const ADMIN_TENANT_FIXTURE = [
  {
    tenantId: 42,
    tenantNo: 'TENANT-0042',
    shortName: '北斗短信',
    fullName: '北斗短信服务有限公司',
    unifiedSocialCreditCode: '91350211M000100Y42',
    legalRepresentativeName: '张三',
    contactName: '李四',
    registeredCapital: '1000',
    businessScope: '短信服务',
    registeredAddress: '厦门市思明区示例路 1 号',
    businessAddress: '厦门市思明区示例路 2 号',
    customerLevel: 3,
    bizManager: 'operator',
    industry: '软件',
    licenseValidUntil: '2030-01-01',
    trademarkUse: false,
    verificationStatus: 'PENDING',
    lifecycleStatus: 'SUBMITTED',
    operatingStatus: 'NORMAL',
    accountRevision: 2,
    qualificationRevision: 7,
    submittedAt: '2026-09-08T08:00:00Z',
    reason: null,
    inspectionStatus: 'COMPLETED',
    inspectedCompanyName: '北斗短信服务有限公司',
    inspectedCreditCode: '91350211M000100Y42',
    inspectionConfidence: 0.98,
    inspectionRequestId: 'inspection-42',
    inspectionCompletedAt: '2026-09-08T08:01:00Z',
  },
  {
    tenantId: 43,
    tenantNo: 'TENANT-0043',
    shortName: '南辰通信',
    fullName: '南辰通信有限公司',
    unifiedSocialCreditCode: '91350211M000100Y43',
    legalRepresentativeName: '王五',
    contactName: '赵六',
    registeredCapital: '800',
    businessScope: '通信服务',
    registeredAddress: '厦门市湖里区示例路 3 号',
    businessAddress: '厦门市湖里区示例路 4 号',
    customerLevel: 2,
    bizManager: 'operator',
    industry: '通信',
    licenseValidUntil: '2031-01-01',
    trademarkUse: true,
    verificationStatus: 'VERIFIED',
    lifecycleStatus: 'SIGNED',
    operatingStatus: 'DISABLED',
    accountRevision: 3,
    qualificationRevision: 8,
    submittedAt: '2026-09-08T09:00:00Z',
    reason: null,
    inspectionStatus: 'COMPLETED',
    inspectedCompanyName: '南辰通信有限公司',
    inspectedCreditCode: '91350211M000100Y43',
    inspectionConfidence: 0.97,
    inspectionRequestId: 'inspection-43',
    inspectionCompletedAt: '2026-09-08T09:01:00Z',
  },
] as const;

async function mockIssue88RouteData(page: Page, route: string) {
  if (route === '/admin/tenants') {
    await page.route('**/api/v1/console/admin/tenants', (request) => request.fulfill({ json: apiResponse(ADMIN_TENANT_FIXTURE) }));
  }
  if (route === '/admin/archive') {
    await page.route('**/api/v1/console/archive/policies', (request) => request.fulfill({
      json: apiResponse([
        { id: 1, dataDomain: 'MESSAGE_TASKS', sourceTable: 'sms_task', retentionDays: 730, hotMonths: 3, partitionUnit: 'MONTH', legalHoldUntil: null, encryptionRequired: true, status: 'ACTIVE', updatedBy: 'admin', updatedAt: '2026-09-01T00:00:00Z' },
        { id: 2, dataDomain: 'RECEIPTS', sourceTable: 'sms_receipt', retentionDays: 365, hotMonths: 2, partitionUnit: 'MONTH', legalHoldUntil: null, encryptionRequired: true, status: 'ACTIVE', updatedBy: 'admin', updatedAt: '2026-09-01T00:00:00Z' },
      ]),
    }));
    await page.route('**/api/v1/console/archive/manifests**', (request) => request.fulfill({
      json: apiResponse([{
        id: 47,
        policyId: 1,
        dataDomain: 'MESSAGE_TASKS',
        sourceTable: 'sms_task',
        partitionKey: '2026-08',
        tenantId: 42,
        archiveStatus: 'COMPLETED',
        rowCount: 128,
        sourceIdentityJson: '{"partition":"2026-08"}',
        manifestJson: '{"rowCount":128}',
        checksumSha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        encryptionKeyVersion: 'archive-v1',
        retentionUntil: '2028-08-31T00:00:00Z',
        legalHoldUntil: null,
        deletionEligible: false,
        failureReason: null,
        createdBy: 'admin',
        createdAt: '2026-09-01T00:00:00Z',
        verifiedAt: '2026-09-01T00:01:00Z',
        restoredAt: null,
        exportedTaskId: null,
      }]),
    }));
  }
}

async function openPanel(panel: Locator) {
  const toggle = panel.getByTestId('query-panel-toggle');
  if (await toggle.count() && await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
  await expect(panel.getByTestId('query-fields')).toBeVisible();
  await expect(panel.getByTestId('query-actions')).toBeVisible();
}

async function fillDifferentValue(control: Locator, baseline: string, index: number, route: string) {
  const tagName = await control.evaluate((element) => element.tagName);
  if (tagName === 'SELECT') {
    const values = await control.locator('option').evaluateAll((options) =>
      options.map((option) => (option as HTMLOptionElement).value));
    const nextValue = values.find((candidate) => candidate !== baseline);
    expect(nextValue, `${route} select ${index + 1} has a testable alternative`).toBeDefined();
    await control.selectOption(nextValue!);
    return;
  }

  const type = await control.getAttribute('type') ?? 'text';
  const testId = await control.getAttribute('data-testid') ?? '';
  const pattern = await control.getAttribute('pattern') ?? '';
  const inputMode = await control.getAttribute('inputmode') ?? '';
  const numericSemantic = pattern.includes('0-9')
    || inputMode === 'numeric'
    || /^\d+$/.test(baseline)
    || (/(tenant|channel|user|task|job|signature|template)/.test(testId) && /(id|filter)/.test(testId));
  const candidates = type === 'date'
    ? ['2026-09-01', '2026-09-02']
    : type === 'datetime-local'
      ? ['2026-09-01T12:00', '2026-09-02T12:00']
      : type === 'month'
        ? ['2025-01', '2025-02']
        : type === 'number'
          ? ['77', '78']
          : type === 'email'
            ? ['issue88@example.com', 'issue88-alt@example.com']
            : /(mobile|phone)/.test(testId)
              ? ['13912345678', '13812345679']
              : numericSemantic
                ? ['77', '78']
                : [`issue-88-${index + 1}`, `issue-88-alt-${index + 1}`];
  const nextValue = candidates.find((candidate) => candidate !== baseline)!;
  await control.fill(nextValue);
}

async function expectPanelContract(panel: Locator, route: string, pageDataRequests: string[]) {
  await openPanel(panel);
  const fields = panel.getByTestId('query-fields');
  const actions = panel.getByTestId('query-actions');
  const result = panel.getByTestId('query-result-table');
  const status = panel.locator('[data-query-result-state]');
  const controls = fields.locator('input:not([type="checkbox"]):not([type="radio"]):not([type="file"]), select:not([multiple]), textarea');
  expect(await controls.count(), `${route} has query controls`).toBeGreaterThan(0);
  expect(
    await panel.locator('input, select, textarea').count(),
    `${route} keeps mutation and operation controls outside the query panel`,
  ).toBe(await fields.locator('input, select, textarea').count());

  for (let index = 0; index < await controls.count(); index += 1) {
    const control = controls.nth(index);
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

  await expect(panel.getByTestId('query-submit')).toBeEnabled();
  await expect(panel.getByTestId('query-reset'), `${route} provides Reset for every panel`).toHaveCount(1);
  await expect(panel.getByTestId('query-refresh'), `${route} provides Refresh for every panel`).toHaveCount(1);
  await expect(result, `${route} provides a query result region`).toHaveCount(1);
  await expect(status, `${route} exposes exactly one query result state`).toHaveCount(1);
  await expect(status).toHaveAttribute('data-state', /^(loading|error|empty|success)$/);
  await expect(status, `${route} initial query settles before retry assertions`).toHaveAttribute(
    'data-state',
    /^(error|empty|success)$/,
  );

  await panel.getByTestId('query-reset').click();
  const canonicalValues = await controls.evaluateAll((elements) => elements.map((element) => (
    element as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
  ).value));

  for (let index = 0; index < await controls.count(); index += 1) {
    await panel.getByTestId('query-reset').click();
    for (let controlIndex = 0; controlIndex < canonicalValues.length; controlIndex += 1) {
      await expect(controls.nth(controlIndex), `${route} reset restores query control ${controlIndex + 1}`).toHaveValue(canonicalValues[controlIndex]);
    }
    await expect(status, `${route} query settles before unchanged retry`).toHaveAttribute(
      'data-state',
      /^(error|empty|success)$/,
    );

    const unchangedRequestStart = pageDataRequests.length;
    if (route !== '/tenant/help/guide') {
      await panel.getByTestId('query-submit').click();
      await expect.poll(
        () => pageDataRequests.length,
        { message: `${route} unchanged query can refetch its data source`, timeout: 5_000 },
      ).toBeGreaterThan(unchangedRequestStart);
    }
    const unchangedRequests = new Set(pageDataRequests.slice(unchangedRequestStart));
    const resultBeforeChangedSubmit = await result.innerText();

    const control = controls.nth(index);
    await fillDifferentValue(control, canonicalValues[index], index, route);
    const changedRequestStart = pageDataRequests.length;
    await panel.getByTestId('query-submit').click();

    if (route === '/tenant/help/guide' || route === '/admin/tenants') {
      await expect.poll(
        () => result.innerText(),
        { message: `${route} field ${index + 1} changes the local result`, timeout: 5_000 },
      ).not.toBe(resultBeforeChangedSubmit);
    } else {
      await expect.poll(
        () => pageDataRequests.length,
        { message: `${route} field ${index + 1} triggers a page-data request`, timeout: 5_000 },
      ).toBeGreaterThan(changedRequestStart);
      const changedRequests = pageDataRequests.slice(changedRequestStart);
      expect(
        changedRequests.some((request) => !unchangedRequests.has(request)),
        `${route} field ${index + 1} changes a request URL or body`,
      ).toBe(true);
    }
  }
  expect(await pageUrl(panel), `${route} query submission keeps the current route`).toContain(route);

  for (const button of await panel.getByRole('button', { name: /^(搜索|查询|重置|刷新)$/ }).all()) {
    expect(await button.evaluate((element) => element.closest('[data-testid="query-actions"]') !== null), `${route} query action is scoped`).toBe(true);
  }

  const actionBox = await actions.boundingBox();
  const panelBox = await panel.boundingBox();
  expect(actionBox, `${route} action area has geometry`).not.toBeNull();
  expect(panelBox, `${route} query panel has geometry`).not.toBeNull();
  expect(actionBox!.y + actionBox!.height, `${route} actions stay inside the query panel`).toBeLessThanOrEqual(panelBox!.y + panelBox!.height + 1);

  const resultBox = await result.boundingBox();
  expect(resultBox, `${route} result area has geometry`).not.toBeNull();
  expect(actionBox!.y + actionBox!.height, `${route} actions stay above the result`).toBeLessThanOrEqual(resultBox!.y + 1);
}

async function pageUrl(locator: Locator) {
  return locator.evaluate(() => window.location.pathname);
}

async function expectEditableControlIdentity(page: Page, route: string) {
  const controls = page.locator('main input:not([type="hidden"]):not([disabled]):not([readonly]), main select:not([disabled]), main textarea:not([disabled]):not([readonly])');
  for (let index = 0; index < await controls.count(); index += 1) {
    const control = controls.nth(index);
    if (!await control.isVisible()) continue;
    const testId = await control.getAttribute('data-testid');
    expect(testId, `${route} editable control ${index + 1} has a stable page-owned test id`).toMatch(/^admin-/);
    const hasAccessibleLabel = await control.evaluate((element) => {
      const nativeControl = element as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;
      return Boolean(nativeControl.labels?.length || element.getAttribute('aria-label') || element.getAttribute('aria-labelledby'));
    });
    expect(hasAccessibleLabel, `${route} editable control ${testId} has an accessible label`).toBe(true);
  }
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

async function expectAdminQueryContract(browser: Browser, issue: '#77' | '#88') {
  const loginContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const loginPage = await loginContext.newPage();
  await mockEmptyDashboard(loginPage);
  await loginAs(loginPage, 'ADMIN');
  const authSession = await loginPage.evaluate(() => window.sessionStorage.getItem('ycsopen.console.auth-session'));
  await loginContext.close();
  expect(authSession, 'admin login stores an authenticated console session').toBeTruthy();

  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.addInitScript((session) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', session);
  }, authSession!);
  const page = await context.newPage();
  const pageDataRequests: string[] = [];
  page.on('request', (request) => {
    const pathname = new URL(request.url()).pathname;
    if (pathname.startsWith('/api/v1/') && !pathname.endsWith('/account-overview')) {
      pageDataRequests.push(`${request.method()} ${request.url()} ${request.postData() ?? ''}`);
    }
  });
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

  try {
    for (const route of ADMIN_ROUTES) {
      pageDataRequests.length = 0;
      try {
        await mockIssue88RouteData(page, route);
        await page.goto(route);
        await expect(page.locator('.layout > main.content')).toBeVisible();
        const expectedPanels = ADMIN_QUERY_PANEL_COUNTS.get(route) ?? 0;
        const panels = page.getByTestId('query-panel');
        await expect(panels, `${route} query panel count`).toHaveCount(expectedPanels);
        for (let index = 0; index < expectedPanels; index += 1) await expectPanelContract(panels.nth(index), route, pageDataRequests);
        await expectEditableControlIdentity(page, route);

        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow, `${route} has no page-level horizontal overflow`).toBeLessThanOrEqual(1);
      } catch (error) {
        throw new Error(`Issue ${issue} admin route acceptance failed at ${route}: ${error instanceof Error ? error.message : String(error)}`);
      }
    }
  } finally {
    await context.close();
  }
}

test('pw-issue-77-admin-query-controls C-ISSUE-77-ADMIN-QUERY-CONTROLS OBL-ISSUE-77-ADMIN-QUERY-CONTROLS', async ({ browser, page }, testInfo) => {
  test.setTimeout(600_000);
  expect(testInfo.project.name).toBe('local-google-chrome');
  await mockEmptyDashboard(page);
  await loginAs(page, 'ADMIN');
  await abortPageDataRequests(page);
  await page.goto('/admin/dashboard');
  await expect(page.getByTestId('query-panel')).toHaveCount(2);
  await expectAdminQueryContract(browser, '#77');
});

test('pw-issue-88-admin-actionable-controls C-ISSUE-88-ADMIN-ACTIONABLE-CONTROLS OBL-ISSUE-88-ADMIN-ACTIONABLE-CONTROLS', async ({ browser, page }, testInfo) => {
  test.setTimeout(600_000);
  expect(testInfo.project.name).toBe('local-google-chrome');
  await mockEmptyDashboard(page);
  await loginAs(page, 'ADMIN');
  await abortPageDataRequests(page);
  await page.goto('/admin/dashboard');
  await expect(page.getByTestId('query-panel')).toHaveCount(2);
  await expectAdminQueryContract(browser, '#88');
});

test('pw-issue-88-archive-result C-ISSUE-88-ARCHIVE-RESULT OBL-ISSUE-88-ARCHIVE-RESULT', async ({ page }, testInfo) => {
  expect(testInfo.project.name).toBe('local-google-chrome');
  await mockEmptyDashboard(page);
  await loginAs(page, 'ADMIN');
  await abortPageDataRequests(page);
  await mockIssue88RouteData(page, '/admin/archive');

  await page.goto('/admin/archive');

  const result = page.getByTestId('query-result-table');
  await expect(result.getByTestId('admin-retention-archive-manifest-table')).toBeVisible();
  await expect(result.getByTestId('admin-retention-archive-manifest-query-status')).toHaveAttribute('data-state', 'success');
  await expect(result.getByTestId('admin-retention-archive-manifest-row')).toContainText('2026-08');
});

async function expectTenantQueryContract(browser: Browser) {
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
      if (pathname.startsWith('/api/v1/')) pageDataRequests.push(`${request.method()} ${request.url()} ${request.postData() ?? ''}`);
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
}

test('pw-issue-77-tenant-query-controls C-ISSUE-77-TENANT-QUERY-CONTROLS OBL-ISSUE-77-TENANT-QUERY-CONTROLS', async ({ browser, page }, testInfo) => {
  test.setTimeout(90_000);
  expect(testInfo.project.name).toBe('local-google-chrome');
  await loginAs(page, 'TENANT_ADMIN');
  await abortPageDataRequests(page);
  await page.goto('/tenant/consumption-ledger');
  await expect(page.getByTestId('query-panel')).toBeVisible();
  await expectTenantQueryContract(browser);
});

test('pw-issue-88-tenant-actionable-controls C-ISSUE-88-TENANT-ACTIONABLE-CONTROLS OBL-ISSUE-88-TENANT-ACTIONABLE-CONTROLS', async ({ browser, page }, testInfo) => {
  test.setTimeout(90_000);
  expect(testInfo.project.name).toBe('local-google-chrome');
  await loginAs(page, 'TENANT_ADMIN');
  await abortPageDataRequests(page);
  await page.goto('/tenant/consumption-ledger');
  await expect(page.getByTestId('query-panel')).toBeVisible();
  await expectTenantQueryContract(browser);
});
