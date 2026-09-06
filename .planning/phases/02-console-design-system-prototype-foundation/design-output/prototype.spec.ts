import { createServer, type Server } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { test, expect, type Page } from "../../../../web/node_modules/@playwright/test";

const prototypePath = resolve(__dirname, "prototype.html");
const tokensPath = resolve(__dirname, "tokens.css");
const evidencePath = (name: string) => resolve(__dirname, "../EVIDENCE", name);
const baseURL = "http://127.0.0.1:41732";
let server: Server;

const expectedRoles = new Map<string, string>();
const assignExpectedRoles = (roles: string, routes: string[]) => routes.forEach((route) => expectedRoles.set(route, roles));
assignExpectedRoles("design-reviewer", ["/shared/console/shell", "/shared/component/states"]);
assignExpectedRoles("chrome-acceptance", ["/shared/responsive/baseline"]);
assignExpectedRoles("authorized-reviewer", ["/shared/console/design/shell/notification/target", "/shared/console/design/form/inline/error"]);
assignExpectedRoles("authorized-operator", ["/shared/console/design/list/empty"]);
assignExpectedRoles("unauthenticated", ["/admin/auth/login", "/tenant/auth/login", "/tenant/register"]);
assignExpectedRoles("platform-administrator,operator,finance", ["/admin/dashboard/realtime", "/admin/dashboard/kpi", "/admin/dashboard/alerts", "/admin/submission/details", "/admin/bulk/details", "/admin/uplink/details", "/admin/receipt/details", "/admin/error/details", "/admin/export/center"]);
assignExpectedRoles("platform-administrator", ["/admin/dashboard/configuration", "/admin/users", "/admin/roles", "/admin/system/accounts", "/admin/system/logs"]);
assignExpectedRoles("operator,finance", ["/admin/tenants", "/admin/tenant/trial/contracts", "/admin/tenant/terminations", "/admin/tenant/access", "/admin/statistics", "/admin/custom/reports"]);
assignExpectedRoles("finance", ["/admin/tenant/recharge/review", "/admin/reconciliation", "/admin/settlements", "/admin/invoices", "/admin/financial/analytics", "/admin/fee/warning"]);
assignExpectedRoles("operator", ["/admin/channel/configuration", "/admin/channel/monitor", "/admin/routing/policy", "/admin/number/portability", "/admin/send/monitor", "/admin/api/status/monitor", "/admin/task/monitor", "/admin/signature/review", "/admin/template/review", "/admin/exemption/policy", "/admin/black/white/lists", "/admin/risk/provider", "/admin/frequency/rules", "/admin/content/safety", "/admin/number/attribution", "/admin/complaints", "/admin/uplinks", "/admin/unsubscribes", "/admin/alert/rules", "/admin/alert/history", "/admin/notification/targets", "/admin/shortlinks"]);
assignExpectedRoles("platform-administrator,operator", ["/admin/status/codes", "/admin/prefixes", "/admin/send/jobs"]);
assignExpectedRoles("organization-administrator", ["/tenant/account/information", "/tenant/qualification", "/tenant/administrators", "/tenant/balance", "/tenant/recharge", "/tenant/statements", "/tenant/invoices", "/tenant/notification/targets"]);
assignExpectedRoles("organization-administrator,business-user,developer", ["/tenant/overview"]);
assignExpectedRoles("organization-administrator,business-user", ["/tenant/send", "/tenant/bulk/send", "/tenant/scheduled/tasks", "/tenant/send/records", "/tenant/templates", "/tenant/signatures", "/tenant/blacklist", "/tenant/uplinks", "/tenant/unsubscribes", "/tenant/shortlinks"]);
assignExpectedRoles("organization-administrator,developer", ["/tenant/webhooks", "/tenant/api/keys", "/tenant/cmpp/access"]);

test.use({ baseURL, viewport: { width: 1440, height: 900 } });

test.beforeAll(async () => {
  server = createServer(async (request, response) => {
    try {
      const isTokens = request.url === "/tokens.css";
      const body = await readFile(isTokens ? tokensPath : prototypePath);
      response.writeHead(200, {
        "content-type": isTokens ? "text/css; charset=utf-8" : "text/html; charset=utf-8",
        "cache-control": "no-store",
        "x-prototype-source": "checked-in-html",
      });
      response.end(body);
    } catch (error) {
      response.writeHead(500, { "content-type": "text/plain; charset=utf-8" });
      response.end(String(error));
    }
  });
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(41732, "127.0.0.1", resolve);
  });
});

test.afterAll(async () => {
  if (!server) return;
  await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

async function assertRealRoute(page: Page, route: string) {
  await test.step("evidence:actual-route-rendered", async () => {
    expect(new URL(page.url()).pathname).toBe(route);
    await expect(page.locator('[data-render-source="prototype-html"]')).toHaveAttribute("data-route", route);
  });
  await test.step("evidence:checked-in-html-source", async () => {
    await expect(page.locator('[data-render-source="prototype-html"]')).toHaveCount(1);
  });
  await expect(page.locator('[data-render-source="prototype-html"]')).toHaveAttribute("data-allowed-roles", expectedRoles.get(route)!);
  await test.step("evidence:page-contract-visible", async () => {
    await expect(page.getByTestId("shared-console-shell-page-title").or(page.locator("[data-testid='shared-auth-login-form-region'] h2"))).toBeVisible();
    await expect(page).toHaveTitle(/YCS Open SMS/);
  });
}

test("pw-obl-design-system-001 C-OBL-DESIGN-SYSTEM-001 OBL-DESIGN-SYSTEM-001", async ({ page }, testInfo) => {
  await page.goto("/shared/console/shell");
  await assertRealRoute(page, "/shared/console/shell");
  await expect(page.getByTestId("shared-console-shell-prototype-shell-001")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-prototype-shell-001")).toHaveAttribute("data-route", "/shared/console/shell");
  await expect(page.getByTestId("shared-console-shell-prototype-shell-001")).toHaveAttribute("data-obligation", "OBL-DESIGN-SYSTEM-001");
  await expect(page.getByTestId("shared-console-shell-roles-portal-registry")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-roles-admin-hierarchy")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-roles-tenant-hierarchy")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-roles-admin-visibility")).toContainText("财务");
  await expect(page.getByTestId("shared-console-shell-roles-tenant-visibility")).toContainText("开发人员");
  await expect(page.getByTestId("shared-console-shell-header-product-wordmark")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-header-environment-badge")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-header-global-search")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-navigation-primary-region")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-page-breadcrumb")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-page-purpose")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-toolbar-region")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-toolbar-status-filter")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-toolbar-refresh-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-toolbar-column-settings-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-toolbar-export-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-metrics-grid")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-metrics-today-card")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-metrics-success-card")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-metrics-pending-card")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-metrics-health-card")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-table-card")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-data-freshness")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-table")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-column-id")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-column-name")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-column-owner")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-column-status")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-column-updated")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-column-actions")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-row-003")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-row-more-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-pagination")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-previous-page-action")).toBeDisabled();
  await expect(page.getByTestId("shared-console-shell-records-next-page-action")).toBeEnabled();
  const testIds = await page.locator("[data-testid]").evaluateAll((nodes) => nodes.map((node) => node.getAttribute("data-testid")));
  expect(new Set(testIds).size).toBe(testIds.length);
  const screenshot = await page.screenshot({ path: evidencePath("OBL-DESIGN-SYSTEM-001.png"), fullPage: true });
  await testInfo.attach("OBL-DESIGN-SYSTEM-001.png", { body: screenshot, contentType: "image/png" });
  await page.getByTestId("shared-console-shell-header-notification-trigger").click();
  await expect(page.getByTestId("shared-console-shell-notification-drawer")).toBeVisible();
  await page.getByTestId("shared-console-shell-notification-drawer-close-action").click();
  await page.getByTestId("shared-console-shell-header-profile-trigger").click();
  await expect(page.getByTestId("shared-console-shell-profile-popover")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-profile-personal-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-profile-security-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-profile-logout-action")).toBeVisible();
  await page.getByTestId("shared-console-shell-header-profile-trigger").click();
  await page.getByTestId("shared-console-shell-records-row-detail-action").click();
  await expect(page.getByTestId("shared-console-shell-record-dialog-overlay")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-record-dialog")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-record-dialog-confirm-action")).toBeVisible();
  await page.getByTestId("shared-console-shell-record-dialog-close-action").click();
  await expect(page.getByTestId("shared-console-shell-records-row-detail-action")).toBeFocused();
  await page.getByTestId("shared-console-shell-records-row-review-action").click();
  await page.getByTestId("shared-console-shell-record-dialog-close-action").click();
  await expect(page.getByTestId("shared-console-shell-records-row-review-action")).toBeFocused();
});

