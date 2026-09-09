import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

function template(overrides: Record<string, unknown> = {}) {
  return {
    id: 8101,
    tenantId: 7,
    templateCode: 'TPL-OK',
    templateName: '登录验证码',
    content: '您的验证码是 ${code}',
    templateType: 'VERIFICATION',
    signatureId: 9101,
    paramCheckRule: 'code:digits(4-8)',
    description: null,
    variableNames: ['code'],
    versionNo: 1,
    previousTemplateId: null,
    auditStatus: 'APPROVED',
    auditComment: null,
    auditTime: null,
    createdAt: '2026-09-09T00:00:00',
    history: [],
    ...overrides,
  };
}

function signature(overrides: Record<string, unknown> = {}) {
  return {
    id: 9101,
    tenantId: 7,
    signCode: 'SIG-OK',
    signContent: '优创云',
    signType: 'ENTERPRISE',
    usageType: 'SELF',
    riskLevel: 'LOW',
    evidenceRef: null,
    applicantName: null,
    auditStatus: 'APPROVED',
    auditComment: null,
    auditTime: null,
    createdAt: '2026-09-09T00:00:00',
    history: [],
    ...overrides,
  };
}

async function mockTenantSendApis(page: Page, options: { networkFirst?: boolean } = {}) {
  let networkFirst = Boolean(options.networkFirst);
  await page.route('**/api/v1/console/tenant/templates', (route: Route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([template(), template({ id: 8102, templateName: '未审核模板', auditStatus: 'PENDING' })])),
  }));
  await page.route('**/api/v1/console/trial-prepaid/tenants/7/overview', (route: Route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse({ trialStatus: 'ACTIVE', remainingQuota: 100, balance: '10.00' })),
  }));
  await page.route('**/api/v1/console/tenant/signatures', (route: Route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse([signature()])),
  }));
  await page.route('**/api/v1/console/tenant/templates/8101/preview', (route: Route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify(apiResponse({ renderedContent: '【优创云】您的验证码是 2468' })),
  }));
  await page.route('**/api/v1/console/tenant/send', async (route: Route) => {
    expect(route.request().method()).toBe('POST');
    const payload = route.request().postDataJSON();
    expect(payload).toEqual(expect.objectContaining({
      phoneNumber: '13800138000',
      templateId: '8101',
      signId: '9101',
      templateParams: expect.objectContaining({ code: expect.any(String) }),
    }));
    expect(payload.submitId).toMatch(/^CONSOLE-.+-0$/);
    if (networkFirst) {
      networkFirst = false;
      await route.abort('internetdisconnected');
      return;
    }
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(apiResponse({ messageId: `MSG-${payload.submitId}`, status: 'PENDING' })),
    });
  });
}

test.describe('Phase 26 tenant console send', () => {
  test('pw-p26-tenant-send-page C-P26-TENANT-SEND-PAGE OBL-F-6-10-A', async ({ page }) => {
    await mockTenantSendApis(page);
    await loginAs(page, 'TENANT_USER');
    await page.goto('/tenant/send');
    await expect(page.getByTestId('tenant-tenant-console-send-page')).toBeVisible();
    await expect(page.getByTestId('tenant-tenant-console-send-template')).toHaveValue('8101');
    await expect(page.getByText('未审核模板')).toHaveCount(0);
    await page.getByTestId('tenant-tenant-console-send-variable-code').fill('2468');
    await page.getByRole('button', { name: '生成预览' }).click();
    await expect(page.getByTestId('tenant-tenant-console-send-preview')).toContainText('【优创云】您的验证码是 2468');
  });

  test('pw-p26-tenant-send-submit C-P26-TENANT-SEND-SUBMIT OBL-F-6-10-B', async ({ page }) => {
    await mockTenantSendApis(page);
    await loginAs(page, 'TENANT_USER');
    await page.goto('/tenant/send');
    await page.getByTestId('tenant-tenant-console-send-variable-code').fill('2468');
    await page.getByTestId('tenant-tenant-console-send-submit').click();
    await expect(page.getByRole('status')).toContainText('已提交 1 条');
  });

  test('pw-p26-network-retry C-P26-NETWORK-RETRY OBL-EDGE-NETWORK-TIMEOUT', async ({ page }) => {
    await mockTenantSendApis(page, { networkFirst: true });
    await loginAs(page, 'TENANT_USER');
    await page.goto('/tenant/send');
    await page.getByTestId('tenant-tenant-console-send-variable-code').fill('2468');
    await page.getByTestId('tenant-tenant-console-send-submit').click();
    await expect(page.getByText('网络异常，请检查网络后重试')).toBeVisible();
    await page.getByTestId('shared-tenant-console-send-network-error-retry').click();
    await expect(page.getByRole('status')).toContainText('已提交 1 条');
  });
});
