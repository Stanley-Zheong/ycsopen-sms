import { expect, test } from '@playwright/test';

test.describe('Phase 41 prototype contract', () => {
  test('pw-p41-intake C-P41-INTAKE OBL-F-9-1-A', async ({ page }) => {
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-page')).toBeVisible();
  });

  test('pw-p41-attribution C-P41-ATTRIBUTION OBL-F-9-1-B', async ({ page }) => {
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-attribution-quality')).toBeVisible();
  });

  test('pw-p41-state C-P41-STATE OBL-F-9-2-A', async ({ page }) => {
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-state-action')).toBeVisible();
  });

  test('pw-p41-remediation C-P41-REMEDIATION OBL-F-9-3-A', async ({ page }) => {
    await page.goto('/admin/complaints');
    await page.getByTestId('admin-complaint-case-complaints-remediation').click();
  });

  test('pw-p41-recovery C-P41-RECOVERY OBL-F-9-3-B', async ({ page }) => {
    await page.goto('/admin/complaints');
    await page.getByTestId('admin-complaint-case-complaints-remediation-recovery').click();
  });

  test('pw-p41-analytics C-P41-ANALYTICS OBL-F-9-4-A', async ({ page }) => {
    await page.goto('/admin/complaint/analytics');
    await expect(page.getByTestId('admin-complaint-case-analytics-page')).toBeVisible();
  });

  test('pw-p41-resource C-P41-RESOURCE-DISABLE OBL-STATE-RESOURCE-DISABLE', async ({ page }) => {
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-resource')).toBeVisible();
  });

  test('pw-p41-accept C-P41-ACCEPT OBL-STATE-COMPLAINT-PROCESS', async ({ page }) => {
    await page.goto('/admin/complaints');
    await page.getByTestId('admin-complaint-case-complaints-accept').click();
  });

  test('pw-p41-handle C-P41-HANDLE OBL-STATE-COMPLAINT-HANDLED', async ({ page }) => {
    await page.goto('/admin/complaints');
    await page.getByTestId('admin-complaint-case-complaints-handle').click();
  });

  test('pw-p41-close C-P41-CLOSE OBL-STATE-COMPLAINT-CLOSE', async ({ page }) => {
    await page.goto('/admin/complaints');
    await page.getByTestId('admin-complaint-case-complaints-close').click();
  });
});
