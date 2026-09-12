import { expect, test, type Locator, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.describe.configure({ mode: 'serial' });
test.use({
  viewport: { width: 1440, height: 900 },
});

async function expectLabelLeftOfControl(panel: Locator, name: string) {
  const labelBox = await panel.getByTestId(`query-label-${name}`).boundingBox();
  const controlBox = await panel.getByTestId(`query-input-${name}`).boundingBox();
  expect(labelBox, `${name} label has a layout box`).not.toBeNull();
  expect(controlBox, `${name} control has a layout box`).not.toBeNull();
  expect(labelBox!.x + labelBox!.width).toBeLessThanOrEqual(controlBox!.x + 1);
}

async function expectAllLabelsLeftOfControls(panel: Locator, names: string[]) {
  for (const name of names) await expectLabelLeftOfControl(panel, name);
}

async function expectAllControlsEmpty(panel: Locator, names: string[]) {
  for (const name of names) {
    await expect(panel.getByTestId(`query-input-${name}`).locator('input, select, textarea')).toHaveValue('');
  }
}

async function expectCollapsedAfterReload(page: Page, panelIndex = 0) {
  await page.reload();
  const panel = page.getByTestId('query-panel').nth(panelIndex);
  await expect(panel.getByTestId('query-panel-fields')).toBeHidden();
  await expect(panel.getByTestId('query-panel-toggle')).toHaveAttribute('aria-expanded', 'false');
}

function tenant(tenantId: number, shortName: string) {
  return {
    tenantId,
    tenantNo: `T${String(tenantId).padStart(4, '0')}`,
    shortName,
    fullName: `${shortName}有限公司`,
    verificationStatus: 'PENDING',
    lifecycleStatus: 'SUBMITTED',
    operatingStatus: 'NORMAL',
    submittedAt: '2026-09-12T08:00:00Z',
    bizManager: '测试经理',
    accountRevision: 1,
    qualificationRevision: 1,
  };
}

test('pw-issue-58-admin-tenants C-ISSUE-58-ADMIN-TENANTS OBL-ISSUE-58-ADMIN-TENANTS', async ({ page }, testInfo) => {
  expect(testInfo.project.name).toBe('local-google-chrome');
  expect(page.context().browser()?.version()).toMatch(/^\d+\.\d+\.\d+\.\d+$/);
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/admin/tenants', (route) => route.fulfill({
    json: apiResponse([
      tenant(8, '贝塔机构'),
      ...Array.from({ length: 11 }, (_, index) => tenant(index + 20, `阿尔法机构${index + 1}`)),
    ]),
  }));
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/tenants');

  const panel = page.getByTestId('query-panel');
  await expect(panel).toBeVisible();
  await expect(panel.getByTestId('query-panel-fields')).toBeVisible();
  await expect(panel.getByTestId('query-panel-toggle')).toHaveCount(0);
  const fields = ['keyword', 'verification-status', 'operating-status'];
  await expectAllLabelsLeftOfControls(panel, fields);

  await panel.getByTestId('query-input-keyword').locator('input').fill('阿尔法');
  await panel.getByTestId('query-input-verification-status').locator('select').selectOption('PENDING');
  await panel.getByTestId('query-input-operating-status').locator('select').selectOption('NORMAL');
  await panel.getByTestId('query-submit').click();
  await expect(panel.getByTestId('query-result-table')).toContainText('阿尔法机构1');
  await expect(panel.getByTestId('query-result-table')).not.toContainText('贝塔机构');
  await page.getByTestId('admin-tenant-qualification-tenants-next').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page-status')).toContainText('第 2 / 2 页');

  await panel.getByTestId('query-reset').click();
  await expectAllControlsEmpty(panel, fields);
  await expect(panel.getByTestId('query-result-table')).toContainText('贝塔机构');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page-status')).toContainText('第 1 / 2 页');

});

const initialAudit = {
  id: 31,
  actor: 'admin-user',
  tenantId: null,
  operation: 'UPDATE_PLATFORM_ROLE_PERMISSIONS',
  resource: 'PUT /api/v1/console/platform-roles/10/permissions',
  result: 'SUCCESS',
  ipAddress: '192.0.2.10',
  traceId: 'trace-audit-31',
  latencyMs: 18,
  requestSummary: 'parameterNames=[roleId]',
  occurredAt: '2026-09-12T08:00:00Z',
};

