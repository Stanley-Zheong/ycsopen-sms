import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p19-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p19' };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('7'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['号码管理员'],
      permissions: [
        { code: 'number-attribution:menu', resourceType: 'MENU' },
        { code: 'number-attribution:read', resourceType: 'API' },
        { code: 'number-attribution:import', resourceType: 'BUTTON' },
        { code: 'number-attribution:portability', resourceType: 'API' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/number-attribution/prefixes/versions', async (route) => route.fulfill({
    json: response([{ id: 1, versionNo: 'V20260909', updateType: 'FULL', status: 'ACTIVE', sourceName: '官方号段', totalRows: 2, conflictCount: 0, actor: 'operator', createdAt: '2026-09-09T00:00:00', activatedAt: '2026-09-09T00:00:00' }]),
  }));
  await page.route('**/api/v1/console/number-attribution/portability', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ id: 2, maskedMobile: '139****0001', originalCarrier: 'MOBILE', currentCarrier: 'TELECOM', portedAt: '2026-09-09', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', status: 'ACTIVE', updatedAt: '2026-09-09T00:00:00' }) });
      return;
    }
    await route.fulfill({ json: response([{ id: 2, maskedMobile: '139****0001', originalCarrier: 'MOBILE', currentCarrier: 'TELECOM', portedAt: '2026-09-09', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', status: 'ACTIVE', updatedAt: '2026-09-09T00:00:00' }]) });
  });
  await page.route('**/api/v1/console/number-attribution/prefixes/import', async (route) => route.fulfill({
    json: response({ versionNo: 'V20260909', success: 2, failed: 0, errors: [] }),
  }));
  await page.route('**/api/v1/console/number-attribution/lookup?**', async (route) => route.fulfill({
    json: response({ mobile: '13912345678', carrier: 'UNICOM', prefixCarrier: 'MOBILE', province: '广东', city: '深圳', source: 'PORTABILITY_CACHE', sourceName: '携转缓存', freshnessExpiresAt: '2026-09-10T00:00:00', providerFailure: false }),
  }));
});

test('pw-p19-attribution C-P19-ATTRIBUTION OBL-F-5-7-A pw-p19-portability C-P19-PORTABILITY OBL-F-5-7-B pw-p19-prefixes C-P19-PREFIX-IMPORT OBL-F-13-4-A', async ({ page }) => {
  await page.goto('/admin/number-attribution');
  await expect(page.getByTestId('admin-number-attribution-portability-attribution-page')).toBeVisible();
  await expect(page.getByTestId('admin-number-attribution-portability-prefixes-page')).toContainText('V20260909');
  await page.getByTestId('admin-prefixes-import').click();
  await expect(page.getByTestId('admin-number-attribution-message')).toContainText('已导入');
  await page.getByTestId('admin-number-attribution-lookup').click();
  await expect(page.getByTestId('admin-number-attribution-result')).toContainText('UNICOM');
  await expect(page.getByTestId('admin-number-attribution-fallback-source')).toContainText('PORTABILITY_CACHE');
  await page.getByTestId('admin-number-portability-save').click();
  await expect(page.getByTestId('admin-number-attribution-message')).toContainText('携号转网缓存已保存');

  await page.goto('/admin/number-portability');
  await expect(page.getByTestId('admin-number-attribution-portability-portability-page')).toBeVisible();
  await page.goto('/admin/prefixes');
  await expect(page.getByTestId('admin-number-attribution-portability-prefixes-page')).toBeVisible();
});

test('pw-issue-65-choice-control-layout C-ISSUE-65-CHOICE-CONTROL-LAYOUT OBL-ISSUE-65-CHOICE-CONTROLS', async ({ page, browser }) => {
  await page.goto('/admin/number-attribution');

  const lookupForm = page.getByTestId('admin-number-attribution-lookup-form');
  const elements = [
    lookupForm.getByTestId('admin-number-attribution-mobile-label'),
    lookupForm.getByTestId('admin-number-attribution-mobile'),
    lookupForm.getByTestId('admin-number-attribution-force-provider-failure-label'),
    lookupForm.getByTestId('admin-number-attribution-force-provider-failure'),
    lookupForm.getByTestId('admin-number-attribution-lookup'),
  ];

  await expect(lookupForm).toBeVisible();
  const boxes = await Promise.all(elements.map((element) => element.boundingBox()));
  for (const box of boxes) expect(box).not.toBeNull();

  const verticalCenters = boxes.map((box) => box!.y + box!.height / 2);
  expect(Math.max(...verticalCenters) - Math.min(...verticalCenters)).toBeLessThanOrEqual(2);
  for (let index = 1; index < boxes.length; index += 1) {
    expect(boxes[index]!.x).toBeGreaterThanOrEqual(boxes[index - 1]!.x + boxes[index - 1]!.width);
  }

  const choiceSize = await elements[3].evaluate((element) => {
    const style = window.getComputedStyle(element);
    return { width: style.width, height: style.height, minHeight: style.minHeight };
  });
  expect(choiceSize).toEqual({ width: '16px', height: '16px', minHeight: '16px' });

  const radioSize = await page.evaluate(() => {
    const radio = document.createElement('input');
    radio.type = 'radio';
    document.body.append(radio);
    const style = window.getComputedStyle(radio);
    const size = { width: style.width, height: style.height, minHeight: style.minHeight };
    radio.remove();
    return size;
  });
  expect(radioSize).toEqual({ width: '16px', height: '16px', minHeight: '16px' });

  const pageVariants = await page.evaluate(() => {
    const host = document.createElement('div');
    host.style.position = 'fixed';
    host.style.inset = '0 auto auto 0';
    host.style.background = 'white';
    host.innerHTML = [
      '<label class="login-remember"><input type="checkbox"><span>记住用户名</span></label>',
      '<label class="qualification-check"><input type="checkbox"><span>确认资料</span></label>',
      '<label class="channel-health-checkbox"><input type="checkbox"><span>主通道</span></label>',
      '<div class="uplink-auto-reply"><label class="inline"><input type="checkbox"><span>启用自动回复</span></label></div>',
      '<div><label><input type="checkbox"><span>角色权限</span></label></div>',
    ].join('');
    document.body.append(host);
    const result = [...host.querySelectorAll('label')].map((label) => {
      const input = label.querySelector('input')!;
      const text = label.querySelector('span')!;
      const style = window.getComputedStyle(input);
      const inputBox = input.getBoundingClientRect();
      const textBox = text.getBoundingClientRect();
      return {
        width: style.width,
        height: style.height,
        minHeight: style.minHeight,
        centerDelta: Math.abs(inputBox.y + inputBox.height / 2 - (textBox.y + textBox.height / 2)),
      };
    });
    host.remove();
    return result;
  });
  expect(pageVariants).toHaveLength(5);
  for (const variant of pageVariants) {
    expect(variant).toMatchObject({ width: '16px', height: '16px', minHeight: '16px' });
    expect(variant.centerDelta).toBeLessThanOrEqual(1);
  }

  await page.setViewportSize({ width: 600, height: 900 });
  await lookupForm.evaluate((element) => { element.style.width = '300px'; });
  const compactInput = await elements[1].boundingBox();
  const compactButton = await elements[4].boundingBox();
  expect(compactInput).not.toBeNull();
  expect(compactButton).not.toBeNull();
  expect(compactButton!.y).toBeGreaterThan(compactInput!.y + compactInput!.height);

  const publicContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const publicPage = await publicContext.newPage();
  try {
    await publicPage.goto('/login');
    const rememberChoice = publicPage.getByTestId('shared-auth-login-remember');
    await expect(rememberChoice).toBeVisible();
    await expect(rememberChoice).toHaveCSS('width', '16px');
    await expect(rememberChoice).toHaveCSS('height', '16px');

    await publicPage.goto('/tenant/register');
    const trademarkChoice = publicPage.getByTestId('tenant-tenant-qualification-qualification-trademark-signature-intent');
    await expect(trademarkChoice).toBeVisible();
    await expect(trademarkChoice).toHaveCSS('width', '16px');
    await expect(trademarkChoice).toHaveCSS('height', '16px');
  } finally {
    await publicContext.close();
  }
});
