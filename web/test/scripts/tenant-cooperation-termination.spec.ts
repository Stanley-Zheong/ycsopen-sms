import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, loginAs, mockEmptyDashboard } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const clearanceBlocked = {
  tenantId: 42,
  lifecycleStatus: 'SIGNED',
  billingMode: 'PREPAID',
  clearancePassed: false,
  items: [
    { code: 'PREPAID_REFUND', passed: false, amountOrCount: 9000, evidence: '预付费余额或冻结金额未退款/解冻' },
    { code: 'POSTPAID_SETTLEMENT', passed: true, amountOrCount: 0, evidence: '后付费结算已完成或非后付费机构' },
  ],
};

const clearancePassed = {
  ...clearanceBlocked,
  clearancePassed: true,
  items: clearanceBlocked.items.map((item) => item.code === 'PREPAID_REFUND'
    ? { ...item, passed: true, amountOrCount: 0, evidence: '预付费余额已清零或非预付费机构' }
    : item),
};

const baseRequest = {
  id: 49,
  tenantId: 42,
  reason: 'VOLUNTARY',
  requestEvidence: 'termination-ticket:T-4901',
  requestStatus: 'BLOCKED_CLEARANCE',
  requestedBy: '7',
  requestedAt: '2026-09-10T08:00:00',
  approvedBy: null,
  approvedAt: null,
  adminOpinion: null,
  effectiveAt: null,
  clearanceSnapshotJson: JSON.stringify(clearanceBlocked),
  participantSnapshotJson: '[]',
  compensationJson: null,
};

const participants = [
  { id: 1, requestId: 49, tenantId: 42, participantCode: 'HTTP_ACCEPTANCE', participantName: 'HTTP 接收入口', participantState: 'READY_TO_REVOKE', blockerCount: 1, evidenceJson: '{"rule":"new submissions rejected by tenant lifecycle"}' },
  { id: 2, requestId: 49, tenantId: 42, participantCode: 'API_KEYS', participantName: 'API Key', participantState: 'REVOKED', blockerCount: 0, evidenceJson: '{"activeOrRetainedCount":0}' },
  { id: 3, requestId: 49, tenantId: 42, participantCode: 'SETTLEMENT', participantName: '后付费结算', participantState: 'CLEARED', blockerCount: 0, evidenceJson: '{"activeOrRetainedCount":0}' },
  { id: 4, requestId: 49, tenantId: 42, participantCode: 'ARCHIVE_RETENTION', participantName: '归档保留', participantState: 'RETAINED', blockerCount: 2, evidenceJson: '{"activeOrRetainedCount":2}' },
];

const inventory = [
  { code: 'HTTP_ACCEPTANCE', name: 'HTTP 接收入口', rule: 'new submissions rejected by tenant lifecycle' },
  { code: 'API_KEYS', name: 'API Key', rule: 'ACTIVE keys are revoked on effect' },
  { code: 'CMPP_SESSIONS', name: 'CMPP 下游会话/凭据', rule: 'ACTIVE protocol credentials are revoked on effect' },
  { code: 'CONSOLE_SESSIONS', name: '控制台会话', rule: 'tenant users and sessions are disabled on effect' },
  { code: 'BULK_SCHEDULED_WORK', name: '批量/定时任务', rule: 'pending/running/paused work is cancelled on effect' },
  { code: 'WEBHOOK', name: 'Webhook 回调', rule: 'active callback config is disabled on effect' },
  { code: 'UPLINK', name: '上行记录', rule: 'history remains retained/readable' },
  { code: 'UNSUBSCRIBE', name: '退订记录', rule: 'history remains retained/readable' },
  { code: 'SIGNATURE', name: '签名资源', rule: 'approved/pending signatures are deactivated on effect' },
  { code: 'TEMPLATE', name: '模板资源', rule: 'approved/pending templates are deactivated on effect' },
  { code: 'BILLING', name: '计费账户', rule: 'prepaid balance/frozen amount must be cleared' },
  { code: 'SETTLEMENT', name: '后付费结算', rule: 'unsettled statements/settlements must be cleared' },
  { code: 'ARCHIVE_RETENTION', name: '归档保留', rule: 'termination never deletes retained history' },
  { code: 'FAILURE_COMPENSATION', name: '失败补偿', rule: 'effect stores per-resource mutation counts' },
  { code: 'IRREVERSIBILITY', name: '终止不可逆', rule: 'terminated tenant cannot be restored by this service' },
];

