import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

type ComplaintRow = typeof baseCase;
type TimelineEvent = {
  id: number;
  complaintId: number;
  eventType: string;
  actor: string | null;
  occurredAt: string;
  fromStatus: string | null;
  toStatus: string | null;
  evidenceText: string | null;
  targetRef: string | null;
  result: string;
  reviewId: string | null;
  failureReason: string | null;
  relatedDisposalId: number | null;
};

const baseCase = {
  id: 1,
  source: 'REGULATOR',
  tenantId: 7,
  channelId: 11,
  signatureId: 8,
  templateId: 9,
  messageId: 'MSG-1',
  contentType: 'MARKETING',
  complainedMobile: '138****8000',
  summary: '监管投诉：营销短信扰民',
  status: 'PENDING',
  attributionQuality: 'COMPLETE',
  opinion: null as string | null,
  remediation: null as string | null,
  requirement: '24小时内反馈' as string | null,
  createdAt: '2026-10-06T08:00:00',
  createdBy: 'operator',
  acceptedAt: null as string | null,
  handledAt: null as string | null,
  closedAt: null as string | null,
  closedNote: null as string | null,
  acceptedBy: null as string | null,
  handledBy: null as string | null,
  closedBy: null as string | null,
};

function event(override: Partial<TimelineEvent> & Pick<TimelineEvent, 'id' | 'eventType'>): TimelineEvent {
  return {
    complaintId: 1,
    actor: 'operator-user',
    occurredAt: `2026-10-06T08:0${override.id}:00`,
    fromStatus: null,
    toStatus: null,
    evidenceText: null,
    targetRef: null,
    result: 'SUCCESS',
    reviewId: null,
    failureReason: null,
    relatedDisposalId: null,
    ...override,
  };
}

function registered(caseId = 1): TimelineEvent {
  return event({ id: 1, complaintId: caseId, eventType: 'REGISTERED', toStatus: 'PENDING', evidenceText: '监管投诉：营销短信扰民' });
}

type Remediation = {
  id: number;
  complaintId: number;
  disposalType: string;
  targetRef: string;
  status: string;
  authorizedReviewId: string | null;
  failureReason: string | null;
  originalComplaintId: number | null;
};

type MockState = {
  rows: ComplaintRow[];
  timeline: Map<number, TimelineEvent[]>;
  remediations: Remediation[];
  createRequests: number;
  createDelayMs: number;
  acceptRequests: number;
  acceptDelayMs: number;
  staleAccept: boolean;
  listFailures: number;
  optionFailures: number;
  detailFailures: number;
};

