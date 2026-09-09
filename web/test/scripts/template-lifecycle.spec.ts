import { expect, test, type Page } from '@playwright/test';

test.use({ viewport: { width: 1440, height: 900 } });
test.describe.configure({ mode: 'serial' });

type TemplateFixture = ReturnType<typeof template>;

function template(overrides: Record<string, unknown> = {}) {
  return {
    id: 1301,
    tenantId: 42,
    templateCode: 'TPL42',
    templateName: '登录验证码',
    content: '您的验证码是 ${code}',
    templateType: 'VERIFY',
    signatureId: 1201,
    paramCheckRule: 'code:digits(4-8)',
    description: '登录验证',
    variableNames: ['code'],
    versionNo: 1,
    previousTemplateId: null,
    auditStatus: 'PENDING',
    auditComment: '',
    auditTime: null,
    createdAt: '2026-09-09T00:00:00',
    history: [{ eventType: 'SUBMITTED', actor: 'tenant:42', opinion: '登录验证', snapshotContent: '您的验证码是 ${code}', variableNames: ['code'], createdAt: '2026-09-09T00:00:00' }],
    ...overrides,
  };
}

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `p13-${subject}`, exp: 4102444800 })}.signature`;
}

async function session(page: Page, userType: 'TENANT_ADMIN' | 'OPERATOR', tenantId: number | null) {
  await page.addInitScript(({ userType, tenantId, accessToken }) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify({ accessToken, userType, tenantId }));
  }, { userType, tenantId, accessToken: token(userType === 'TENANT_ADMIN' ? '42' : '7') });
}

async function routeApi(page: Page) {
  let tenantRows: TemplateFixture[] = [template({
    auditStatus: 'REJECTED',
    auditComment: '变量说明不足',
    content: '尊敬的 ${name}，本次消费 ${amount} 元',
    variableNames: ['name', 'amount'],
    paramCheckRule: 'name:text(1-20),amount:number(1-12)',
  })];
  let adminRows: TemplateFixture[] = [template()];
  await page.route('**/api/v1/console/tenant/templates', async (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      const created = template({ id: 1302, templateName: body.templateName, content: body.content, signatureId: body.signatureId, paramCheckRule: body.paramCheckRule, auditStatus: 'PENDING' });
      tenantRows = [created, ...tenantRows];
      await route.fulfill({ json: api(created) });
      return;
    }
    await route.fulfill({ json: api(tenantRows) });
  });
  await page.route('**/api/v1/console/tenant/templates/*/preview', async (route) => {
    const body = route.request().postDataJSON();
    await route.fulfill({ json: api({ renderedContent: `尊敬的 ${body.variables.name}，本次消费 ${body.variables.amount} 元` }) });
  });
  await page.route('**/api/v1/console/tenant/templates/1301/resubmit', async (route) => {
    const created = template({ id: 1303, previousTemplateId: 1301, versionNo: 2, auditStatus: 'PENDING' });
    tenantRows = [created, ...tenantRows];
    await route.fulfill({ json: api(created) });
  });
  await page.route('**/api/v1/console/templates/review**', async (route) => {
    await route.fulfill({ json: api({ summary: { total: 1, pending: 1, approved: 0, rejected: 0, amendmentRequired: 0 }, items: adminRows }) });
  });
  await page.route('**/api/v1/console/templates/1301/decisions', async (route) => {
    const body = route.request().postDataJSON();
    const auditStatus = body.decision === 'APPROVE' ? 'APPROVED' : body.decision === 'REJECT' ? 'REJECTED' : 'AMENDMENT_REQUIRED';
    adminRows = adminRows.map((row) => row.id === 1301 ? template({ ...row, auditStatus, auditComment: body.opinion }) : row);
    await route.fulfill({ json: api(adminRows[0]) });
  });
}

function api(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-09T00:00:00Z', traceId: 'p13-chrome' };
}

test('pw-p13-template-application pw-p13-template-name pw-p13-template-content pw-p13-template-type pw-p13-template-signature pw-p13-template-param-rule C-P13-TEMPLATE-APPLICATION C-P13-FIELD-NAME C-P13-FIELD-CONTENT C-P13-FIELD-TYPE C-P13-FIELD-SIGNATURE C-P13-FIELD-PARAM-RULE OBL-F-3-4-A OBL-FIELD-TEMPLATE-NAME OBL-FIELD-TEMPLATE-CONTENT OBL-FIELD-TEMPLATE-TYPE OBL-FIELD-TEMPLATE-SIGNATURE OBL-FIELD-TEMPLATE-PARAM-RULE', async ({ page }) => {
  await session(page, 'TENANT_ADMIN', 42);
  await routeApi(page);
  await page.goto('/tenant/templates');
  await expect(page.getByTestId('tenant-template-lifecycle-templates-page')).toBeVisible();
  await page.getByTestId('tenant-template-lifecycle-templates-form-name').fill('登录验证码');
  await page.getByTestId('tenant-template-lifecycle-templates-form-content').fill('您的验证码是 ${code}');
  await page.getByTestId('tenant-template-lifecycle-templates-form-type').selectOption('verification');
  await page.getByTestId('tenant-template-lifecycle-templates-form-signature').fill('1201');
  await page.getByTestId('tenant-template-lifecycle-templates-form-param-rule').fill('code:digits(4-8)');
  await page.getByTestId('tenant-template-lifecycle-templates-submit').click();
  await expect(page.getByRole('status')).toContainText('模板申请已提交');
});

test('pw-p13-template-variable-preview C-P13-TEMPLATE-PREVIEW OBL-F-3-4-B', async ({ page }) => {
  await session(page, 'TENANT_ADMIN', 42);
  await routeApi(page);
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-variable-name').fill('张三');
  await page.getByTestId('tenant-template-lifecycle-templates-variable-amount').fill('99.5');
  await page.getByTestId('tenant-template-lifecycle-templates-variable-preview').click();
  await expect(page.getByText('尊敬的 张三，本次消费 99.5 元')).toBeVisible();
});

test('pw-p13-template-review pw-p13-template-decision pw-p13-template-approve pw-p13-template-reject C-P13-TEMPLATE-REVIEW C-P13-TEMPLATE-DECISION C-P13-STATE-APPROVE C-P13-STATE-REJECT OBL-F-3-5-A OBL-F-3-5-B OBL-STATE-REVIEW-APPROVE OBL-STATE-REVIEW-REJECT', async ({ page }) => {
  await session(page, 'OPERATOR', null);
  await routeApi(page);
  await page.goto('/admin/templates/review');
  await expect(page.getByTestId('admin-template-lifecycle-template-review-page')).toBeVisible();
  await expect(page.getByTestId('admin-template-lifecycle-template-review-stats')).toContainText('待审核 1');
  await page.getByTestId('admin-template-lifecycle-template-review-filters').fill('验证码');
  await expect(page.getByTestId('admin-template-lifecycle-template-review-row')).toContainText('code');
  await page.getByTestId('admin-template-lifecycle-template-review-decision-open').click();
  await page.getByTestId('admin-template-lifecycle-template-review-decision-opinion').fill('材料完整');
  await expect(page.getByTestId('admin-template-lifecycle-template-review-approve')).toBeVisible();
  await expect(page.getByTestId('admin-template-lifecycle-template-review-reject')).toBeVisible();
  await page.getByTestId('admin-template-lifecycle-template-review-decision-status').selectOption('AMENDMENT_REQUIRED');
  await page.getByTestId('admin-template-lifecycle-template-review-decision').click();
  await expect(page.getByRole('status')).toContainText('模板审核结果已保存');
  await expect(page.getByTestId('admin-template-lifecycle-template-review-row')).toContainText('AMENDMENT_REQUIRED');
});

test('pw-p13-template-resubmit C-P13-STATE-RESUBMIT OBL-STATE-REVIEW-RESUBMIT', async ({ page }) => {
  await session(page, 'TENANT_ADMIN', 42);
  await routeApi(page);
  await page.goto('/tenant/templates');
  await page.getByTestId('tenant-template-lifecycle-templates-resubmit').click();
  await expect(page.getByRole('status')).toContainText('模板重新提交成功');
});
