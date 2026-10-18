import { expect, test, type Locator, type Page } from '@playwright/test';
import { apiResponse, mockEmptyDashboard, navigateWithinSpa, seedAuthenticatedSession } from './helpers';

test.describe.configure({ timeout: 600_000 });
test.use({ viewport: { width: 1440, height: 900 } });

// Complete literal Admin page inventory from src/router/routes.tsx. Redirect-only
// aliases and the standalone login page do not render AdminLayout forms.
const ADMIN_ROUTES = [
  '/admin/dashboard',
  '/admin/dashboard/configuration',
  '/admin/api/status',
  '/admin/tenants',
  '/admin/channel/configuration',
  '/admin/channel/health',
  '/admin/channel/pools',
  '/admin/routing-policy',
  '/admin/signatures/review',
  '/admin/templates/review',
  '/admin/exemption/policy',
  '/admin/review-history',
  '/admin/riskcontrol',
  '/admin/content-safety',
  '/admin/frequency/rules',
  '/admin/number-attribution',
  '/admin/number-portability',
  '/admin/prefixes',
  '/admin/status-codes',
  '/admin/tenant-trial-contracts',
  '/admin/balance-audit',
  '/admin/tenant-recharge-review',
  '/admin/reconciliation',
  '/admin/settlements',
  '/admin/invoices',
  '/admin/submission/details',
  '/admin/send/details',
  '/admin/receipt/details',
  '/admin/error/details',
  '/admin/push/failures',
  '/admin/bulk/details',
  '/admin/send/jobs',
  '/admin/complaints',
  '/admin/complaint/analytics',
  '/admin/tenant-risk',
  '/admin/uplink',
  '/admin/unsubscribes',
  '/admin/records',
  '/admin/statistics',
  '/admin/statistics/resources',
  '/admin/custom/reports',
  '/admin/export-center',
  '/admin/archive',
  '/admin/shortlinks/review',
  '/admin/tenant/terminations',
  '/admin/finance',
  '/admin/fee/warning',
  '/admin/alerts',
  '/admin/tools',
  '/admin/system/users',
  '/admin/system/roles',
  '/admin/system/login-history',
  '/admin/system/logs',
  '/admin/system/security-events',
  '/admin/system/configuration',
  '/admin/account-overview',
] as const;

// Fail closed when an Admin form route stops rendering controls or a new form route is
// added without updating this acceptance inventory.
const EXPECTED_FORM_ROUTES = [
  '/admin/alerts',
  '/admin/archive',
  '/admin/balance-audit',
  '/admin/bulk/details',
  '/admin/channel/health',
  '/admin/complaints',
  '/admin/content-safety',
  '/admin/custom/reports',
  '/admin/dashboard',
  '/admin/dashboard/configuration',
  '/admin/error/details',
  '/admin/exemption/policy',
  '/admin/export-center',
  '/admin/fee/warning',
  '/admin/finance',
  '/admin/frequency/rules',
  '/admin/invoices',
  '/admin/number-attribution',
  '/admin/number-portability',
  '/admin/prefixes',
  '/admin/push/failures',
  '/admin/receipt/details',
  '/admin/reconciliation',
  '/admin/records',
  '/admin/review-history',
  '/admin/riskcontrol',
  '/admin/routing-policy',
  '/admin/send/details',
  '/admin/send/jobs',
  '/admin/settlements',
  '/admin/shortlinks/review',
  '/admin/signatures/review',
  '/admin/status-codes',
  '/admin/submission/details',
  '/admin/system/login-history',
  '/admin/system/logs',
  '/admin/system/security-events',
  '/admin/templates/review',
  '/admin/tenant-recharge-review',
  '/admin/tenant-risk',
  '/admin/tenant-trial-contracts',
  '/admin/tenant/terminations',
  '/admin/tenants',
  '/admin/unsubscribes',
  '/admin/uplink',
] as const;

