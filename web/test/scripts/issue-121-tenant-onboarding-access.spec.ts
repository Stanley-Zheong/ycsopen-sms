import { expect, test, type Page, type Route } from '@playwright/test';
import { apiResponse, seedAuthenticatedSession } from './helpers';

test.use({ viewport: { width: 1440, height: 900 } });

const reviewTenant = {
  tenantId: 42, tenantNo: 'TENANT-0042', shortName: '示例机构', fullName: '示例机构有限公司',
  unifiedSocialCreditCode: '91350211M000100Y43', legalRepresentativeName: '张三', contactName: '李四',
  registeredCapital: '1000', businessScope: '软件服务', registeredAddress: '厦门市示例路 1 号',
  businessAddress: '厦门市示例路 2 号', customerLevel: 3, bizManager: 'operator', industry: '软件',
  licenseValidUntil: '2030-01-01', trademarkUse: false, verificationStatus: 'PENDING',
  lifecycleStatus: 'SUBMITTED', operatingStatus: 'NORMAL', accountRevision: 2, qualificationRevision: 7,
  submittedAt: '2026-09-08T08:00:00Z', reason: null, inspectionStatus: 'COMPLETED',
  inspectedCompanyName: '示例机构有限公司', inspectedCreditCode: '91350211M000100Y43',
  inspectionConfidence: 0.98, inspectionRequestId: 'inspection-safe-id',
  inspectionCompletedAt: '2026-09-08T08:01:00Z', trialQuota: null, trialStartAt: null, trialEndAt: null,
};

const safeKey = {
  id: 41, appKey: 'public-app-key', name: 'issue-121-key', description: 'synthetic', status: 'ACTIVE',
  ipWhitelist: '127.0.0.1/32', perSecond: 10, perMinute: 100, perHour: 1_000, perDay: 10_000,
  expireTime: '2099-01-01T00:00:00Z', lastUsedTime: '2026-10-05T10:30:00Z', appSecretMask: '******',
};

type AuditRow = { id:number; actor:string; operation:string; resourceId:string; result:string; occurredAt:string };
type ApiKeyRow = typeof safeKey;
interface TenantApiState {
  keys: ApiKeyRow[];
  audits: AuditRow[];
  keyMode?: 'ok' | 'error' | 'denied';
  auditMode?: 'ok' | 'error';
  createMode?: 'ok' | 'rejected' | 'unknown';
  revokeMode?: 'ok' | 'error';
  keyBarrier?: Promise<void>;
  auditBarrier?: Promise<void>;
  createBarrier?: Promise<void>;
  revokeBarrier?: Promise<void>;
  createRequests?: number;
}

async function json(route: Route, data: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(apiResponse(data)) });
}

async function installTenantApi(page: Page, state: TenantApiState) {
  await page.route('**/api/v1/console/tenant/api-keys**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/audits')) {
      if (state.auditBarrier) await state.auditBarrier;
      if (state.auditMode === 'error') return json(route, null, 500);
      return json(route, state.audits);
    }
    if (path.endsWith('/revoke')) {
      if (state.revokeBarrier) await state.revokeBarrier;
      if (state.revokeMode === 'error') return json(route, null, 500);
      state.keys = state.keys.map((key) => ({ ...key, status: 'DISABLED' }));
      state.audits = [{ id: 102, actor: 'tenant-admin', operation: 'TENANT_API_KEY_REVOKE', resourceId: '41', result: 'SUCCESS', occurredAt: '2026-10-05T10:01:00Z' }, ...state.audits];
      return json(route, null);
    }
    if (request.method() === 'POST') {
      state.createRequests = (state.createRequests ?? 0) + 1;
      if (state.createBarrier) await state.createBarrier;
      if (state.createMode === 'rejected') return json(route, null, 400);
      if (state.createMode === 'unknown') return json(route, null, 500);
      state.keys = [{ ...safeKey }];
      state.audits = [{ id: 101, actor: 'tenant-admin', operation: 'TENANT_API_KEY_CREATE', resourceId: '41', result: 'SUCCESS', occurredAt: '2026-10-05T10:00:00Z' }];
      return json(route, { ...safeKey, appSecret: 'issue-121-secret-once' });
    }
    if (state.keyBarrier) await state.keyBarrier;
    if (state.keyMode === 'error') return json(route, null, 500);
    if (state.keyMode === 'denied') return json(route, null, 403);
    return json(route, state.keys);
  });
}

