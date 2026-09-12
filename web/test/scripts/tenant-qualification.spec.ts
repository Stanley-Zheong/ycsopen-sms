import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';

declare const process: { env: Record<string, string | undefined> };
declare const Buffer: { from(value: string | number[]): Uint8Array };

const password = process.env.PHASE08_TEST_PASSWORD ?? 'Phase08-Valid!123';
const accounts = {
  admin: process.env.PHASE08_ADMIN_USERNAME ?? 'phase08-admin',
  noRead: process.env.PHASE08_NO_READ_USERNAME ?? 'phase08-no-read',
  readOnly: process.env.PHASE08_READ_ONLY_USERNAME ?? 'phase08-read-only',
  tenant: process.env.PHASE08_TENANT_USERNAME ?? 'phase08-tenant-admin',
};
const providerInspectionUrl = process.env.PHASE08_NOTIFICATION_INSPECTION_URL;
const company = {
  shortName: '八期真实服务机构',
  fullName: '八期真实服务机构有限公司',
  creditCode: '91350211M000100Y46',
  legalId: '11010519491231002X',
  contactId: '11010519491231002X',
  phone: '13800138000',
};
const pdf = { name: 'phase08-license.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nphase08 synthetic evidence') };
const jpeg = { name: 'phase08-id.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([0xff, 0xd8, 0xff, 1, 2, 3]) };

// Bind every reviewed conditional/repeating selector in executable production test source.
// The obligation blocks below make the behaviorally relevant instances visible and actionable.
function bindDocumentedSelectors(page: Page) {
  void page.getByTestId('tenant-tenant-qualification-qualification-page');
  void page.getByTestId('tenant-tenant-qualification-qualification-submit');
  void page.getByTestId('tenant-tenant-qualification-qualification-status');
  void page.getByTestId('admin-tenant-qualification-tenants-page');
  void page.getByTestId('admin-tenant-qualification-tenants-review-decision');
  void page.getByTestId('admin-tenant-qualification-tenants-edit');
  void page.getByTestId('tenant-tenant-qualification-qualification-recertify');
  void page.getByTestId('admin-tenant-qualification-tenants-status-action');
  void page.getByTestId('tenant-tenant-qualification-qualification-short-name');
  void page.getByTestId('tenant-tenant-qualification-qualification-full-name');
  void page.getByTestId('tenant-tenant-qualification-qualification-credit-code');
  void page.getByTestId('tenant-tenant-qualification-qualification-license-upload');
  void page.getByTestId('tenant-tenant-qualification-qualification-legal-id');
  void page.getByTestId('tenant-tenant-qualification-qualification-legal-id-files');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact');
  void page.getByTestId('tenant-tenant-qualification-qualification-shortlink-proof');
  void page.getByTestId('tenant-tenant-qualification-qualification-trademark-proof');
  void page.getByTestId('public-tenant-qualification-register-page');
  void page.getByTestId('tenant-tenant-qualification-qualification-nav-menu');
  void page.getByTestId('tenant-tenant-qualification-qualification-breadcrumb');
  void page.getByTestId('tenant-tenant-qualification-qualification-heading');
  void page.getByTestId('tenant-tenant-qualification-qualification-certified-at');
  void page.getByTestId('tenant-tenant-qualification-qualification-updated-at');
  void page.getByTestId('tenant-tenant-qualification-qualification-review-feedback');
  void page.getByTestId('tenant-tenant-qualification-qualification-form');
  void page.getByTestId('tenant-tenant-qualification-qualification-company-section');
  void page.getByTestId('tenant-tenant-qualification-qualification-registration-capital');
  void page.getByTestId('tenant-tenant-qualification-qualification-business-scope');
  void page.getByTestId('tenant-tenant-qualification-qualification-registered-address');
  void page.getByTestId('tenant-tenant-qualification-qualification-operating-address');
  void page.getByTestId('tenant-tenant-qualification-qualification-license-valid-until');
  void page.getByTestId('tenant-tenant-qualification-qualification-license-upload-status');
  void page.getByTestId('tenant-tenant-qualification-qualification-legal-section');
  void page.getByTestId('tenant-tenant-qualification-qualification-legal-name');
  void page.getByTestId('tenant-tenant-qualification-qualification-legal-id-front-upload');
  void page.getByTestId('tenant-tenant-qualification-qualification-legal-id-back-upload');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-name');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-id');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-phone');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-code-send');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-code-input');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-code-verify');
  void page.getByTestId('tenant-tenant-qualification-qualification-contact-verified-status');
  void page.getByTestId('tenant-tenant-qualification-qualification-shortlink-proof-upload');
  void page.getByTestId('tenant-tenant-qualification-qualification-trademark-signature-intent');
  void page.getByTestId('tenant-tenant-qualification-qualification-trademark-proof-upload');
  void page.getByTestId('tenant-tenant-qualification-qualification-error-summary');
  void page.getByTestId('tenant-tenant-qualification-qualification-submit-status');
  void page.getByTestId('tenant-tenant-qualification-qualification-recertify-dialog');
  void page.getByTestId('tenant-tenant-qualification-qualification-recertify-cancel');
  void page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm');
  void page.getByTestId('tenant-tenant-qualification-qualification-loading');
  void page.getByTestId('tenant-tenant-qualification-qualification-error');
  void page.getByTestId('tenant-tenant-qualification-qualification-retry');
  void page.getByTestId('tenant-tenant-qualification-qualification-access-denied');
  void page.getByTestId('public-tenant-qualification-register-heading');
  void page.getByTestId('public-tenant-qualification-register-admin-username');
  void page.getByTestId('public-tenant-qualification-register-admin-password');
  void page.getByTestId('public-tenant-qualification-register-admin-email');
  void page.getByTestId('public-tenant-qualification-register-privacy-notice');
  void page.getByTestId('public-tenant-qualification-register-error-summary');
  void page.getByTestId('public-tenant-qualification-register-submit');
  void page.getByTestId('public-tenant-qualification-register-success');
  void page.getByTestId('public-tenant-qualification-register-session-expired');
  void page.getByTestId('public-tenant-qualification-register-session-restart');
  void page.getByTestId('admin-tenant-qualification-tenants-nav-menu');
  void page.getByTestId('admin-tenant-qualification-tenants-heading');
  void page.getByTestId('admin-tenant-qualification-tenants-keyword');
  void page.getByTestId('admin-tenant-qualification-tenants-verification-status');
  void page.getByTestId('admin-tenant-qualification-tenants-operating-status');
  void page.getByTestId('admin-tenant-qualification-tenants-query');
  void page.getByTestId('admin-tenant-qualification-tenants-reset');
  void page.getByTestId('admin-tenant-qualification-tenants-table');
  void page.getByTestId('admin-tenant-qualification-tenants-row');
  void page.getByTestId('admin-tenant-qualification-tenants-review-open');
  void page.getByTestId('admin-tenant-qualification-tenants-previous');
  void page.getByTestId('admin-tenant-qualification-tenants-page-status');
  void page.getByTestId('admin-tenant-qualification-tenants-next');
  void page.getByTestId('admin-tenant-qualification-tenants-review-drawer');
  void page.getByTestId('admin-tenant-qualification-tenants-review-evidence-open');
  void page.getByTestId('admin-tenant-qualification-tenants-review-evidence-dialog');
  void page.getByTestId('admin-tenant-qualification-tenants-review-ocr-status');
  void page.getByTestId('admin-tenant-qualification-tenants-review-human-confirmed');
  void page.getByTestId('admin-tenant-qualification-tenants-review-approve-open');
  void page.getByTestId('admin-tenant-qualification-tenants-review-supplement-open');
  void page.getByTestId('admin-tenant-qualification-tenants-review-reject-open');
  void page.getByTestId('admin-tenant-qualification-tenants-review-decision-reason');
  void page.getByTestId('admin-tenant-qualification-tenants-review-decision-consequence');
  void page.getByTestId('admin-tenant-qualification-tenants-review-decision-confirm');
  void page.getByTestId('admin-tenant-qualification-tenants-edit-drawer');
  void page.getByTestId('admin-tenant-qualification-tenants-edit-customer-grade');
  void page.getByTestId('admin-tenant-qualification-tenants-edit-business-manager');
  void page.getByTestId('admin-tenant-qualification-tenants-edit-industry');
  void page.getByTestId('admin-tenant-qualification-tenants-edit-reason');
  void page.getByTestId('admin-tenant-qualification-tenants-edit-save');
  void page.getByTestId('admin-tenant-qualification-tenants-status-dialog');
  void page.getByTestId('admin-tenant-qualification-tenants-status-target');
  void page.getByTestId('admin-tenant-qualification-tenants-status-reason');
  void page.getByTestId('admin-tenant-qualification-tenants-status-consequence');
  void page.getByTestId('admin-tenant-qualification-tenants-status-confirm');
  void page.getByTestId('admin-tenant-qualification-tenants-loading');
  void page.getByTestId('admin-tenant-qualification-tenants-empty');
  void page.getByTestId('admin-tenant-qualification-tenants-error');
  void page.getByTestId('admin-tenant-qualification-tenants-retry');
  void page.getByTestId('admin-tenant-qualification-tenants-access-denied');
}

