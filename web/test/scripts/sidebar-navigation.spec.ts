import { expect, test } from '@playwright/test';
import { loginAs, mockEmptyDashboard, navigateWithinSpa } from './helpers';

test('pw-issue-51-admin-sidebar C-ISSUE-51-ADMIN OBL-ISSUE-51-ADMIN-SIDEBAR issue-51-shared-sidebar-01 admin menu keeps one PRD group open and follows deep routes', async ({ page }) => {
  await mockEmptyDashboard(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/dashboard');

  const overview = page.getByTestId('admin-console-navigation-overview-group-toggle');
  const details = page.getByTestId('admin-console-navigation-message-details-group-toggle');
  await expect(overview).toHaveAttribute('aria-expanded', 'true');
  await details.press('Enter');
  await expect(details).toBeFocused();
  await expect(details).toHaveCSS('outline-color', 'rgb(255, 255, 255)');
  await expect(details).toHaveAttribute('aria-expanded', 'true');
  await expect(overview).toHaveAttribute('aria-expanded', 'false');
  await expect(page.locator('.sidebar-menu-panel:not([hidden])')).toHaveCount(1);

  await page.getByTestId('admin-message-operations-submission-details-nav-menu').click();
  await expect(page).toHaveURL(/\/admin\/submission\/details$/);
  await expect(details).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByTestId('admin-message-operations-submission-details-nav-menu')).toHaveAttribute('aria-current', 'page');

  await navigateWithinSpa(page, '/admin/channel/health');
  await expect(page.getByTestId('admin-console-navigation-channel-management-group-toggle')).toHaveAttribute('aria-expanded', 'true');
  await expect(details).toHaveAttribute('aria-expanded', 'false');
  await expect(page.getByTestId('admin-channel-health-nav-menu')).toHaveAttribute('aria-current', 'page');
});

test('pw-issue-51-tenant-sidebar C-ISSUE-51-TENANT OBL-ISSUE-51-TENANT-SIDEBAR issue-51-shared-sidebar-01 tenant menu covers routed pages and role restrictions', async ({ page }) => {
  await loginAs(page, 'TENANT_USER');
  await page.goto('/tenant/overview');

  await expect(page.getByTestId('tenant-console-navigation-overview-group-toggle')).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByTestId('tenant-console-navigation-account-settings-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-configuration-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-template-management-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-signature-management-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-uplink-query-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-webhook-delivery-webhooks-nav-menu')).toHaveCount(0);
  await expect(page.getByTestId('tenant-tenant-access-api-keys-nav-menu')).toHaveCount(0);
  await expect(page.getByTestId('tenant-tenant-access-cmpp-access-nav-menu')).toHaveCount(0);
  await page.goto('/tenant/scheduled/tasks');
  const sendManagement = page.getByTestId('tenant-console-navigation-send-management-group-toggle');
  await expect(sendManagement).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByTestId('tenant-bulk-scheduled-scheduled-tasks-nav-menu')).toHaveAttribute('aria-current', 'page');
  await expect(page.locator('.sidebar-menu-panel:not([hidden])')).toHaveCount(1);

  await loginAs(page, 'TENANT_DEV');
  await page.goto('/tenant/webhooks');
  const configuration = page.getByTestId('tenant-console-navigation-configuration-group-toggle');
  await expect(configuration).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByTestId('tenant-webhook-delivery-webhooks-nav-menu')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-nav-menu')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-cmpp-access-nav-menu')).toBeVisible();
  await expect(page.getByTestId('tenant-console-navigation-account-settings-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-template-management-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-signature-management-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-account-management-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-short-links-group-toggle')).toHaveCount(0);
  await expect(page.getByTestId('tenant-console-navigation-uplink-query-group-toggle')).toBeVisible();
});
