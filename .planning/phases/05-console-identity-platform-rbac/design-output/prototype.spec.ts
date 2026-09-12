import { expect, test } from "@playwright/test";

test("pw-p5-account-create C-P5-ACCOUNT-CREATE OBL-F-1-1-A", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-create").click();
});
test("pw-p5-account-disable-region C-P5-ACCOUNT-DISABLE-REGION OBL-F-1-1-B", async ({ page }) => {
  await page.goto("/admin/system/users");
  await expect(page.getByTestId("admin-console-identity-users-row-disable")).toBeVisible();
});
test("pw-p5-permission-tree C-P5-PERMISSION-TREE OBL-F-1-2-A", async ({ page }) => {
  await page.goto("/admin/system/roles");
  await expect(page.getByTestId("admin-console-identity-roles-permission-tree")).toBeVisible();
});
test("pw-p5-permission-live-save C-P5-PERMISSION-LIVE OBL-F-1-2-B", async ({ page }) => {
  await page.goto("/admin/system/roles");
  await page.getByTestId("admin-console-identity-roles-save").click();
});
test("pw-p5-role-migrate C-P5-ROLE-MIGRATE OBL-F-1-2-C", async ({ page }) => {
  await page.goto("/admin/system/roles");
  await expect(page.getByTestId("admin-console-identity-roles-migrate-dialog")).toBeVisible();
});
test("pw-p5-login C-P5-LOGIN OBL-F-1-4-A", async ({ page }) => {
  await page.goto("/admin/auth/login");
  await expect(page.getByTestId("admin-console-identity-auth-intro-title")).toHaveText("企业短信运营工作台");
  await expect(page.getByTestId("admin-console-identity-auth-intro-summary")).toContainText("多租户、多通道短信业务");
  const remember = page.getByTestId("shared-auth-login-remember");
  await expect(remember).toBeVisible();
  expect(await remember.boundingBox()).toMatchObject({ width: 16, height: 16 });
  const rememberLabel = remember.locator("..");
  expect((await rememberLabel.boundingBox())?.height).toBeGreaterThanOrEqual(40);
  await page.getByTestId("admin-console-identity-auth-login-submit").click();
});
test("pw-p5-logout C-P5-LOGOUT OBL-F-1-4-B", async ({ page }) => {
  await page.goto("/admin/account-overview");
  await page.getByTestId("shared-console-identity-profile-logout").click();
});
test("pw-p5-login-history C-P5-LOGIN-HISTORY OBL-F-1-4-C", async ({ page }) => {
  await page.goto("/admin/system/login-history");
  await expect(page.getByTestId("shared-console-identity-profile-login-history")).toBeVisible();
});
test("pw-p5-overview C-P5-OVERVIEW OBL-F-1-5-A", async ({ page }) => {
  await page.goto("/admin/account-overview");
  await expect(page.getByTestId("admin-console-identity-account-overview")).toBeVisible();
});
test("pw-p5-username C-P5-USERNAME OBL-FIELD-ACCOUNT-USERNAME", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-form-username").fill("valid_user");
});
test("pw-p5-password C-P5-PASSWORD OBL-FIELD-ACCOUNT-PASSWORD", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-form-password").fill("StrongPass1");
});
test("pw-p5-phone C-P5-PHONE OBL-FIELD-ACCOUNT-PHONE", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-form-phone").fill("13800138000");
});
test("pw-p5-type C-P5-TYPE OBL-FIELD-ACCOUNT-TYPE", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-form-type").selectOption("ADMIN");
});
test("pw-p5-validity C-P5-VALIDITY OBL-FIELD-ACCOUNT-VALIDITY", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-form-validity").fill("2027-01-01");
});
test("pw-p5-lock C-P5-LOCK OBL-STATE-ACCOUNT-LOCK", async ({ page }) => {
  await page.goto("/admin/system/users");
  await expect(page.getByTestId("admin-console-identity-users-state")).toBeVisible();
});
test("pw-p5-unlock C-P5-UNLOCK OBL-STATE-ACCOUNT-UNLOCK", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-unlock").click();
});
test("pw-p5-disable C-P5-DISABLE OBL-STATE-ACCOUNT-DISABLE", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-disable").click();
});
test("pw-p5-enable C-P5-ENABLE OBL-STATE-ACCOUNT-ENABLE", async ({ page }) => {
  await page.goto("/admin/system/users");
  await page.getByTestId("admin-console-identity-users-enable").click();
});
test("pw-p5-internal-error C-P5-INTERNAL-ERROR OBL-EDGE-INTERNAL-ERROR", async ({ page }) => {
  await page.goto("/admin/system/users");
  await expect(page.getByTestId("shared-console-identity-internal-error-message")).toContainText("系统繁忙，请稍后再试");
});