const GRID_REGIONS = [
  { route: '/admin/exemption/policy', selector: 'section.card.exemption-policy-form' },
  { route: '/admin/riskcontrol', selector: '[data-testid="admin-blacklist-risk-black-white-lists-form"]' },
  { route: '/admin/content-safety', selector: '[data-testid="admin-runtime-content-content-safety-form"]' },
  { route: '/admin/frequency/rules', selector: '[data-testid="admin-frequency-api-frequency-rules-form"]' },
  { route: '/admin/prefixes', selector: '[data-testid="admin-number-attribution-portability-prefixes-page"] .number-attribution-form' },
  { route: '/admin/uplink', selector: '.uplink-query-panel .query-panel-field-grid' },
  { route: '/admin/custom/reports', selector: '.custom-report-form-grid' },
] as const;

const PRESERVED_LAYOUT_REGIONS = [
  { route: '/admin/alerts', selector: '.alert-engine-rule-grid', columns: 2 },
  { route: '/admin/archive', selector: '.retention-archive-grid', columns: 2 },
  { route: '/admin/tenant/terminations', selector: '.tenant-termination-grid', columns: 3 },
] as const;

const REPRESENTATIVE_SELECTS = [
  { route: '/admin/exemption/policy', testId: 'admin-auditable-exemption-exemption-policy-type' },
  { route: '/admin/riskcontrol', testId: 'admin-blacklist-risk-black-white-lists-type' },
  { route: '/admin/content-safety', testId: 'admin-runtime-content-content-safety-category' },
  { route: '/admin/frequency/rules', testId: 'admin-frequency-api-frequency-rules-type' },
  { route: '/admin/prefixes', testId: 'admin-prefixes-update-type' },
] as const;

const ORDINARY_CONTROL_SELECTOR = [
  'input:not([type="checkbox"]):not([type="radio"]):not([type="file"]):not([type="hidden"]):not([type="range"]):not([type="color"]):not([type="button"]):not([type="submit"]):not([type="reset"])',
  'select:not([multiple])',
].join(',');

async function mockUnavailablePageData(page: Page) {
  await page.route('**/api/v1/console/**', (route) => route.fulfill({
    status: 503,
    json: apiResponse(null, 'layout-only fixture'),
  }));
  await page.route('**/api/v1/console/account-overview', (route) => route.fulfill({
    json: apiResponse({
      id: 7,
      username: 'admin-user',
      userType: 'ADMIN',
      roleNames: ['系统管理员'],
      permissions: [],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/channel-health/monitor', (route) => route.fulfill({
    json: apiResponse([]),
  }));
  await page.route('**/api/v1/console/channel-health/pools', (route) => route.fulfill({
    json: apiResponse([]),
  }));
  await mockEmptyDashboard(page);
}

async function settleLayout(page: Page) {
  await page.evaluate(() => new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
  }));
}

async function visitAdminRoute(page: Page, route: string, reload = false) {
  if (reload || page.url() === 'about:blank') await page.goto(route);
  else await navigateWithinSpa(page, route);
  await settleLayout(page);
}

