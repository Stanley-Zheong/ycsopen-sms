import { test, expect } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p15-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p15' };
}

const rows = [
  {
    decisionId: 'SIGNATURE:1',
    resourceType: 'SIGNATURE',
    resourceId: 1,
    resourceCode: 'YCSIG',
    resourceVersion: 'v1',
    tenantId: 42,
    decisionState: 'APPROVED',
    actor: 'operator-7',
    reason: '材料完整',
    riskLevel: 'HIGH',
    evidenceRef: 'pobj-proof-1',
    submittedSnapshot: '优创硕安',
    lifecycleLink: '/admin/signatures/review?keyword=YCSIG',
    createdAt: '2026-09-09T01:00:00',
  },
  {
    decisionId: 'TEMPLATE:1',
    resourceType: 'TEMPLATE',
    resourceId: 2,
    resourceCode: 'TPL-001',
    resourceVersion: 'v2',
    tenantId: 42,
    decisionState: 'REJECTED',
    actor: 'operator-8',
    reason: '变量说明不足',
    riskLevel: null,
    evidenceRef: 'signature:1',
    submittedSnapshot: '验证码 ${code}',
    lifecycleLink: '/admin/templates/review?keyword=TPL-001',
    createdAt: '2026-09-09T02:00:00',
  },
];

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('7'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['审核员'],
      permissions: [
        { code: 'review-history:menu', resourceType: 'MENU' },
        { code: 'review-history:read', resourceType: 'API' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/review-history**', async (route) => route.fulfill({ json: response(rows) }));
  await page.route('**/api/v1/console/review-history/detail**', async (route) => route.fulfill({ json: response(rows[1]) }));
});

test('pw-p15-review-history C-P15-REVIEW-HISTORY OBL-IA-ADMIN-REVIEW-HISTORY', async ({ page }) => {
  await page.goto('/admin/review-history');
  await expect(page.getByTestId('admin-resource-review-history-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-resource-review-history-review-filters')).toBeVisible();
  await page.getByTestId('query-panel-toggle').click();
  await page.getByTestId('admin-resource-review-history-review-filters').getByLabel('资源类型').selectOption('SIGNATURE');
  await page.getByTestId('admin-resource-review-history-review-filters').getByLabel('开始时间').fill('2026-09-09T00:00');
  await page.getByTestId('admin-resource-review-history-review-filters').getByLabel('结束时间').fill('2026-09-09T23:59');
  await page.getByTestId('query-submit').click();
  await expect(page.getByTestId('admin-resource-review-history-review-table')).toContainText('SIGNATURE:1');
  await expect(page.getByTestId('admin-resource-review-history-review-pagination')).toContainText('第 1 页，每页 50 条');
  await expect(page.getByTestId('admin-resource-review-history-review-page-prev')).toBeDisabled();
  await expect(page.getByTestId('admin-resource-review-history-review-page-next')).toBeDisabled();
  await page.getByTestId('admin-resource-review-history-review-detail-open').nth(1).click();
  await expect(page.getByTestId('admin-resource-review-history-review-detail-drawer')).toContainText('TEMPLATE:1');
});

test('pw-issue-80-review-history-empty-table C-ISSUE-80-REVIEW-HISTORY-EMPTY OBL-ISSUE-80-REVIEW-HISTORY-EMPTY', async ({ page }) => {
  await page.route('**/api/v1/console/review-history**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const result = requestUrl.searchParams.get('keyword') === 'YCSIG' ? rows.slice(0, 1) : [];
    await route.fulfill({ json: response(result) });
  });
  await page.goto('/admin/review-history');

  const queryPanel = page.getByTestId('query-panel');
  await expect(queryPanel).toBeVisible();
  await queryPanel.getByTestId('query-panel-toggle').click();
  const queryActions = queryPanel.getByTestId('query-actions');
  await expect(queryActions.getByTestId('query-submit')).toBeVisible();
  await expect(queryActions.getByTestId('query-reset')).toBeVisible();
  const controlHeights = await queryPanel.getByTestId('query-fields').locator('input, select').evaluateAll(
    (controls) => controls.map((control) => control.getBoundingClientRect().height),
  );
  expect(controlHeights).toHaveLength(8);
  expect(controlHeights.every((height) => height >= 38 && height <= 42)).toBe(true);
  const actionHeights = await queryActions.locator('button').evaluateAll(
    (buttons) => buttons.map((button) => button.getBoundingClientRect().height),
  );
  expect(actionHeights.every((height) => height >= 38 && height <= 42)).toBe(true);

  const keywordInput = queryPanel.getByTestId('query-input-keyword').locator('input');
  let draftRequestCount = 0;
  page.on('request', (request) => {
    if (request.url().includes('/console/review-history') && request.url().includes('keyword=YCSIG')) {
      draftRequestCount += 1;
    }
  });
  await keywordInput.fill('YCSIG');
  await page.waitForTimeout(150);
  expect(draftRequestCount).toBe(0);
  await Promise.all([
    page.waitForRequest((request) => request.url().includes('/console/review-history')
      && request.url().includes('keyword=YCSIG') && request.url().includes('page=0')),
    queryActions.getByTestId('query-submit').click(),
  ]);
  const table = page.getByTestId('data-table');
  await expect(table).toContainText('SIGNATURE:1');

  await queryActions.getByTestId('query-reset').click();
  await expect(keywordInput).toHaveValue('');

  await expect(table).toBeVisible();
  await expect(table.getByRole('columnheader')).toHaveText([
    '决定', '资源', '机构', '状态', '审核人', '原因', '时间', '操作',
  ]);
  const empty = table.getByTestId('table-empty');
  await expect(empty).toContainText('暂无审核记录');
  await expect(empty.locator('td')).toHaveAttribute('colspan', '8');

  const geometry = await page.evaluate(() => {
    const actions = document.querySelector<HTMLElement>('[data-testid="query-actions"]')!;
    const header = document.querySelector<HTMLElement>('[data-testid="data-table"] thead')!;
    const emptyRow = document.querySelector<HTMLElement>('[data-testid="table-empty"]')!;
    const actionsBox = actions.getBoundingClientRect();
    const headerBox = header.getBoundingClientRect();
    const emptyBox = emptyRow.getBoundingClientRect();
    return {
      actionsBeforeHeader: actionsBox.bottom <= headerBox.top,
      headerBeforeEmpty: headerBox.bottom <= emptyBox.top,
      pageHasNoHorizontalScroll: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    };
  });
  expect(geometry).toEqual({
    actionsBeforeHeader: true,
    headerBeforeEmpty: true,
    pageHasNoHorizontalScroll: true,
  });
});
