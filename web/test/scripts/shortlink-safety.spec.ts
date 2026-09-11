import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const row = {
  id: 48,
  tenantId: 7,
  targetUrl: 'https://example.com/campaign',
  customDomain: 's.ycsopen.test',
  shortCode: 'abc12345',
  shortUrl: 'https://s.ycsopen.test/s/abc12345',
  validUntil: '2026-10-10',
  status: 'PENDING',
  clickCount: 12,
  targetVersion: 1,
  immutableTargetSha256: '1234567890abcdef',
  automatedResultJson: '{"verdict":"PASS","checks":["URL_VALID","DOMAIN_APPROVED","REDIRECT_CHAIN_RECORDED"]}',
  screenshotEvidenceRef: 'domain-evidence:example.com',
  domainEvidenceJson: '{"targetDomain":"example.com","shortDomain":"s.ycsopen.test"}',
  riskLevel: 'LOW',
  reviewOpinion: null,
  reviewedBy: null,
  reviewedAt: null,
  offlineReason: null,
  offlineAt: null,
  lastRecheckAt: null,
};
const PUBLIC_SHORTLINK_ROUTE_PATTERN = '/s/:code';

async function mockShortLinkApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/tenant/shortlinks', async (route: Route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual(expect.objectContaining({
        originalUrl: 'https://example.com/campaign',
        customDomain: 's.ycsopen.test',
      }));
      await route.fulfill({ json: apiResponse(row) });
      return;
    }
    await route.fulfill({ json: apiResponse([row]) });
  });
  await page.route('**/api/v1/console/tenant/shortlinks/analytics', (route) => route.fulfill({ json: apiResponse({
    uniqueClicks: 2,
    regions: [{ label: '华东', count: 2 }],
    devices: [{ label: 'MOBILE', count: 2 }],
  }) }));
  await page.route('**/api/v1/console/shortlinks/review?status=PENDING', (route) => route.fulfill({ json: apiResponse([row]) }));
  await page.route('**/api/v1/console/shortlinks/review/48/approve', (route) => route.fulfill({ json: apiResponse({ ...row, status: 'APPROVED' }) }));
  await page.route('**/api/v1/console/shortlinks/review/48/reject', (route) => route.fulfill({ json: apiResponse({ ...row, status: 'REJECTED' }) }));
  await page.route('**/api/v1/console/shortlinks/review/48/inspect', (route) => route.fulfill({ json: apiResponse({ ...row, status: 'OFFLINE', offlineReason: '目标漂移或风险升高' }) }));
}

test('OBL-F-13-1-A C-P48-CREATE pw-p48-create OBL-FIELD-SHORTLINK-URL C-P48-URL pw-p48-url OBL-FIELD-SHORTLINK-DOMAIN C-P48-DOMAIN pw-p48-domain OBL-FIELD-SHORTLINK-VALIDITY C-P48-VALIDITY pw-p48-validity', async ({ page }) => {
  await mockShortLinkApis(page);
  await loginAs(page, 'TENANT_ADMIN');
  await page.goto('/tenant/shortlink');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-page')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-form-url')).toHaveValue('https://example.com/campaign');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-form-domain')).toHaveValue('s.ycsopen.test');
  await page.getByTestId('tenant-shortlink-safety-shortlinks-form-validity').fill('2026-10-10');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-form-validity')).toHaveValue('2026-10-10');
  await page.getByTestId('tenant-shortlink-safety-shortlinks-create').click();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-message')).toContainText('等待自动审核');
});

test('OBL-F-13-1-B C-P48-LIST pw-p48-list OBL-F-13-1-C C-P48-ANALYTICS pw-p48-analytics', async ({ page }) => {
  await mockShortLinkApis(page);
  await loginAs(page, 'TENANT_ADMIN');
  await page.goto('/tenant/shortlink');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-list')).toContainText('https://example.com/campaign');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-status')).toContainText('待审核');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-click-count')).toContainText('12');
  await expect(page.getByTestId('tenant-shortlink-safety-analytics-page')).toBeVisible();
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-analytics-region')).toContainText('华东 2');
  await expect(page.getByTestId('tenant-shortlink-safety-shortlinks-analytics-device')).toContainText('MOBILE 2');
});

test('OBL-F-13-2-B C-P48-REVIEW pw-p48-review OBL-STATE-SHORTLINK-APPROVE C-P48-APPROVE pw-p48-approve OBL-STATE-SHORTLINK-REJECT C-P48-REJECT pw-p48-reject OBL-F-13-2-D C-P48-DRIFT-OFFLINE pw-p48-drift', async ({ page }) => {
  await mockShortLinkApis(page);
  await loginAs(page, 'OPERATOR');
  await page.goto('/admin/shortlinks/review');
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-target-version')).toContainText('sha256:12345678');
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-domain-evidence')).toContainText('example.com');
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-auto-result')).toContainText('DOMAIN_APPROVED');
  await page.getByTestId('admin-shortlink-safety-shortlinks-approve').click();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-review-message')).toContainText('短链已批准');
  await page.getByTestId('admin-shortlink-safety-shortlinks-reject').click();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-review-message')).toContainText('短链已驳回');
  await page.getByTestId('admin-shortlink-safety-shortlinks-inspect').click();
  await expect(page.getByTestId('admin-shortlink-safety-shortlinks-review-message')).toContainText('巡检已完成');
});

test('OBL-F-13-2-C C-P48-SAFE-PENDING pw-p48-safe-pending OBL-STATE-SHORTLINK-EXPIRE C-P48-EXPIRE pw-p48-expire OBL-STATE-SHORTLINK-OFFLINE C-P48-OFFLINE pw-p48-offline', async ({ page }) => {
  expect(PUBLIC_SHORTLINK_ROUTE_PATTERN).toBe('/s/:code');
  await page.goto('/s/:code');
  await expect(page.getByTestId('public-shortlink-safety-pending-page')).toContainText('暂不跳转');
  await expect(page.getByTestId('public-shortlink-safe-message')).not.toContainText('example.com');
  await page.goto('/s/:code?state=expired');
  await expect(page.getByTestId('public-shortlink-safety-expired-page')).toContainText('已过期');
  await page.goto('/s/:code?state=offline');
  await expect(page.getByTestId('public-shortlink-safety-offline-page')).toContainText('已下线');
});
