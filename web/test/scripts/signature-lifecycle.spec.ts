import { expect, test, type Page } from '@playwright/test';

test.use({ viewport: { width: 1440, height: 900 } });
test.describe.configure({ mode: 'serial' });

const signature = {
  id: 1201,
  tenantId: 42,
  signCode: 'SGN42',
  signContent: '优创硕安',
  signType: 'TRADEMARK',
  usageType: 'SELF',
  riskLevel: 'HIGH',
  evidenceRef: 'pobj-proof-1',
  applicantName: '申请人A',
  auditStatus: 'PENDING',
  auditComment: '',
  auditTime: null,
  createdAt: '2026-09-08T00:00:00',
  history: [{ eventType: 'SUBMITTED', actor: 'tenant:42', opinion: '提交申请', riskLevel: 'HIGH', createdAt: '2026-09-08T00:00:00' }],
};

type FilingFixture = {
  signatureId: number;
  channelId: number;
  channelName: string;
  protocol: string;
  operator: string;
  status: string;
  providerRequestId: string | null;
  resultMessage: string | null;
  attemptCount: number;
  channelEligible: boolean;
  channelEligibilityReason: string;
};

const filings: FilingFixture[] = [
  { signatureId: 1201, channelId: 10, channelName: 'cmpp-main', protocol: 'CMPP', operator: 'MOBILE', status: 'NONE', providerRequestId: null, resultMessage: null, attemptCount: 0, channelEligible: true, channelEligibilityReason: 'ELIGIBLE' },
  { signatureId: 1201, channelId: 11, channelName: 'cmpp-paused', protocol: 'CMPP', operator: 'MOBILE', status: 'REGISTERED', providerRequestId: 'filing-1201-11-1', resultMessage: 'ok', attemptCount: 1, channelEligible: false, channelEligibilityReason: 'STATUS_PAUSED' },
];

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  const header = encode({ alg: 'none', typ: 'JWT' });
  const payload = encode({ sub: subject, jti: `p12-${subject}`, exp: 4102444800 });
  return `${header}.${payload}.signature`;
}

async function session(page: Page, userType: 'TENANT_ADMIN' | 'OPERATOR', tenantId: number | null) {
  await page.addInitScript(({ userType, tenantId, accessToken }) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify({ accessToken, userType, tenantId }));
  }, { userType, tenantId, accessToken: token(userType === 'TENANT_ADMIN' ? '42' : '7') });
}

async function routeApi(page: Page) {
  let localFilings = filings.map((filing) => ({ ...filing }));
  await page.route('**/api/v1/console/tenant/signatures', async (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      await route.fulfill({ json: api({ ...signature, id: 1202, signContent: body.signContent, signType: body.signType, evidenceRef: body.evidenceRef }) });
      return;
    }
    await route.fulfill({ json: api([signature]) });
  });
  await page.route('**/api/v1/console/tenant/signatures/1201/usable-channels', async (route) => {
    await route.fulfill({ json: api([]) });
  });
  await page.route('**/api/v1/console/signatures/review**', async (route) => {
    await route.fulfill({ json: api({ summary: { total: 1, pending: 1, approved: 0, rejected: 0, supplementRequired: 0, highRisk: 1 }, items: [signature] }) });
  });
  await page.route('**/api/v1/console/signatures/1201/decisions', async (route) => {
    await route.fulfill({ json: api({ ...signature, auditStatus: 'APPROVED', auditComment: '材料完整，同意启用' }) });
  });
  await page.route('**/api/v1/console/signatures/1201/filings', async (route) => {
    await route.fulfill({ json: api(localFilings) });
  });
  await page.route('**/api/v1/console/signatures/1201/filings/10/request', async (route) => {
    const current = localFilings.find((filing) => filing.channelId === 10) ?? filings[0];
    const attemptCount = current.status === 'FAILED' ? current.attemptCount + 1 : Math.max(current.attemptCount, 1);
    const updated = { ...current, status: 'REGISTERING', providerRequestId: `filing-1201-10-${attemptCount}`, attemptCount };
    localFilings = localFilings.map((filing) => filing.channelId === 10 ? updated : filing);
    await route.fulfill({ json: api(updated) });
  });
  await page.route('**/api/v1/console/signatures/1201/filings/10/result', async (route) => {
    const body = route.request().postDataJSON();
    const current = localFilings.find((filing) => filing.channelId === 10) ?? filings[0];
    const updated = { ...current, status: body.status, resultMessage: body.resultMessage };
    localFilings = localFilings.map((filing) => filing.channelId === 10 ? updated : filing);
    await route.fulfill({ json: api(updated) });
  });
}

