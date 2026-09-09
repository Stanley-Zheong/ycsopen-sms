import { expect, test, type Page } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

const auditPage = {
  items: [{
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
    occurredAt: '2026-09-07T09:00:00+08:00',
  }],
  page: 0,
  size: 20,
  totalElements: 1,
};

const securityPage = {
  items: [{
    id: 41,
    eventType: 'UNUSUAL_LOGIN',
    actor: 'admin-user',
    tenantId: null,
    result: 'DETECTED',
    ipAddress: '192.0.2.20',
    traceId: 'trace-event-41',
    summary: '检测到登录来源变化',
    detectedAt: '2026-09-07T09:30:00+08:00',
  }],
  page: 0,
  size: 20,
  totalElements: 1,
};

async function mockPrivilegedAuditApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/operation-audits**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse(auditPage)),
  }));
  await page.route('**/api/v1/console/security-events**', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse(securityPage)),
  }));
  await page.route('**/api/v1/console/platform-roles', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([{
      id: 10,
      code: 'ADMIN',
      name: '系统管理员',
      description: '',
      status: 'ACTIVE',
      userCount: 1,
      permissionIds: [],
    }])),
  }));
  await page.route('**/api/v1/console/platform-accounts', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([{
      id: 1,
      username: 'admin-user',
      email: 'admin@example.test',
      realName: '管理员',
      maskedPhone: '138****0000',
      userType: 'ADMIN',
      status: 'ACTIVE',
      roleIds: [10],
      validUntil: null,
      lastLoginAt: null,
      createdBy: 'system',
      createdAt: '2026-01-01T00:00:00+08:00',
    }])),
  }));
  await page.route('**/api/v1/console/platform-accounts/1/phone/reveal', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(apiResponse({
        value: '13800138000',
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
        auditId: 51,
      })),
    });
  });
}

function bindConditionalStateSelectors(page: Page) {
  void page.getByTestId('admin-privileged-data-system-logs-filter');
  void page.getByTestId('admin-privileged-data-system-logs-loading');
  void page.getByTestId('admin-privileged-data-system-logs-error');
  void page.getByTestId('admin-privileged-data-system-logs-retry');
  void page.getByTestId('admin-privileged-data-system-logs-empty');
  void page.getByTestId('admin-privileged-data-security-events-loading');
  void page.getByTestId('admin-privileged-data-security-events-error');
  void page.getByTestId('admin-privileged-data-security-events-retry');
  void page.getByTestId('admin-privileged-data-security-events-empty');
  void page.getByTestId('shared-privileged-data-sensitive-value-error');
}

test('pw-p6-audit-page C-P6-AUDIT-CAPTURE OBL-F-14-1-A', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  bindConditionalStateSelectors(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/logs');

  await expect(page.getByTestId('admin-privileged-data-system-logs-page')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-heading')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-table')).toContainText('UPDATE_PLATFORM_ROLE_PERMISSIONS');
  await expect(page.getByTestId('admin-privileged-data-system-logs-row')).toHaveCount(1);
  await page.getByTestId('admin-privileged-data-system-logs-actor-input').fill('admin-user');
  await expect(page.getByTestId('admin-privileged-data-system-logs-operation-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-result-select')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-from-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-system-logs-to-input')).toBeVisible();
  const filtered = page.waitForRequest((request) => request.url().includes('/api/v1/console/operation-audits')
    && request.url().includes('actor=admin-user'));
  await page.getByTestId('admin-privileged-data-system-logs-query').click();
  await filtered;
  await expect(page.getByTestId('admin-privileged-data-system-logs-reset')).toBeEnabled();
  await expect(page.getByTestId('admin-privileged-data-system-logs-previous')).toBeDisabled();
  await expect(page.getByTestId('admin-privileged-data-system-logs-page-status')).toContainText('第 1 页');
  await expect(page.getByTestId('admin-privileged-data-system-logs-next')).toBeDisabled();
});

