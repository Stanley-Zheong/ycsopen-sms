import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p21-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p21' };
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('21'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 21,
      username: 'operator-21',
      userType: 'OPERATOR',
      roleNames: ['路由管理员'],
      permissions: [
        { code: 'routing-policy:menu', resourceType: 'MENU' },
        { code: 'routing-policy:read', resourceType: 'API' },
        { code: 'routing-policy:write', resourceType: 'API' },
        { code: 'routing-policy:import', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/routing-policy/versions', async (route) => route.fulfill({
    json: response([{ id: 1, versionNo: 'RP20260909', status: 'ACTIVE', sourceName: '运营策略', effectiveAt: '2026-09-09T00:00:00', actor: 'operator', createdAt: '2026-09-09T00:00:00' }]),
  }));
  await page.route('**/api/v1/console/routing-policy/rules', async (route) => route.fulfill({
    json: response([{ id: 1, versionNo: 'RP20260909', priority: 10, conditionType: 'CARRIER', conditionValue: 'MOBILE', targetType: 'CHANNEL', targetRef: 'CH_MAIN', weight: 100, status: 'ACTIVE' }]),
  }));
  await page.route('**/api/v1/console/routing-policy/circuits', async (route) => route.fulfill({
    json: response([{ id: 1, channelCode: 'CH_MAIN', status: 'CLOSED', failureCount: 1, successCount: 5, latencyMs: 120, history: 'init -> success:CLOSED' }]),
  }));
  await page.route('**/api/v1/console/routing-policy/import', async (route) => route.fulfill({
    json: response({ versionNo: 'RP20260909', imported: 1, status: 'ACTIVE' }),
  }));
  await page.route('**/api/v1/console/routing-policy/simulate', async (route) => route.fulfill({
    json: response({ versionNo: 'RP20260909', matchedRuleId: 1, targetType: 'CHANNEL', targetRef: 'CH_MAIN', explanation: 'rule 1 matched version RP20260909 target CHANNEL/CH_MAIN', circuitStatus: 'CLOSED', retryPolicy: { normalizedCategory: 'FAILURE', retryable: true, delaySeconds: 30, maxAttempts: 3, status: 'DEFAULT' } }),
  }));
  await page.route('**/api/v1/console/routing-policy/circuits/CH_MAIN/record?**', async (route) => route.fulfill({
    json: response({ id: 1, channelCode: 'CH_MAIN', status: 'OPEN', failureCount: 3, successCount: 5, latencyMs: 900, history: 'failure:OPEN' }),
  }));
  await page.route('**/api/v1/console/routing-policy/retry', async (route) => route.fulfill({
    json: response({ normalizedCategory: 'FAILURE', retryable: true, delaySeconds: 30, maxAttempts: 3, status: 'ACTIVE' }),
  }));
});

test('pw-p21-author C-P21-ROUTE-AUTHOR OBL-F-5-8-A pw-p21-simulator C-P21-ROUTE-SIMULATOR OBL-F-5-8-B pw-p21-circuit C-P21-CIRCUIT OBL-F-5-9-B pw-p21-retry C-P21-RETRY OBL-F-5-10-A pw-p21-flow C-P21-ROUTING-FLOW OBL-FLOW-12-2-ROUTING', async ({ page }) => {
  await page.goto('/admin/routing-policy');
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-page')).toBeVisible();
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-row')).toContainText('CH_MAIN');
  await page.getByTestId('admin-routing-circuit-routing-policy-import').click();
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-message')).toContainText('已导入');
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-simulator')).toBeVisible();
  await page.getByTestId('admin-routing-circuit-routing-policy-simulate').click();
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-simulation-result')).toContainText('CH_MAIN');
  await expect(page.getByTestId('admin-routing-circuit-routing-circuit-state')).toContainText('CLOSED');
  await page.getByTestId('admin-routing-circuit-routing-circuit-record').click();
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-message')).toContainText('熔断状态已记录');
  await expect(page.getByTestId('admin-routing-circuit-routing-retry-rules')).toBeVisible();
  await page.getByTestId('admin-routing-circuit-routing-retry-save').click();
  await expect(page.getByTestId('admin-routing-circuit-routing-policy-message')).toContainText('重试策略已保存');
});