test('pw-issue-58-operation-audit C-ISSUE-58-OPERATION-AUDIT OBL-ISSUE-58-OPERATION-AUDIT', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/operation-audits**', (route) => {
    const url = new URL(route.request().url());
    const operation = url.searchParams.get('operation');
    const row = operation ? { ...initialAudit, id: 32, operation } : initialAudit;
    return route.fulfill({ json: apiResponse({ items: [row], page: 0, size: 20, totalElements: 1 }) });
  });
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/logs');

  const panel = page.getByTestId('query-panel');
  await expect(panel.getByTestId('query-panel-fields')).toBeHidden();
  await panel.getByTestId('query-panel-toggle').click();
  const fields = ['actor', 'operation', 'result', 'from', 'to'];
  await expectAllLabelsLeftOfControls(panel, fields);
  await panel.getByTestId('query-input-actor').locator('input').fill('admin-user');
  await panel.getByTestId('query-input-operation').locator('input').fill('EXPORT_REPORT');
  await panel.getByTestId('query-input-result').locator('select').selectOption('SUCCESS');
  await panel.getByTestId('query-input-from').locator('input').fill('2026-09-12T00:00');
  await panel.getByTestId('query-input-to').locator('input').fill('2026-09-13T00:00');
  const filteredRequest = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return url.pathname.endsWith('/operation-audits')
      && url.searchParams.get('actor') === 'admin-user'
      && url.searchParams.get('operation') === 'EXPORT_REPORT'
      && url.searchParams.get('result') === 'SUCCESS'
      && url.searchParams.get('from') === '2026-09-12T00:00:00.000Z'
      && url.searchParams.get('to') === '2026-09-13T00:00:00.000Z';
  });
  await panel.getByTestId('query-submit').click();
  await filteredRequest;
  await expect(panel.getByTestId('query-result-table')).toContainText('EXPORT_REPORT');

  await panel.getByTestId('query-reset').click();
  await expectAllControlsEmpty(panel, fields);
  await expect(panel.getByTestId('query-result-table')).toContainText('UPDATE_PLATFORM_ROLE_PERMISSIONS');
  await expect(page.getByTestId('admin-privileged-data-system-logs-page-status')).toContainText('第 1 页');

  await panel.getByTestId('query-panel-toggle').click();
  await expectCollapsedAfterReload(page);
});

const uplink = {
  id: 101,
  tenantId: 7,
  sourceProtocol: 'HTTP',
  sourceConnector: 'HTTP-API',
  sourceEventId: 'HTTP-UP-1',
  messageId: 'MSG-1',
  phoneMasked: '138****8000',
  content: '回复帮助',
  contentKeyword: '帮助',
  state: 'NORMALIZED',
  carrier: 'CMCC',
  province: '北京',
  city: '北京',
  destination: 'https://callback.example.test/uplink',
  channelId: 3,
  signatureId: 4,
  productCode: 'STANDARD',
  pushState: 'PUSH_FAILED',
  pushEventId: 501,
  receiveTime: '2026-09-12T08:00:00Z',
  createdAt: '2026-09-12T08:00:00Z',
  updatedAt: '2026-09-12T08:01:00Z',
};

test('pw-issue-58-admin-uplinks C-ISSUE-58-ADMIN-UPLINKS OBL-ISSUE-58-ADMIN-UPLINKS', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route(/\/api\/v1\/console\/uplinks(?:\?.*)?$/, (route: Route) => {
    const keyword = new URL(route.request().url()).searchParams.get('keyword');
    const rows = keyword === '帮助' ? [uplink] : [uplink, { ...uplink, id: 102, messageId: 'MSG-2', content: '普通上行', contentKeyword: '普通' }];
    return route.fulfill({ json: apiResponse(rows) });
  });
  await page.route(/\/api\/v1\/console\/uplinks\/push-monitor(?:\?.*)?$/, (route: Route) => route.fulfill({
    json: apiResponse([{ eventId: 501, tenantId: 7, sourceId: 'UPLINK:101', logicalId: 'UPLINK:101', destinationUrl: uplink.destination, state: 'PUSH_FAILED', attemptCount: 5, maxAttempts: 5, attemptRows: 5, nextAttemptAt: null, updatedAt: uplink.updatedAt, latencyMs: 60000 }]),
  }));
  await loginAs(page, 'OPERATOR');
  await page.goto('/admin/uplink');

  const panels = page.getByTestId('query-panel');
  await expect(panels).toHaveCount(2);
  const panel = panels.nth(0);
  await expect(panel.getByTestId('query-panel-fields')).toBeHidden();
  await panel.getByTestId('query-panel-toggle').click();
  const fields = ['tenant-id', 'phone-number', 'keyword', 'carrier', 'push-state', 'start-time', 'end-time'];
  await expectAllLabelsLeftOfControls(panel, fields);
  await panel.getByTestId('query-input-tenant-id').locator('input').fill('7');
  await panel.getByTestId('query-input-phone-number').locator('input').fill('138');
  await panel.getByTestId('query-input-keyword').locator('input').fill('帮助');
  await panel.getByTestId('query-input-carrier').locator('input').fill('CMCC');
  await panel.getByTestId('query-input-push-state').locator('select').selectOption('PUSH_FAILED');
  await panel.getByTestId('query-input-start-time').locator('input').fill('2026-09-12T00:00');
  await panel.getByTestId('query-input-end-time').locator('input').fill('2026-09-13T00:00');
  const filteredRequest = page.waitForRequest((request) => {
    const params = new URL(request.url()).searchParams;
    return params.get('tenantId') === '7'
      && params.get('phoneNumber') === '138'
      && params.get('keyword') === '帮助'
      && params.get('carrier') === 'CMCC'
      && params.get('pushState') === 'PUSH_FAILED'
      && params.get('startTime') === '2026-09-12T00:00'
      && params.get('endTime') === '2026-09-13T00:00';
  });
  await panel.getByTestId('query-submit').click();
  await filteredRequest;
  await expect(panel.getByTestId('query-result-table')).toContainText('帮助');
  await expect(panel.getByTestId('query-result-table')).not.toContainText('普通');

  await panel.getByTestId('query-reset').click();
  await expectAllControlsEmpty(panel, fields);
  await expect(panel.getByTestId('query-result-table')).toContainText('普通');

  await panel.getByTestId('query-panel-toggle').click();
  await expectCollapsedAfterReload(page);
});