async function mockTerminationApis(page: Page) {
  await mockEmptyDashboard(page);
  await page.route('**/api/v1/console/tenant-terminations?**', (route: Route) => route.fulfill({ json: apiResponse([baseRequest]) }));
  await page.route('**/api/v1/console/tenant-terminations/participants', (route: Route) => route.fulfill({ json: apiResponse(inventory) }));
  await page.route('**/api/v1/console/tenant-terminations', async (route: Route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual(expect.objectContaining({
        tenantId: 42,
        reason: 'VOLUNTARY',
        requestEvidence: 'termination-ticket:T-4901',
      }));
      await route.fulfill({ json: apiResponse({ request: baseRequest, participants, audits: [] }) });
      return;
    }
    await route.fulfill({ json: apiResponse([baseRequest]) });
  });
  await page.route('**/api/v1/console/tenant-terminations/49', (route: Route) => route.fulfill({ json: apiResponse({
    request: baseRequest,
    participants,
    audits: [{ id: 1, requestId: 49, tenantId: 42, action: 'REQUEST', actor: '7', resultStatus: 'BLOCKED_CLEARANCE', evidenceJson: '{"financeClearance":false}', createdAt: '2026-09-10T08:00:00' }],
  }) }));
  await page.route('**/api/v1/console/tenant-terminations/49/refresh-clearance', (route: Route) => route.fulfill({ json: apiResponse({
    request: { ...baseRequest, requestStatus: 'PENDING_ADMIN_APPROVAL', clearanceSnapshotJson: JSON.stringify(clearancePassed) },
    participants,
    audits: [{ id: 2, requestId: 49, tenantId: 42, action: 'REFRESH_CLEARANCE', actor: '7', resultStatus: 'PENDING_ADMIN_APPROVAL', evidenceJson: '{"financeClearance":true}', createdAt: '2026-09-10T08:05:00' }],
  }) }));
  await page.route('**/api/v1/console/tenant-terminations/49/approve', (route: Route) => route.fulfill({ json: apiResponse({
    request: { ...baseRequest, requestStatus: 'APPROVED', approvedBy: '7', approvedAt: '2026-09-10T08:06:00', clearanceSnapshotJson: JSON.stringify(clearancePassed) },
    participants,
    audits: [{ id: 3, requestId: 49, tenantId: 42, action: 'APPROVE', actor: '7', resultStatus: 'APPROVED', evidenceJson: '{"opinion":"finance and operation clearance accepted"}', createdAt: '2026-09-10T08:06:00' }],
  }) }));
  await page.route('**/api/v1/console/tenant-terminations/49/effect', (route: Route) => route.fulfill({ json: apiResponse({
    request: { ...baseRequest, requestStatus: 'EFFECTIVE', approvedBy: '7', approvedAt: '2026-09-10T08:06:00', effectiveAt: '2026-09-10T08:07:00', clearanceSnapshotJson: JSON.stringify(clearancePassed), compensationJson: '{"apiKeysRevoked":2}' },
    participants: participants.map((item) => item.participantCode === 'HTTP_ACCEPTANCE' ? { ...item, participantState: 'REVOKED', blockerCount: 0 } : item),
    audits: [{ id: 4, requestId: 49, tenantId: 42, action: 'EFFECT', actor: '7', resultStatus: 'EFFECTIVE', evidenceJson: '{"mutation":{"apiKeysRevoked":2}}', createdAt: '2026-09-10T08:07:00' }],
  }) }));
}

test('OBL-F-2-10-A C-P49-REQUEST pw-p49-request OBL-FLOW-12-1-TERMINATION C-P49-FLOW pw-p49-flow', async ({ page }) => {
  await mockTerminationApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/tenant/terminations');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-page')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-request')).toBeVisible();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-tenant-id')).toHaveValue('42');
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-submit').click();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-message')).toContainText('终止请求已创建');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-table')).toContainText('自愿终止');
});

test('OBL-F-2-10-B C-P49-CLEARANCE pw-p49-clearance OBL-TERMINATION-PARTICIPANTS-001 C-P49-PARTICIPANTS pw-p49-participants', async ({ page }) => {
  await mockTerminationApis(page);
  await loginAs(page, 'FINANCE');
  await page.goto('/admin/tenant/terminations');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-clearance')).toContainText('阻塞');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-participants')).toContainText('ARCHIVE_RETENTION');
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-detail').click();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-participants')).toContainText('API_KEYS');
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-refresh-clearance').click();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-clearance-status')).toContainText('通过');
});

test('OBL-STATE-TENANT-TERMINATE C-P49-APPROVE pw-p49-approve OBL-F-2-10-C C-P49-EFFECT pw-p49-effect OBL-F-2-10-D C-P49-TIMELINE pw-p49-timeline', async ({ page }) => {
  await mockTerminationApis(page);
  await loginAs(page, 'ADMIN');
  await page.goto('/admin/tenant/terminations');
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-detail').click();
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-approve').click();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-message')).toContainText('终止请求已审批');
  await page.getByTestId('admin-tenant-cooperation-tenant-termination-effect').click();
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-message')).toContainText('终止已生效');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-participants')).toContainText('REVOKED');
  await expect(page.getByTestId('admin-tenant-cooperation-tenant-termination-timeline')).toContainText('EFFECT');
});
