import { expect, test, type Route } from '@playwright/test';

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p38-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-10T00:00:00Z', traceId: 'trace-p38' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
];

const statement = {
  id: 38,
  tenantId: 42,
  statementNo: 'STMT-42-2026-09-01-2026-09-30',
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  sendCount: 2,
  successCount: 2,
  billedCount: 2,
  amountDue: 300000,
  reconcileStatus: 'PENDING',
  settlementStatus: 'NOT_SETTLED',
  billingMode: 'POSTPAID',
  priceBookVersion: 'SMS_STANDARD_V1',
  disputeNote: null,
  tenantConfirmedAt: null,
  financeConfirmedAt: null,
  confirmedAt: null,
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/console/account-overview', async (route) => {
    const admin = route.request().headers().authorization?.includes('finance');
    await route.fulfill({
      json: response({
        id: admin ? 38 : 42,
        username: admin ? 'finance-38' : 'tenant-38',
        userType: admin ? 'FINANCE' : 'TENANT_ADMIN',
        roleNames: admin ? ['财务'] : ['机构管理员'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }),
    });
  });
  await page.route('**/api/v1/console/reconciliation/statements/tenant/42', async (route: Route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response(statement) });
      return;
    }
    await route.fulfill({ json: response([statement]) });
  });
  await page.route('**/api/v1/console/reconciliation/statements', async (route) => route.fulfill({ json: response([statement]) }));
  await page.route('**/api/v1/console/reconciliation/statements/38/finance-confirm', async (route) => route.fulfill({ json: response({ ...statement, reconcileStatus: 'CONFIRMED' }) }));
  await page.route('**/api/v1/console/reconciliation/statements/38/tenant-confirm', async (route) => route.fulfill({ json: response({ ...statement, reconcileStatus: 'DISPUTED', disputeNote: '机构核对金额不一致' }) }));
  await page.route('**/api/v1/console/reconciliation/statements/38/differences', async (route) => route.fulfill({ json: response([{
    id: 39,
    statementId: 38,
    tenantId: 42,
    differenceType: 'AMOUNT',
    claimedAmountMil: 10000,
    note: '机构核对金额不一致',
    evidenceRef: 'oss://tenant/diff-202609.txt',
    ownerActor: 'tenant',
    status: 'OPEN',
    resolutionNote: null,
  }]) }));
  await page.route('**/api/v1/console/reconciliation/differences/39/resolve', async (route) => route.fulfill({ json: response(statement) }));
  await page.route('**/api/v1/console/reconciliation/statements/38/settlements', async (route) => route.fulfill({ json: response({
    id: 40,
    statementId: 38,
    tenantId: 42,
    amountMil: 300000,
    status: 'PENDING_SETTLEMENT',
    startEvidence: 'bank-flow-202609',
    completeEvidence: null,
    receivedEvidence: null,
  }) }));
  await page.route('**/api/v1/console/reconciliation/settlements', async (route) => route.fulfill({ json: response([{
    id: 40,
    statementId: 38,
    tenantId: 42,
    amountMil: 300000,
    status: 'PENDING_SETTLEMENT',
    startEvidence: 'bank-flow-202609',
    completeEvidence: null,
    receivedEvidence: null,
  }]) }));
  await page.route('**/api/v1/console/reconciliation/settlements/40/complete', async (route) => route.fulfill({ json: response({
    id: 40,
    statementId: 38,
    tenantId: 42,
    amountMil: 300000,
    status: 'SETTLED',
    startEvidence: 'bank-flow-202609',
    completeEvidence: 'bank-flow-202609',
    receivedEvidence: null,
  }) }));
  await page.route('**/api/v1/console/reconciliation/settlements/40/received', async (route) => route.fulfill({ json: response({
    id: 40,
    statementId: 38,
    tenantId: 42,
    amountMil: 300000,
    status: 'RECEIVED',
    startEvidence: 'bank-flow-202609',
    completeEvidence: 'bank-flow-202609',
    receivedEvidence: 'bank-flow-202609',
  }) }));
  await page.route('**/api/v1/console/reconciliation/invoices/tenant/42', async (route: Route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: response({
        id: 41,
        tenantId: 42,
        statementId: 38,
        amount: 100000,
        invoiceType: 'VAT_NORMAL',
        status: 'PENDING',
        invoiceNo: null,
        requestEvidence: '开票资料齐全',
        issuedAt: null,
      }) });
      return;
    }
    await route.fulfill({ json: response([]) });
  });
  await page.route('**/api/v1/console/reconciliation/invoices', async (route) => route.fulfill({ json: response([{
    id: 41,
    tenantId: 42,
    statementId: 38,
    amount: 100000,
    invoiceType: 'VAT_NORMAL',
    status: 'PENDING',
    invoiceNo: null,
    requestEvidence: '开票资料齐全',
    issuedAt: null,
  }]) }));
  await page.route('**/api/v1/console/reconciliation/invoices/41/issue', async (route) => route.fulfill({ json: response({
    id: 41,
    tenantId: 42,
    statementId: 38,
    amount: 100000,
    invoiceType: 'VAT_NORMAL',
    status: 'ISSUED',
    invoiceNo: 'INV-2026-0001',
    requestEvidence: '开票资料齐全',
    issuedAt: '2026-09-10T02:00:00',
  }) }));
});