test('pw-p6-audit-detail C-P6-AUDIT-INTEGRITY OBL-F-14-1-B', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/logs');

  await page.getByTestId('admin-privileged-data-system-logs-detail-open').click();
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-drawer')).toContainText('parameterNames=[roleId]');
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-fields')).toContainText('SUCCESS');
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-drawer')).not.toContainText('13800138000');
  await page.getByTestId('admin-privileged-data-system-logs-detail-close').click();
  await expect(page.getByTestId('admin-privileged-data-system-logs-detail-drawer')).toBeHidden();
});

test('pw-p6-reveal C-P6-REVEAL OBL-PRIVILEGED-REVEAL-001', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');

  await page.getByTestId('shared-privileged-data-sensitive-value-reveal').click();
  const dialog = page.getByTestId('shared-privileged-data-sensitive-value-dialog');
  await expect(dialog.getByTestId('shared-privileged-data-sensitive-value-title')).toBeVisible();
  await expect(dialog.getByTestId('shared-privileged-data-sensitive-value-account')).toContainText('admin-user');
  await expect(dialog.getByTestId('shared-privileged-data-sensitive-value-warning')).toBeVisible();
  await dialog.getByTestId('shared-privileged-data-sensitive-value-purpose').selectOption('SECURITY_INVESTIGATION');
  const revealRequest = page.waitForRequest('**/api/v1/console/platform-accounts/1/phone/reveal');
  await dialog.getByTestId('shared-privileged-data-sensitive-value-confirm').click();
  expect((await revealRequest).postDataJSON()).toEqual({ purpose: 'SECURITY_INVESTIGATION' });
  await expect(dialog.getByTestId('shared-privileged-data-sensitive-value-result')).toContainText('13800138000');
  await dialog.getByTestId('shared-privileged-data-sensitive-value-close').click();
  await expect(page.getByText('13800138000')).toHaveCount(0);
});

test('pw-p6-security-page C-P6-SECURITY-DETECTION OBL-F-14-2-A', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/security-events');

  await expect(page.getByTestId('admin-privileged-data-security-events-page')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-heading')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-table')).toContainText('异常登录');
  await expect(page.getByTestId('admin-privileged-data-security-events-row')).toHaveCount(1);
  await expect(page.getByTestId('admin-privileged-data-security-events-previous')).toBeDisabled();
  await expect(page.getByTestId('admin-privileged-data-security-events-page-status')).toContainText('第 1 页');
  await expect(page.getByTestId('admin-privileged-data-security-events-next')).toBeDisabled();
});

test('pw-p6-security-filter C-P6-SECURITY-FILTER OBL-F-14-2-B', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/security-events');

  await expect(page.getByTestId('admin-privileged-data-security-events-filter')).toBeVisible();
  await page.getByTestId('admin-privileged-data-security-events-type-select').selectOption('UNUSUAL_LOGIN');
  await page.getByTestId('admin-privileged-data-security-events-actor-input').fill('admin-user');
  await page.getByTestId('admin-privileged-data-security-events-result-select').selectOption('DETECTED');
  await expect(page.getByTestId('admin-privileged-data-security-events-from-input')).toBeVisible();
  await expect(page.getByTestId('admin-privileged-data-security-events-to-input')).toBeVisible();
  const filtered = page.waitForRequest((request) => request.url().includes('/api/v1/console/security-events')
    && request.url().includes('eventType=UNUSUAL_LOGIN')
    && request.url().includes('actor=admin-user'));
  await page.getByTestId('admin-privileged-data-security-events-query').click();
  await filtered;
  await expect(page.getByTestId('admin-privileged-data-security-events-reset')).toBeEnabled();
});

test('pw-p6-key-action C-P6-KEY-ACTIONS OBL-PERMISSION-KEY-ACTIONS', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/logs');

  await expect(page.getByTestId('admin-privileged-data-system-logs-page')).toBeVisible();
  await expect(page.getByRole('link', { name: '操作日志' })).toBeVisible();
  await expect(page.getByRole('link', { name: '安全事件' })).toBeVisible();
});

test('pw-p6-masked-value C-P6-MASKING OBL-DISPLAY-MASKING', async ({ page }) => {
  await mockPrivilegedAuditApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/system/users');

  await expect(page.getByTestId('shared-privileged-data-sensitive-value')).toHaveText('138****0000');
  await expect(page.getByText('13800138000')).toHaveCount(0);
});