async function mockComplaintApis(page: Page, complaintRows: ComplaintRow[] = [{ ...baseCase }]) {
  await mockEmptyDashboard(page);
  const state: MockState = {
    rows: complaintRows.map((row) => ({ ...row })),
    timeline: new Map(complaintRows.map((row) => [row.id, [registered(row.id)]])),
    remediations: [],
    createRequests: 0,
    createDelayMs: 0,
    acceptRequests: 0,
    acceptDelayMs: 0,
    staleAccept: false,
    listFailures: 0,
    optionFailures: 0,
    detailFailures: 0,
  };

  const row = (id: number) => state.rows.find((item) => item.id === id)!;
  const replace = (id: number, next: ComplaintRow) => {
    state.rows = state.rows.map((item) => item.id === id ? next : item);
  };
  const append = (id: number, next: TimelineEvent) => state.timeline.set(id, [...(state.timeline.get(id) ?? []), next]);
  const idFrom = (route: Route) => Number(new URL(route.request().url()).pathname.match(/complaints\/(\d+)/)?.[1]);

  await page.route('**/api/v1/console/complaint-reference-options', async (route) => {
    if (state.optionFailures > 0) {
      state.optionFailures -= 1;
      await route.fulfill({ status: 500, json: apiResponse(null, 'unavailable') });
      return;
    }
    await route.fulfill({ json: apiResponse({
      tenants: [{ id: 7, label: '机构七', tenantId: null }, { id: 9, label: '机构九', tenantId: null }],
      channels: [{ id: 11, label: '主通道', tenantId: null }],
      signatures: [{ id: 8, label: '【优创】', tenantId: 7 }, { id: 18, label: '【机构九】', tenantId: 9 }],
      templates: [{ id: 9, label: '营销模板', tenantId: 7 }],
    }) });
  });

  await page.route('**/api/v1/console/complaints', async (route) => {
    if (route.request().method() === 'POST') {
      const input = route.request().postDataJSON();
      state.createRequests += 1;
      if (state.createDelayMs > 0) await new Promise((resolve) => setTimeout(resolve, state.createDelayMs));
      expect(input.attributionQuality).toBeUndefined();
      const created: ComplaintRow = {
        ...baseCase,
        ...input,
        id: Math.max(0, ...state.rows.map((item) => item.id)) + 1,
        attributionQuality: input.tenantId && input.channelId && input.signatureId && input.templateId ? 'COMPLETE' : 'UNKNOWN',
        status: 'PENDING',
      };
      state.rows = [created, ...state.rows];
      state.timeline.set(created.id, [registered(created.id)]);
      await route.fulfill({ json: apiResponse(created) });
      return;
    }
    if (state.listFailures > 0) {
      state.listFailures -= 1;
      await route.fulfill({ status: 500, json: apiResponse(null, 'unavailable') });
      return;
    }
    await route.fulfill({ json: apiResponse(state.rows) });
  });

  await page.route(/\/api\/v1\/console\/complaints\/\d+$/, async (route) => {
    if (state.detailFailures > 0) {
      state.detailFailures -= 1;
      await route.fulfill({ status: 500, json: apiResponse(null, 'unavailable') });
      return;
    }
    const id = idFrom(route);
    await route.fulfill({ json: apiResponse({
      complaint: row(id),
      timeline: state.timeline.get(id) ?? [],
      remediations: state.remediations.filter((item) => item.complaintId === id),
    }) });
  });

  await page.route(/\/api\/v1\/console\/complaints\/\d+\/accept$/, async (route) => {
    const id = idFrom(route);
    const input = route.request().postDataJSON();
    state.acceptRequests += 1;
    if (state.acceptDelayMs > 0) await new Promise((resolve) => setTimeout(resolve, state.acceptDelayMs));
    const next = { ...row(id), status: 'PROCESSING', opinion: input.opinion, acceptedAt: '2026-10-06T08:01:00', acceptedBy: 'operator-user' };
    replace(id, next);
    append(id, event({ id: (state.timeline.get(id)?.length ?? 0) + 1, complaintId: id, eventType: 'ACCEPTED', fromStatus: 'PENDING', toStatus: 'PROCESSING', evidenceText: input.opinion }));
    if (state.staleAccept) {
      state.staleAccept = false;
      await route.fulfill({ status: 409, json: { ...apiResponse({ errorCode: 'COMPLAINT_STATE_STALE' }, '案件状态已变化'), code: 409 } });
      return;
    }
    await route.fulfill({ json: apiResponse(next) });
  });

  await page.route(/\/api\/v1\/console\/complaints\/\d+\/handle$/, async (route) => {
    const id = idFrom(route);
    const input = route.request().postDataJSON();
    const next = { ...row(id), status: 'PROCESSED', opinion: input.opinion, remediation: input.remediation, requirement: input.requirement, handledAt: '2026-10-06T08:02:00', handledBy: 'operator-user' };
    replace(id, next);
    append(id, event({ id: (state.timeline.get(id)?.length ?? 0) + 1, complaintId: id, eventType: 'HANDLED', fromStatus: 'PROCESSING', toStatus: 'PROCESSED', evidenceText: `${input.opinion} / ${input.remediation} / ${input.requirement}` }));
    await route.fulfill({ json: apiResponse(next) });
  });

  await page.route(/\/api\/v1\/console\/complaints\/\d+\/close$/, async (route) => {
    const id = idFrom(route);
    const input = route.request().postDataJSON();
    const next = { ...row(id), status: 'CLOSED', closedNote: input.opinion, closedAt: '2026-10-06T08:06:00', closedBy: 'operator-user' };
    replace(id, next);
    append(id, event({ id: (state.timeline.get(id)?.length ?? 0) + 1, complaintId: id, eventType: 'CLOSED', fromStatus: 'PROCESSED', toStatus: 'CLOSED', evidenceText: input.opinion }));
    await route.fulfill({ json: apiResponse(next) });
  });

  await page.route(/\/api\/v1\/console\/complaints\/\d+\/remediations$/, async (route) => {
    const id = idFrom(route);
    const input = route.request().postDataJSON();
    const remediation: Remediation = {
      id: state.remediations.length + 2,
      complaintId: id,
      disposalType: input.disposalType,
      targetRef: input.targetRef,
      status: 'FAILED',
      authorizedReviewId: input.authorizedReviewId,
      failureReason: '处置执行失败，请根据安全审计日志排查',
      originalComplaintId: id,
    };
    state.remediations = [remediation, ...state.remediations];
    append(id, event({ id: (state.timeline.get(id)?.length ?? 0) + 1, complaintId: id, eventType: 'REMEDIATION_FAILED', fromStatus: 'PROCESSED', toStatus: 'PROCESSED', evidenceText: input.reason, targetRef: input.targetRef, result: 'FAILED', reviewId: input.authorizedReviewId, failureReason: remediation.failureReason, relatedDisposalId: remediation.id }));
    await route.fulfill({ json: apiResponse(remediation) });
  });

  await page.route(/\/api\/v1\/console\/complaints\/\d+\/recoveries$/, async (route) => {
    const id = idFrom(route);
    const input = route.request().postDataJSON();
    const failed = state.remediations.find((item) => item.id === input.disposalRecordId)!;
    const recovered = { ...failed, status: 'RECOVERED', authorizedReviewId: input.authorizedReviewId };
    state.remediations = state.remediations.map((item) => item.id === recovered.id ? recovered : item);
    append(id, event({ id: (state.timeline.get(id)?.length ?? 0) + 1, complaintId: id, eventType: 'RECOVERED', fromStatus: row(id).status, toStatus: row(id).status, evidenceText: input.resumeCondition, targetRef: recovered.targetRef, reviewId: input.authorizedReviewId, relatedDisposalId: recovered.id }));
    await route.fulfill({ json: apiResponse(recovered) });
  });

  await page.route('**/api/v1/console/complaint-analytics', (route) => route.fulfill({ json: apiResponse({
    totalCount: 2,
    unknownAttributionCount: 1,
    byTenant: [{ dimension: 'tenant:7', count: 1 }],
    bySignature: [{ dimension: 'signature:8', count: 1 }],
    byContentType: [{ dimension: 'MARKETING', count: 2 }],
    trend: [{ day: '2026-09-12', count: 2 }],
  }) }));
  return state;
}

