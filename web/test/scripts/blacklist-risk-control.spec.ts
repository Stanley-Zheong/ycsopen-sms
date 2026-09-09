import { test, expect } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p16-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'trace-p16' };
}

const entry = {
  id: 1,
  tenantId: 42,
  maskedMobile: '139****0001',
  mobileRef: 'ref-13900000001',
  listType: 'BLACK',
  reason: '投诉风险',
  source: 'MANUAL',
  status: 'ACTIVE',
  createdBy: '7',
  createdAt: '2026-09-09T00:00:00',
  expiresAt: null,
};

const provider = {
  id: 1,
  providerName: 'local-risk',
  providerUrl: 'https://risk.example.test',
  credentialRef: 'secret-ref',
  checkLevel: 'ADVANCED',
  thresholdScore: 80,
  timeoutMs: 500,
  fallbackPolicy: 'CACHE',
  status: 'ACTIVE',
  cacheTtlSeconds: 300,
  createdBy: '7',
  createdAt: '2026-09-09T00:00:00',
};

const decision = {
  id: 9,
  requestId: 'risk-1',
  tenantId: 42,
  mobileRef: 'opaque-risk-1',
  sourceCategory: 'THIRD_PARTY_RISK',
  riskResult: 'BLOCK',
  traceReason: '第三方风险名单命中，发送任务未创建且未计费',
  taskCreated: false,
  charged: false,
  createdAt: '2026-09-09T00:00:00',
};

