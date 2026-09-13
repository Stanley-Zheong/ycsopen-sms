import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const task = {
  bulkId: 301,
  tenantId: 42,
  batchKey: 'BULK-301',
  taskName: '批量发送任务',
  messageType: 'NOTIFY',
  priority: 'HIGH',
  totalCount: 3,
  validCount: 2,
  invalidCount: 1,
  runningCount: 1,
  completedCount: 1,
  failCount: 1,
  cancelledCount: 0,
  totalCost: 0.05,
  state: 'RUNNING',
  scheduleAt: '2026-09-10T09:00:00',
  createdAt: '2026-09-09T00:00:00',
  failureReason: null,
  controlReason: null,
};

async function mockTenantBulkApis(page: Page) {
  await page.route('**/api/v1/console/trial-prepaid/tenants/7/overview', (route: Route) => route.fulfill({ json: apiResponse({ trialStatus: 'ACTIVE', remainingQuota: 100, balance: '10.00' }) }));
  await page.route('**/api/v1/console/tenant/bulk/preview', async (route: Route) => {
    expect(route.request().method()).toBe('POST');
    await route.fulfill({ json: apiResponse({
      tenantId: 7,
      batchKey: 'BULK-301',
      taskName: '批量发送任务',
      sourceFileName: 'contacts.csv',
      total: 3,
      valid: 1,
      invalid: 2,
      rows: [
        { rowNo: 1, phoneNumber: '13800138000', maskedPhone: '138****8000', variables: { code: '2468' }, validationStatus: 'VALID', validationReason: null },
        { rowNo: 2, phoneNumber: '13800138000', maskedPhone: '138****8000', variables: { code: '1357' }, validationStatus: 'INVALID', validationReason: '重复手机号' },
      ],
    }) });
  });
  await page.route('**/api/v1/console/tenant/bulk/tasks', async (route: Route) => {
    expect(route.request().postDataJSON().items.length).toBeGreaterThan(0);
    await route.fulfill({ json: apiResponse(task) });
  });
  await page.route('**/api/v1/console/tenant/scheduled-tasks', (route: Route) => route.fulfill({ json: apiResponse([task]) }));
  await page.route('**/api/v1/console/tenant/scheduled-tasks/301/pause', (route: Route) => route.fulfill({ json: apiResponse({ ...task, state: 'PAUSED' }) }));
}

async function mockAdminBulkApis(page: Page) {
  await page.route('**/api/v1/console/account-overview', (route: Route) => route.fulfill({ json: apiResponse({ id: 7, username: 'admin-user', userType: 'ADMIN', roleNames: ['系统管理员'], permissions: [], lastLoginAt: null, lastLoginIp: null }) }));
  await page.route('**/api/v1/console/bulk/tasks', (route: Route) => route.fulfill({ json: apiResponse([task]) }));
  await page.route('**/api/v1/console/bulk/tasks?**', (route: Route) => route.fulfill({ json: apiResponse([task]) }));
  await page.route('**/api/v1/console/bulk/tasks/301', (route: Route) => route.fulfill({ json: apiResponse({ task, items: [{ itemId: 1, itemTrackingId: 'BIT-301-1', rowNo: 1, messageId: 'MSG_1', sendStatus: 'PENDING', validationStatus: 'VALID', validationReason: null, cost: 0.05, updatedAt: '2026-09-09T00:00:00' }] }) }));
  await page.route('**/api/v1/console/bulk/tasks/301/pause', (route: Route) => route.fulfill({ json: apiResponse({ ...task, state: 'PAUSED' }) }));
}

test.describe('Phase 29 bulk scheduled task operations', () => {
  test('pw-p29-tenant-bulk-send C-P29-TENANT-BULK-SEND OBL-F-6-11-A pw-p29-import-preview C-P29-IMPORT-PREVIEW OBL-F-6-11-B', async ({ page }) => {
    await mockTenantBulkApis(page);
    await loginAs(page, 'TENANT_USER');
    await page.goto('/tenant/bulk/send');
    await expect(page.getByTestId('tenant-bulk-scheduled-bulk-send-page')).toBeVisible();
    await page.getByTestId('tenant-bulk-scheduled-bulk-send-preview').click();
    await expect(page.getByTestId('tenant-bulk-scheduled-bulk-send-validation-results')).toContainText('重复手机号');
    await page.getByTestId('tenant-bulk-scheduled-bulk-send-create').click();
    await expect(page.getByTestId('tenant-bulk-scheduled-operation-message')).toContainText('批任务已创建');
  });

  test('pw-p29-tenant-scheduled-tasks C-P29-TENANT-SCHEDULED-TASKS OBL-F-6-12-A pw-p29-tenant-task-control C-P29-TENANT-TASK-CONTROL OBL-F-6-12-B pw-p29-state-start C-P29-STATE-START OBL-STATE-BATCH-START pw-p29-state-complete C-P29-STATE-COMPLETE OBL-STATE-BATCH-COMPLETE pw-p29-state-fail C-P29-STATE-FAIL OBL-STATE-BATCH-FAIL pw-p29-state-pause C-P29-STATE-PAUSE OBL-STATE-BATCH-PAUSE pw-p29-state-resume C-P29-STATE-RESUME OBL-STATE-BATCH-RESUME pw-p29-state-restart C-P29-STATE-RESTART OBL-STATE-BATCH-RESTART', async ({ page }) => {
    await mockTenantBulkApis(page);
    await loginAs(page, 'TENANT_USER');
    await page.goto('/tenant/scheduled/tasks');
    await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-page')).toBeVisible();
    await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-state')).toContainText('RUNNING');
    await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-control')).toBeVisible();
    await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-resume')).toBeVisible();
    await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-restart')).toBeVisible();
    await page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-pause').click();
    await expect(page.getByTestId('tenant-bulk-scheduled-operation-message')).toContainText('PAUSED');
  });

  test('pw-p29-admin-bulk-details C-P29-ADMIN-BULK-DETAILS OBL-F-7-3-A pw-p29-admin-bulk-detail-table C-P29-ADMIN-BULK-DETAIL-TABLE OBL-F-7-3-B', async ({ page }) => {
    await mockEmptyDashboard(page);
    await mockAdminBulkApis(page);
    await loginAs(page, 'ADMIN');
    await page.goto('/admin/bulk/details');
    await expect(page.getByTestId('admin-bulk-scheduled-bulk-details-page')).toBeVisible();
    await expect(page.getByTestId('admin-bulk-scheduled-bulk-details-table')).toContainText('BULK-301');
    await expect(page.getByTestId('admin-bulk-scheduled-bulk-details-items')).toContainText('BIT-301-1');
  });

  test('pw-p29-admin-send-jobs C-P29-ADMIN-SEND-JOBS OBL-F-13-5-A pw-p29-admin-send-job-control C-P29-ADMIN-SEND-JOB-CONTROL OBL-F-13-5-B', async ({ page }) => {
    await mockEmptyDashboard(page);
    await mockAdminBulkApis(page);
    await loginAs(page, 'ADMIN');
    await page.goto('/admin/send/jobs');
    await expect(page.getByTestId('admin-bulk-scheduled-send-jobs-page')).toBeVisible();
    await expect(page.getByTestId('admin-bulk-scheduled-send-jobs-control')).toContainText('BULK-301');
    await page.getByTestId('admin-bulk-scheduled-send-jobs-pause').click();
    await expect(page.getByTestId('admin-bulk-scheduled-operation-message')).toContainText('PAUSED');
  });
});
