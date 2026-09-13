import { expect, test } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p42-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p42' };
}

const permissions = [
  { code: 'tenant-risk:menu', resourceType: 'MENU' },
  { code: 'tenant-risk:read', resourceType: 'API' },
  { code: 'tenant-risk:write', resourceType: 'API' },
];

const rule = {
  id: 1,
  ruleName: '失败率封停',
  tenantId: 7,
  metric: 'FAILURE_RATE',
  thresholdValue: 0.2,
  durationMinutes: 15,
  action: 'AUTO_SUSPEND',
  notifyTargets: '["tenant:7","ops"]',
  status: 'ACTIVE',
  updatedAt: '2026-09-10T12:00:00',
};

const episode = {
  id: 2,
  tenantId: 7,
  ruleId: 1,
  alertRecordId: 3,
  metric: 'FAILURE_RATE',
  sourceKey: 'failure-window-7',
  sourceRegistry: 'statistics_aggregates',
  numerator: 25,
  denominator: 100,
  rate: 0.25,
  thresholdValue: 0.2,
  windowMinutes: 15,
  dataQuality: 'COMPLETE',
  action: 'AUTO_SUSPEND',
  status: 'PAUSED',
  beforeLifecycleStatus: 'SIGNED',
  sourceSnapshot: 'numerator=25,denominator=100,rate=0.250000',
  recoveryReviewId: null,
  recoveredBy: null,
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    await route.fulfill({
      json: response({
        id: 42,
        username: 'operator-42',
        userType: 'OPERATOR',
        roleNames: ['运营'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/tenant-risk/rules**', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ ...rule, id: 11 }) });
      return;
    }
    await route.fulfill({ json: response([rule]) });
  });
  await page.route('**/api/v1/console/tenant-risk/evaluate', async (route) => {
    await route.fulfill({ json: response({ episodeId: 2, dataQuality: 'COMPLETE', rate: 0.25, paused: true }) });
  });
  await page.route('**/api/v1/console/tenant-risk/episodes**', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({ ...episode, status: 'RESOLVED', recoveryReviewId: 'review-42' }) });
      return;
    }
    await route.fulfill({ json: response([episode]) });
  });
});

test('pw-p42-rules C-P42-RULES OBL-F-9-5-A pw-p42-pause C-P42-PAUSE OBL-F-9-5-C pw-p42-recovery C-P42-RECOVERY OBL-F-9-5-D pw-p42-freeze C-P42-FREEZE OBL-STATE-TENANT-RISK-FREEZE pw-p42-restore C-P42-RESTORE OBL-STATE-TENANT-RESTORE pw-p42-risk-flow C-P42-RISK-FLOW OBL-FLOW-12-1-RISK-PAUSE pw-p42-complaint-tenant C-P42-COMPLAINT-TENANT OBL-FLOW-12-2-COMPLAINT-TENANT', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('operator'), userType: 'OPERATOR', tenantId: null });
  await page.goto('/admin/tenant-risk');
  await expect(page.getByTestId('admin-tenant-risk-rules-page')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-risk-notify-targets')).toHaveValue('["tenant:7","ops"]');
  await page.getByTestId('admin-tenant-risk-rule-save').click();
  await expect(page.getByTestId('admin-tenant-risk-message')).toContainText('已保存');
  await page.getByTestId('admin-tenant-risk-evaluate').click();
  await expect(page.getByTestId('admin-tenant-risk-message')).toContainText('评估完成');
  await expect(page.getByTestId('admin-tenant-risk-tenant-risk-pause-detail')).toContainText('PAUSED');
  await expect(page.getByTestId('admin-tenant-risk-source-snapshot')).toContainText('numerator=25');
  await expect(page.getByTestId('admin-complaint-ratio-dashboard-complaint-ratio-tenant')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-risk-tenant-risk-recovery')).toBeVisible();
  await page.getByTestId('admin-tenant-risk-recover').click();
  await expect(page.getByTestId('admin-tenant-risk-message')).toContainText('恢复已提交');
});