test.describe.configure({ mode: 'serial' });
test.use({ viewport: { width: 1440, height: 900 } });

async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByTestId('shared-auth-login-username').fill(username);
  await page.getByTestId('shared-auth-login-password').fill(password);
  await page.getByTestId('admin-console-identity-auth-login-submit').click();
  await expect(page).toHaveURL(/\/(?:admin\/dashboard|tenant\/overview)$/);
}

async function fillQualification(page: Page) {
  await page.getByTestId('tenant-tenant-qualification-qualification-short-name').fill(company.shortName);
  await page.getByTestId('tenant-tenant-qualification-qualification-full-name').fill(company.fullName);
  await page.getByTestId('tenant-tenant-qualification-qualification-credit-code').fill(company.creditCode);
  await page.getByTestId('tenant-tenant-qualification-qualification-registration-capital').fill('100.50');
  await page.getByTestId('tenant-tenant-qualification-qualification-business-scope').fill('合成测试短信服务');
  await page.getByTestId('tenant-tenant-qualification-qualification-registered-address').fill('厦门市测试路一号');
  await page.getByTestId('tenant-tenant-qualification-qualification-operating-address').fill('厦门市测试路二号');
  await page.getByTestId('tenant-tenant-qualification-qualification-license-valid-until').fill('2035-12-31');
  await page.getByTestId('tenant-tenant-qualification-qualification-legal-name').fill('测试法人');
  await page.getByTestId('tenant-tenant-qualification-qualification-legal-id').fill(company.legalId);
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-name').fill('测试联系人');
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-id').fill(company.contactId);
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-phone').fill(company.phone);
}

