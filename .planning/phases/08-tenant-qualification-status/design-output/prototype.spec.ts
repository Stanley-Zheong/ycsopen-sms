import { expect, test } from "@playwright/test";

test("pw-p8-qualification-form C-P8-QUALIFICATION-FORM OBL-F-2-1-A", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-page")).toBeVisible();
});
test("pw-p8-qualification-submit C-P8-QUALIFICATION-SUBMIT OBL-F-2-1-B", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-submit")).toBeVisible();
});
test("pw-p8-qualification-status C-P8-QUALIFICATION-STATUS OBL-F-2-1-C", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-status")).toBeVisible();
});
test("pw-p8-review-workspace C-P8-REVIEW-WORKSPACE OBL-F-2-2-A", async ({ page }) => {
  await page.goto("/admin/tenants");
  await expect(page.getByTestId("admin-tenant-qualification-tenants-page")).toBeVisible();
});
test("pw-p8-review-decision C-P8-REVIEW-DECISION OBL-F-2-2-B", async ({ page }) => {
  await page.goto("/admin/tenants");
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-decision")).toBeVisible();
});
test("pw-p8-tenant-edit C-P8-TENANT-EDIT OBL-F-2-3-A", async ({ page }) => {
  await page.goto("/admin/tenants");
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit")).toBeVisible();
});
test("pw-p8-recertify C-P8-RECERTIFY OBL-F-2-3-B", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-recertify")).toBeVisible();
});
test("pw-p8-status-action C-P8-STATUS-ACTION OBL-F-2-4-A", async ({ page }) => {
  await page.goto("/admin/tenants");
  await expect(page.getByTestId("admin-tenant-qualification-tenants-status-action")).toBeVisible();
});
test("pw-p8-short-name C-P8-SHORT-NAME OBL-FIELD-TENANT-SHORT-NAME", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-short-name")).toBeVisible();
});
test("pw-p8-full-name C-P8-FULL-NAME OBL-FIELD-TENANT-FULL-NAME", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-full-name")).toBeVisible();
});
test("pw-p8-credit-code C-P8-CREDIT-CODE OBL-FIELD-TENANT-CREDIT-CODE", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-credit-code")).toBeVisible();
});
test("pw-p8-license C-P8-LICENSE OBL-FIELD-TENANT-LICENSE", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-license-upload")).toBeVisible();
});
test("pw-p8-legal-id C-P8-LEGAL-ID OBL-FIELD-TENANT-LEGAL-ID", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-legal-id")).toBeVisible();
});
test("pw-p8-legal-id-files C-P8-LEGAL-ID-FILES OBL-FIELD-TENANT-LEGAL-ID-FILES", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-legal-id-files")).toBeVisible();
});
test("pw-p8-contact C-P8-CONTACT OBL-FIELD-TENANT-CONTACT", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact")).toBeVisible();
});
test("pw-p8-shortlink-proof C-P8-SHORTLINK-PROOF OBL-FIELD-TENANT-SHORTLINK-PROOF", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-shortlink-proof")).toBeVisible();
});
test("pw-p8-trademark-proof C-P8-TRADEMARK-PROOF OBL-FIELD-TENANT-TRADEMARK-PROOF", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-trademark-proof")).toBeVisible();
});
test("pw-p8-register C-P8-REGISTER OBL-FLOW-12-1-REGISTER", async ({ page }) => {
  await page.goto("/tenant/register");
  await expect(page.getByTestId("public-tenant-qualification-register-page")).toBeVisible();
});

test("Phase 08 complete prototype element inventory", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-nav-menu")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-breadcrumb")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-heading")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-certified-at")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-updated-at")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-review-feedback")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-form")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-company-section")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-registration-capital")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-business-scope")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-registered-address")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-operating-address")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-license-valid-until")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-license-upload-status")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-legal-section")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-legal-name")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-legal-id-front-upload")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-legal-id-back-upload")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-name")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-id")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-phone")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-code-send")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-code-input")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-code-verify")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-contact-verified-status")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-shortlink-proof-upload")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-trademark-signature-intent")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-trademark-proof-upload")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-error-summary")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-submit-status")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-recertify-dialog")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-recertify-cancel")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-recertify-confirm")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-loading")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-error")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-retry")).toBeVisible();
  await expect(page.getByTestId("tenant-tenant-qualification-qualification-access-denied")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-heading")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-admin-username")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-admin-password")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-admin-email")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-privacy-notice")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-error-summary")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-submit")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-success")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-session-expired")).toBeVisible();
  await expect(page.getByTestId("public-tenant-qualification-register-session-restart")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-nav-menu")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-heading")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-keyword")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-verification-status")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-operating-status")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-query")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-reset")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-table")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-row")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-previous")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-page-status")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-next")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-drawer")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-evidence-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-evidence-legal-id-front-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-evidence-legal-id-back-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-evidence-dialog")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-ocr-status")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-human-confirmed")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-approve-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-supplement-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-reject-open")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-decision-reason")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-decision-consequence")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-review-decision-confirm")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit-drawer")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit-customer-grade")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit-business-manager")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit-industry")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit-reason")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-edit-save")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-status-dialog")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-status-target")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-status-reason")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-status-consequence")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-status-confirm")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-loading")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-empty")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-error")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-retry")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-qualification-tenants-access-denied")).toBeVisible();
});
