import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const policy = { id: 1, dataDomain: 'MESSAGE_TASKS', sourceTable: 'message_tasks', retentionDays: 730, hotMonths: 3, partitionUnit: 'MONTH', legalHoldUntil: null, encryptionRequired: true, status: 'ACTIVE', updatedBy: 'phase47', updatedAt: '2026-09-10T08:00:00' };
const manifest = { id: 47, policyId: 1, dataDomain: 'MESSAGE_TASKS', sourceTable: 'message_tasks', partitionKey: '2026-01', tenantId: 42, archiveStatus: 'COMPLETED', rowCount: 2, sourceIdentityJson: '{"ids":[1,2],"businessKeys":["MSG-1"]}', manifestJson: '{"rowCount":2}', checksumSha256: 'abcdef1234567890', encryptionKeyVersion: 'archive-v1', retentionUntil: '2028-01-01T00:00:00', legalHoldUntil: null, deletionEligible: false, failureReason: null, createdBy: '7', createdAt: '2026-09-10T08:00:00', verifiedAt: null, restoredAt: null, exportedTaskId: null };
const corrupted = { ...manifest, id: 48, archiveStatus: 'CORRUPTED', failureReason: '归档密文无法解密', deletionEligible: false };

async function mockArchive(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/archive/policies', (route: Route) => route.fulfill({ json: apiResponse([policy]) }));
  await page.route('**/api/v1/console/archive/policies/MESSAGE_TASKS', (route: Route) => route.fulfill({ json: apiResponse({ ...policy, updatedBy: '7' }) }));
  await page.route('**/api/v1/console/archive/manifests?**', (route: Route) => route.fulfill({ json: apiResponse([manifest, corrupted]) }));
  await page.route('**/api/v1/console/archive/manifests/scan', (route: Route) => route.fulfill({ json: apiResponse(manifest) }));
  await page.route('**/api/v1/console/archive/manifests/47/verify', (route: Route) => route.fulfill({ json: apiResponse({ ...manifest, verifiedAt: '2026-09-10T08:02:00' }) }));
  await page.route('**/api/v1/console/archive/manifests/47/restore', (route: Route) => route.fulfill({
    json: apiResponse({ id: 90, manifestId: 47, requestType: 'RESTORE', status: 'COMPLETED', requestedBy: '7', resultMessage: '已恢复', restoredRecordCount: 2, exportTaskId: null, createdAt: '2026-09-10T08:02:00', completedAt: '2026-09-10T08:02:01' }),
  }));
  await page.route('**/api/v1/console/archive/manifests/47/export', (route: Route) => route.fulfill({
    json: apiResponse({ id: 91, manifestId: 47, requestType: 'EXPORT', status: 'COMPLETED', requestedBy: '7', resultMessage: '已导出', restoredRecordCount: 2, exportTaskId: 46, createdAt: '2026-09-10T08:03:00', completedAt: '2026-09-10T08:03:01' }),
  }));
}

test('OBL-NFR-RETENTION-TWO-YEAR C-P47-POLICY pw-p47-policy OBL-NFR-HOT-COLD C-P47-SCAN pw-p47-scan', async ({ page }) => {
  await mockArchive(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/archive');
  await expect(page.getByTestId('admin-retention-archive-page')).toBeVisible();
  await expect(page.getByTestId('admin-retention-archive-policy-card')).toContainText('message_tasks');
  await page.getByTestId('admin-retention-archive-policy-save').click();
  await expect(page.getByTestId('admin-retention-archive-message')).toContainText('归档策略已保存');
  await page.getByTestId('admin-retention-archive-scan').click();
  await expect(page.getByTestId('admin-retention-archive-message')).toContainText('归档扫描完成');
});

test('OBL-NFR-ARCHIVE-ENCRYPT C-P47-VERIFY pw-p47-verify OBL-NFR-ARCHIVE-RESTORE C-P47-RESTORE pw-p47-restore OBL-DOD-06-DATA C-P47-EXPORT pw-p47-export', async ({ page }) => {
  await mockArchive(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/archive');
  await expect(page.getByTestId('admin-retention-archive-manifest-table')).toContainText('abcdef123456');
  await expect(page.getByTestId('admin-retention-archive-deletion-eligibility').first()).toContainText('保留中');
  await expect(page.getByTestId('admin-retention-archive-manifest-restore')).toHaveCount(2);
  await page.getByTestId('admin-retention-archive-manifest-verify').first().click();
  await expect(page.getByTestId('admin-retention-archive-message')).toContainText('归档校验完成');
  await page.getByTestId('admin-retention-archive-manifest-restore').first().click();
  await expect(page.getByTestId('admin-retention-archive-message')).toContainText('恢复任务完成');
  await page.getByTestId('admin-retention-archive-export').first().click();
  await expect(page.getByTestId('admin-retention-archive-message')).toContainText('归档导出任务已创建');
});
