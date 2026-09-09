import { expect, test } from '@playwright/test';

test('pw-p13-template-application C-P13-TEMPLATE-APPLICATION OBL-F-3-4-A', async ({ page }) => {
  await page.goto('/tenant/templates');
  await expect(page.getByTestId('tenant-template-lifecycle-templates-page')).toBeVisible();
});

test('pw-p13-template-variable-preview C-P13-TEMPLATE-PREVIEW OBL-F-3-4-B', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-variable-preview').click();
});

test('pw-p13-template-review C-P13-TEMPLATE-REVIEW OBL-F-3-5-A', async ({ page }) => {
  await page.goto('/admin/templates/review');
  await expect(page.getByTestId('admin-template-lifecycle-template-review-page')).toBeVisible();
});

test('pw-p13-template-decision C-P13-TEMPLATE-DECISION OBL-F-3-5-B', async ({ page }) => {
  await page.goto('/admin/templates/review');
  await page.getByTestId('admin-template-lifecycle-template-review-decision').click();
});

test('pw-p13-template-name C-P13-FIELD-NAME OBL-FIELD-TEMPLATE-NAME', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-form-name').fill('登录验证码');
});

test('pw-p13-template-content C-P13-FIELD-CONTENT OBL-FIELD-TEMPLATE-CONTENT', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-form-content').fill('您的验证码是 ${code}');
});

test('pw-p13-template-type C-P13-FIELD-TYPE OBL-FIELD-TEMPLATE-TYPE', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-form-type').selectOption('verification');
});

test('pw-p13-template-signature C-P13-FIELD-SIGNATURE OBL-FIELD-TEMPLATE-SIGNATURE', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-form-signature').fill('1201');
});

test('pw-p13-template-param-rule C-P13-FIELD-PARAM-RULE OBL-FIELD-TEMPLATE-PARAM-RULE', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-form-param-rule').fill('code:digits(4-8)');
});

test('pw-p13-template-approve C-P13-STATE-APPROVE OBL-STATE-REVIEW-APPROVE', async ({ page }) => {
  await page.goto('/admin/templates/review');
  await page.getByTestId('admin-template-lifecycle-template-review-approve').click();
});

test('pw-p13-template-reject C-P13-STATE-REJECT OBL-STATE-REVIEW-REJECT', async ({ page }) => {
  await page.goto('/admin/templates/review');
  await page.getByTestId('admin-template-lifecycle-template-review-reject').click();
});

test('pw-p13-template-resubmit C-P13-STATE-RESUBMIT OBL-STATE-REVIEW-RESUBMIT', async ({ page }) => {
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-resubmit').click();
});
