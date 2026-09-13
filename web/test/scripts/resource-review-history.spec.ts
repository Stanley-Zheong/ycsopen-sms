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
  await page.getByTestId('admin-resource-review-history-review-filters').getByLabel('资源类型').selectOption('SIGNATURE');
  await page.getByTestId('admin-resource-review-history-review-filters').getByLabel('开始时间').fill('2026-09-09T00:00');
  await page.getByTestId('admin-resource-review-history-review-filters').getByLabel('结束时间').fill('2026-09-09T23:59');
  await expect(page.getByTestId('admin-resource-review-history-review-table')).toContainText('SIGNATURE:1');
  await expect(page.getByTestId('admin-resource-review-history-review-pagination')).toContainText('第 1 页，每页 50 条');
  await expect(page.getByTestId('admin-resource-review-history-review-page-prev')).toBeDisabled();
  await expect(page.getByTestId('admin-resource-review-history-review-page-next')).toBeDisabled();
  await page.getByTestId('admin-resource-review-history-review-detail-open').nth(1).click();
  await expect(page.getByTestId('admin-resource-review-history-review-detail-drawer')).toContainText('TEMPLATE:1');
});
