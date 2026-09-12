import { expect, test, type Locator, type Page } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.describe.configure({ mode: 'serial' });
test.use({ viewport: { width: 1440, height: 900 } });

function tenant(tenantId: number, shortName: string) {
  return {
    tenantId,
    tenantNo: `T${String(tenantId).padStart(4, '0')}`,
    shortName,
    fullName: `${shortName}有限公司`,
    verificationStatus: 'PENDING',
    lifecycleStatus: 'SUBMITTED',
    operatingStatus: 'NORMAL',
    submittedAt: '2026-09-12T08:00:00Z',
    bizManager: '测试经理',
    accountRevision: 1,
    qualificationRevision: 1,
  };
}

async function expectIntrinsicButtonWidth(button: Locator) {
  await expect(button).toBeVisible();
  const box = await button.boundingBox();
  expect(box, 'button has a layout box').not.toBeNull();
  expect(box!.width, 'button stays close to its label width').toBeLessThan(200);
}

async function expectQueryGridAtBoundary(
  page: Page,
  width: number,
  columns: number,
) {
  await page.setViewportSize({ width, height: 900 });
  const panel = page.getByTestId('query-panel');
  const fields = panel.getByTestId('query-panel-fields');
  const toggle = panel.getByTestId('query-panel-toggle');

  if (columns === 3) {
    await expect(toggle).toHaveCount(0);
    await expect(fields).toBeVisible();
  } else {
    await expect(toggle).toBeVisible();
    if (!await fields.isVisible()) await toggle.click();
    await expect(fields).toBeVisible();
  }

  const controls = [
    panel.getByTestId('query-input-keyword').locator('input'),
    panel.getByTestId('query-input-verification-status').locator('select'),
    panel.getByTestId('query-input-operating-status').locator('select'),
  ];
  const boxes = await Promise.all(controls.map((control) => control.boundingBox()));
  for (const box of boxes) {
    expect(box, `query control has a layout box at ${width}px`).not.toBeNull();
    expect(box!.height, `controls share the 40px height at ${width}px`).toBe(40);
    expect(box!.width, `controls remain compact at ${width}px`).toBeLessThanOrEqual(280);
  }

  const rowPositions = boxes.map((box) => Math.round(box!.y));
  expect(new Set(rowPositions).size, `${width}px uses ${columns} query-grid columns`).toBe(
    Math.ceil(controls.length / columns),
  );
}

test('pw-issue-63-query-controls C-ISSUE-63-QUERY-CONTROLS OBL-ISSUE-63-QUERY-CONTROLS', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/admin/tenants', (route) => route.fulfill({
    json: apiResponse([tenant(8, '贝塔机构')]),
  }));
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/tenants');

  await expectQueryGridAtBoundary(page, 1201, 3);
  await expectQueryGridAtBoundary(page, 1200, 2);
  await expectQueryGridAtBoundary(page, 901, 2);
  await expectQueryGridAtBoundary(page, 900, 1);
});

test('pw-issue-63-action-buttons C-ISSUE-63-ACTION-BUTTONS OBL-ISSUE-63-ACTION-BUTTONS', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/routing-policy/versions', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/routing-policy/rules', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/routing-policy/circuits', (route) => route.fulfill({ json: apiResponse([]) }));
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/routing-policy');

  await expectIntrinsicButtonWidth(page.getByTestId('admin-routing-circuit-routing-policy-import'));
  await expectIntrinsicButtonWidth(page.getByTestId('admin-routing-circuit-routing-policy-simulate'));
  await expectIntrinsicButtonWidth(page.getByTestId('admin-routing-circuit-routing-circuit-record'));
  await expectIntrinsicButtonWidth(page.getByTestId('admin-routing-circuit-routing-retry-save'));
});

test('pw-issue-73-primary-control-height C-ISSUE-73-PRIMARY-CONTROL-HEIGHT OBL-ISSUE-73-PRIMARY-CONTROL-HEIGHT', async ({ page }) => {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/routing-policy/versions', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/routing-policy/rules', (route) => route.fulfill({ json: apiResponse([]) }));
  await page.route('**/api/v1/console/routing-policy/circuits', (route) => route.fulfill({ json: apiResponse([]) }));
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/routing-policy');

  const controls = [
    page.getByTestId('admin-routing-circuit-routing-policy-version'),
    page.getByTestId('admin-routing-circuit-routing-policy-import-input'),
    page.getByTestId('admin-routing-circuit-routing-retry-category'),
  ];

  for (const control of controls) {
    const box = await control.boundingBox();
    expect(box, 'primary control has a layout box').not.toBeNull();
    expect(box!.height, 'primary controls share the 40px height').toBe(40);
  }

  const textarea = page.getByTestId('admin-routing-circuit-routing-policy-import-input');
  await textarea.focus();
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Tab');
  await expect(textarea).toBeFocused();
  const focusStyle = await textarea.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      focusVisible: element.matches(':focus-visible'),
      outlineColor: style.outlineColor,
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      outlineOffset: style.outlineOffset,
    };
  });
  expect(focusStyle.focusVisible, 'textarea receives visible focus').toBe(true);
  expect(focusStyle).toEqual({
    focusVisible: true,
    outlineColor: 'rgba(12, 133, 232, 0.28)',
    outlineStyle: 'solid',
    outlineWidth: '3px',
    outlineOffset: '2px',
  });

  await page.goto('/tenant/register');
  const publicRegistrationInput = page.getByTestId('public-tenant-qualification-register-admin-username');
  const publicRegistrationBox = await publicRegistrationInput.boundingBox();
  expect(publicRegistrationBox, 'public registration input has a layout box').not.toBeNull();
  expect(publicRegistrationBox!.height, 'public registration input keeps the shared 40px minimum height').toBe(40);
});