test('pw-issue-121-approval-result C-ISSUE-121-APPROVAL-RESULT OBL-ISSUE-121-APPROVAL-RESULT', async ({ page }) => {
  await seedAuthenticatedSession(page, 'ADMIN');
  await page.route('**/api/v1/console/account-overview', (route) => json(route, {
    id: 7, username: 'admin', userType: 'ADMIN', roleNames: ['系统管理员'], permissions: [],
    lastLoginAt: null, lastLoginIp: null,
  }));
  await page.route('**/api/v1/console/admin/tenants**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/events')) return json(route, []);
    if (path.endsWith('/decision')) return json(route, {
      ...reviewTenant, verificationStatus: 'VERIFIED', lifecycleStatus: 'TRIAL', qualificationRevision: 8,
      trialQuota: 500, trialStartAt: '2026-10-05T09:00:00Z', trialEndAt: '2026-10-19T09:00:00Z',
    });
    if (path.endsWith('/42')) return json(route, reviewTenant);
    return json(route, [reviewTenant]);
  });

  await page.goto('/admin/tenants');
  await page.getByTestId('admin-tenant-qualification-tenants-review-open').click();
  const drawer = page.getByTestId('admin-tenant-qualification-tenants-review-drawer');
  const drawerBounds = await page.getByRole('dialog').boundingBox();
  expect(drawerBounds).not.toBeNull();
  expect(Math.round((drawerBounds?.x ?? 0) + (drawerBounds?.width ?? 0))).toBe(1440);
  expect(drawerBounds?.x ?? 0).toBeGreaterThan(700);
  await drawer.getByTestId('admin-tenant-qualification-tenants-review-human-confirmed').check();
  await drawer.getByTestId('admin-tenant-qualification-tenants-review-approve-open').click();
  const decision = page.getByTestId('admin-tenant-qualification-tenants-review-decision');
  await decision.getByTestId('admin-tenant-qualification-tenants-review-decision-reason').fill('资料核对一致');
  await decision.getByTestId('admin-tenant-qualification-tenants-review-decision-confirm').click();

  const result = page.getByTestId('admin-tenant-qualification-tenants-approval-result');
  await expect(page.getByTestId('admin-tenant-qualification-tenants-approval-result')).toBeVisible();
  await expect(result.getByTestId('admin-tenant-qualification-tenants-approval-result-tenant-id')).toHaveText('42');
  await expect(result.getByTestId('admin-tenant-qualification-tenants-approval-result-tenant-no')).toHaveText('TENANT-0042');
  await expect(result.getByTestId('admin-tenant-qualification-tenants-approval-result-trial-quota')).toHaveText('500');
  await expect(result.getByTestId('admin-tenant-qualification-tenants-approval-result-trial-start-at')).not.toHaveText('—');
  await expect(result.getByTestId('admin-tenant-qualification-tenants-approval-result-trial-end-at')).not.toHaveText('—');
  await expect(result).not.toContainText(/password|App Secret|密码|密钥/i);
});

