import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const caseRow = {
  id: 1,
  source: 'REGULATOR',
  tenantId: 7,
  channelId: 11,
  signatureId: 8,
  templateId: 9,
  messageId: 'MSG-1',
  contentType: 'MARKETING',
  complainedMobile: '13800138000',
  summary: '监管投诉：营销短信扰民',
  status: 'PENDING',
  attributionQuality: 'COMPLETE',
  opinion: null,
  remediation: null,
  requirement: '24小时内反馈',
  acceptedAt: null,
  handledAt: null,
  closedAt: null,
  closedNote: null,
};

async function mockComplaintApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/complaints', async (route: Route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ source: 'REGULATOR', tenantId: 7 }));
      await route.fulfill({ json: apiResponse(caseRow) });
      return;
    }
    await route.fulfill({ json: apiResponse([caseRow]) });
  });
  await page.route('**/api/v1/console/complaints/1/accept', (route: Route) => route.fulfill({ json: apiResponse({ ...caseRow, status: 'PROCESSING' }) }));
  await page.route('**/api/v1/console/complaints/1/handle', (route: Route) => route.fulfill({ json: apiResponse({ ...caseRow, status: 'PROCESSED', opinion: '投诉属实', remediation: '暂停通道' }) }));
  await page.route('**/api/v1/console/complaints/1/close', (route: Route) => route.fulfill({ json: apiResponse({ ...caseRow, status: 'CLOSED', closedNote: '复核关闭' }) }));
  await page.route('**/api/v1/console/complaints/1/remediations', (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({
      disposalType: 'SUSPEND_CHANNEL',
      targetRef: 'channel:11',
      authorizedReviewId: 'review-41-1',
      reason: '投诉集中',
    }));
    return route.fulfill({ json: apiResponse({ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'APPLIED', authorizedReviewId: 'review-41-1', failureReason: null, originalComplaintId: 1 }) });
  });
  await page.route('**/api/v1/console/complaints/1/recoveries', (route: Route) => {
    expect(route.request().postDataJSON()).toEqual(expect.objectContaining({
      disposalRecordId: 2,
      authorizedReviewId: 'review-41-recovery',
      resumeCondition: '授权复核通过后恢复',
    }));
    return route.fulfill({ json: apiResponse({ id: 2, complaintId: 1, disposalType: 'SUSPEND_CHANNEL', targetRef: 'channel:11', status: 'RECOVERED', authorizedReviewId: 'review-41-recovery', failureReason: null, originalComplaintId: 1 }) });
  });
  await page.route('**/api/v1/console/complaint-analytics', (route: Route) => route.fulfill({ json: apiResponse({
    totalCount: 2,
    unknownAttributionCount: 1,
    byTenant: [{ dimension: 'tenant:7', count: 1 }],
    bySignature: [{ dimension: 'signature:8', count: 1 }],
    byContentType: [{ dimension: 'MARKETING', count: 2 }],
  }) }));
}

test.describe('Phase 41 complaint case management', () => {
  test('pw-p41-intake C-P41-INTAKE OBL-F-9-1-A pw-p41-attribution C-P41-ATTRIBUTION OBL-F-9-1-B', async ({ page }) => {
    await mockComplaintApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-page')).toBeVisible();
    await expect(page.getByTestId('admin-complaint-case-complaints-attribution-quality')).toContainText('COMPLETE');
    await page.getByTestId('admin-complaint-case-complaints-create').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('投诉案件已登记');
  });

  test('pw-p41-state C-P41-STATE OBL-F-9-2-A pw-p41-accept C-P41-ACCEPT OBL-STATE-COMPLAINT-PROCESS pw-p41-handle C-P41-HANDLE OBL-STATE-COMPLAINT-HANDLED pw-p41-close C-P41-CLOSE OBL-STATE-COMPLAINT-CLOSE', async ({ page }) => {
    await mockComplaintApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-state-action')).toContainText('PENDING');
    await page.getByTestId('admin-complaint-case-complaints-accept').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('投诉案件已接单');
    await page.getByTestId('admin-complaint-case-complaints-handle').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('投诉案件已处理');
    await page.getByTestId('admin-complaint-case-complaints-close').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('投诉案件已关闭');
  });

  test('pw-p41-remediation C-P41-REMEDIATION OBL-F-9-3-A pw-p41-recovery C-P41-RECOVERY OBL-F-9-3-B pw-p41-resource C-P41-RESOURCE-DISABLE OBL-STATE-RESOURCE-DISABLE', async ({ page }) => {
    await mockComplaintApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-resource')).toContainText('signature:8');
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeDisabled();
    await page.getByTestId('admin-complaint-case-complaints-remediation').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('处置已记录');
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeEnabled();
    await page.getByTestId('admin-complaint-case-complaints-remediation-recovery').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('恢复已记录');
  });

  test('pw-p41-analytics C-P41-ANALYTICS OBL-F-9-4-A', async ({ page }) => {
    await mockComplaintApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaint/analytics');
    await expect(page.getByTestId('admin-complaint-case-analytics-page')).toContainText('投诉趋势与分布');
    await expect(page.getByTestId('admin-complaint-case-analytics-quality')).toContainText('未知归因 1');
  });
});
