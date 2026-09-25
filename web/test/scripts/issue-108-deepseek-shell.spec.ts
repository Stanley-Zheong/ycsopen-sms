import { expect, test, type Page } from '@playwright/test';
import { loginAs, mockEmptyDashboard } from './helpers';

/**
 * issue-108-deepseek-shell — Chrome acceptance for the DeepSeek-Platform-aligned sign-in
 * surface and the shared Admin/Tenant console shell.
 *
 * Covers the routes named by issue #108: `/login`, `/admin/auth/login`, `/admin/dashboard`,
 * `/tenant/overview`. Visual source of truth and borrowed values are recorded in
 * .planning/frontend-spirits/01-foundation-contract/evidence/deepseek-platform-visual-observation.md
 */

const DESKTOP = { width: 1440, height: 900 };
const MOBILE = { width: 390, height: 844 };

async function readShell(page: Page) {
  return page.evaluate(() => {
    const pick = (selector: string) => {
      const element = document.querySelector(selector) as HTMLElement | null;
      if (!element) return null;
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      return {
        box: {
          x: Math.round(rect.x),
          y: Math.round(rect.y),
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        },
        background: style.backgroundColor,
        maxWidth: style.maxWidth,
        classList: [...element.classList].sort(),
      };
    };
    const shell = document.querySelector('[data-testid="shared-console-shell"]');
    return {
      shell: pick('[data-testid="shared-console-shell"]'),
      sidebar: pick('[data-testid="shared-console-shell-sidebar"]'),
      brand: pick('[data-testid="shared-console-shell-brand"]'),
      topbar: pick('[data-testid="shared-console-shell-topbar"]'),
      content: pick('[data-testid="shared-console-shell-content"]'),
      container: pick('[data-testid="shared-console-shell-content-container"]'),
      primaryNavIsInsideSidebar: Boolean(
        document.querySelector('[data-testid="shared-console-shell-sidebar"] nav.sidebar-menu'),
      ),
      mainIsDirectChild: Boolean(shell?.querySelector(':scope > main.content')),
      childTags: shell ? [...shell.children].map((child) => child.tagName) : [],
      overflow: {
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      },
    };
  });
}

async function openConsole(page: Page, userType: 'ADMIN' | 'TENANT_ADMIN', route: string) {
  await mockEmptyDashboard(page);
  await loginAs(page, userType);
  await page.goto(route);
  await expect(page.getByTestId('shared-console-shell')).toBeVisible();
  return readShell(page);
}

async function expectNoHorizontalScroll(page: Page, label: string) {
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    bodyScrollWidth: document.body.scrollWidth,
  }));
  expect(
    overflow.scrollWidth,
    `${label}: documentElement.scrollWidth must not exceed clientWidth`,
  ).toBeLessThanOrEqual(overflow.clientWidth);
  expect(
    overflow.bodyScrollWidth,
    `${label}: body.scrollWidth must not exceed clientWidth`,
  ).toBeLessThanOrEqual(overflow.clientWidth);
}

async function expectNoOverlap(page: Page, firstSelector: string, secondSelector: string, label: string) {
  const boxes = await page.evaluate(({ a, b }) => {
    const read = (selector: string) => {
      const element = document.querySelector(selector);
      if (!element) return null;
      const rect = element.getBoundingClientRect();
      return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
    };
    return { a: read(a), b: read(b) };
  }, { a: firstSelector, b: secondSelector });
  expect(boxes.a, `${label}: ${firstSelector} is rendered`).not.toBeNull();
  expect(boxes.b, `${label}: ${secondSelector} is rendered`).not.toBeNull();
  const left = boxes.a!;
  const right = boxes.b!;
  const horizontallySeparated = left.x + left.width <= right.x + 1 || right.x + right.width <= left.x + 1;
  const verticallySeparated = left.y + left.height <= right.y + 1 || right.y + right.height <= left.y + 1;
  expect(horizontallySeparated || verticallySeparated, `${label}: boxes must not overlap`).toBe(true);
}