const degradedDecision = {
  ...decision,
  id: 10,
  sourceCategory: 'THIRD_PARTY_DEGRADED',
  riskResult: 'DEGRADED_CACHE',
  traceReason: '第三方风险服务失败，按新鲜缓存策略降级',
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('7'), userType: 'OPERATOR', tenantId: null });
  await page.route('**/api/v1/console/account-overview', async (route) => route.fulfill({
    json: response({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['风控员'],
      permissions: [
        { code: 'blacklist:menu', resourceType: 'MENU' },
        { code: 'blacklist:read', resourceType: 'API' },
        { code: 'blacklist:write', resourceType: 'API' },
        { code: 'blacklist:import', resourceType: 'BUTTON' },
        { code: 'blacklist:export', resourceType: 'BUTTON' },
        { code: 'risk-provider:read', resourceType: 'API' },
        { code: 'risk-provider:write', resourceType: 'API' },
        { code: 'risk-analysis:read', resourceType: 'API' },
        { code: 'risk-analysis:check', resourceType: 'API' },
        { code: 'risk-analysis:appeal', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    }),
  }));
  await page.route('**/api/v1/console/risk/check', async (route) => {
    const body = route.request().postDataJSON() as { forceProviderFailure?: boolean };
    await route.fulfill({ json: response(body.forceProviderFailure ? [degradedDecision] : [decision]) });
  });
  await page.route('**/api/v1/console/risk/appeals', async (route) => route.fulfill({
    json: response({ id: 1, decisionId: 9, originalResult: 'BLOCK', appealResult: 'FALSE_POSITIVE' }),
  }));
  await page.route('**/api/v1/console/risk/blacklist/import', async (route) => route.fulfill({
    json: response({ success: 1, failed: 1, errors: ['bad-mobile'] }),
  }));
  await page.route('**/api/v1/console/risk/blacklist/export-request', async (route) => route.fulfill({
    json: response({ requestId: 'export-1', matchedRows: 1, status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/risk/blacklist/export-request?**', async (route) => route.fulfill({
    json: response({ requestId: 'export-1', matchedRows: 1, status: 'REQUESTED' }),
  }));
  await page.route('**/api/v1/console/risk/provider', async (route) => {
    if (route.request().method() === 'POST') await route.fulfill({ json: response(provider) });
    else await route.fulfill({ json: response([provider]) });
  });
  await page.route('**/api/v1/console/risk/analytics', async (route) => route.fulfill({
    json: response({ total: 1, blocked: 1, allowed: 0, systemHits: 0, tenantHits: 0, providerHits: 1, degraded: 0, appeals: 0 }),
  }));
  await page.route('**/api/v1/console/risk/blacklist', async (route) => {
    if (route.request().method() === 'POST') await route.fulfill({ json: response(entry) });
    else await route.fulfill({ json: response([entry]) });
  });
  await page.route('**/api/v1/console/risk/blacklist?**', async (route) => route.fulfill({ json: response([entry]) }));
  await page.route('**/api/v1/console/risk/blacklist/1/disable', async (route) => route.fulfill({
    json: response({ ...entry, status: 'DISABLED' }),
  }));
});

test('pw-p16-list C-P16-LIST OBL-F-5-2-A pw-p16-import C-P16-IMPORT-EXPORT OBL-F-5-2-B pw-p16-provider C-P16-PROVIDER-CONFIG OBL-F-5-3-A pw-p16-analytics C-P16-ANALYTICS OBL-F-5-4-A pw-p16-appeal C-P16-APPEAL OBL-F-5-4-B pw-p16-degraded C-P16-RISK-OUTAGE OBL-EDGE-RISK-OUTAGE', async ({ page }) => {
  await page.goto('/admin/riskcontrol');
  await expect(page.getByTestId('admin-blacklist-risk-page')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-page')).toContainText('139****0001');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-filters')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-filter-tenant')).toHaveValue('42');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-filter-type')).toHaveValue('');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-filter-status')).toHaveValue('ACTIVE');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-form')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-tenant')).toHaveValue('42');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-mobile')).toHaveValue('13900000001');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-type')).toHaveValue('BLACK');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-source')).toHaveValue('MANUAL');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-reason')).toHaveValue('投诉风险');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-import-input')).toContainText('bad-mobile');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-table')).toContainText('MANUAL');
  await expect(page.getByTestId('admin-blacklist-risk-black-white-lists-row')).toContainText('ACTIVE');
  await page.getByTestId('admin-blacklist-risk-black-white-lists-save').click();
  await expect(page.getByTestId('admin-blacklist-risk-message')).toContainText('黑白名单已保存');
  await page.getByTestId('admin-blacklist-risk-black-white-lists-import').click();
  await expect(page.getByTestId('admin-blacklist-risk-message')).toContainText('导入完成');
  await page.getByTestId('admin-blacklist-risk-black-white-lists-export').click();
  await expect(page.getByTestId('admin-blacklist-risk-message')).toContainText('导出请求已登记');
  await page.getByTestId('admin-blacklist-risk-black-white-lists-disable').click();
  await expect(page.getByTestId('admin-blacklist-risk-message')).toContainText('黑白名单记录已移除');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-page')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-name')).toHaveValue('local-risk');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-url')).toHaveValue('https://risk.example.test');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-credential')).toHaveValue('secret-ref');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-level')).toHaveValue('ADVANCED');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-threshold')).toHaveValue('80');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-timeout')).toHaveValue('500');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-fallback')).toHaveValue('CACHE');
  await page.getByTestId('admin-blacklist-risk-risk-provider-save').click();
  await expect(page.getByTestId('admin-blacklist-risk-message')).toContainText('第三方风控配置已保存');
  await expect(page.getByTestId('admin-blacklist-risk-risk-provider-list')).toContainText('local-risk');
  await expect(page.getByTestId('admin-blacklist-risk-intercept-analytics-page')).toBeVisible();
  await expect(page.getByTestId('admin-blacklist-risk-intercept-analytics-cards')).toContainText('第三方 1');
  await expect(page.getByTestId('admin-blacklist-risk-intercept-check-tenant')).toHaveValue('42');
  await expect(page.getByTestId('admin-blacklist-risk-intercept-check-input')).toContainText('13900009999');
  await page.getByTestId('admin-blacklist-risk-intercept-check-run').click();
  await expect(page.getByTestId('admin-blacklist-risk-intercept-decisions-table')).toContainText('THIRD_PARTY_RISK');
  await expect(page.getByTestId('admin-blacklist-risk-intercept-decision-row')).toContainText('BLOCK');
  await expect(page.getByTestId('admin-blacklist-risk-intercept-decisions-table')).toContainText('未创建');
  await page.getByTestId('admin-blacklist-risk-intercept-appeal').click();
  await expect(page.getByTestId('admin-blacklist-risk-message')).toContainText('误判申诉已记录');
  await page.getByTestId('admin-blacklist-risk-risk-provider-degraded').click();
  await expect(page.getByTestId('admin-blacklist-risk-intercept-decisions-table')).toContainText('THIRD_PARTY_DEGRADED');
  await expect(page.getByTestId('admin-blacklist-risk-intercept-decisions-table')).toContainText('DEGRADED_CACHE');
});