async function openFirstCase(page: Page) {
  await page.getByTestId('admin-complaint-case-complaints-open-case').first().click();
  await expect(page.getByTestId('admin-complaint-case-complaints-case-id')).toBeVisible();
}

test.describe('Issue 124 complaint case context', () => {
  test('pw-issue-78-complaints-layout C-ISSUE-78-COMPLAINTS-LAYOUT OBL-ISSUE-78-COMPLAINTS-LAYOUT keeps four cards and table geometry stable', async ({ page }) => {
    const state = await mockComplaintApis(page, []);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');

    const cardIds = ['admin-complaint-case-complaints-intake-card', 'admin-complaint-case-complaints-attribution-card', 'admin-complaint-case-complaints-list-card', 'admin-complaint-case-complaints-case-workspace'];
    const titles = ['投诉登记', '归因与要求', '投诉列表', '单案工作区'];
    const boxes = [];
    for (const [index, id] of cardIds.entries()) {
      const card = page.getByTestId(id);
      await expect(card).toHaveClass(/\bcard\b/);
      await expect(card.getByRole('heading', { name: titles[index], exact: true })).toBeVisible();
      boxes.push(await card.boundingBox());
    }
    for (let index = 1; index < boxes.length; index += 1) {
      expect(boxes[index]!.y).toBeGreaterThanOrEqual(boxes[index - 1]!.y + boxes[index - 1]!.height);
    }
    for (const control of await page.locator('[data-testid$="-card"] input, [data-testid$="-card"] select, [data-testid="form-submit"]').all()) {
      const box = await control.boundingBox();
      expect(box!.height).toBeGreaterThanOrEqual(38);
      expect(box!.height).toBeLessThanOrEqual(42);
    }
    await expect(page.getByTestId('data-table').locator('th')).toHaveText(['案件', '来源', '摘要', '归因质量', '状态', '处置资源', '要求', '动作']);
    await expect(page.getByTestId('table-empty')).toContainText('暂无投诉记录');

    state.rows = [{ ...baseCase, source: 'USER_REPORT', summary: '用户提交的长文本投诉摘要，用于证明窄列内容不会挤压相邻列或造成页面横向滚动', status: 'PROCESSING', attributionQuality: 'UNKNOWN', requirement: '立即停止发送并在二十四小时内反馈完整处置结论' }];
    state.timeline.set(1, [registered()]);
    await page.reload();
    const row = page.getByTestId('admin-complaint-case-complaints-row');
    await expect(row.getByTestId('admin-complaint-case-complaints-row-case-id')).toHaveText('#1');
    expect(await row.locator('.complaint-table-truncate').count()).toBe(6);
    for (const item of await row.locator('.complaint-table-truncate').all()) {
      const style = await item.evaluate((element) => ({ overflow: getComputedStyle(element).overflow, ellipsis: getComputedStyle(element).textOverflow }));
      expect(style).toEqual({ overflow: 'hidden', ellipsis: 'ellipsis' });
    }
    await openFirstCase(page);
    const width = await page.evaluate(() => ({ client: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
    expect(width.scroll).toBeLessThanOrEqual(width.client);
  });

  test('pw-issue-124-intake C-124-INTAKE OBL-F-9-1-A pw-p41-intake C-P41-INTAKE pw-p41-data-model C-P41-DATA OBL-DATA-10-9-COMPLAINT submits only explicit intake data once', async ({ page }) => {
    const state = await mockComplaintApis(page, []);
    state.createDelayMs = 300;
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');

    await expect(page.getByTestId('admin-complaint-case-complaints-page')).toBeVisible();
    await expect(page.getByTestId('admin-complaint-case-complaints-intake-card')).toBeVisible();
    await expect(page.getByTestId('admin-complaint-case-complaints-source')).toHaveValue('');
    await expect(page.getByTestId('admin-complaint-case-complaints-tenant-id')).toHaveValue('');
    await page.getByTestId('admin-complaint-case-complaints-source').selectOption('USER_REPORT');
    await page.getByTestId('admin-complaint-case-complaints-summary').fill('用户投诉短信内容');
    await page.getByTestId('form-submit').evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
    await expect.poll(() => state.createRequests).toBe(1);
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('投诉案件已登记');
    await expect(page.getByTestId('admin-complaint-case-complaints-case-id')).toContainText('#1');
    await expect(page.getByTestId('admin-complaint-case-complaints-attribution-policy')).toContainText('服务端');
  });

  test('pw-issue-124-attribution C-124-ATTRIBUTION OBL-F-9-1-B pw-p41-attribution C-P41-ATTRIBUTION preserves controlled reference ownership and server-derived quality', async ({ page }) => {
    await mockComplaintApis(page, []);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');

    await page.getByTestId('admin-complaint-case-complaints-source').selectOption('USER_REPORT');
    await page.getByTestId('admin-complaint-case-complaints-summary').fill('完整归因投诉');
    await page.getByTestId('admin-complaint-case-complaints-tenant-id').selectOption('7');
    await page.getByTestId('admin-complaint-case-complaints-signature-id').selectOption('8');
    await page.getByTestId('admin-complaint-case-complaints-tenant-id').selectOption('9');
    await expect(page.getByTestId('admin-complaint-case-complaints-signature-id')).toHaveValue('');
    await page.getByTestId('admin-complaint-case-complaints-tenant-id').selectOption('7');
    await page.getByTestId('admin-complaint-case-complaints-channel-id').selectOption('11');
    await page.getByTestId('admin-complaint-case-complaints-signature-id').selectOption('8');
    await page.getByTestId('admin-complaint-case-complaints-template-id').selectOption('9');
    await page.getByTestId('admin-complaint-case-complaints-content-type').selectOption('NOTIFY');
    await page.getByTestId('form-submit').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-message')).toContainText('投诉案件已登记');
    await expect(page.getByTestId('admin-complaint-case-complaints-attribution-quality')).toHaveText('COMPLETE');
  });

  test('pw-issue-124-action-context C-124-ACTION-CONTEXT OBL-F-9-2-A pw-p41-state C-P41-STATE pw-p41-accept C-P41-ACCEPT OBL-STATE-COMPLAINT-PROCESS pw-p41-handle C-P41-HANDLE OBL-STATE-COMPLAINT-HANDLED pw-p41-close C-P41-CLOSE OBL-STATE-COMPLAINT-CLOSE binds dialogs and runs the state flow', async ({ page }) => {
    const state = await mockComplaintApis(page);
    state.acceptDelayMs = 300;
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await openFirstCase(page);

    await expect(page.getByTestId('admin-complaint-case-complaints-case-workspace')).toBeVisible();
    await expect(page.getByTestId('admin-complaint-case-complaints-state-action')).toBeVisible();
    await page.getByTestId('admin-complaint-case-complaints-accept').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-action-target')).toContainText('案件 #1 · 机构 7');
    await page.getByTestId('admin-complaint-case-complaints-opinion').fill('不应保留');
    await page.getByTestId('admin-complaint-case-complaints-action-backdrop').click({ position: { x: 4, y: 4 } });
    await expect(page.getByTestId('admin-complaint-case-complaints-accept')).toBeFocused();

    await page.getByTestId('admin-complaint-case-complaints-accept').click();
    await page.getByTestId('admin-complaint-case-complaints-action-close').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-accept')).toBeFocused();

    await page.getByTestId('admin-complaint-case-complaints-accept').click();
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('admin-complaint-case-complaints-accept')).toBeFocused();

    await page.getByTestId('admin-complaint-case-complaints-accept').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-opinion')).toHaveValue('');
    await page.getByTestId('admin-complaint-case-complaints-opinion').fill('投诉属实，接单核查');
    await page.getByTestId('admin-complaint-case-complaints-action-confirm').evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
    await expect(page.getByTestId('admin-complaint-case-complaints-action-cancel')).toBeDisabled();
    await expect(page.getByTestId('admin-complaint-case-complaints-action-close')).toBeDisabled();
    await expect(page.getByTestId('modal')).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('admin-complaint-case-complaints-action-dialog')).toBeVisible();
    await expect.poll(() => state.acceptRequests).toBe(1);
    await expect(page.getByTestId('admin-complaint-case-complaints-case-status')).toHaveText('PROCESSING');

    await page.getByTestId('admin-complaint-case-complaints-handle').click();
    await page.getByTestId('admin-complaint-case-complaints-opinion').fill('投诉属实');
    await page.getByTestId('admin-complaint-case-complaints-remediation-note').fill('暂停相关资源');
    await page.getByTestId('admin-complaint-case-complaints-state-requirement').fill('24小时内反馈');
    await page.getByTestId('admin-complaint-case-complaints-action-confirm').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-case-status')).toHaveText('PROCESSED');
    await page.getByTestId('admin-complaint-case-complaints-close').click();
    await page.getByTestId('admin-complaint-case-complaints-close-note').fill('复核关闭');
    await page.getByTestId('admin-complaint-case-complaints-action-confirm').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-case-status')).toHaveText('CLOSED');
  });

  test('Issue 124 timeline regression renders chronological audit evidence and honest legacy gaps', async ({ page }) => {
    const state = await mockComplaintApis(page, [{ ...baseCase, status: 'CLOSED' }]);
    state.timeline.set(1, [
      registered(),
      event({ id: 2, eventType: 'ACCEPTED', fromStatus: 'PENDING', toStatus: 'PROCESSING', evidenceText: null }),
      event({ id: 3, eventType: 'HANDLED', fromStatus: 'PROCESSING', toStatus: 'PROCESSED', evidenceText: '核查并整改' }),
      event({ id: 4, eventType: 'CLOSED', fromStatus: 'PROCESSED', toStatus: 'CLOSED', evidenceText: '复核关闭' }),
    ]);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await openFirstCase(page);
    const events = page.getByTestId('admin-complaint-case-complaints-timeline-event');
    await expect(events).toHaveCount(4);
    await expect(events.nth(0)).toContainText('REGISTERED');
    await expect(events.nth(1)).toContainText('未保留历史证据');
    await expect(events.nth(3)).toContainText('CLOSED');
  });

  test('Issue 124 stale-state regression refreshes current state and locks duplicate submission', async ({ page }) => {
    const state = await mockComplaintApis(page);
    state.staleAccept = true;
    state.acceptDelayMs = 200;
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await openFirstCase(page);
    await page.getByTestId('admin-complaint-case-complaints-accept').click();
    await page.getByTestId('admin-complaint-case-complaints-opinion').fill('接单核查');
    await page.getByTestId('admin-complaint-case-complaints-action-confirm').evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
    await expect(page.getByTestId('admin-complaint-case-complaints-action-stale')).toContainText('案件状态已变化');
    await expect.poll(() => state.acceptRequests).toBe(1);
    await expect(page.getByTestId('admin-complaint-case-complaints-action-current-status')).toContainText('PROCESSING');
    await expect(page.getByTestId('admin-complaint-case-complaints-action-confirm')).toBeDisabled();
    await expect(page.getByTestId('admin-complaint-case-complaints-action-cancel')).toBeEnabled();
    await page.getByTestId('admin-complaint-case-complaints-action-cancel').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-accept')).toBeDisabled();
    await expect(page.getByTestId('admin-complaint-case-complaints-handle')).toBeEnabled();
  });

  test('Issue 124 loading regression retries independent reads', async ({ page }) => {
    const state = await mockComplaintApis(page);
    state.listFailures = 1;
    state.optionFailures = 1;
    state.detailFailures = 1;
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await page.getByTestId('admin-complaint-case-complaints-list-retry').click();
    await page.getByTestId('admin-complaint-case-complaints-reference-options-retry').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-tenant-id').locator('option')).toContainText(['未知机构', '机构七 (#7)', '机构九 (#9)']);
    await page.getByTestId('admin-complaint-case-complaints-open-case').click();
    await page.getByTestId('admin-complaint-case-complaints-case-workspace-retry').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-case-id')).toHaveText('#1');
  });

  test('pw-issue-124-remediation C-124-REMEDIATION OBL-F-9-3-A pw-p41-remediation C-P41-REMEDIATION OBL-STATE-RESOURCE-DISABLE pw-p41-resource C-P41-RESOURCE-DISABLE persists exact failed remediation evidence', async ({ page }) => {
    await mockComplaintApis(page, [{ ...baseCase, status: 'PROCESSED' }]);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await openFirstCase(page);
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-resource')).toContainText('channel:11');
    await page.getByTestId('admin-complaint-case-complaints-remediation').click();
    await page.getByTestId('admin-complaint-case-complaints-disposal-type').selectOption('SUSPEND_CHANNEL');
    await expect(page.getByTestId('admin-complaint-case-complaints-action-target-ref')).toContainText('channel:11');
    await page.getByTestId('admin-complaint-case-complaints-review-id').fill('review-124');
    await page.getByTestId('admin-complaint-case-complaints-remediation-reason').fill('投诉集中');
    await page.getByTestId('admin-complaint-case-complaints-action-confirm').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-timeline')).toContainText('REMEDIATION_FAILED');
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeEnabled();
  });

  test('pw-issue-124-recovery C-124-FAILED-RECOVERY OBL-F-9-3-B pw-p41-recovery C-P41-RECOVERY recovers the exact latest failed record without rewriting history', async ({ page }) => {
    const state = await mockComplaintApis(page, [{ ...baseCase, status: 'PROCESSED' }]);
    const failed: Remediation = {
      id: 2,
      complaintId: 1,
      disposalType: 'SUSPEND_CHANNEL',
      targetRef: 'channel:11',
      status: 'FAILED',
      authorizedReviewId: 'review-124',
      failureReason: '处置执行失败，请根据安全审计日志排查',
      originalComplaintId: 1,
    };
    state.remediations = [failed];
    state.timeline.set(1, [
      registered(),
      event({ id: 2, eventType: 'REMEDIATION_FAILED', fromStatus: 'PROCESSED', toStatus: 'PROCESSED', evidenceText: '投诉集中', targetRef: failed.targetRef, result: 'FAILED', reviewId: failed.authorizedReviewId, failureReason: failed.failureReason, relatedDisposalId: failed.id }),
    ]);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaints');
    await openFirstCase(page);

    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeEnabled();
    await page.getByTestId('admin-complaint-case-complaints-remediation-recovery').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-recovery-record')).toContainText('#2');
    await page.getByTestId('admin-complaint-case-complaints-recovery-review-id').fill('review-124-recovery');
    await page.getByTestId('admin-complaint-case-complaints-recovery-condition').fill('授权复核通过');
    await page.getByTestId('admin-complaint-case-complaints-action-confirm').click();
    await expect(page.getByTestId('admin-complaint-case-complaints-timeline')).toContainText('RECOVERED');
    await expect(page.getByTestId('admin-complaint-case-complaints-timeline')).toContainText('处置执行失败，请根据安全审计日志排查');
    await expect(page.getByTestId('admin-complaint-case-complaints-timeline')).not.toContainText('provider-secret');
    await expect(page.getByTestId('admin-complaint-case-complaints-remediation-recovery')).toBeDisabled();
  });

  test('Issue 124 role regression keeps FINANCE read-only', async ({ page }) => {
    let optionRequests = 0;
    await mockComplaintApis(page);
    await page.route('**/api/v1/console/complaint-reference-options', (route) => {
      optionRequests += 1;
      return route.fulfill({ json: apiResponse({ tenants: [], channels: [], signatures: [], templates: [] }) });
    });
    await loginAs(page, 'FINANCE');
    await page.goto('/admin/complaints');
    await expect(page.getByTestId('entity-form')).toHaveCount(0);
    await openFirstCase(page);
    await expect(page.getByTestId('admin-complaint-case-complaints-case-workspace')).toContainText('REGISTERED');
    await expect(page.getByTestId('admin-complaint-case-complaints-accept')).toHaveCount(0);
    expect(optionRequests).toBe(0);
  });

  test('pw-p41-analytics C-P41-ANALYTICS OBL-F-9-4-A renders complaint analytics', async ({ page }) => {
    await mockComplaintApis(page);
    await loginAs(page, 'OPERATOR');
    await page.goto('/admin/complaint/analytics');
    await expect(page.getByTestId('admin-complaint-case-analytics-page')).toContainText('投诉趋势与分布');
    await expect(page.getByTestId('admin-complaint-case-analytics-quality')).toContainText('未知归因 1');
    await expect(page.getByTestId('admin-complaint-case-analytics-trend')).toContainText('2026-09-12：2');
  });
});
