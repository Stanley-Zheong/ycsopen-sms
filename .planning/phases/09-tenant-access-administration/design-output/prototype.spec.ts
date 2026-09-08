import { test, expect } from '@playwright/test';

const documentedTenantAccessTestIds = [
  'tenant-tenant-access-administrators-page',
  'tenant-tenant-access-administrators-create',
  'tenant-tenant-access-administrators-role',
  'tenant-tenant-access-api-keys-page',
  'tenant-tenant-access-api-keys-secret-once',
  'tenant-tenant-access-cmpp-access-page',
  'tenant-tenant-access-administrators-row',
  'tenant-tenant-access-administrators-create-dialog',
  'tenant-tenant-access-administrators-username-field',
  'tenant-tenant-access-administrators-role-field',
  'tenant-tenant-access-administrators-status-action',
  'tenant-tenant-access-api-keys-create-dialog',
  'tenant-tenant-access-api-keys-name-field',
  'tenant-tenant-access-api-keys-expiry-field',
  'tenant-tenant-access-api-keys-ip-allow-list-field',
  'tenant-tenant-access-api-keys-rate-policy-field',
  'tenant-tenant-access-api-keys-row',
  'tenant-tenant-access-api-keys-revoke',
  'tenant-tenant-access-api-keys-secret-dialog',
  'tenant-tenant-access-cmpp-request-form',
  'tenant-tenant-access-cmpp-endpoint',
  'tenant-tenant-access-cmpp-connection-policy',
  'tenant-tenant-access-cmpp-row',
  'tenant-tenant-access-cmpp-secret-once',
  'tenant-tenant-access-cmpp-revoke',
];

test('pw-p9-tenant-access-selector-inventory C-P9-DESIGN-SELECTORS OBL-F-2-7-A', async ({ page }) => {
  await page.goto('/tenant/administrators');
  await expect(page.getByTestId('tenant-tenant-access-administrators-row')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-administrators-create-dialog')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-administrators-username-field')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-administrators-role-field')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-administrators-status-action')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-create-dialog')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-name-field')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-expiry-field')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-ip-allow-list-field')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-rate-policy-field')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-row')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-revoke')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-dialog')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-request-form')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-endpoint')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-connection-policy')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-row')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-secret-once')).toBeAttached();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-revoke')).toBeAttached();
});

test('pw-p9-tenant-administrators-page C-P9-ADMIN-SETTINGS OBL-F-2-7-A', async ({ page }) => {
  await page.goto('/tenant/administrators');
  await expect(page.getByTestId('tenant-tenant-access-administrators-page')).toBeVisible();
});

test('pw-p9-tenant-administrators-create C-P9-SUBACCOUNT-CREATE OBL-F-1-3-A', async ({ page }) => {
  await page.goto('/tenant/administrators');
  await expect(page.getByTestId('tenant-tenant-access-administrators-create')).toBeVisible();
});

test('pw-p9-tenant-administrators-role C-P9-SUBACCOUNT-ISOLATION OBL-F-1-3-B', async ({ page }) => {
  await page.goto('/tenant/administrators');
  await expect(page.getByTestId('tenant-tenant-access-administrators-role')).toBeVisible();
});

test('pw-p9-api-key-page C-P9-API-KEY-POLICY OBL-F-2-6-A', async ({ page }) => {
  await page.goto('/tenant/api/keys');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-page')).toBeVisible();
});

test('pw-p9-api-key-secret-once C-P9-API-KEY-SECRET OBL-F-2-6-B', async ({ page }) => {
  await page.goto('/tenant/api/keys');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toBeVisible();
});

test('pw-p9-cmpp-page C-P9-CMPP-CREDENTIAL OBL-F-2-6-C', async ({ page }) => {
  await page.goto('/tenant/cmpp/access');
  await expect(page.getByTestId('tenant-tenant-access-cmpp-access-page')).toBeVisible();
});