test.describe('issue-108 sign-in surface', () => {
  test.use({ viewport: DESKTOP });

  test('pw-issue-108-login-split renders a brand panel beside a white form panel', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByTestId('shared-auth-login-page')).toBeVisible();
    await expect(page.getByTestId('shared-auth-login-background')).toHaveClass(/login-shell/);
    await expect(page.getByTestId('shared-auth-login-card')).toHaveClass(/login-card/);

    const intro = page.locator('.login-intro');
    const formPanel = page.locator('.login-form-panel');
    await expect(intro).toBeVisible();
    await expect(formPanel).toBeVisible();

    const introBox = (await intro.boundingBox())!;
    const panelBox = (await formPanel.boundingBox())!;
    expect(introBox.x).toBe(0);
    expect(introBox.width).toBeGreaterThan(300);
    expect(panelBox.x).toBeGreaterThanOrEqual(introBox.x + introBox.width - 1);
    expect(introBox.height).toBeGreaterThanOrEqual(900);

    // Left panel is the coloured brand surface; right panel is pure white.
    const introBackground = await intro.evaluate((element) => getComputedStyle(element).backgroundColor);
    const panelBackground = await formPanel.evaluate((element) => getComputedStyle(element).backgroundColor);
    expect(panelBackground).toBe('rgb(255, 255, 255)');
    expect(introBackground).not.toBe('rgb(255, 255, 255)');

    // Brand copy carries explicit hierarchy and readable contrast on the dark panel.
    await expect(page.locator('.login-intro h1')).toHaveText('统一管理短信发送、路由策略与财务结算');
    expect(await page.locator('.login-intro h1').evaluate((element) => getComputedStyle(element).color))
      .toBe('rgb(255, 255, 255)');
    expect(await page.locator('.login-intro h1').evaluate((element) => getComputedStyle(element).fontSize))
      .toBe('32px');
    await expect(page.locator('.login-intro h1')).toBeVisible();

    // The form column is a restrained, centered card inside the white panel.
    const cardBox = (await page.getByTestId('shared-auth-login-card').boundingBox())!;
    expect(cardBox.x).toBeGreaterThanOrEqual(panelBox.x);
    expect(cardBox.x + cardBox.width).toBeLessThanOrEqual(panelBox.x + panelBox.width + 1);
    expect(cardBox.width).toBeLessThanOrEqual(420);

    await expectNoOverlap(page, '.login-intro', '.login-form-panel', 'login split');
    await expectNoHorizontalScroll(page, '/login desktop');
  });

  test('pw-issue-108-login-alias /admin/auth/login renders the same sign-in surface', async ({ page }) => {
    await page.goto('/login');
    const primaryIntro = (await page.locator('.login-intro').boundingBox())!;
    const primaryPanel = (await page.locator('.login-form-panel').boundingBox())!;

    await page.goto('/admin/auth/login');
    await expect(page.getByTestId('shared-auth-login-card')).toBeVisible();
    const aliasIntro = (await page.locator('.login-intro').boundingBox())!;
    const aliasPanel = (await page.locator('.login-form-panel').boundingBox())!;

    expect(Math.round(aliasIntro.width)).toBe(Math.round(primaryIntro.width));
    expect(Math.round(aliasIntro.height)).toBe(Math.round(primaryIntro.height));
    expect(Math.round(aliasPanel.width)).toBe(Math.round(primaryPanel.width));
    expect(Math.round(aliasPanel.x)).toBe(Math.round(primaryPanel.x));
    expect(Math.round(aliasIntro.x)).toBe(Math.round(primaryIntro.x));
    await expectNoHorizontalScroll(page, '/admin/auth/login desktop');
  });

  test('pw-issue-108-login-selectors keeps the stable auth contract and control sizing', async ({ page }) => {
    await page.goto('/login');

    for (const testId of [
      'shared-auth-login-page',
      'shared-auth-login-background',
      'shared-auth-login-card',
      'shared-auth-login-username',
      'shared-auth-login-password',
      'shared-auth-login-remember',
      'shared-auth-login-submit',
      'admin-console-identity-auth-login-submit',
    ]) {
      await expect(page.getByTestId(testId), `${testId} is rendered`).toBeVisible();
    }

    await expect(page.getByTestId('shared-auth-login-error')).toHaveCount(0);
    await expect(page.getByTestId('shared-auth-login-remember')).toHaveClass(/login-remember-input/);

    const checkbox = (await page.getByTestId('shared-auth-login-remember').boundingBox())!;
    expect(checkbox.width).toBeGreaterThanOrEqual(14);
    expect(checkbox.height).toBeLessThanOrEqual(18);
    expect(checkbox.height).toBeGreaterThanOrEqual(14);

    for (const id of ['shared-auth-login-username', 'shared-auth-login-password', 'admin-console-identity-auth-login-submit']) {
      const box = (await page.getByTestId(id).boundingBox())!;
      expect(box.height, `${id} uses the 48px auth control height`).toBe(48);
    }
  });

  test('pw-issue-108-login-states keeps focus, hover, disabled and error states visible and stable', async ({ page }) => {
    await page.route('**/api/v1/console/auth/login', async (route) => {
      await new Promise((resolve) => { setTimeout(resolve, 1200); });
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'AUTH_INVALID_CREDENTIALS', message: '用户名或密码错误' }),
      });
    });
    await page.goto('/login');

    const submit = page.getByTestId('admin-console-identity-auth-login-submit');
    const submitBefore = (await submit.boundingBox())!;

    // Visible focus state on the field. border-color is transitioned, so poll until it settles.
    const username = page.getByTestId('shared-auth-login-username');
    await username.focus();
    const focus = await username.evaluate((element) => {
      const style = getComputedStyle(element);
      return {
        focusVisible: element.matches(':focus-visible'),
        outlineStyle: style.outlineStyle,
        outlineWidth: style.outlineWidth,
        outlineOffset: style.outlineOffset,
        outlineColor: style.outlineColor,
      };
    });
    expect(focus.focusVisible, 'focused field matches :focus-visible').toBe(true);
    expect(focus.outlineStyle).toBe('solid');
    expect(focus.outlineWidth).toBe('3px');
    expect(focus.outlineOffset).toBe('2px');
    expect(focus.outlineColor).toBe('rgba(57, 100, 254, 0.28)');
    await expect
      .poll(async () => username.evaluate((element) => getComputedStyle(element).borderColor))
      .toBe('rgb(57, 100, 254)');

    // Visible hover state on the submit control.
    const baseBackground = await submit.evaluate((element) => getComputedStyle(element).backgroundColor);
    await submit.hover();
    await expect
      .poll(async () => submit.evaluate((element) => getComputedStyle(element).backgroundColor))
      .not.toBe(baseBackground);

    // Loading state: disabled + relabelled, with no layout movement and no duplicate submit.
    await page.getByTestId('shared-auth-login-username').fill('issue108-admin');
    await page.getByTestId('shared-auth-login-password').fill('issue108-password');
    await submit.click();

    await expect(submit).toBeDisabled();
    await expect(page.getByTestId('shared-auth-login-submit')).toHaveText('登录中…');
    const submitPending = (await submit.boundingBox())!;
    expect(Math.round(submitPending.x)).toBe(Math.round(submitBefore.x));
    expect(Math.round(submitPending.width)).toBe(Math.round(submitBefore.width));
    expect(Math.round(submitPending.y)).toBe(Math.round(submitBefore.y));

    // Error state: message in the reserved slot, and the submit control does not move.
    await expect(page.getByTestId('shared-auth-login-error')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('shared-auth-login-error')).toHaveText('用户名或密码错误，或账号已被锁定');
    await expect(page.getByTestId('shared-auth-login-password')).toHaveValue('');
    await expect(submit).toBeEnabled();

    const submitAfterError = (await submit.boundingBox())!;
    expect(Math.round(submitAfterError.x)).toBe(Math.round(submitBefore.x));
    expect(Math.round(submitAfterError.y)).toBe(Math.round(submitBefore.y));
    expect(Math.round(submitAfterError.width)).toBe(Math.round(submitBefore.width));

    // The error never pushes the button out of the card or the card out of the panel.
    const cardBox = (await page.getByTestId('shared-auth-login-card').boundingBox())!;
    const panelBox = (await page.locator('.login-form-panel').boundingBox())!;
    expect(submitAfterError.x + submitAfterError.width).toBeLessThanOrEqual(cardBox.x + cardBox.width + 1);
    expect(cardBox.x + cardBox.width).toBeLessThanOrEqual(panelBox.x + panelBox.width + 1);
    await expectNoHorizontalScroll(page, '/login error state');
  });

  test('pw-issue-108-login-mobile stacks into one column without horizontal scrolling', async ({ page }) => {
    await page.setViewportSize(MOBILE);
    await page.goto('/login');

    await expect(page.getByTestId('shared-auth-login-card')).toBeVisible();
    const intro = (await page.locator('.login-intro').boundingBox())!;
    const panel = (await page.locator('.login-form-panel').boundingBox())!;

    expect(Math.round(intro.width)).toBe(MOBILE.width);
    expect(Math.round(panel.width)).toBe(MOBILE.width);
    expect(panel.y).toBeGreaterThanOrEqual(intro.y + intro.height - 1);
    expect(intro.x).toBe(0);
    expect(panel.x).toBe(0);

    const formBackground = await page.locator('.login-form-panel').evaluate((element) => getComputedStyle(element).backgroundColor);
    expect(formBackground).toBe('rgb(255, 255, 255)');

    await expectNoOverlap(page, '.login-intro', '.login-form-panel', 'login mobile');
    await expectNoHorizontalScroll(page, '/login 390x844');
  });
});