test('pw-issue-121-api-key-lifecycle C-ISSUE-121-API-KEY-LIFECYCLE OBL-ISSUE-121-API-KEY-LIFECYCLE', async ({ page }) => {
  const state: TenantApiState = { keys: [], audits: [] };
  await seedAuthenticatedSession(page, 'TENANT_ADMIN');
  await installTenantApi(page, state);
  await page.goto('/tenant/config');
  await expect(page).toHaveURL(/\/tenant\/api\/keys$/);
  await expect(page.getByTestId('tenant-tenant-access-api-keys-page')).toBeVisible();
  await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('issue-121-key');
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toHaveText('issue-121-secret-once');
  await page.getByTestId('tenant-tenant-access-api-keys-secret-acknowledge').click();
  const row = page.getByTestId('tenant-tenant-access-api-keys-row');
  await expect(row.getByTestId('tenant-tenant-access-api-keys-secret-mask')).toHaveText('******');
  await row.getByTestId('tenant-tenant-access-api-keys-revoke').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-revoke-target')).toContainText('issue-121-key');
  await page.getByTestId('tenant-tenant-access-api-keys-revoke-confirm').click();
  await expect(row).toContainText('DISABLED');
});

test('pw-issue-121-secret-safety C-ISSUE-121-SECRET-SAFETY OBL-ISSUE-121-SECRET-SAFETY', async ({ page }) => {
  const state: TenantApiState = { keys: [], audits: [] };
  await seedAuthenticatedSession(page, 'TENANT_ADMIN');
  await installTenantApi(page, state);
  await page.goto('/tenant/api/keys');
  await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('issue-121-key');
  state.keyMode = 'error';
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toHaveText('issue-121-secret-once');
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-secret-once')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-error')).toBeVisible();
  expect(await page.evaluate(() => JSON.stringify(window.sessionStorage))).not.toContain('issue-121-secret-once');
  await page.goto('/tenant/overview');
  await expect(page.getByText('issue-121-secret-once')).toHaveCount(0);
});

test('pw-issue-121-tenant-audit C-ISSUE-121-TENANT-AUDIT OBL-ISSUE-121-TENANT-AUDIT', async ({ page }) => {
  const state: TenantApiState = {
    keys: [{ ...safeKey }],
    audits: [
      { id: 102, actor: 'tenant-admin', operation: 'TENANT_API_KEY_REVOKE', resourceId: '41', result: 'SUCCESS', occurredAt: '2026-10-05T10:01:00Z' },
      { id: 101, actor: 'tenant-admin', operation: 'TENANT_API_KEY_CREATE', resourceId: '41', result: 'SUCCESS', occurredAt: '2026-10-05T10:00:00Z' },
    ],
  };
  await seedAuthenticatedSession(page, 'TENANT_ADMIN');
  await installTenantApi(page, state);
  await page.goto('/tenant/api/keys');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-audits-section')).toBeVisible();
  const auditRows = page.getByTestId('tenant-tenant-access-api-keys-audits-row');
  await expect(auditRows).toHaveCount(2);
  await expect(auditRows.nth(0)).toContainText('TENANT_API_KEY_REVOKE');
  await expect(auditRows.nth(1)).toContainText('TENANT_API_KEY_CREATE');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-audits-section')).not.toContainText('issue-121-secret-once');
});

