import { expect, test, type Page } from '@playwright/test';
import { loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

async function loginTenant(page: Page) {
  await mockEmptyDashboard(page);
  await loginAs(page, 'TENANT_DEV');
}

test('OBL-IA-TENANT-HELP-GUIDE C-P50-GUIDE pw-p50-guide', async ({ page }) => {
  await loginTenant(page);
  await page.goto('/tenant/help/guide');
  await expect(page.getByTestId('tenant-tenant-help-guide-page')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-help-content-version')).toContainText('2026.09');
  await page.getByTestId('tenant-tenant-help-guide-search-input').fill('短链');
  await expect(page.getByTestId('tenant-tenant-help-guide-results')).toContainText('短链管理');
});

test('OBL-IA-TENANT-HELP-API C-P50-API pw-p50-api', async ({ page }) => {
  await loginTenant(page);
  await page.goto('/tenant/help/api');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-page')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-help-api-docs-endpoint')).toContainText('/api/v1/sms/send');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-auth-headers')).toContainText('X-Signature');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-auth-headers')).toContainText('Unix 秒');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-request-table')).toContainText('submitId');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-response-table')).toContainText('data.status');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-error-table')).toContainText('TENANT_LIFECYCLE_INELIGIBLE');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-error-table')).toContainText('ROUTING_REJECTED');
  await expect(page.getByTestId('tenant-tenant-help-api-docs-example')).toContainText('callbackUrl');
});

test('OBL-IA-TENANT-HELP-SERVICE C-P50-SERVICE pw-p50-service', async ({ page }) => {
  await loginTenant(page);
  await page.goto('/tenant/help/customer-service');
  await expect(page.getByTestId('tenant-tenant-help-customer-service-page')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-help-customer-service-availability')).toContainText('工作日');
  await expect(page.getByTestId('tenant-tenant-help-customer-service-destination')).toContainText('support@ycsopen.example');
  await page.getByTestId('tenant-tenant-help-customer-service-fallback-action').click();
  await expect(page.getByTestId('tenant-tenant-help-customer-service-fallback')).toContainText('traceId');
});