async function expandQueryPanels(page: Page) {
  const toggles = page.getByTestId('query-panel-toggle');
  for (let index = 0; index < await toggles.count(); index += 1) {
    const toggle = toggles.nth(index);
    if (await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
  }
}

async function expectOrdinaryControlsBounded(page: Page, route: string) {
  const controls = page.locator(`[data-console-kind="admin"] main.content :is(${ORDINARY_CONTROL_SELECTOR}):visible`);
  const controlCount = await controls.count();
  if (controlCount === 0) return false;

  const measurements = await controls.evaluateAll((elements) => elements.map((element) => {
    const control = element as HTMLInputElement | HTMLSelectElement;
    const container = (control.closest('.query-panel') ?? control.closest('main.content')) as HTMLElement | null;
    const controlBox = control.getBoundingClientRect();
    const containerBox = container?.getBoundingClientRect();
    let intrinsicWidth: number | null = null;
    if (control instanceof HTMLSelectElement) {
      const style = getComputedStyle(control);
      const intrinsic = control.cloneNode(true) as HTMLSelectElement;
      intrinsic.removeAttribute('id');
      intrinsic.removeAttribute('data-testid');
      Object.assign(intrinsic.style, {
        position: 'fixed',
        visibility: 'hidden',
        width: 'max-content',
        minWidth: '0',
        maxWidth: 'none',
        font: style.font,
        padding: style.padding,
        border: style.border,
      });
      document.body.append(intrinsic);
      intrinsicWidth = intrinsic.getBoundingClientRect().width;
      intrinsic.remove();
    }
    return {
      testId: control.dataset.testid ?? control.getAttribute('aria-label') ?? control.tagName,
      width: controlBox.width,
      containerWidth: containerBox?.width ?? document.documentElement.clientWidth,
      intrinsicWidth,
    };
  }));

  for (const measurement of measurements) {
    expect(measurement.width, `${route} ${measurement.testId} stays under 420px`).toBeLessThanOrEqual(420);
    expect(
      measurement.width,
      `${route} ${measurement.testId} stays within 30% of its query or main-content container`,
    ).toBeLessThanOrEqual((measurement.containerWidth * 0.3) + 1);
    const availableWidth = Math.min(420, measurement.containerWidth * 0.3);
    if (measurement.intrinsicWidth !== null && measurement.intrinsicWidth <= availableWidth + 1) {
      expect(
        measurement.width + 1,
        `${route} ${measurement.testId} shows its longest option when it fits the desktop cap`,
      ).toBeGreaterThanOrEqual(measurement.intrinsicWidth);
    }
  }
  return true;
}

async function directFieldBoxes(region: Locator) {
  return region.locator(':scope > :is(label, .query-panel-field):visible').evaluateAll((fields) => fields.map((field) => {
    const box = field.getBoundingClientRect();
    const overflowingChildren = [...field.children]
      .filter((child) => {
        const childBox = child.getBoundingClientRect();
        return childBox.width > 0 && (
          childBox.left < box.left - 1
          || childBox.right > box.right + 1
        );
      })
      .map((child) => `${child.tagName.toLowerCase()}.${child.className}`);
    return {
      x: Math.round(box.x),
      y: Math.round(box.y),
      overflowingChildren,
    };
  }));
}

async function directChildColumnCount(region: Locator) {
  const origins = await region.locator(':scope > *:visible').evaluateAll((children) => children.map((child) => (
    Math.round(child.getBoundingClientRect().x)
  )));
  return new Set(origins).size;
}

async function computedGridColumnCount(region: Locator) {
  return region.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(/\s+/).length);
}

async function measureSelect(select: Locator) {
  return select.evaluate((element) => {
    const control = element as HTMLSelectElement;
    const style = getComputedStyle(control);
    const intrinsic = control.cloneNode(true) as HTMLSelectElement;
    intrinsic.removeAttribute('id');
    intrinsic.removeAttribute('data-testid');
    Object.assign(intrinsic.style, {
      position: 'fixed',
      visibility: 'hidden',
      width: 'max-content',
      minWidth: '0',
      maxWidth: 'none',
      font: style.font,
      padding: style.padding,
      border: style.border,
    });
    document.body.append(intrinsic);
    const intrinsicWidth = intrinsic.getBoundingClientRect().width;
    intrinsic.remove();
    const container = (control.closest('.query-panel') ?? control.closest('main.content')) as HTMLElement;
    return {
      width: control.getBoundingClientRect().width,
      intrinsicWidth,
      containerWidth: container.getBoundingClientRect().width,
    };
  });
}