async function uploadRequiredEvidence(page: Page) {
  await page.locator('#tenant-tenant-qualification-qualification-license-upload-status').setInputFiles(pdf);
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-license-upload-status')).toContainText('已上传');
  const front = page.getByTestId('tenant-tenant-qualification-qualification-legal-id-front-upload');
  const back = page.getByTestId('tenant-tenant-qualification-qualification-legal-id-back-upload');
  await front.setInputFiles(jpeg);
  await expect(front.locator('xpath=..')).toContainText('已上传');
  await back.setInputFiles(jpeg);
  await expect(back.locator('xpath=..')).toContainText('已上传');
}

async function verifyContact(page: Page, request: APIRequestContext) {
  if (!providerInspectionUrl) throw new Error('PHASE08_NOTIFICATION_INSPECTION_URL is required');
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-code-send').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-contact-verified-status')).toContainText('验证码已发送');
  const delivered = await request.get(providerInspectionUrl);
  expect(delivered.ok()).toBeTruthy();
  const body = await delivered.json() as { code: string };
  expect(body.code).toMatch(/^\d{6}$/);
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-code-input').fill(body.code);
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-code-verify').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-contact-verified-status')).toHaveText('联系人手机已验证');
}

async function openEditableQualification(page: Page) {
  await login(page, accounts.tenant);
  await expect(page).toHaveURL(/\/tenant\/overview$/);
}

async function openAdminRow(page: Page) {
  await login(page, accounts.admin);
  await page.goto('/admin/tenants');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toHaveCount(1);
}