test.describe('issue-108 shared console shell', () => {
  test.use({ viewport: DESKTOP });

  test('pw-issue-108-shell-shared Admin and Tenant render one identical shell contract', async ({ page }) => {
    const admin = await openConsole(page, 'ADMIN', '/admin/dashboard');
    const tenant = await openConsole(page, 'TENANT_ADMIN', '/tenant/overview');

    for (const [name, metrics] of [['Admin', admin], ['Tenant', tenant]] as const) {
      expect(metrics.shell, `${name} shell root exists`).not.toBeNull();
      expect(metrics.shell!.classList).toEqual(['app-shell', 'layout']);
      expect(metrics.sidebar!.classList).toEqual(['app-sidebar', 'sidebar']);
      expect(metrics.content!.classList).toEqual(['app-content', 'content']);
      expect(metrics.mainIsDirectChild, `${name} keeps main.content a direct child of .layout`).toBe(true);
      expect(metrics.childTags, `${name} shell children order`).toEqual(['ASIDE', 'MAIN']);
      expect(metrics.primaryNavIsInsideSidebar, `${name} navigation lives in the sidebar`).toBe(true);
      expect(metrics.shell!.background).toBe('rgb(255, 255, 255)');
      expect(metrics.sidebar!.background).toBe('rgb(249, 250, 251)');
      expect(metrics.sidebar!.box.width).toBe(260);
      expect(metrics.sidebar!.box.x).toBeLessThan(metrics.content!.box.x);
      expect(metrics.container!.maxWidth).toBe('1120px');
    }

    // Equal geometry and surface between the two consoles — no second sidebar or header.
    expect(admin.sidebar!.box.width).toBe(tenant.sidebar!.box.width);
    expect(admin.sidebar!.background).toBe(tenant.sidebar!.background);
    expect(admin.shell!.background).toBe(tenant.shell!.background);
    expect(admin.container!.maxWidth).toBe(tenant.container!.maxWidth);
    expect(admin.topbar!.box.height).toBe(tenant.topbar!.box.height);

    await expectNoHorizontalScroll(page, '/tenant/overview desktop');
  });

  test('pw-issue-108-shell-hooks exposes stable semantic shell selectors and page header', async ({ page }) => {
    await openConsole(page, 'ADMIN', '/admin/dashboard');

    await expect(page.getByTestId('shared-console-shell')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-sidebar')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-brand')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-topbar')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-breadcrumb')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-workspace')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-content-container')).toBeVisible();
    await expect(page.getByTestId('shared-console-shell-topbar')).toHaveJSProperty('tagName', 'HEADER');
    await expect(page.getByTestId('shared-console-shell-breadcrumb')).toHaveAttribute('aria-label', '面包屑');

    await expect(page.getByTestId('shared-console-shell-brand')).toContainText('YCSAN-SMS 平台管理后台');
    await expect(page.getByTestId('shared-console-shell-workspace')).toHaveText('平台管理后台');
    await expect(page.getByTestId('shared-console-shell-breadcrumb')).toContainText('仪表盘');

    // Page title stays inside the centered content container, below the header band.
    const container = (await page.getByTestId('shared-console-shell-content-container').boundingBox())!;
    const topbar = (await page.getByTestId('shared-console-shell-topbar').boundingBox())!;
    const title = (await page.locator('h1').first().boundingBox())!;
    expect(title.y).toBeGreaterThanOrEqual(topbar.y + topbar.height - 1);
    expect(title.x).toBeGreaterThanOrEqual(container.x - 1);
    expect(title.x + title.width).toBeLessThanOrEqual(container.x + container.width + 1);

    await expectNoOverlap(page, '[data-testid="shared-console-shell-sidebar"]', '[data-testid="shared-console-shell-content"]', 'shell desktop');
  });

  test('pw-issue-108-shell-mobile keeps both consoles reachable without horizontal scrolling', async ({ page }) => {
    await page.setViewportSize(MOBILE);

    const admin = await openConsole(page, 'ADMIN', '/admin/dashboard');
    expect(admin.sidebar!.box.width).toBe(MOBILE.width);
    expect(admin.sidebar!.box.y).toBeLessThan(admin.content!.box.y);
    await expect(page.getByTestId('shared-console-shell-sidebar')).toBeVisible();
    await expectNoHorizontalScroll(page, '/admin/dashboard 390x844');

    // Navigation stays clickable at the mobile breakpoint instead of being hidden.
    const channelGroup = page.getByTestId('admin-console-navigation-channel-management-group-toggle');
    await channelGroup.scrollIntoViewIfNeeded();
    await expect(channelGroup).toBeVisible();
    await channelGroup.click();
    await expect(channelGroup).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('.sidebar-menu-panel:not([hidden])')).toHaveCount(1);

    const tenant = await openConsole(page, 'TENANT_ADMIN', '/tenant/overview');
    expect(tenant.sidebar!.box.width).toBe(MOBILE.width);
    expect(tenant.sidebar!.box.y).toBeLessThan(tenant.content!.box.y);
    await expect(page.getByTestId('tenant-console-navigation-send-management-group-toggle')).toBeVisible();
    await expectNoHorizontalScroll(page, '/tenant/overview 390x844');
  });
});
