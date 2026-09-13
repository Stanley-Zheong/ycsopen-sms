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

async function mockComplaintApis(page: Page, complaintRows = [caseRow]) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/complaints', async (route: Route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual(expect.objectContaining({ source: 'REGULATOR', tenantId: 7 }));
      await route.fulfill({ json: apiResponse(caseRow) });
      return;
    }
    await route.fulfill({ json: apiResponse(complaintRows) });
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
  test('pw-issue-78-complaints-layout C-ISSUE-78-COMPLAINTS-LAYOUT OBL-ISSUE-78-COMPLAINTS-LAYOUT keeps complaint sections, controls, actions and table states structurally aligned', async ({ page }, testInfo) => {
    await mockComplaintApis(page, []);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');

    const cardIds = [
      'admin-complaint-case-complaints-intake-card',
      'admin-complaint-case-complaints-attribution-card',
      'admin-complaint-case-complaints-evidence-card',
      'admin-complaint-case-complaints-list-card',
    ];
    const cardTitles = ['投诉登记', '归因与要求', '处理证据', '投诉列表'];
    const cardBoxes = [];
    for (const [index, cardId] of cardIds.entries()) {
      const card = page.getByTestId(cardId);
      await expect(card).toHaveClass(/\bcard\b/);
      await expect(card.getByRole('heading', { name: cardTitles[index], exact: true })).toBeVisible();
      cardBoxes.push(await card.boundingBox());
    }
    for (let index = 1; index < cardBoxes.length; index += 1) {
      expect(cardBoxes[index - 1]).not.toBeNull();
      expect(cardBoxes[index]).not.toBeNull();
      expect(cardBoxes[index]!.y).toBeGreaterThanOrEqual(cardBoxes[index - 1]!.y + cardBoxes[index - 1]!.height);
    }

    const controls = page.locator('[data-testid$="-card"] input, [data-testid$="-card"] select');
    expect(await controls.count()).toBeGreaterThan(0);
    for (const control of await controls.all()) {
      const box = await control.boundingBox();
      expect(box).not.toBeNull();
      expect(box!.height).toBeGreaterThanOrEqual(38);
      expect(box!.height).toBeLessThanOrEqual(42);
    }

    const requirementBox = await page.getByTestId('admin-complaint-case-complaints-requirement').boundingBox();
    const submitBox = await page.getByTestId('form-submit').boundingBox();
    await expect(page.getByTestId('form-actions')).toContainText('登记投诉');
    expect(requirementBox).not.toBeNull();
    expect(submitBox).not.toBeNull();
    expect(Math.abs(requirementBox!.y - submitBox!.y)).toBeLessThanOrEqual(2);
    expect(Math.abs(requirementBox!.y + requirementBox!.height - (submitBox!.y + submitBox!.height))).toBeLessThanOrEqual(2);

    const table = page.getByTestId('data-table');
    const headers = table.locator('th');
    await expect(headers).toHaveText(['来源', '摘要', '归因质量', '状态', '处置资源', '要求', '动作']);
    for (const header of await headers.all()) {
      await expect(header).toBeVisible();
      const dimensions = await header.evaluate((element) => ({
        clientWidth: element.clientWidth,
        scrollWidth: element.scrollWidth,
      }));
      expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
    }
    await expect(page.getByTestId('table-empty')).toContainText('暂无投诉记录');
    const viewportMetrics = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));
    expect(viewportMetrics.scrollWidth).toBeLessThanOrEqual(viewportMetrics.clientWidth);

    const controlBoxes = await page.locator('[data-testid$="-card"] input, [data-testid$="-card"] select, [data-testid="form-submit"]').evaluateAll((elements) => (
      elements.map((element) => {
        const rect = element.getBoundingClientRect();
        return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
      })
    ));
    for (let leftIndex = 0; leftIndex < controlBoxes.length; leftIndex += 1) {
      for (let rightIndex = leftIndex + 1; rightIndex < controlBoxes.length; rightIndex += 1) {
        const left = controlBoxes[leftIndex];
        const right = controlBoxes[rightIndex];
        const overlaps = left.left < right.right && left.right > right.left && left.top < right.bottom && left.bottom > right.top;
        expect(overlaps).toBe(false);
      }
    }
    await page.screenshot({ path: testInfo.outputPath('complaints-layout.png'), fullPage: true });

    const populatedRow = {
      ...caseRow,
      source: 'USER_REPORT',
      summary: '用户通过监管渠道提交的长文本投诉摘要，用于验证窄列中的内容不会挤压相邻列',
      status: 'PROCESSING',
      attributionQuality: 'UNKNOWN',
      requirement: '要求立即停止相关发送并在二十四小时内提供完整处置反馈与复核结论',
    };
    await page.unroute('**/api/v1/console/complaints');
    await page.route('**/api/v1/console/complaints', (route: Route) => route.fulfill({ json: apiResponse([populatedRow]) }));
    await page.reload();

    const row = page.getByTestId('admin-complaint-case-complaints-row');
    await expect(row.locator('td').nth(0).locator('.complaint-table-truncate')).toHaveAttribute('title', 'USER_REPORT');
    await expect(page.getByTestId('admin-complaint-case-complaints-attribution-quality').locator('.complaint-table-truncate')).toHaveAttribute('title', 'UNKNOWN');
    await expect(page.getByTestId('admin-complaint-case-complaints-state-action').locator('.complaint-table-truncate')).toHaveAttribute('title', 'PROCESSING');

    const truncatedCellMetrics = await row.locator('.complaint-table-truncate').evaluateAll((elements) => (
      elements.map((element) => {
        const elementRect = element.getBoundingClientRect();
        const cellRect = element.closest('td')!.getBoundingClientRect();
        const style = getComputedStyle(element);
        return {
          elementLeft: elementRect.left,
          elementRight: elementRect.right,
          cellLeft: cellRect.left,
          cellRight: cellRect.right,
          overflow: style.overflow,
          textOverflow: style.textOverflow,
          whiteSpace: style.whiteSpace,
        };
      })
    ));
    expect(truncatedCellMetrics).toHaveLength(6);
    for (const metric of truncatedCellMetrics) {
      expect(metric.elementLeft).toBeGreaterThanOrEqual(metric.cellLeft);
      expect(metric.elementRight).toBeLessThanOrEqual(metric.cellRight);
      expect(metric.overflow).toBe('hidden');
      expect(metric.textOverflow).toBe('ellipsis');
      expect(metric.whiteSpace).toBe('nowrap');
    }

    const populatedViewportMetrics = await page.evaluate(() => ({
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }));
    expect(populatedViewportMetrics.scrollWidth).toBeLessThanOrEqual(populatedViewportMetrics.clientWidth);

    const actionBoxes = await row.locator('.complaint-table-actions button').evaluateAll((elements) => (
      elements.map((element) => {
        const rect = element.getBoundingClientRect();
        return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
      })
    ));
    for (let leftIndex = 0; leftIndex < actionBoxes.length; leftIndex += 1) {
      for (let rightIndex = leftIndex + 1; rightIndex < actionBoxes.length; rightIndex += 1) {
        const left = actionBoxes[leftIndex];
        const right = actionBoxes[rightIndex];
        const overlaps = left.left < right.right && left.right > right.left && left.top < right.bottom && left.bottom > right.top;
        expect(overlaps).toBe(false);
      }
    }
    await page.screenshot({ path: testInfo.outputPath('complaints-layout-populated.png'), fullPage: true });
  });

  test('pw-p41-intake C-P41-INTAKE OBL-F-9-1-A pw-p41-attribution C-P41-ATTRIBUTION OBL-F-9-1-B', async ({ page }) => {
    await mockComplaintApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('admin-complaint-case-complaints-page')).toBeVisible();
    await expect(page.getByTestId('admin-complaint-case-complaints-attribution-quality')).toContainText('COMPLETE');
    await page.getByTestId('form-submit').click();
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