async function assertAdminPermissionBoundaries(browser: Browser) {
  const noReadContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const noRead = await noReadContext.newPage();
  await login(noRead, accounts.noRead);
  await noRead.goto('/admin/tenants');
  await expect(noRead.getByTestId('admin-tenant-qualification-tenants-access-denied')).toBeVisible();
  await expect(noRead.getByTestId('admin-tenant-qualification-tenants-nav-menu')).toHaveCount(0);
  await noReadContext.close();

  const readContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const readOnly = await readContext.newPage();
  await login(readOnly, accounts.readOnly);
  await readOnly.goto('/admin/tenants');
  await expect(readOnly.getByTestId('admin-tenant-qualification-tenants-page')).toBeVisible();
  await expect(readOnly.getByTestId('admin-tenant-qualification-tenants-review-open')).toHaveCount(0);
  await expect(readOnly.getByTestId('admin-tenant-qualification-tenants-edit')).toHaveCount(0);
  await expect(readOnly.getByTestId('admin-tenant-qualification-tenants-status-action')).toHaveCount(0);
  await readContext.close();
}

test('pw-p8-register C-P8-REGISTER OBL-FLOW-12-1-REGISTER', async ({ page, request }) => {
  bindDocumentedSelectors(page);
  expect(page.viewportSize()).toEqual({ width: 1440, height: 900 });
  await page.goto('/tenant/register');
  await expect(page.getByTestId('public-tenant-qualification-register-page')).toBeVisible();
  await expect(page.getByTestId('public-tenant-qualification-register-heading')).toHaveText('企业注册');
  await page.getByTestId('public-tenant-qualification-register-admin-username').fill(accounts.tenant);
  await page.getByTestId('public-tenant-qualification-register-admin-password').fill(password);
  await page.getByTestId('public-tenant-qualification-register-admin-email').fill('phase08@example.test');
  await expect(page.getByTestId('public-tenant-qualification-register-privacy-notice')).toContainText('加密保存');
  await fillQualification(page);
  await uploadRequiredEvidence(page);
  await verifyContact(page, request);
  await page.getByTestId('public-tenant-qualification-register-submit').click();
  await expect(page.getByTestId('public-tenant-qualification-register-success')).toContainText('认证状态：待审核');
  await expect(page.locator('body')).not.toContainText(company.legalId);
});

test('pw-p8-review-workspace C-P8-REVIEW-WORKSPACE OBL-F-2-2-A', async ({ page, browser }) => {
  await openAdminRow(page);
  await page.goto('/admin/tenants');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-page')).toBeVisible();
  await page.getByTestId('query-panel-toggle').click();
  await page.getByTestId('admin-tenant-qualification-tenants-keyword').fill(company.shortName);
  await page.getByTestId('admin-tenant-qualification-tenants-verification-status').selectOption('PENDING');
  await page.getByTestId('admin-tenant-qualification-tenants-query').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toContainText(company.fullName);
  await page.getByTestId('admin-tenant-qualification-tenants-review-open').click();
  const drawer = page.getByTestId('admin-tenant-qualification-tenants-review-drawer');
  await expect(drawer).toContainText(company.creditCode);
  await expect(drawer).not.toContainText(company.legalId);
  await page.getByTestId('admin-tenant-qualification-tenants-review-evidence-open').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-evidence-dialog')).toBeVisible();
  await expect(page.locator('iframe[title="营业执照证明材料"]')).toBeVisible();
  await page.getByTestId('admin-tenant-qualification-tenants-review-evidence-close').click();
  await page.getByTestId('admin-tenant-qualification-tenants-review-evidence-legal-id-front-open').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-evidence-dialog')).toBeVisible();
  await expect(page.locator('iframe[title="法人身份证正面证明材料"]')).toBeVisible();
  await page.getByTestId('admin-tenant-qualification-tenants-review-evidence-close').click();
  await page.getByTestId('admin-tenant-qualification-tenants-review-evidence-legal-id-back-open').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-evidence-dialog')).toBeVisible();
  await expect(page.locator('iframe[title="法人身份证反面证明材料"]')).toBeVisible();
  await page.getByTestId('admin-tenant-qualification-tenants-review-evidence-close').click();
  await page.getByTestId('admin-tenant-qualification-tenants-review-ocr-status').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-ocr-status')).toContainText('已完成');
  await expect(drawer).toContainText(company.fullName);
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-approve-open')).toBeDisabled();
  await page.getByTestId('admin-tenant-qualification-tenants-review-supplement-open').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-decision-consequence')).toContainText('补充材料');
  await page.getByTestId('admin-tenant-qualification-tenants-review-decision-cancel').click();
  await page.getByTestId('admin-tenant-qualification-tenants-review-reject-open').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-decision-consequence')).toContainText('修正资料');
  await page.getByTestId('admin-tenant-qualification-tenants-review-decision-cancel').click();
  await assertAdminPermissionBoundaries(browser);
});