test('pw-issue-121-page-states C-ISSUE-121-PAGE-STATES OBL-ISSUE-121-PAGE-STATES', async ({ page }) => {
  let releaseKeys: () => void = () => {};
  let releaseAudits: () => void = () => {};
  const state: TenantApiState = {
    keys: [], audits: [], auditMode: 'error',
    keyBarrier: new Promise<void>((resolve) => { releaseKeys = () => resolve(); }),
    auditBarrier: new Promise<void>((resolve) => { releaseAudits = () => resolve(); }),
  };
  await seedAuthenticatedSession(page, 'TENANT_ADMIN');
  await installTenantApi(page, state);
  await page.goto('/tenant/api/keys');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-heading')).toBeVisible();
  const dataRegions = page.getByTestId('data-table');
  await expect(dataRegions).toHaveCount(2);
  const keyDataRegion = dataRegions.nth(0);
  const auditDataRegion = dataRegions.nth(1);
  const keyTable = page.getByTestId('tenant-tenant-access-api-keys-table');
  await expect(page.getByTestId('tenant-tenant-access-api-keys-table')).toBeVisible();
  await expect(keyDataRegion).toContainText('App Key');
  await expect(auditDataRegion).toContainText('操作人');
  await expect(keyTable.locator('thead')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-loading')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-audits-loading')).toBeVisible();
  const initialKeys = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/tenant/api-keys'));
  const initialAudits = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/tenant/api-keys/audits'));
  state.keyBarrier = undefined;
  state.auditBarrier = undefined;
  releaseKeys();
  releaseAudits();
  await Promise.all([initialKeys, initialAudits]);
  await expect(keyDataRegion.getByTestId('table-empty')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-audits-error')).toBeVisible();
  state.auditMode = 'ok';
  await page.getByTestId('tenant-tenant-access-api-keys-audits-retry').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-audits-empty')).toBeVisible();
  await expect(auditDataRegion.getByTestId('table-empty')).toBeVisible();

  state.keyMode = 'error';
  await page.getByTestId('tenant-tenant-access-api-keys-refresh').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-error')).toBeVisible();
  state.keyMode = 'ok';
  state.keys = [{ ...safeKey }];
  await page.getByTestId('tenant-tenant-access-api-keys-retry').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-row')).toBeVisible();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-last-used-time')).toHaveText('2026/10/5 18:30:00');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  expect(await page.evaluate(() => {
    const legacy = document.createElement('section');
    legacy.className = 'card';
    legacy.setAttribute('role', 'dialog');
    document.body.append(legacy);
    const position = getComputedStyle(legacy).position;
    legacy.remove();
    return position;
  })).toBe('fixed');

  await page.getByTestId('tenant-tenant-access-api-keys-revoke').click();
  state.revokeMode = 'error';
  let releaseRevoke: () => void = () => {};
  state.revokeBarrier = new Promise<void>((resolve) => { releaseRevoke = () => resolve(); });
  await page.getByTestId('tenant-tenant-access-api-keys-revoke-confirm').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-revoke-confirm')).toBeDisabled();
  state.revokeBarrier = undefined;
  releaseRevoke();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-revoke-error')).toBeVisible();
  await page.getByTestId('tenant-tenant-access-api-keys-revoke-cancel').click();

  await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
  await expect(page.getByTestId('modal')).toHaveCSS('position', 'relative');
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('entity-form').getByRole('alert')).toContainText('名称不能为空');
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('definite-rejection');
  state.createMode = 'rejected';
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('entity-form').getByRole('alert')).toContainText('请求被拒绝');
  await expect(page.getByTestId('form-submit')).toBeEnabled();

  state.createMode = 'unknown';
  let releaseCreate: () => void = () => {};
  state.createBarrier = new Promise<void>((resolve) => { releaseCreate = () => resolve(); });
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('unknown-outcome');
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('form-submit')).toBeDisabled();
  state.createBarrier = undefined;
  releaseCreate();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-create-unknown')).toBeVisible();
  await expect(page.getByTestId('form-submit')).toBeDisabled();
  await page.getByTestId('form-cancel').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-create-unknown-guard')).toContainText('unknown-outcome');
  await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('unknown-outcome');
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('entity-form').getByRole('alert')).toContainText('上一次同名创建结果仍未知');
  expect(state.createRequests).toBe(2);
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('second-unknown');
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-create-unknown')).toBeVisible();
  expect(state.createRequests).toBe(3);
  await page.getByTestId('form-cancel').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-create-unknown-guard')).toContainText('unknown-outcome、second-unknown');
  await page.getByTestId('tenant-tenant-access-api-keys-create-dialog').click();
  await page.getByTestId('tenant-tenant-access-api-keys-name').fill('unknown-outcome');
  await page.getByTestId('form-submit').click();
  await expect(page.getByTestId('entity-form').getByRole('alert')).toContainText('上一次同名创建结果仍未知');
  expect(state.createRequests).toBe(3);
  await page.getByTestId('form-cancel').click();

  state.keyMode = 'denied';
  await page.getByTestId('tenant-tenant-access-api-keys-refresh').click();
  await expect(page.getByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});