function api(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-08T00:00:00Z', traceId: 'p12-chrome' };
}

test('pw-p12-signature-application pw-p12-signature-submit C-P12-APPLICATION-COMPLETE C-P12-APPLICATION-PENDING OBL-F-3-1-A OBL-F-3-1-B', async ({ page }) => {
  await session(page, 'TENANT_ADMIN', 42);
  await routeApi(page);
  await page.goto('/tenant/signatures');
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-page')).toBeVisible();
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-application-dialog')).toHaveCount(0);
  await page.getByTestId('tenant-signature-lifecycle-signatures-application-open').click();
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-application-form')).toBeVisible();
  await expect(page.getByTestId('tenant-signature-lifecycle-signatures-history')).toContainText('SUBMITTED');
  await page.getByLabel('签名内容').fill('新商标');
  await page.getByLabel('签名类型').selectOption('TRADEMARK');
  await page.getByLabel('证明材料').fill('pobj-proof-2');
  await page.getByTestId('tenant-signature-lifecycle-signatures-application-submit').click();
  await expect(page.getByRole('status')).toContainText('签名申请已提交');
});

test('pw-p12-usable-channels C-P12-USABLE-CHANNELS OBL-F-3-3-C', async ({ page }) => {
  await session(page, 'TENANT_ADMIN', 42);
  await routeApi(page);
  await page.goto('/tenant/signatures');
  await page.getByTestId('tenant-signature-lifecycle-signatures-usable-channels').click();
  await expect(page.getByText('暂无可用通道')).toBeVisible();
});

test('pw-p12-review-stats pw-p12-review-filters pw-p12-review-decision C-P12-REVIEW-STATS C-P12-REVIEW-FILTERS C-P12-REVIEW-DECISION OBL-F-3-2-A OBL-F-3-2-B OBL-F-3-2-C', async ({ page }) => {
  await session(page, 'OPERATOR', null);
  await routeApi(page);
  await page.goto('/admin/signatures/review');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-stats')).toContainText('待审核 1');
  await page.getByTestId('admin-signature-lifecycle-signature-review-filters').fill('优创');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-review-row')).toContainText('pobj-proof-1');
  await page.getByTestId('admin-signature-lifecycle-signature-review-decision-open').click();
  await page.getByTestId('admin-signature-lifecycle-signature-review-decision-status').selectOption('SUPPLEMENT_REQUIRED');
  await page.getByTestId('admin-signature-lifecycle-signature-review-decision-opinion').fill('材料完整，同意启用');
  await page.getByTestId('admin-signature-lifecycle-signature-review-decision').click();
  await expect(page.getByRole('status')).toContainText('审核结果已保存');
});

test('pw-p12-filing-matrix pw-p12-filing-retry C-P12-FILING-MATRIX C-P12-FILING-RETRY OBL-F-3-3-A OBL-F-3-3-B', async ({ page }) => {
  await session(page, 'OPERATOR', null);
  await routeApi(page);
  await page.goto('/admin/signatures/review');
  await page.getByTestId('admin-signature-lifecycle-signature-filing-matrix-open').click();
  await expect(page.getByTestId('admin-signature-lifecycle-signature-filing-matrix')).toContainText('cmpp-main');
  await page.getByTestId('admin-signature-lifecycle-signature-filing-retry').click();
  await expect(page.getByRole('status')).toContainText('报备请求已提交');
  await page.getByTestId('admin-signature-lifecycle-signature-filing-result-message').fill('carrier accepted');
  await page.getByTestId('admin-signature-lifecycle-signature-filing-result-status').selectOption('FAILED');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-filing-result-failed')).toBeVisible();
  await page.getByTestId('admin-signature-lifecycle-signature-filing-result-failed').click();
  await expect(page.getByRole('status')).toContainText('报备结果已记录');
  await page.getByTestId('admin-signature-lifecycle-signature-filing-retry').click();
  await expect(page.getByRole('status')).toContainText('报备请求已提交');
  await page.getByTestId('admin-signature-lifecycle-signature-filing-result-status').selectOption('REGISTERED');
  await expect(page.getByTestId('admin-signature-lifecycle-signature-filing-result-registered')).toBeVisible();
  await page.getByTestId('admin-signature-lifecycle-signature-filing-result-registered').click();
  await expect(page.getByRole('status')).toContainText('报备结果已记录');
});