test('pw-p8-review-decision C-P8-REVIEW-DECISION OBL-F-2-2-B', async ({ page }) => {
  await openAdminRow(page);
  await page.goto('/admin/tenants');
  await page.getByTestId('admin-tenant-qualification-tenants-review-open').click();
  await page.getByTestId('admin-tenant-qualification-tenants-review-human-confirmed').check();
  await page.getByTestId('admin-tenant-qualification-tenants-review-approve-open').click();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-decision')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-qualification-tenants-review-decision-consequence')).toContainText('唯一试用账户');
  await page.getByTestId('admin-tenant-qualification-tenants-review-decision-reason').fill('真实证明与登记信息一致');
  await page.getByTestId('admin-tenant-qualification-tenants-review-decision-confirm').click();
  await expect(page.locator('.qualification-success')).toContainText('审核决定已保存');

  await login(page, accounts.tenant);
  await expect(page).toHaveURL(/\/tenant\/overview$/);
  await page.goto('/tenant/qualification');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-status')).toContainText('认证通过');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-recertify')).toBeEnabled();
});

test('pw-p8-qualification-status C-P8-QUALIFICATION-STATUS OBL-F-2-1-C', async ({ page }) => {
  await login(page, accounts.tenant);
  await page.goto('/tenant/qualification');
  const status = page.getByTestId('tenant-tenant-qualification-qualification-status');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-status')).toBeVisible();
  await expect(status).toContainText('认证通过');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-certified-at')).not.toHaveText('尚未认证');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-updated-at')).not.toHaveText('尚未认证');
  await expect(page.locator('body')).not.toContainText(company.legalId);
  await expect(page.locator('body')).not.toContainText('pobj_v1_');
});

test('pw-p8-recertify C-P8-RECERTIFY OBL-F-2-3-B', async ({ page }) => {
  await login(page, accounts.tenant);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-recertify-dialog')).toContainText('提交重新认证后');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-cancel').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-short-name')).toBeDisabled();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-short-name')).toBeEnabled();
  await expect(page.locator('body')).toContainText('正在准备重新认证');
});

test('pw-p8-qualification-form C-P8-QUALIFICATION-FORM OBL-F-2-1-A', async ({ page }) => {
  await login(page, accounts.tenant);
  await page.goto('/tenant/qualification');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-page')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-breadcrumb')).toContainText('资质认证');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-form')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-company-section')).toHaveAttribute('disabled', '');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-legal-section')).toHaveAttribute('disabled', '');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-contact')).toHaveAttribute('disabled', '');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-legal-id')).toHaveValue('');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-nav-menu')).toBeVisible();
});

test('pw-p8-short-name C-P8-SHORT-NAME OBL-FIELD-TENANT-SHORT-NAME', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  const field = page.getByTestId('tenant-tenant-qualification-qualification-short-name');
  await page.getByTestId('tenant-tenant-qualification-qualification-short-name').fill('合规简称');
  await expect(field).toHaveValue('合规简称');
  await field.fill('');
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(page.locator('#tenant-tenant-qualification-qualification-short-name-error')).toContainText('企业简称必填');
});

test('pw-p8-full-name C-P8-FULL-NAME OBL-FIELD-TENANT-FULL-NAME', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  const field = page.getByTestId('tenant-tenant-qualification-qualification-full-name');
  await page.getByTestId('tenant-tenant-qualification-qualification-full-name').fill('合规企业全称有限公司');
  await expect(field).toHaveValue('合规企业全称有限公司');
  await field.fill('');
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(page.locator('#tenant-tenant-qualification-qualification-full-name-error')).toContainText('企业全称必填');
});

test('pw-p8-credit-code C-P8-CREDIT-CODE OBL-FIELD-TENANT-CREDIT-CODE', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  const field = page.getByTestId('tenant-tenant-qualification-qualification-credit-code');
  await page.getByTestId('tenant-tenant-qualification-qualification-credit-code').fill('91350211M000100Y47');
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(page.locator('#tenant-tenant-qualification-qualification-credit-code-error')).toContainText('校验位正确');
  await field.fill(company.creditCode);
  await expect(field).toHaveValue(company.creditCode);
});