test('pw-issue-87-admin-form-contract C-ISSUE-87-ADMIN-FORM-WIDTH C-ISSUE-87-HORIZONTAL-CONTAINMENT C-ISSUE-87-FOUR-COLUMN-LAYOUT C-ISSUE-87-SELECT-OPTION-FIT', async ({ page }) => {
  await mockUnavailablePageData(page);
  await seedAuthenticatedSession(page, 'ADMIN');

  await test.step('all terminal Admin routes satisfy width and horizontal containment', async () => {
    const visitedFormRoutes = new Set<string>();
    for (const [index, route] of ADMIN_ROUTES.entries()) {
      await visitAdminRoute(page, route, index % 12 === 0);
      await expandQueryPanels(page);
      if (await expectOrdinaryControlsBounded(page, route)) visitedFormRoutes.add(route);

      const viewport = await page.evaluate(() => ({
        clientWidth: document.documentElement.clientWidth,
        scrollWidth: document.documentElement.scrollWidth,
        offenders: [...document.querySelectorAll<HTMLElement>('body *')]
          .filter((element) => element.getBoundingClientRect().right > document.documentElement.clientWidth + 1)
          .slice(0, 5)
          .map((element) => `${element.tagName.toLowerCase()}.${element.className}[${Math.round(element.getBoundingClientRect().right)}]`),
      }));
      expect(
        viewport.scrollWidth,
        `${route} has no document-level horizontal overflow; offenders=${viewport.offenders.join(',')}`,
      ).toBe(viewport.clientWidth);
    }
    expect([...visitedFormRoutes].sort(), 'all known Admin form routes render bounded controls').toEqual(
      [...EXPECTED_FORM_ROUTES].sort(),
    );
  });

  await test.step('representative forms use exactly four desktop columns', async () => {
    for (const { route, selector } of GRID_REGIONS) {
      await visitAdminRoute(page, route);
      await expandQueryPanels(page);
      const region = page.locator(selector);
      await expect(region).toBeVisible();
      const boxes = await directFieldBoxes(region);
      expect(boxes.length, `${route} has enough fields to prove the desktop grid`).toBeGreaterThanOrEqual(4);
      for (const [fieldIndex, box] of boxes.entries()) {
        expect(
          box.overflowingChildren,
          `${route} field ${fieldIndex + 1} keeps its controls and labels inside the grid cell`,
        ).toEqual([]);
      }
      const columns = [...new Set(boxes.map((box) => box.x))].sort((left, right) => left - right);
      expect(columns.length, `${route} fits exactly four desktop columns`).toBe(4);
      for (let fieldIndex = 0; fieldIndex < boxes.length; fieldIndex += 1) {
        expect(boxes[fieldIndex].x, `${route} field ${fieldIndex + 1} uses its stable grid column`).toBe(
          columns[fieldIndex % 4],
        );
      }
    }


    for (const { route, selector, columns } of PRESERVED_LAYOUT_REGIONS) {
      await visitAdminRoute(page, route);
      const region = page.locator(selector).first();
      await expect(region).toBeVisible();
      expect(
        await directChildColumnCount(region),
        `${route} preserves its page-owned non-field layout`,
      ).toBe(columns);
    }

    await visitAdminRoute(page, '/admin/channel/configuration');
    await page.getByTestId('admin-channel-configuration-channel-create-open').click();
    const channelConfigurationGrid = page.locator('.channel-configuration-grid');
    await expect(channelConfigurationGrid).toBeVisible();
    expect(
      await directChildColumnCount(channelConfigurationGrid),
      'the 720px channel configuration dialog preserves three usable columns',
    ).toBe(3);

    await page.route(/\/api\/v1\/console\/contracts\/workbench(?:\?.*)?$/, (apiRoute) => apiRoute.fulfill({
      json: apiResponse([{ tenantId: 42, tenantNo: 'TENANT-42', shortName: '测试机构', fullName: '测试机构有限公司',
        salesOwner: 'Alice', industry: 'SaaS', configurationSnapshotVersion: 'TRIAL-SNAPSHOT-V1-1234567890ABCDEF',
        lifecycleStatus: 'TRIAL_FROZEN', trialStatus: 'TRIAL_FROZEN', trialStartAt: '2026-09-01T00:00:00',
        trialEndAt: '2026-09-30T00:00:00', remainingDays: 0, quotaUsed: 400, quotaTotal: 500,
        messageCount: 2, successCount: 1, successRate: 0.5, complaintCount: 1, complaintRate: 0.5,
        statisticsAt: '2026-09-07T12:00:00', dataQuality: 'COMPLETE',
        sourceRegistry: 'tenants:trial_accounts:message_tasks:complaints', conversionEligible: true,
        ineligibilityReasons: [] }]),
    }));
    await page.route('**/api/v1/console/contracts/price-books', (apiRoute) => apiRoute.fulfill({
      json: apiResponse([{ priceBookVersion: 'SMS_STANDARD_V1', productCode: 'SMS', unitPriceMil: 50 }]),
    }));
    await visitAdminRoute(page, '/admin/tenant-trial-contracts');
    await page.getByTestId('admin-trial-conversion-workbench-row-convert').click();
    const postpaidFields = page.getByTestId('admin-contract-pricing-tenant-contract-postpaid-fields');
    await expect(postpaidFields).toBeVisible();
    const postpaidBounds = await postpaidFields.boundingBox();
    const outerContractBounds = await postpaidFields.locator('..').boundingBox();
    expect(postpaidBounds, 'postpaid field group has a layout box').not.toBeNull();
    expect(outerContractBounds, 'outer contract form has a layout box').not.toBeNull();
    expect(postpaidBounds!.x, 'nested postpaid fields start at the outer grid origin').toBeCloseTo(
      outerContractBounds!.x,
      0,
    );
    expect(postpaidBounds!.width, 'nested postpaid fields span the outer grid').toBeCloseTo(
      outerContractBounds!.width,
      0,
    );
    expect(await computedGridColumnCount(postpaidFields), 'nested postpaid fields retain four usable tracks').toBe(4);
    const postpaidFieldBoxes = await directFieldBoxes(postpaidFields);
    for (const [fieldIndex, box] of postpaidFieldBoxes.entries()) {
      expect(
        box.overflowingChildren,
        `nested postpaid field ${fieldIndex + 1} stays inside its grid cell`,
      ).toEqual([]);
    }
  });

  await test.step('static and data-backed selects fit their option content within the desktop cap', async () => {
    for (const { route, testId } of REPRESENTATIVE_SELECTS) {
      await visitAdminRoute(page, route);
      await expandQueryPanels(page);
      const select = page.getByTestId(testId);
      await expect(select).toBeVisible();
      const measurement = await measureSelect(select);
      expect(measurement.width + 1, `${route} ${testId} shows its longest option`).toBeGreaterThanOrEqual(
        measurement.intrinsicWidth,
      );
      expect(measurement.width, `${route} ${testId} does not fill its row`).toBeLessThanOrEqual(420);
    }

    await page.route('**/api/v1/console/channel-health/monitor', (apiRoute) => apiRoute.fulfill({
      json: apiResponse([{
        channelId: 87,
        channelName: '华东核心国际短信主通道一号高可用生产链路灾备监控专线二号线跨区域容灾保障链路',
        protocol: 'HTTP',
        operator: 'CMCC',
        status: 'ACTIVE',
        healthState: 'HEALTHY',
        timeoutRate: 0,
        failureRate: 0,
        averageLatencyMs: 12,
        reasonCode: null,
        candidateEligible: true,
        candidateReasonCode: null,
        eventCount: 1,
        pauseReason: null,
        pausedBy: null,
        pausedAt: null,
      }]),
    }));
    await page.route('**/api/v1/console/channel-health/pools', (apiRoute) => apiRoute.fulfill({
      json: apiResponse([]),
    }));
    await visitAdminRoute(page, '/admin/channel/pools');
    await page.getByTestId('admin-channel-health-channel-pools-create-open').click();
    const channelPoolGrid = page.getByTestId('admin-channel-health-channel-pools-weight-editor');
    expect(
      await directChildColumnCount(channelPoolGrid),
      'the 720px channel pool dialog preserves two usable columns',
    ).toBe(2);
    await page.getByTestId('admin-channel-health-channel-pools-member-add').click();
    const dynamicSelect = page.getByTestId('admin-channel-health-channel-pools-member-channel');
    await expect(dynamicSelect).toBeVisible();
    const dynamic = await measureSelect(dynamicSelect);
    expect(dynamic.intrinsicWidth, 'fixture exercises the 30% cap').toBeGreaterThan(dynamic.containerWidth * 0.3);
    expect(dynamic.width, 'data-backed select respects the desktop 30% cap').toBeLessThanOrEqual(
      (dynamic.containerWidth * 0.3) + 1,
    );
    expect(dynamic.width).toBeLessThanOrEqual(420);
  });
});