test("pw-obl-design-system-002 C-OBL-DESIGN-SYSTEM-002 OBL-DESIGN-SYSTEM-002", async ({ page }, testInfo) => {
  await page.goto("/shared/console/shell");
  await assertRealRoute(page, "/shared/console/shell");
  const tokenResponse = await page.request.get("/tokens.css");
  expect(tokenResponse.ok()).toBeTruthy();
  expect(tokenResponse.headers()["content-type"]).toContain("text/css");
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue("--color-brand").trim())).toBe("#0c85e8");
  await expect(page.locator(".header")).toHaveCSS("background-color", "rgb(18, 50, 80)");
  await expect(page.locator(".status.warning").first()).toHaveCSS("background-color", "rgb(255, 248, 230)");
  await expect(page.getByTestId("shared-console-shell-prototype-shell-002")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-prototype-shell-002")).toHaveAttribute("data-route", "/shared/console/shell");
  await expect(page.getByTestId("shared-console-shell-prototype-shell-002")).toHaveAttribute("data-obligation", "OBL-DESIGN-SYSTEM-002");
  const screenshot = await page.screenshot({ path: evidencePath("OBL-DESIGN-SYSTEM-002.png"), fullPage: true });
  await testInfo.attach("OBL-DESIGN-SYSTEM-002.png", { body: screenshot, contentType: "image/png" });
  await expect(page.getByTestId("shared-console-shell-design-token-board")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-design-pencil-source-map")).toContainText("83 Obligation Route Map");
  await expect(page.getByTestId("shared-console-shell-design-ycsan-reference-board")).toContainText("f4f8aae9");
  await expect(page.getByTestId("shared-console-shell-design-ycsan-reference-board")).toContainText("6931f74f");
  await expect(page.getByTestId("shared-console-shell-design-token-primary-action")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-design-token-secondary-action")).toBeVisible();
  await page.getByTestId("shared-console-shell-page-primary-action").click();
  await expect(page.getByTestId("shared-console-shell-feedback-status-toast")).toHaveText("操作已完成");
  await page.goto("/shared/console/design/form/inline/error");
  await page.getByTestId("shared-console-design-form-review-submit-action").click();
  await expect(page.getByTestId("shared-console-design-form-review-reason-error")).toHaveCSS("background-color", "rgb(255, 240, 242)");
});

test("pw-obl-design-system-003 C-OBL-DESIGN-SYSTEM-003 OBL-DESIGN-SYSTEM-003", async ({ page }) => {
  await page.goto("/shared/component/states");
  await assertRealRoute(page, "/shared/component/states");
  await expect(page.getByTestId("shared-component-states-prototype-shell-003")).toBeVisible();
  await expect(page.getByTestId("shared-component-states-prototype-shell-003")).toHaveAttribute("data-route", "/shared/component/states");
  await expect(page.getByTestId("shared-component-states-prototype-shell-003")).toHaveAttribute("data-obligation", "OBL-DESIGN-SYSTEM-003");
  await test.step("evidence:required-state-catalog-visible", async () => {
    await expect(page.getByTestId("shared-component-states-catalog-grid")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-loading-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-empty-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-partial-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-success-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-warning-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-error-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-retry-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-stale-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-permission-denied-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-destructive-confirmation-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-toast-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-modal-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-drawer-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-popover-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-tooltip-state")).toBeVisible();
    await expect(page.getByTestId("shared-component-states-catalog-floating-control-state")).toBeVisible();
  });
});

test("pw-obl-design-system-004 C-OBL-DESIGN-SYSTEM-004 OBL-DESIGN-SYSTEM-004", async ({ page }, testInfo) => {
  await page.goto("/shared/responsive/baseline");
  await assertRealRoute(page, "/shared/responsive/baseline");
  await expect(page.getByTestId("shared-responsive-baseline-prototype-shell-004")).toBeVisible();
  await expect(page.getByTestId("shared-responsive-baseline-prototype-shell-004")).toHaveAttribute("data-route", "/shared/responsive/baseline");
  await expect(page.getByTestId("shared-responsive-baseline-prototype-shell-004")).toHaveAttribute("data-obligation", "OBL-DESIGN-SYSTEM-004");
  expect(page.viewportSize()).toEqual({ width: 1440, height: 900 });
  await expect(page.getByTestId("shared-responsive-baseline-layout-board")).toBeVisible();
  await expect(page.getByTestId("shared-responsive-baseline-chart-volume")).toBeVisible();
  await expect(page.getByTestId("shared-responsive-baseline-chart-table-alternative")).toBeVisible();
  const screenshot = await page.screenshot({ path: evidencePath("OBL-DESIGN-SYSTEM-004.png"), fullPage: true });
  await testInfo.attach("OBL-DESIGN-SYSTEM-004.png", { body: screenshot, contentType: "image/png" });
});

test("pw-obl-design-system-005 C-OBL-DESIGN-SYSTEM-005 OBL-DESIGN-SYSTEM-005", async ({ page }) => {
  await page.goto("/shared/console/design/shell/notification/target");
  await assertRealRoute(page, "/shared/console/design/shell/notification/target");
  await expect(page.getByTestId("shared-console-design-shell-notification-target")).toBeVisible();
  await expect(page.getByTestId("shared-console-design-shell-notification-target")).toHaveAttribute("data-route", "/shared/console/design/shell/notification/target");
  await expect(page.getByTestId("shared-console-design-shell-notification-target")).toHaveAttribute("data-obligation", "OBL-DESIGN-SYSTEM-005");
  await test.step("evidence:accessibility-inventory-visible", async () => {
    await expect(page.getByTestId("shared-console-shell-accessibility-inventory")).toBeVisible();
    await expect(page.getByTestId("shared-console-shell-accessibility-keyboard-focus")).toBeVisible();
    await expect(page.getByTestId("shared-console-shell-accessibility-status-announcement")).toBeVisible();
    await expect(page.getByTestId("shared-console-shell-accessibility-chart-alternative")).toBeVisible();
    await page.getByTestId("shared-console-shell-accessibility-skip-link").focus();
    await expect(page.getByTestId("shared-console-shell-accessibility-skip-link")).toBeFocused();
  });
});

test("pw-obl-ia-admin-login C-OBL-IA-ADMIN-LOGIN OBL-IA-ADMIN-LOGIN", async ({ page }) => {
  await page.goto("/admin/auth/login");
  await assertRealRoute(page, "/admin/auth/login");
  await expect(page.getByTestId("admin-auth-login-prototype-shell-006")).toBeVisible();
  await expect(page.getByTestId("admin-auth-login-prototype-shell-006")).toHaveAttribute("data-route", "/admin/auth/login");
  await expect(page.getByTestId("admin-auth-login-prototype-shell-006")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-LOGIN");
  await expect(page.getByTestId("shared-auth-login-form-region")).toBeVisible();
  await expect(page.getByTestId("shared-auth-login-form-account-field")).toBeVisible();
  await expect(page.getByTestId("shared-auth-login-form-password-field")).toBeVisible();
  await expect(page.getByTestId("shared-auth-login-form-submit-action")).toBeEnabled();
});

test("pw-obl-ia-admin-dash-realtime C-OBL-IA-ADMIN-DASH-REALTIME OBL-IA-ADMIN-DASH-REALTIME", async ({ page }) => {
  await page.goto("/admin/dashboard/realtime");
  await assertRealRoute(page, "/admin/dashboard/realtime");
  await expect(page.getByTestId("admin-dashboard-realtime-prototype-shell-007")).toBeVisible();
  await expect(page.getByTestId("admin-dashboard-realtime-prototype-shell-007")).toHaveAttribute("data-route", "/admin/dashboard/realtime");
  await expect(page.getByTestId("admin-dashboard-realtime-prototype-shell-007")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DASH-REALTIME");
  await page.getByTestId("shared-console-shell-toolbar-keyword-search").fill("华北");
  await expect(page.getByTestId("shared-console-shell-records-row-001")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-records-row-002")).toBeHidden();
});

test("pw-obl-ia-admin-dash-kpi C-OBL-IA-ADMIN-DASH-KPI OBL-IA-ADMIN-DASH-KPI", async ({ page }) => {
  await page.goto("/admin/dashboard/kpi");
  await assertRealRoute(page, "/admin/dashboard/kpi");
  await expect(page.getByTestId("admin-dashboard-kpi-prototype-shell-008")).toBeVisible();
  await expect(page.getByTestId("admin-dashboard-kpi-prototype-shell-008")).toHaveAttribute("data-route", "/admin/dashboard/kpi");
  await expect(page.getByTestId("admin-dashboard-kpi-prototype-shell-008")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DASH-KPI");

});

test("pw-obl-ia-admin-dash-alert C-OBL-IA-ADMIN-DASH-ALERT OBL-IA-ADMIN-DASH-ALERT", async ({ page }) => {
  await page.goto("/admin/dashboard/alerts");
  await assertRealRoute(page, "/admin/dashboard/alerts");
  await expect(page.getByTestId("admin-dashboard-alerts-prototype-shell-009")).toBeVisible();
  await expect(page.getByTestId("admin-dashboard-alerts-prototype-shell-009")).toHaveAttribute("data-route", "/admin/dashboard/alerts");
  await expect(page.getByTestId("admin-dashboard-alerts-prototype-shell-009")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DASH-ALERT");

});

test("pw-obl-ia-admin-dash-config C-OBL-IA-ADMIN-DASH-CONFIG OBL-IA-ADMIN-DASH-CONFIG", async ({ page }) => {
  await page.goto("/admin/dashboard/configuration");
  await assertRealRoute(page, "/admin/dashboard/configuration");
  await expect(page.getByTestId("admin-dashboard-configuration-prototype-shell-010")).toBeVisible();
  await expect(page.getByTestId("admin-dashboard-configuration-prototype-shell-010")).toHaveAttribute("data-route", "/admin/dashboard/configuration");
  await expect(page.getByTestId("admin-dashboard-configuration-prototype-shell-010")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DASH-CONFIG");
  await expect(page.getByTestId("shared-console-shell-page-role-visibility")).toContainText("平台管理员");
  await expect(page.getByTestId("shared-console-shell-header-current-role-badge")).toHaveText("平台管理员");
  await page.getByTestId("shared-console-shell-header-role-switcher").selectOption("operator");
  await expect(page.getByTestId("shared-console-shell-permission-denied-panel")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-navigation-admin-dashboard-configuration-link")).toHaveCount(0);
  await expect(page.getByTestId("shared-console-shell-navigation-admin-channel-configuration-link")).toBeVisible();
  await page.getByTestId("shared-console-shell-header-role-switcher").selectOption("platform-administrator");
  await expect(page.getByTestId("shared-console-shell-permission-denied-panel")).toHaveCount(0);
  await expect(page.getByTestId("shared-console-shell-navigation-admin-dashboard-configuration-link")).toBeVisible();
  await expect(page.getByTestId("shared-console-shell-navigation-admin-tenant-recharge-review-link")).toHaveCount(0);
});

test("pw-obl-ia-admin-users C-OBL-IA-ADMIN-USERS OBL-IA-ADMIN-USERS", async ({ page }) => {
  await page.goto("/admin/users");
  await assertRealRoute(page, "/admin/users");
  await expect(page.getByTestId("admin-users-prototype-shell-011")).toBeVisible();
  await expect(page.getByTestId("admin-users-prototype-shell-011")).toHaveAttribute("data-route", "/admin/users");
  await expect(page.getByTestId("admin-users-prototype-shell-011")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-USERS");

});

test("pw-obl-ia-admin-tenants C-OBL-IA-ADMIN-TENANTS OBL-IA-ADMIN-TENANTS", async ({ page }) => {
  await page.goto("/admin/tenants");
  await assertRealRoute(page, "/admin/tenants");
  await expect(page.getByTestId("admin-tenants-prototype-shell-012")).toBeVisible();
  await expect(page.getByTestId("admin-tenants-prototype-shell-012")).toHaveAttribute("data-route", "/admin/tenants");
  await expect(page.getByTestId("admin-tenants-prototype-shell-012")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TENANTS");

});

test("pw-obl-ia-admin-tenant-trial-contract C-OBL-IA-ADMIN-TENANT-TRIAL-CONTRACT OBL-IA-ADMIN-TENANT-TRIAL-CONTRACT", async ({ page }) => {
  await page.goto("/admin/tenant/trial/contracts");
  await assertRealRoute(page, "/admin/tenant/trial/contracts");
  await expect(page.getByTestId("admin-tenant-trial-contracts-prototype-shell-013")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-trial-contracts-prototype-shell-013")).toHaveAttribute("data-route", "/admin/tenant/trial/contracts");
  await expect(page.getByTestId("admin-tenant-trial-contracts-prototype-shell-013")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TENANT-TRIAL-CONTRACT");

});

test("pw-obl-ia-admin-tenant-termination C-OBL-IA-ADMIN-TENANT-TERMINATION OBL-IA-ADMIN-TENANT-TERMINATION", async ({ page }) => {
  await page.goto("/admin/tenant/terminations");
  await assertRealRoute(page, "/admin/tenant/terminations");
  await expect(page.getByTestId("admin-tenant-terminations-prototype-shell-014")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-terminations-prototype-shell-014")).toHaveAttribute("data-route", "/admin/tenant/terminations");
  await expect(page.getByTestId("admin-tenant-terminations-prototype-shell-014")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TENANT-TERMINATION");

});

test("pw-obl-ia-admin-tenant-access C-OBL-IA-ADMIN-TENANT-ACCESS OBL-IA-ADMIN-TENANT-ACCESS", async ({ page }) => {
  await page.goto("/admin/tenant/access");
  await assertRealRoute(page, "/admin/tenant/access");
  await expect(page.getByTestId("admin-tenant-access-prototype-shell-015")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-access-prototype-shell-015")).toHaveAttribute("data-route", "/admin/tenant/access");
  await expect(page.getByTestId("admin-tenant-access-prototype-shell-015")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TENANT-ACCESS");

});

test("pw-obl-ia-admin-tenant-recharge C-OBL-IA-ADMIN-TENANT-RECHARGE OBL-IA-ADMIN-TENANT-RECHARGE", async ({ page }) => {
  await page.goto("/admin/tenant/recharge/review");
  await assertRealRoute(page, "/admin/tenant/recharge/review");
  await expect(page.getByTestId("admin-tenant-recharge-review-prototype-shell-016")).toBeVisible();
  await expect(page.getByTestId("admin-tenant-recharge-review-prototype-shell-016")).toHaveAttribute("data-route", "/admin/tenant/recharge/review");
  await expect(page.getByTestId("admin-tenant-recharge-review-prototype-shell-016")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TENANT-RECHARGE");
  await expect(page.getByTestId("shared-console-shell-header-current-role-badge")).toHaveText("财务");
  await expect(page.getByTestId("shared-console-shell-page-role-visibility")).toHaveText("允许角色：财务");
});

test("pw-obl-ia-admin-roles C-OBL-IA-ADMIN-ROLES OBL-IA-ADMIN-ROLES", async ({ page }) => {
  await page.goto("/admin/roles");
  await assertRealRoute(page, "/admin/roles");
  await expect(page.getByTestId("admin-roles-prototype-shell-017")).toBeVisible();
  await expect(page.getByTestId("admin-roles-prototype-shell-017")).toHaveAttribute("data-route", "/admin/roles");
  await expect(page.getByTestId("admin-roles-prototype-shell-017")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-ROLES");

});

test("pw-obl-ia-admin-channel-config C-OBL-IA-ADMIN-CHANNEL-CONFIG OBL-IA-ADMIN-CHANNEL-CONFIG", async ({ page }) => {
  await page.goto("/admin/channel/configuration");
  await assertRealRoute(page, "/admin/channel/configuration");
  await expect(page.getByTestId("admin-channel-configuration-prototype-shell-018")).toBeVisible();
  await expect(page.getByTestId("admin-channel-configuration-prototype-shell-018")).toHaveAttribute("data-route", "/admin/channel/configuration");
  await expect(page.getByTestId("admin-channel-configuration-prototype-shell-018")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-CHANNEL-CONFIG");

});

test("pw-obl-ia-admin-channel-monitor C-OBL-IA-ADMIN-CHANNEL-MONITOR OBL-IA-ADMIN-CHANNEL-MONITOR", async ({ page }) => {
  await page.goto("/admin/channel/monitor");
  await assertRealRoute(page, "/admin/channel/monitor");
  await expect(page.getByTestId("admin-channel-monitor-prototype-shell-019")).toBeVisible();
  await expect(page.getByTestId("admin-channel-monitor-prototype-shell-019")).toHaveAttribute("data-route", "/admin/channel/monitor");
  await expect(page.getByTestId("admin-channel-monitor-prototype-shell-019")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-CHANNEL-MONITOR");

});

test("pw-obl-ia-admin-routing C-OBL-IA-ADMIN-ROUTING OBL-IA-ADMIN-ROUTING", async ({ page }) => {
  await page.goto("/admin/routing/policy");
  await assertRealRoute(page, "/admin/routing/policy");
  await expect(page.getByTestId("admin-routing-policy-prototype-shell-020")).toBeVisible();
  await expect(page.getByTestId("admin-routing-policy-prototype-shell-020")).toHaveAttribute("data-route", "/admin/routing/policy");
  await expect(page.getByTestId("admin-routing-policy-prototype-shell-020")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-ROUTING");

});

test("pw-obl-ia-admin-portability C-OBL-IA-ADMIN-PORTABILITY OBL-IA-ADMIN-PORTABILITY", async ({ page }) => {
  await page.goto("/admin/number/portability");
  await assertRealRoute(page, "/admin/number/portability");
  await expect(page.getByTestId("admin-number-portability-prototype-shell-021")).toBeVisible();
  await expect(page.getByTestId("admin-number-portability-prototype-shell-021")).toHaveAttribute("data-route", "/admin/number/portability");
  await expect(page.getByTestId("admin-number-portability-prototype-shell-021")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-PORTABILITY");

});

test("pw-obl-ia-admin-send-monitor C-OBL-IA-ADMIN-SEND-MONITOR OBL-IA-ADMIN-SEND-MONITOR", async ({ page }) => {
  await page.goto("/admin/send/monitor");
  await assertRealRoute(page, "/admin/send/monitor");
  await expect(page.getByTestId("admin-send-monitor-prototype-shell-022")).toBeVisible();
  await expect(page.getByTestId("admin-send-monitor-prototype-shell-022")).toHaveAttribute("data-route", "/admin/send/monitor");
  await expect(page.getByTestId("admin-send-monitor-prototype-shell-022")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-SEND-MONITOR");

});

test("pw-obl-ia-admin-api-status C-OBL-IA-ADMIN-API-STATUS OBL-IA-ADMIN-API-STATUS", async ({ page }) => {
  await page.goto("/admin/api/status/monitor");
  await assertRealRoute(page, "/admin/api/status/monitor");
  await expect(page.getByTestId("admin-api-status-monitor-prototype-shell-023")).toBeVisible();
  await expect(page.getByTestId("admin-api-status-monitor-prototype-shell-023")).toHaveAttribute("data-route", "/admin/api/status/monitor");
  await expect(page.getByTestId("admin-api-status-monitor-prototype-shell-023")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-API-STATUS");

});

test("pw-obl-ia-admin-task-monitor C-OBL-IA-ADMIN-TASK-MONITOR OBL-IA-ADMIN-TASK-MONITOR", async ({ page }) => {
  await page.goto("/admin/task/monitor");
  await assertRealRoute(page, "/admin/task/monitor");
  await expect(page.getByTestId("admin-task-monitor-prototype-shell-024")).toBeVisible();
  await expect(page.getByTestId("admin-task-monitor-prototype-shell-024")).toHaveAttribute("data-route", "/admin/task/monitor");
  await expect(page.getByTestId("admin-task-monitor-prototype-shell-024")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TASK-MONITOR");

});

test("pw-obl-ia-admin-signature-review C-OBL-IA-ADMIN-SIGNATURE-REVIEW OBL-IA-ADMIN-SIGNATURE-REVIEW", async ({ page }) => {
  await page.goto("/admin/signature/review");
  await assertRealRoute(page, "/admin/signature/review");
  await expect(page.getByTestId("admin-signature-review-prototype-shell-025")).toBeVisible();
  await expect(page.getByTestId("admin-signature-review-prototype-shell-025")).toHaveAttribute("data-route", "/admin/signature/review");
  await expect(page.getByTestId("admin-signature-review-prototype-shell-025")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-SIGNATURE-REVIEW");

});

test("pw-obl-ia-admin-template-review C-OBL-IA-ADMIN-TEMPLATE-REVIEW OBL-IA-ADMIN-TEMPLATE-REVIEW", async ({ page }) => {
  await page.goto("/admin/template/review");
  await assertRealRoute(page, "/admin/template/review");
  await expect(page.getByTestId("admin-template-review-prototype-shell-026")).toBeVisible();
  await expect(page.getByTestId("admin-template-review-prototype-shell-026")).toHaveAttribute("data-route", "/admin/template/review");
  await expect(page.getByTestId("admin-template-review-prototype-shell-026")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-TEMPLATE-REVIEW");

});

test("pw-obl-ia-admin-exemptions C-OBL-IA-ADMIN-EXEMPTIONS OBL-IA-ADMIN-EXEMPTIONS", async ({ page }) => {
  await page.goto("/admin/exemption/policy");
  await assertRealRoute(page, "/admin/exemption/policy");
  await expect(page.getByTestId("admin-exemption-policy-prototype-shell-027")).toBeVisible();
  await expect(page.getByTestId("admin-exemption-policy-prototype-shell-027")).toHaveAttribute("data-route", "/admin/exemption/policy");
  await expect(page.getByTestId("admin-exemption-policy-prototype-shell-027")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-EXEMPTIONS");

});

test("pw-obl-ia-admin-blacklist C-OBL-IA-ADMIN-BLACKLIST OBL-IA-ADMIN-BLACKLIST", async ({ page }) => {
  await page.goto("/admin/black/white/lists");
  await assertRealRoute(page, "/admin/black/white/lists");
  await expect(page.getByTestId("admin-black-white-lists-prototype-shell-028")).toBeVisible();
  await expect(page.getByTestId("admin-black-white-lists-prototype-shell-028")).toHaveAttribute("data-route", "/admin/black/white/lists");
  await expect(page.getByTestId("admin-black-white-lists-prototype-shell-028")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-BLACKLIST");

});

test("pw-obl-ia-admin-risk-provider C-OBL-IA-ADMIN-RISK-PROVIDER OBL-IA-ADMIN-RISK-PROVIDER", async ({ page }) => {
  await page.goto("/admin/risk/provider");
  await assertRealRoute(page, "/admin/risk/provider");
  await expect(page.getByTestId("admin-risk-provider-prototype-shell-029")).toBeVisible();
  await expect(page.getByTestId("admin-risk-provider-prototype-shell-029")).toHaveAttribute("data-route", "/admin/risk/provider");
  await expect(page.getByTestId("admin-risk-provider-prototype-shell-029")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-RISK-PROVIDER");

});

test("pw-obl-ia-admin-frequency C-OBL-IA-ADMIN-FREQUENCY OBL-IA-ADMIN-FREQUENCY", async ({ page }) => {
  await page.goto("/admin/frequency/rules");
  await assertRealRoute(page, "/admin/frequency/rules");
  await expect(page.getByTestId("admin-frequency-rules-prototype-shell-030")).toBeVisible();
  await expect(page.getByTestId("admin-frequency-rules-prototype-shell-030")).toHaveAttribute("data-route", "/admin/frequency/rules");
  await expect(page.getByTestId("admin-frequency-rules-prototype-shell-030")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-FREQUENCY");

});

test("pw-obl-ia-admin-content C-OBL-IA-ADMIN-CONTENT OBL-IA-ADMIN-CONTENT", async ({ page }) => {
  await page.goto("/admin/content/safety");
  await assertRealRoute(page, "/admin/content/safety");
  await expect(page.getByTestId("admin-content-safety-prototype-shell-031")).toBeVisible();
  await expect(page.getByTestId("admin-content-safety-prototype-shell-031")).toHaveAttribute("data-route", "/admin/content/safety");
  await expect(page.getByTestId("admin-content-safety-prototype-shell-031")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-CONTENT");

});

test("pw-obl-ia-admin-number C-OBL-IA-ADMIN-NUMBER OBL-IA-ADMIN-NUMBER", async ({ page }) => {
  await page.goto("/admin/number/attribution");
  await assertRealRoute(page, "/admin/number/attribution");
  await expect(page.getByTestId("admin-number-attribution-prototype-shell-032")).toBeVisible();
  await expect(page.getByTestId("admin-number-attribution-prototype-shell-032")).toHaveAttribute("data-route", "/admin/number/attribution");
  await expect(page.getByTestId("admin-number-attribution-prototype-shell-032")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-NUMBER");

});

test("pw-obl-ia-admin-complaints C-OBL-IA-ADMIN-COMPLAINTS OBL-IA-ADMIN-COMPLAINTS", async ({ page }) => {
  await page.goto("/admin/complaints");
  await assertRealRoute(page, "/admin/complaints");
  await expect(page.getByTestId("admin-complaints-prototype-shell-033")).toBeVisible();
  await expect(page.getByTestId("admin-complaints-prototype-shell-033")).toHaveAttribute("data-route", "/admin/complaints");
  await expect(page.getByTestId("admin-complaints-prototype-shell-033")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-COMPLAINTS");

});

test("pw-obl-ia-admin-uplink C-OBL-IA-ADMIN-UPLINK OBL-IA-ADMIN-UPLINK", async ({ page }) => {
  await page.goto("/admin/uplinks");
  await assertRealRoute(page, "/admin/uplinks");
  await expect(page.getByTestId("admin-uplinks-prototype-shell-034")).toBeVisible();
  await expect(page.getByTestId("admin-uplinks-prototype-shell-034")).toHaveAttribute("data-route", "/admin/uplinks");
  await expect(page.getByTestId("admin-uplinks-prototype-shell-034")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-UPLINK");

});

test("pw-obl-ia-admin-unsubscribe C-OBL-IA-ADMIN-UNSUBSCRIBE OBL-IA-ADMIN-UNSUBSCRIBE", async ({ page }) => {
  await page.goto("/admin/unsubscribes");
  await assertRealRoute(page, "/admin/unsubscribes");
  await expect(page.getByTestId("admin-unsubscribes-prototype-shell-035")).toBeVisible();
  await expect(page.getByTestId("admin-unsubscribes-prototype-shell-035")).toHaveAttribute("data-route", "/admin/unsubscribes");
  await expect(page.getByTestId("admin-unsubscribes-prototype-shell-035")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-UNSUBSCRIBE");

});

test("pw-obl-ia-admin-detail-submit C-OBL-IA-ADMIN-DETAIL-SUBMIT OBL-IA-ADMIN-DETAIL-SUBMIT", async ({ page }) => {
  await page.goto("/admin/submission/details");
  await assertRealRoute(page, "/admin/submission/details");
  await expect(page.getByTestId("admin-submission-details-prototype-shell-036")).toBeVisible();
  await expect(page.getByTestId("admin-submission-details-prototype-shell-036")).toHaveAttribute("data-route", "/admin/submission/details");
  await expect(page.getByTestId("admin-submission-details-prototype-shell-036")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DETAIL-SUBMIT");

});

test("pw-obl-ia-admin-detail-bulk C-OBL-IA-ADMIN-DETAIL-BULK OBL-IA-ADMIN-DETAIL-BULK", async ({ page }) => {
  await page.goto("/admin/bulk/details");
  await assertRealRoute(page, "/admin/bulk/details");
  await expect(page.getByTestId("admin-bulk-details-prototype-shell-037")).toBeVisible();
  await expect(page.getByTestId("admin-bulk-details-prototype-shell-037")).toHaveAttribute("data-route", "/admin/bulk/details");
  await expect(page.getByTestId("admin-bulk-details-prototype-shell-037")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DETAIL-BULK");

});

test("pw-obl-ia-admin-detail-uplink C-OBL-IA-ADMIN-DETAIL-UPLINK OBL-IA-ADMIN-DETAIL-UPLINK", async ({ page }) => {
  await page.goto("/admin/uplink/details");
  await assertRealRoute(page, "/admin/uplink/details");
  await expect(page.getByTestId("admin-uplink-details-prototype-shell-038")).toBeVisible();
  await expect(page.getByTestId("admin-uplink-details-prototype-shell-038")).toHaveAttribute("data-route", "/admin/uplink/details");
  await expect(page.getByTestId("admin-uplink-details-prototype-shell-038")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DETAIL-UPLINK");

});

test("pw-obl-ia-admin-detail-receipt C-OBL-IA-ADMIN-DETAIL-RECEIPT OBL-IA-ADMIN-DETAIL-RECEIPT", async ({ page }) => {
  await page.goto("/admin/receipt/details");
  await assertRealRoute(page, "/admin/receipt/details");
  await expect(page.getByTestId("admin-receipt-details-prototype-shell-039")).toBeVisible();
  await expect(page.getByTestId("admin-receipt-details-prototype-shell-039")).toHaveAttribute("data-route", "/admin/receipt/details");
  await expect(page.getByTestId("admin-receipt-details-prototype-shell-039")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DETAIL-RECEIPT");

});

test("pw-obl-ia-admin-detail-error C-OBL-IA-ADMIN-DETAIL-ERROR OBL-IA-ADMIN-DETAIL-ERROR", async ({ page }) => {
  await page.goto("/admin/error/details");
  await assertRealRoute(page, "/admin/error/details");
  await expect(page.getByTestId("admin-error-details-prototype-shell-040")).toBeVisible();
  await expect(page.getByTestId("admin-error-details-prototype-shell-040")).toHaveAttribute("data-route", "/admin/error/details");
  await expect(page.getByTestId("admin-error-details-prototype-shell-040")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-DETAIL-ERROR");

});

test("pw-obl-ia-admin-export C-OBL-IA-ADMIN-EXPORT OBL-IA-ADMIN-EXPORT", async ({ page }) => {
  await page.goto("/admin/export/center");
  await assertRealRoute(page, "/admin/export/center");
  await expect(page.getByTestId("admin-export-center-prototype-shell-041")).toBeVisible();
  await expect(page.getByTestId("admin-export-center-prototype-shell-041")).toHaveAttribute("data-route", "/admin/export/center");
  await expect(page.getByTestId("admin-export-center-prototype-shell-041")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-EXPORT");

});

test("pw-obl-ia-admin-stats C-OBL-IA-ADMIN-STATS OBL-IA-ADMIN-STATS", async ({ page }) => {
  await page.goto("/admin/statistics");
  await assertRealRoute(page, "/admin/statistics");
  await expect(page.getByTestId("admin-statistics-prototype-shell-042")).toBeVisible();
  await expect(page.getByTestId("admin-statistics-prototype-shell-042")).toHaveAttribute("data-route", "/admin/statistics");
  await expect(page.getByTestId("admin-statistics-prototype-shell-042")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-STATS");

});

test("pw-obl-ia-admin-reports C-OBL-IA-ADMIN-REPORTS OBL-IA-ADMIN-REPORTS", async ({ page }) => {
  await page.goto("/admin/custom/reports");
  await assertRealRoute(page, "/admin/custom/reports");
  await expect(page.getByTestId("admin-custom-reports-prototype-shell-043")).toBeVisible();
  await expect(page.getByTestId("admin-custom-reports-prototype-shell-043")).toHaveAttribute("data-route", "/admin/custom/reports");
  await expect(page.getByTestId("admin-custom-reports-prototype-shell-043")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-REPORTS");

});

test("pw-obl-ia-admin-reconciliation C-OBL-IA-ADMIN-RECONCILIATION OBL-IA-ADMIN-RECONCILIATION", async ({ page }) => {
  await page.goto("/admin/reconciliation");
  await assertRealRoute(page, "/admin/reconciliation");
  await expect(page.getByTestId("admin-reconciliation-prototype-shell-044")).toBeVisible();
  await expect(page.getByTestId("admin-reconciliation-prototype-shell-044")).toHaveAttribute("data-route", "/admin/reconciliation");
  await expect(page.getByTestId("admin-reconciliation-prototype-shell-044")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-RECONCILIATION");

});

test("pw-obl-ia-admin-settlement C-OBL-IA-ADMIN-SETTLEMENT OBL-IA-ADMIN-SETTLEMENT", async ({ page }) => {
  await page.goto("/admin/settlements");
  await assertRealRoute(page, "/admin/settlements");
  await expect(page.getByTestId("admin-settlements-prototype-shell-045")).toBeVisible();
  await expect(page.getByTestId("admin-settlements-prototype-shell-045")).toHaveAttribute("data-route", "/admin/settlements");
  await expect(page.getByTestId("admin-settlements-prototype-shell-045")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-SETTLEMENT");

});

test("pw-obl-ia-admin-invoice C-OBL-IA-ADMIN-INVOICE OBL-IA-ADMIN-INVOICE", async ({ page }) => {
  await page.goto("/admin/invoices");
  await assertRealRoute(page, "/admin/invoices");
  await expect(page.getByTestId("admin-invoices-prototype-shell-046")).toBeVisible();
  await expect(page.getByTestId("admin-invoices-prototype-shell-046")).toHaveAttribute("data-route", "/admin/invoices");
  await expect(page.getByTestId("admin-invoices-prototype-shell-046")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-INVOICE");

});

test("pw-obl-ia-admin-profit C-OBL-IA-ADMIN-PROFIT OBL-IA-ADMIN-PROFIT", async ({ page }) => {
  await page.goto("/admin/financial/analytics");
  await assertRealRoute(page, "/admin/financial/analytics");
  await expect(page.getByTestId("admin-financial-analytics-prototype-shell-047")).toBeVisible();
  await expect(page.getByTestId("admin-financial-analytics-prototype-shell-047")).toHaveAttribute("data-route", "/admin/financial/analytics");
  await expect(page.getByTestId("admin-financial-analytics-prototype-shell-047")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-PROFIT");

});

test("pw-obl-ia-admin-fee-warning C-OBL-IA-ADMIN-FEE-WARNING OBL-IA-ADMIN-FEE-WARNING", async ({ page }) => {
  await page.goto("/admin/fee/warning");
  await assertRealRoute(page, "/admin/fee/warning");
  await expect(page.getByTestId("admin-fee-warning-prototype-shell-048")).toBeVisible();
  await expect(page.getByTestId("admin-fee-warning-prototype-shell-048")).toHaveAttribute("data-route", "/admin/fee/warning");
  await expect(page.getByTestId("admin-fee-warning-prototype-shell-048")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-FEE-WARNING");

});

test("pw-obl-ia-admin-alert-rules C-OBL-IA-ADMIN-ALERT-RULES OBL-IA-ADMIN-ALERT-RULES", async ({ page }) => {
  await page.goto("/admin/alert/rules");
  await assertRealRoute(page, "/admin/alert/rules");
  await expect(page.getByTestId("admin-alert-rules-prototype-shell-049")).toBeVisible();
  await expect(page.getByTestId("admin-alert-rules-prototype-shell-049")).toHaveAttribute("data-route", "/admin/alert/rules");
  await expect(page.getByTestId("admin-alert-rules-prototype-shell-049")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-ALERT-RULES");

});

test("pw-obl-ia-admin-alert-history C-OBL-IA-ADMIN-ALERT-HISTORY OBL-IA-ADMIN-ALERT-HISTORY", async ({ page }) => {
  await page.goto("/admin/alert/history");
  await assertRealRoute(page, "/admin/alert/history");
  await expect(page.getByTestId("admin-alert-history-prototype-shell-050")).toBeVisible();
  await expect(page.getByTestId("admin-alert-history-prototype-shell-050")).toHaveAttribute("data-route", "/admin/alert/history");
  await expect(page.getByTestId("admin-alert-history-prototype-shell-050")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-ALERT-HISTORY");

});

test("pw-obl-ia-admin-notification-targets C-OBL-IA-ADMIN-NOTIFICATION-TARGETS OBL-IA-ADMIN-NOTIFICATION-TARGETS", async ({ page }) => {
  await page.goto("/admin/notification/targets");
  await assertRealRoute(page, "/admin/notification/targets");
  await expect(page.getByTestId("admin-notification-targets-prototype-shell-051")).toBeVisible();
  await expect(page.getByTestId("admin-notification-targets-prototype-shell-051")).toHaveAttribute("data-route", "/admin/notification/targets");
  await expect(page.getByTestId("admin-notification-targets-prototype-shell-051")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-NOTIFICATION-TARGETS");

});

test("pw-obl-ia-admin-shortlinks C-OBL-IA-ADMIN-SHORTLINKS OBL-IA-ADMIN-SHORTLINKS", async ({ page }) => {
  await page.goto("/admin/shortlinks");
  await assertRealRoute(page, "/admin/shortlinks");
  await expect(page.getByTestId("admin-shortlinks-prototype-shell-052")).toBeVisible();
  await expect(page.getByTestId("admin-shortlinks-prototype-shell-052")).toHaveAttribute("data-route", "/admin/shortlinks");
  await expect(page.getByTestId("admin-shortlinks-prototype-shell-052")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-SHORTLINKS");

});

test("pw-obl-ia-admin-status-codes C-OBL-IA-ADMIN-STATUS-CODES OBL-IA-ADMIN-STATUS-CODES", async ({ page }) => {
  await page.goto("/admin/status/codes");
  await assertRealRoute(page, "/admin/status/codes");
  await expect(page.getByTestId("admin-status-codes-prototype-shell-053")).toBeVisible();
  await expect(page.getByTestId("admin-status-codes-prototype-shell-053")).toHaveAttribute("data-route", "/admin/status/codes");
  await expect(page.getByTestId("admin-status-codes-prototype-shell-053")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-STATUS-CODES");

});

test("pw-obl-ia-admin-prefixes C-OBL-IA-ADMIN-PREFIXES OBL-IA-ADMIN-PREFIXES", async ({ page }) => {
  await page.goto("/admin/prefixes");
  await assertRealRoute(page, "/admin/prefixes");
  await expect(page.getByTestId("admin-prefixes-prototype-shell-054")).toBeVisible();
  await expect(page.getByTestId("admin-prefixes-prototype-shell-054")).toHaveAttribute("data-route", "/admin/prefixes");
  await expect(page.getByTestId("admin-prefixes-prototype-shell-054")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-PREFIXES");

});

test("pw-obl-ia-admin-jobs C-OBL-IA-ADMIN-JOBS OBL-IA-ADMIN-JOBS", async ({ page }) => {
  await page.goto("/admin/send/jobs");
  await assertRealRoute(page, "/admin/send/jobs");
  await expect(page.getByTestId("admin-send-jobs-prototype-shell-055")).toBeVisible();
  await expect(page.getByTestId("admin-send-jobs-prototype-shell-055")).toHaveAttribute("data-route", "/admin/send/jobs");
  await expect(page.getByTestId("admin-send-jobs-prototype-shell-055")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-JOBS");

});

test("pw-obl-ia-admin-accounts C-OBL-IA-ADMIN-ACCOUNTS OBL-IA-ADMIN-ACCOUNTS", async ({ page }) => {
  await page.goto("/admin/system/accounts");
  await assertRealRoute(page, "/admin/system/accounts");
  await expect(page.getByTestId("admin-system-accounts-prototype-shell-056")).toBeVisible();
  await expect(page.getByTestId("admin-system-accounts-prototype-shell-056")).toHaveAttribute("data-route", "/admin/system/accounts");
  await expect(page.getByTestId("admin-system-accounts-prototype-shell-056")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-ACCOUNTS");

});

test("pw-obl-ia-admin-logs C-OBL-IA-ADMIN-LOGS OBL-IA-ADMIN-LOGS", async ({ page }) => {
  await page.goto("/admin/system/logs");
  await assertRealRoute(page, "/admin/system/logs");
  await expect(page.getByTestId("admin-system-logs-prototype-shell-057")).toBeVisible();
  await expect(page.getByTestId("admin-system-logs-prototype-shell-057")).toHaveAttribute("data-route", "/admin/system/logs");
  await expect(page.getByTestId("admin-system-logs-prototype-shell-057")).toHaveAttribute("data-obligation", "OBL-IA-ADMIN-LOGS");

});

test("pw-obl-ia-tenant-login C-OBL-IA-TENANT-LOGIN OBL-IA-TENANT-LOGIN", async ({ page }) => {
  await page.goto("/tenant/auth/login");
  await assertRealRoute(page, "/tenant/auth/login");
  await expect(page.getByTestId("tenant-auth-login-prototype-shell-058")).toBeVisible();
  await expect(page.getByTestId("tenant-auth-login-prototype-shell-058")).toHaveAttribute("data-route", "/tenant/auth/login");
  await expect(page.getByTestId("tenant-auth-login-prototype-shell-058")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-LOGIN");

});

test("pw-obl-ia-tenant-register C-OBL-IA-TENANT-REGISTER OBL-IA-TENANT-REGISTER", async ({ page }) => {
  await page.goto("/tenant/register");
  await assertRealRoute(page, "/tenant/register");
  await expect(page.getByTestId("tenant-register-prototype-shell-059")).toBeVisible();
  await expect(page.getByTestId("tenant-register-prototype-shell-059")).toHaveAttribute("data-route", "/tenant/register");
  await expect(page.getByTestId("tenant-register-prototype-shell-059")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-REGISTER");

});

test("pw-obl-ia-tenant-account C-OBL-IA-TENANT-ACCOUNT OBL-IA-TENANT-ACCOUNT", async ({ page }) => {
  await page.goto("/tenant/account/information");
  await assertRealRoute(page, "/tenant/account/information");
  await expect(page.getByTestId("tenant-account-information-prototype-shell-060")).toBeVisible();
  await expect(page.getByTestId("tenant-account-information-prototype-shell-060")).toHaveAttribute("data-route", "/tenant/account/information");
  await expect(page.getByTestId("tenant-account-information-prototype-shell-060")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-ACCOUNT");

});

test("pw-obl-ia-tenant-qualification C-OBL-IA-TENANT-QUALIFICATION OBL-IA-TENANT-QUALIFICATION", async ({ page }) => {
  await page.goto("/tenant/qualification");
  await assertRealRoute(page, "/tenant/qualification");
  await expect(page.getByTestId("tenant-qualification-prototype-shell-061")).toBeVisible();
  await expect(page.getByTestId("tenant-qualification-prototype-shell-061")).toHaveAttribute("data-route", "/tenant/qualification");
  await expect(page.getByTestId("tenant-qualification-prototype-shell-061")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-QUALIFICATION");

});

test("pw-obl-ia-tenant-admins C-OBL-IA-TENANT-ADMINS OBL-IA-TENANT-ADMINS", async ({ page }) => {
  await page.goto("/tenant/administrators");
  await assertRealRoute(page, "/tenant/administrators");
  await expect(page.getByTestId("tenant-administrators-prototype-shell-062")).toBeVisible();
  await expect(page.getByTestId("tenant-administrators-prototype-shell-062")).toHaveAttribute("data-route", "/tenant/administrators");
  await expect(page.getByTestId("tenant-administrators-prototype-shell-062")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-ADMINS");

});

test("pw-obl-ia-tenant-overview C-OBL-IA-TENANT-OVERVIEW OBL-IA-TENANT-OVERVIEW", async ({ page }) => {
  await page.goto("/tenant/overview");
  await assertRealRoute(page, "/tenant/overview");
  await expect(page.getByTestId("tenant-overview-prototype-shell-063")).toBeVisible();
  await expect(page.getByTestId("tenant-overview-prototype-shell-063")).toHaveAttribute("data-route", "/tenant/overview");
  await expect(page.getByTestId("tenant-overview-prototype-shell-063")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-OVERVIEW");
  await page.getByTestId("shared-console-shell-header-role-switcher").selectOption("developer");
  await expect(page.getByTestId("shared-console-shell-header-current-role-badge")).toHaveText("开发人员");
  await expect(page.getByTestId("shared-console-shell-navigation-tenant-send-link")).toHaveCount(0);
  await expect(page.getByTestId("shared-console-shell-navigation-tenant-api-keys-link")).toBeVisible();
});

test("pw-obl-ia-tenant-send C-OBL-IA-TENANT-SEND OBL-IA-TENANT-SEND", async ({ page }) => {
  await page.goto("/tenant/send");
  await assertRealRoute(page, "/tenant/send");
  await expect(page.getByTestId("tenant-send-prototype-shell-064")).toBeVisible();
  await expect(page.getByTestId("tenant-send-prototype-shell-064")).toHaveAttribute("data-route", "/tenant/send");
  await expect(page.getByTestId("tenant-send-prototype-shell-064")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-SEND");
  await page.getByTestId("shared-console-shell-toolbar-primary-action").click();
  await expect(page.getByTestId("shared-console-shell-feedback-status-toast")).toHaveText("操作已完成");
});

test("pw-obl-ia-tenant-bulk C-OBL-IA-TENANT-BULK OBL-IA-TENANT-BULK", async ({ page }) => {
  await page.goto("/tenant/bulk/send");
  await assertRealRoute(page, "/tenant/bulk/send");
  await expect(page.getByTestId("tenant-bulk-send-prototype-shell-065")).toBeVisible();
  await expect(page.getByTestId("tenant-bulk-send-prototype-shell-065")).toHaveAttribute("data-route", "/tenant/bulk/send");
  await expect(page.getByTestId("tenant-bulk-send-prototype-shell-065")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-BULK");

});

test("pw-obl-ia-tenant-schedule C-OBL-IA-TENANT-SCHEDULE OBL-IA-TENANT-SCHEDULE", async ({ page }) => {
  await page.goto("/tenant/scheduled/tasks");
  await assertRealRoute(page, "/tenant/scheduled/tasks");
  await expect(page.getByTestId("tenant-scheduled-tasks-prototype-shell-066")).toBeVisible();
  await expect(page.getByTestId("tenant-scheduled-tasks-prototype-shell-066")).toHaveAttribute("data-route", "/tenant/scheduled/tasks");
  await expect(page.getByTestId("tenant-scheduled-tasks-prototype-shell-066")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-SCHEDULE");

});

test("pw-obl-ia-tenant-send-records C-OBL-IA-TENANT-SEND-RECORDS OBL-IA-TENANT-SEND-RECORDS", async ({ page }) => {
  await page.goto("/tenant/send/records");
  await assertRealRoute(page, "/tenant/send/records");
  await expect(page.getByTestId("tenant-send-records-prototype-shell-067")).toBeVisible();
  await expect(page.getByTestId("tenant-send-records-prototype-shell-067")).toHaveAttribute("data-route", "/tenant/send/records");
  await expect(page.getByTestId("tenant-send-records-prototype-shell-067")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-SEND-RECORDS");

});

test("pw-obl-ia-tenant-templates C-OBL-IA-TENANT-TEMPLATES OBL-IA-TENANT-TEMPLATES", async ({ page }) => {
  await page.goto("/tenant/templates");
  await assertRealRoute(page, "/tenant/templates");
  await expect(page.getByTestId("tenant-templates-prototype-shell-068")).toBeVisible();
  await expect(page.getByTestId("tenant-templates-prototype-shell-068")).toHaveAttribute("data-route", "/tenant/templates");
  await expect(page.getByTestId("tenant-templates-prototype-shell-068")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-TEMPLATES");

});

test("pw-obl-ia-tenant-signatures C-OBL-IA-TENANT-SIGNATURES OBL-IA-TENANT-SIGNATURES", async ({ page }) => {
  await page.goto("/tenant/signatures");
  await assertRealRoute(page, "/tenant/signatures");
  await expect(page.getByTestId("tenant-signatures-prototype-shell-069")).toBeVisible();
  await expect(page.getByTestId("tenant-signatures-prototype-shell-069")).toHaveAttribute("data-route", "/tenant/signatures");
  await expect(page.getByTestId("tenant-signatures-prototype-shell-069")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-SIGNATURES");

});

test("pw-obl-ia-tenant-balance C-OBL-IA-TENANT-BALANCE OBL-IA-TENANT-BALANCE", async ({ page }) => {
  await page.goto("/tenant/balance");
  await assertRealRoute(page, "/tenant/balance");
  await expect(page.getByTestId("tenant-balance-prototype-shell-070")).toBeVisible();
  await expect(page.getByTestId("tenant-balance-prototype-shell-070")).toHaveAttribute("data-route", "/tenant/balance");
  await expect(page.getByTestId("tenant-balance-prototype-shell-070")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-BALANCE");

});

test("pw-obl-ia-tenant-recharge C-OBL-IA-TENANT-RECHARGE OBL-IA-TENANT-RECHARGE", async ({ page }) => {
  await page.goto("/tenant/recharge");
  await assertRealRoute(page, "/tenant/recharge");
  await expect(page.getByTestId("tenant-recharge-prototype-shell-071")).toBeVisible();
  await expect(page.getByTestId("tenant-recharge-prototype-shell-071")).toHaveAttribute("data-route", "/tenant/recharge");
  await expect(page.getByTestId("tenant-recharge-prototype-shell-071")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-RECHARGE");

});

test("pw-obl-ia-tenant-statements C-OBL-IA-TENANT-STATEMENTS OBL-IA-TENANT-STATEMENTS", async ({ page }) => {
  await page.goto("/tenant/statements");
  await assertRealRoute(page, "/tenant/statements");
  await expect(page.getByTestId("tenant-statements-prototype-shell-072")).toBeVisible();
  await expect(page.getByTestId("tenant-statements-prototype-shell-072")).toHaveAttribute("data-route", "/tenant/statements");
  await expect(page.getByTestId("tenant-statements-prototype-shell-072")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-STATEMENTS");

});

test("pw-obl-ia-tenant-invoices C-OBL-IA-TENANT-INVOICES OBL-IA-TENANT-INVOICES", async ({ page }) => {
  await page.goto("/tenant/invoices");
  await assertRealRoute(page, "/tenant/invoices");
  await expect(page.getByTestId("tenant-invoices-prototype-shell-073")).toBeVisible();
  await expect(page.getByTestId("tenant-invoices-prototype-shell-073")).toHaveAttribute("data-route", "/tenant/invoices");
  await expect(page.getByTestId("tenant-invoices-prototype-shell-073")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-INVOICES");

});

test("pw-obl-ia-tenant-blacklist C-OBL-IA-TENANT-BLACKLIST OBL-IA-TENANT-BLACKLIST", async ({ page }) => {
  await page.goto("/tenant/blacklist");
  await assertRealRoute(page, "/tenant/blacklist");
  await expect(page.getByTestId("tenant-blacklist-prototype-shell-074")).toBeVisible();
  await expect(page.getByTestId("tenant-blacklist-prototype-shell-074")).toHaveAttribute("data-route", "/tenant/blacklist");
  await expect(page.getByTestId("tenant-blacklist-prototype-shell-074")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-BLACKLIST");

});

test("pw-obl-ia-tenant-webhook C-OBL-IA-TENANT-WEBHOOK OBL-IA-TENANT-WEBHOOK", async ({ page }) => {
  await page.goto("/tenant/webhooks");
  await assertRealRoute(page, "/tenant/webhooks");
  await expect(page.getByTestId("tenant-webhooks-prototype-shell-075")).toBeVisible();
  await expect(page.getByTestId("tenant-webhooks-prototype-shell-075")).toHaveAttribute("data-route", "/tenant/webhooks");
  await expect(page.getByTestId("tenant-webhooks-prototype-shell-075")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-WEBHOOK");

});

test("pw-obl-ia-tenant-api-keys C-OBL-IA-TENANT-API-KEYS OBL-IA-TENANT-API-KEYS", async ({ page }) => {
  await page.goto("/tenant/api/keys");
  await assertRealRoute(page, "/tenant/api/keys");
  await expect(page.getByTestId("tenant-api-keys-prototype-shell-076")).toBeVisible();
  await expect(page.getByTestId("tenant-api-keys-prototype-shell-076")).toHaveAttribute("data-route", "/tenant/api/keys");
  await expect(page.getByTestId("tenant-api-keys-prototype-shell-076")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-API-KEYS");

});

test("pw-obl-ia-tenant-cmpp C-OBL-IA-TENANT-CMPP OBL-IA-TENANT-CMPP", async ({ page }) => {
  await page.goto("/tenant/cmpp/access");
  await assertRealRoute(page, "/tenant/cmpp/access");
  await expect(page.getByTestId("tenant-cmpp-access-prototype-shell-077")).toBeVisible();
  await expect(page.getByTestId("tenant-cmpp-access-prototype-shell-077")).toHaveAttribute("data-route", "/tenant/cmpp/access");
  await expect(page.getByTestId("tenant-cmpp-access-prototype-shell-077")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-CMPP");

});

test("pw-obl-ia-tenant-notification-targets C-OBL-IA-TENANT-NOTIFICATION-TARGETS OBL-IA-TENANT-NOTIFICATION-TARGETS", async ({ page }) => {
  await page.goto("/tenant/notification/targets");
  await assertRealRoute(page, "/tenant/notification/targets");
  await expect(page.getByTestId("tenant-notification-targets-prototype-shell-078")).toBeVisible();
  await expect(page.getByTestId("tenant-notification-targets-prototype-shell-078")).toHaveAttribute("data-route", "/tenant/notification/targets");
  await expect(page.getByTestId("tenant-notification-targets-prototype-shell-078")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-NOTIFICATION-TARGETS");

});

test("pw-obl-ia-tenant-uplinks C-OBL-IA-TENANT-UPLINKS OBL-IA-TENANT-UPLINKS", async ({ page }) => {
  await page.goto("/tenant/uplinks");
  await assertRealRoute(page, "/tenant/uplinks");
  await expect(page.getByTestId("tenant-uplinks-prototype-shell-079")).toBeVisible();
  await expect(page.getByTestId("tenant-uplinks-prototype-shell-079")).toHaveAttribute("data-route", "/tenant/uplinks");
  await expect(page.getByTestId("tenant-uplinks-prototype-shell-079")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-UPLINKS");

});

test("pw-obl-ia-tenant-unsubscribes C-OBL-IA-TENANT-UNSUBSCRIBES OBL-IA-TENANT-UNSUBSCRIBES", async ({ page }) => {
  await page.goto("/tenant/unsubscribes");
  await assertRealRoute(page, "/tenant/unsubscribes");
  await expect(page.getByTestId("tenant-unsubscribes-prototype-shell-080")).toBeVisible();
  await expect(page.getByTestId("tenant-unsubscribes-prototype-shell-080")).toHaveAttribute("data-route", "/tenant/unsubscribes");
  await expect(page.getByTestId("tenant-unsubscribes-prototype-shell-080")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-UNSUBSCRIBES");

});

test("pw-obl-ia-tenant-shortlinks C-OBL-IA-TENANT-SHORTLINKS OBL-IA-TENANT-SHORTLINKS", async ({ page }) => {
  await page.goto("/tenant/shortlinks");
  await assertRealRoute(page, "/tenant/shortlinks");
  await expect(page.getByTestId("tenant-shortlinks-prototype-shell-081")).toBeVisible();
  await expect(page.getByTestId("tenant-shortlinks-prototype-shell-081")).toHaveAttribute("data-route", "/tenant/shortlinks");
  await expect(page.getByTestId("tenant-shortlinks-prototype-shell-081")).toHaveAttribute("data-obligation", "OBL-IA-TENANT-SHORTLINKS");

});

test("pw-obl-edge-empty-list C-OBL-EDGE-EMPTY-LIST OBL-EDGE-EMPTY-LIST", async ({ page }) => {
  await page.goto("/shared/console/design/list/empty");
  await assertRealRoute(page, "/shared/console/design/list/empty");
  await expect(page.getByTestId("shared-console-design-list-empty")).toBeVisible();
  await expect(page.getByTestId("shared-console-design-list-empty")).toHaveAttribute("data-route", "/shared/console/design/list/empty");
  await expect(page.getByTestId("shared-console-design-list-empty")).toHaveAttribute("data-obligation", "OBL-EDGE-EMPTY-LIST");
  await expect(page.getByTestId("shared-console-design-list-empty-panel")).toBeVisible();
  await test.step("evidence:illustration-visible", async () => {
    await expect(page.getByTestId("shared-console-design-list-empty-illustration")).toBeVisible();
  });
  await test.step("evidence:guidance-visible", async () => {
    await expect(page.getByTestId("shared-console-design-list-empty-panel")).toContainText("调整筛选条件");
  });
  await test.step("evidence:bulk-disabled", async () => {
    await expect(page.getByTestId("shared-console-design-list-empty-bulk-action")).toBeDisabled();
  });
  await test.step("evidence:export-disabled", async () => {
    await expect(page.getByTestId("shared-console-design-list-empty-export-action")).toBeDisabled();
  });
  await expect(page.getByTestId("shared-console-design-list-empty-create-action")).toBeEnabled();
});

test("pw-obl-edge-review-validation C-OBL-EDGE-REVIEW-VALIDATION OBL-EDGE-REVIEW-VALIDATION", async ({ page }) => {
  await page.goto("/shared/console/design/form/inline/error");
  await assertRealRoute(page, "/shared/console/design/form/inline/error");
  await expect(page.getByTestId("shared-console-design-form-inline-error")).toBeVisible();
  await expect(page.getByTestId("shared-console-design-form-inline-error")).toHaveAttribute("data-route", "/shared/console/design/form/inline/error");
  await expect(page.getByTestId("shared-console-design-form-inline-error")).toHaveAttribute("data-obligation", "OBL-EDGE-REVIEW-VALIDATION");
  await expect(page.getByTestId("shared-console-design-form-review-validation-panel")).toBeVisible();
  await expect(page.getByTestId("shared-console-design-form-review-form")).toBeVisible();
  await expect(page.getByTestId("shared-console-design-form-review-cancel-action")).toBeEnabled();
  const requests = [];
  page.on("request", (request) => requests.push(request.url()));
  const identity = await page.evaluate(() => performance.timeOrigin);
  await expect(page.getByTestId("shared-console-design-form-review-submit-action")).toBeEnabled();
  await test.step("evidence:failed-submit-triggered", async () => {
    await page.getByTestId("shared-console-design-form-review-submit-action").click();
  });
  await test.step("evidence:exact-reason-visible", async () => {
    await expect(page.getByTestId("shared-console-design-form-review-reason-error")).toHaveText("请输入不少于 5 个字的驳回原因");
  });
  await test.step("evidence:invalid-field-focused", async () => {
    await expect(page.getByTestId("shared-console-design-form-review-reason-input")).toHaveAttribute("aria-invalid", "true");
    await expect(page.getByTestId("shared-console-design-form-review-reason-input")).toBeFocused();
  });
  await test.step("evidence:submit-blocked", async () => {
    await expect(page.getByTestId("shared-console-design-form-review-submit-action")).toBeDisabled();
  });
  await test.step("evidence:zero-navigation", async () => {
    expect(await page.evaluate(() => performance.timeOrigin)).toBe(identity);
    expect(new URL(page.url()).pathname).toBe("/shared/console/design/form/inline/error");
  });
  await test.step("evidence:zero-request", async () => {
    expect(requests).toEqual([]);
  });
});