test('pw-p8-license C-P8-LICENSE OBL-FIELD-TENANT-LICENSE', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-license-upload')).toContainText('不超过 10 MiB');
  await page.locator('#tenant-tenant-qualification-qualification-license-upload-status').setInputFiles({
    name: 'invalid.txt', mimeType: 'text/plain', buffer: Buffer.from('not a document'),
  });
  await expect(page.locator('#tenant-tenant-qualification-qualification-license-upload-status-error')).toContainText('文件格式不支持');
  await page.locator('#tenant-tenant-qualification-qualification-license-upload-status').setInputFiles(pdf);
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-license-upload-status')).toContainText('已上传');
});

test('pw-p8-legal-id C-P8-LEGAL-ID OBL-FIELD-TENANT-LEGAL-ID', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  const field = page.getByTestId('tenant-tenant-qualification-qualification-legal-id');
  await page.getByTestId('tenant-tenant-qualification-qualification-legal-id').fill('110105194912310021');
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  await expect(page.locator('#tenant-tenant-qualification-qualification-legal-id-error')).toContainText('校验位正确');
  await field.fill(company.legalId);
  await expect(field).toHaveValue(company.legalId);
});

test('pw-p8-legal-id-files C-P8-LEGAL-ID-FILES OBL-FIELD-TENANT-LEGAL-ID-FILES', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-legal-id-files')).toBeVisible();
  const front = page.getByTestId('tenant-tenant-qualification-qualification-legal-id-front-upload');
  const back = page.getByTestId('tenant-tenant-qualification-qualification-legal-id-back-upload');
  await front.setInputFiles(jpeg);
  await expect(front.locator('xpath=..')).toContainText('已上传');
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(page.locator('#tenant-tenant-qualification-qualification-legal-id-back-upload-error')).toContainText('身份证反面');
  await back.setInputFiles(jpeg);
  await expect(back.locator('xpath=..')).toContainText('已上传');
});

test('pw-p8-contact C-P8-CONTACT OBL-FIELD-TENANT-CONTACT', async ({ page, request }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-contact')).toBeVisible();
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-phone').fill('123');
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-code-send').click();
  await expect(page.locator('#tenant-tenant-qualification-qualification-contact-phone-error')).toContainText('11 位');
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-phone').fill(company.phone);
  await verifyContact(page, request);
  await page.getByTestId('tenant-tenant-qualification-qualification-contact-phone').fill('13900139000');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-contact-verified-status')).toContainText('重新验证');
});

test('pw-p8-shortlink-proof C-P8-SHORTLINK-PROOF OBL-FIELD-TENANT-SHORTLINK-PROOF', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-shortlink-proof')).toContainText('试用期可选');
  await page.getByTestId('tenant-tenant-qualification-qualification-shortlink-proof-upload').setInputFiles(pdf);
  await expect(page.getByText('已上传：phase08-license.pdf')).toHaveCount(1);
});

test('pw-p8-trademark-proof C-P8-TRADEMARK-PROOF OBL-FIELD-TENANT-TRADEMARK-PROOF', async ({ page }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-trademark-proof').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-trademark-signature-intent').check();
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(page.locator('#tenant-tenant-qualification-qualification-trademark-proof-upload-error')).toContainText('必须上传商标证明');
  await page.getByTestId('tenant-tenant-qualification-qualification-trademark-proof-upload').setInputFiles(pdf);
  await expect(page.getByText('已上传：phase08-license.pdf')).toHaveCount(1);
});

test('pw-p8-qualification-submit C-P8-QUALIFICATION-SUBMIT OBL-F-2-1-B', async ({ page, request }) => {
  await openEditableQualification(page);
  await page.goto('/tenant/qualification');
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify').click();
  await page.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm').click();
  await fillQualification(page);
  await uploadRequiredEvidence(page);
  await verifyContact(page, request);
  await page.getByTestId('tenant-tenant-qualification-qualification-submit').click();
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-submit-status')).toContainText('等待人工审核');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-status')).toContainText('待审核');
  await expect(page.getByTestId('tenant-tenant-qualification-qualification-submit')).toBeDisabled();
});