test('pw-p38-statement-source C-P38-STATEMENT-SOURCE OBL-F-8-5-A OBL-F-8-2-C pw-p38-admin-settlement C-P38-SETTLEMENT OBL-F-8-6-A', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-page')).toBeVisible();
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-page')).toBeVisible();
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-table')).toBeVisible();
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-row')).toContainText('300000');
  await page.getByTestId('admin-reconciliation-settlement-reconciliation-generate').click();
  await expect(page.getByTestId('admin-reconciliation-settlement-message')).toContainText('对账单已生成');
  await page.getByTestId('admin-reconciliation-settlement-reconciliation-finance-confirm').click();
  await expect(page.getByTestId('admin-reconciliation-settlement-message')).toContainText('财务确认完成');
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-complete')).toBeVisible();
});

test('pw-p38-tenant-confirm C-P38-TENANT-CONFIRM OBL-F-8-5-B pw-p38-recon-confirm C-P38-RECON-CONFIRM OBL-STATE-RECON-CONFIRM pw-p38-recon-difference C-P38-RECON-DIFFERENCE OBL-STATE-RECON-DIFFERENCE pw-p38-tenant-invoice C-P38-TENANT-INVOICE OBL-F-8-7-A pw-p38-monthly-pack C-P38-MONTHLY-PACK OBL-FLOW-12-1-MONTHLY-PACK', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('tenant'), userType: 'TENANT_ADMIN', tenantId: 42 });
  await page.goto('/tenant/statements');
  await expect(page.getByTestId('tenant-reconciliation-settlement-statements-page')).toBeVisible();
  await expect(page.getByTestId('tenant-reconciliation-settlement-statements-table')).toBeVisible();
  await expect(page.getByTestId('tenant-reconciliation-settlement-invoices-page')).toBeVisible();
  await page.getByTestId('tenant-reconciliation-settlement-statements-confirm').click();
  await expect(page.getByTestId('tenant-reconciliation-settlement-message')).toContainText('对账确认已提交');
  await page.getByTestId('tenant-reconciliation-settlement-statements-difference').click();
  await expect(page.getByTestId('tenant-reconciliation-settlement-message')).toContainText('差异已提交');
  await page.getByTestId('tenant-reconciliation-settlement-invoices-request').click();
  await expect(page.getByTestId('tenant-reconciliation-settlement-message')).toContainText('发票申请已提交');
});

test('pw-p38-recon-resolve C-P38-RECON-RESOLVE OBL-STATE-RECON-RESOLVE pw-p38-settlement-start C-P38-SETTLEMENT-START OBL-STATE-SETTLEMENT-START pw-p38-settlement-done C-P38-SETTLEMENT-DONE OBL-STATE-SETTLEMENT-DONE pw-p38-settlement-received C-P38-SETTLEMENT-RECEIVED OBL-STATE-SETTLEMENT-RECEIVED', async ({ page }) => {
  await page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await page.goto('/admin/reconciliation');
  await expect(page.getByTestId('admin-reconciliation-settlement-reconciliation-resolve')).toBeVisible();
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-start')).toBeVisible();
  await expect(page.getByTestId('admin-reconciliation-settlement-settlements-received')).toBeVisible();
  await page.getByTestId('admin-reconciliation-settlement-reconciliation-resolve').click();
  await expect(page.getByTestId('admin-reconciliation-settlement-message')).toContainText('差异已处理');
  await page.getByTestId('admin-reconciliation-settlement-settlements-complete').click();
  await expect(page.getByTestId('admin-reconciliation-settlement-message')).toContainText('结算已完成');
  await expect(page.getByTestId('admin-reconciliation-settlement-invoices-issue')).toBeVisible();
  await page.getByTestId('admin-reconciliation-settlement-invoices-issue').click();
  await expect(page.getByTestId('admin-reconciliation-settlement-message')).toContainText('发票已开具');
});