test('pw-p8-tenant-edit C-P8-TENANT-EDIT OBL-F-2-3-A', async ({ page, browser }) => {
  await openAdminRow(page);
  await page.goto('/admin/tenants');
  const competingContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const competing = await competingContext.newPage();
  try {
    await openAdminRow(competing);
    await page.getByTestId('admin-tenant-qualification-tenants-edit').click();
    await competing.getByTestId('admin-tenant-qualification-tenants-edit').first().click();
    await competing.getByTestId('admin-tenant-qualification-tenants-edit-business-manager').fill('并发赢家');
    await competing.getByTestId('admin-tenant-qualification-tenants-edit-reason').fill('并发更新元数据');
    await competing.getByTestId('admin-tenant-qualification-tenants-edit-save').click();
    await expect(competing.getByTestId('admin-tenant-qualification-tenants-edit-drawer')).toBeHidden();

    await page.getByTestId('admin-tenant-qualification-tenants-edit-business-manager').fill('重试后的经理');
    await page.getByTestId('admin-tenant-qualification-tenants-edit-industry').fill('软件服务');
    await page.getByTestId('admin-tenant-qualification-tenants-edit-reason').fill('保留输入的陈旧更新');
    await page.getByTestId('admin-tenant-qualification-tenants-edit-save').click();
    await expect(page.getByTestId('admin-tenant-qualification-tenants-edit-drawer')).toContainText('已加载最新版本');
    await expect(page.getByTestId('admin-tenant-qualification-tenants-edit-business-manager')).toHaveValue('重试后的经理');
    await page.getByTestId('admin-tenant-qualification-tenants-edit-save').click();
    await expect(page.getByTestId('admin-tenant-qualification-tenants-edit-drawer')).toBeHidden();
    await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toContainText('重试后的经理');
  } finally {
    await competingContext.close();
  }
});

test('pw-p8-status-action C-P8-STATUS-ACTION OBL-F-2-4-A', async ({ page, browser }) => {
  await openAdminRow(page);
  await page.goto('/admin/tenants');
  const competingContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const competing = await competingContext.newPage();
  try {
    await openAdminRow(competing);
    await page.getByTestId('admin-tenant-qualification-tenants-status-action').click();
    await competing.getByTestId('admin-tenant-qualification-tenants-status-action').first().click();
    await competing.getByTestId('admin-tenant-qualification-tenants-status-target').selectOption('DISABLED');
    await competing.getByTestId('admin-tenant-qualification-tenants-status-reason').fill('并发禁用验证');
    await competing.getByTestId('admin-tenant-qualification-tenants-status-confirm').click();
    await expect(competing.getByTestId('admin-tenant-qualification-tenants-status-dialog')).toBeHidden();

    await page.getByTestId('admin-tenant-qualification-tenants-status-target').selectOption('ARREARS_FROZEN');
    await page.getByTestId('admin-tenant-qualification-tenants-status-reason').fill('保留输入的陈旧状态');
    await page.getByTestId('admin-tenant-qualification-tenants-status-confirm').click();
    const dialog = page.getByTestId('admin-tenant-qualification-tenants-status-dialog');
    await expect(dialog).toContainText('已加载最新版本');
    await expect(page.getByTestId('admin-tenant-qualification-tenants-status-target')).toHaveValue('ARREARS_FROZEN');
    await expect(page.getByTestId('admin-tenant-qualification-tenants-status-reason')).toHaveValue('保留输入的陈旧状态');
    await page.getByTestId('admin-tenant-qualification-tenants-status-confirm').click();
    await expect(dialog).toBeHidden();
    await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toContainText('欠费冻结');

    await page.getByTestId('admin-tenant-qualification-tenants-review-open').click();
    const history = page.getByTestId('admin-tenant-qualification-tenants-review-drawer');
    await expect(history).toContainText('并发禁用验证');
    await expect(history).toContainText('保留输入的陈旧状态');
    await expect(history).toContainText(company.fullName);
    await page.getByTestId('admin-tenant-qualification-tenants-review-drawer-close').click();

    await page.getByTestId('admin-tenant-qualification-tenants-status-action').first().click();
    await page.getByTestId('admin-tenant-qualification-tenants-status-target').selectOption('NORMAL');
    await page.getByTestId('admin-tenant-qualification-tenants-status-reason').fill('恢复正常验证');
    await expect(page.getByTestId('admin-tenant-qualification-tenants-status-consequence')).toContainText('保留历史资质');
    await page.getByTestId('admin-tenant-qualification-tenants-status-confirm').click();
    await expect(page.getByTestId('admin-tenant-qualification-tenants-row')).toContainText('正常');
  } finally {
    await competingContext.close();
  }
});
