import {
  chromium,
  expect,
  test,
  type BrowserContext,
  type Page,
  type Route,
} from '@playwright/test';

declare const process: { env: Record<string, string | undefined> };

const chromeEndpoint = process.env.YCSOPEN_CHROME_CDP_ENDPOINT;
const webPort = process.env.YCSOPEN_WEB_PORT ?? '4173';
const appOrigin = `http://issue79-${webPort}.test`;

test.skip(!chromeEndpoint, 'YCSOPEN_CHROME_CDP_ENDPOINT is required for actual Google Chrome acceptance');
test.describe.configure({ mode: 'serial', timeout: 60_000 });

function token(subject: string) {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({ sub: subject, jti: `issue79-${subject}`, exp: 4102444800 })}.signature`;
}

function response(data: unknown) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-13T00:00:00Z', traceId: 'trace-issue79' };
}

const permissions = [
  { code: 'trial-prepaid:menu', resourceType: 'MENU' },
  { code: 'trial-prepaid:read', resourceType: 'API' },
  { code: 'trial-prepaid:write', resourceType: 'API' },
];

const summary = {
  tenantId: 42,
  channelId: 7,
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  sourceCount: 3,
  billableCount: 2,
  providerCostMil: 120,
  revenueMil: 100,
  profitMil: -20,
  priceBookVersion: 'SMS_STANDARD_V1',
  unitPriceMil: 50,
  formulaVersion: 'FINANCIAL_SOURCE_V1',
  formula: 'providerCostMil=sum(message_tasks.cost*1000), revenueMil=billableFinalCount*tenant_price_books.unit_price_mil, profitMil=revenueMil-providerCostMil',
  freshnessAt: '2026-09-03T12:00:00',
};

const source = {
  taskId: 1001,
  messageId: 'MSG-79-A',
  tenantId: 42,
  channelId: 7,
  sendStatus: 'SENT',
  finalStatus: 'DELIVERED',
  providerCostMil: 40,
  revenueMil: 50,
  profitMil: 10,
  priceBookVersion: 'SMS_STANDARD_V1',
  unitPriceMil: 50,
  formulaVersion: 'FINANCIAL_SOURCE_V1',
  formula: summary.formula,
  freshnessAt: '2026-09-03T12:00:00',
};

interface ChromeSession {
  context: BrowserContext;
  page: Page;
  browserVersion: string;
}

interface MockOptions {
  onSummary?: (route: Route, requestIndex: number) => Promise<void>;
  drilldownUrls?: string[];
}

async function connectChrome(): Promise<ChromeSession> {
  const response = await fetch(`${chromeEndpoint}/json/version`);
  expect(response.ok, 'Chrome CDP version endpoint is reachable').toBeTruthy();
  const version = await response.json() as { Browser?: string; webSocketDebuggerUrl?: string };
  expect(version.Browser, 'acceptance browser is Google Chrome').toMatch(/^Chrome\/\d+/);
  expect(version.webSocketDebuggerUrl, 'Chrome exposes a CDP websocket').toBeTruthy();

  const browser = await chromium.connectOverCDP(version.webSocketDebuggerUrl!);
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  return { context, page, browserVersion: version.Browser! };
}

async function installAppRoutes(page: Page, options: MockOptions = {}) {
  let summaryRequestIndex = 0;
  await page.route(`${appOrigin}/**`, async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/v1/console/account-overview') {
      await route.fulfill({ json: response({
        id: 79,
        username: 'finance-79',
        userType: 'FINANCE',
        roleNames: ['财务'],
        permissions,
        lastLoginAt: null,
        lastLoginIp: null,
      }) });
      return;
    }
    if (url.pathname === '/api/v1/console/financial/analytics/drilldown') {
      options.drilldownUrls?.push(url.toString());
      await route.fulfill({ json: response([source]) });
      return;
    }
    if (url.pathname === '/api/v1/console/financial/analytics') {
      const index = summaryRequestIndex++;
      if (options.onSummary) {
        await options.onSummary(route, index);
      } else {
        await route.fulfill({ json: response([summary]) });
      }
      return;
    }

    const upstream = await fetch(`http://127.0.0.1:${webPort}${url.pathname}${url.search}`);
    await route.fulfill({
      status: upstream.status,
      body: await upstream.text(),
      contentType: upstream.headers.get('content-type') ?? 'text/plain; charset=utf-8',
      headers: { 'cache-control': 'no-store' },
    });
  });
}

async function openFinance(options: MockOptions = {}) {
  const session = await connectChrome();
  await installAppRoutes(session.page, options);
  await session.page.addInitScript((value) => {
    window.sessionStorage.setItem('ycsopen.console.auth-session', JSON.stringify(value));
  }, { accessToken: token('finance'), userType: 'FINANCE', tenantId: null });
  await session.page.goto(`${appOrigin}/admin/finance`);
  await expect(session.page.getByTestId('admin-finance-overview-title')).toBeVisible();
  return session;
}

async function expandQuery(page: Page) {
  const toggle = page.getByTestId('query-panel-toggle');
  if (await toggle.getAttribute('aria-expanded') === 'false') await toggle.click();
  await expect(page.getByTestId('query-fields')).toBeVisible();
}

async function expectNoHorizontalScroll(page: Page) {
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.viewportWidth);
}

async function closeSession(session: ChromeSession) {
  await Promise.race([
    session.context.close(),
    new Promise<void>((resolve) => setTimeout(resolve, 5_000)),
  ]);
}

test('pw-issue-79-finance-query-layout C-ISSUE-79-FINANCE-QUERY-LAYOUT OBL-ISSUE-79-FINANCE-QUERY', async () => {
  const drilldownUrls: string[] = [];
  const session = await openFinance({ drilldownUrls });
  const { page } = session;
  try {
    expect(session.browserVersion).toMatch(/^Chrome\/\d+/);
    await expect(page.getByTestId('admin-finance-overview-title')).toHaveText('财务总览');
    await expandQuery(page);

    const panel = page.getByTestId('query-panel');
    const fields = panel.getByTestId('query-fields');
    const actions = panel.getByTestId('query-actions');
    const fieldNames = ['start-date', 'end-date', 'tenant-id', 'channel-id'];
    const nativeControlIds = [
      'admin-financial-source-financial-analytics-start-date',
      'admin-financial-source-financial-analytics-end-date',
      'admin-financial-source-financial-analytics-tenant-filter',
      'admin-financial-source-financial-analytics-channel-filter',
    ];

    for (const fieldName of fieldNames) {
      await expect(fields.getByTestId(`query-label-${fieldName}`)).toBeVisible();
      await expect(fields.getByTestId(`query-input-${fieldName}`)).toBeVisible();
    }
    const controlBoxes = [];
    for (const controlId of nativeControlIds) {
      const control = fields.getByTestId(controlId);
      await expect(control).toBeVisible();
      const box = await control.boundingBox();
      expect(box, `${controlId} has a layout box`).not.toBeNull();
      expect(box!.height).toBeGreaterThanOrEqual(38);
      expect(box!.height).toBeLessThanOrEqual(42);
      controlBoxes.push(box!);
    }

    const actionIds = [
      'query-submit',
      'query-reset',
      'admin-financial-source-financial-analytics-drilldown',
    ];
    const actionBoxes = [];
    for (const actionId of actionIds) {
      const action = actions.getByTestId(actionId);
      await expect(action).toBeVisible();
      const box = await action.boundingBox();
      expect(box, `${actionId} has a layout box`).not.toBeNull();
      expect(box!.height).toBeGreaterThanOrEqual(38);
      expect(box!.height).toBeLessThanOrEqual(42);
      actionBoxes.push(box!);
    }
    expect(Math.max(...actionBoxes.map((box) => box.y)) - Math.min(...actionBoxes.map((box) => box.y))).toBeLessThanOrEqual(1);
    const controlAndActionHeights = [...controlBoxes, ...actionBoxes].map((box) => box.height);
    expect(Math.max(...controlAndActionHeights) - Math.min(...controlAndActionHeights)).toBeLessThanOrEqual(1);
    await expect(actions.getByTestId('query-submit').getByTestId('admin-financial-source-financial-analytics-apply')).toBeVisible();

    const initialEndDate = new URL(page.url()).pathname;
    await fields.getByTestId('admin-financial-source-financial-analytics-end-date').fill('2026-09-10');
    await actions.getByTestId('admin-financial-source-financial-analytics-drilldown').click();
    await expect(page.getByTestId('admin-financial-source-financial-analytics-drilldown-row')).toBeVisible();
    expect(new URL(drilldownUrls[drilldownUrls.length - 1]).searchParams.get('endDate')).not.toBe('2026-09-10');
    expect(initialEndDate).toBe('/admin/finance');

    const appliedRequest = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return url.pathname === '/api/v1/console/financial/analytics'
        && url.searchParams.get('endDate') === '2026-09-10';
    });
    await actions.getByTestId('admin-financial-source-financial-analytics-apply').click();
    await appliedRequest;

    for (const width of [1000, 800]) {
      await page.setViewportSize({ width, height: 900 });
      await expandQuery(page);
      await expect(panel).toBeVisible();
      for (const fieldName of fieldNames) await expect(fields.getByTestId(`query-input-${fieldName}`)).toBeVisible();
      for (const actionId of actionIds) await expect(actions.getByTestId(actionId)).toBeVisible();
      const panelBox = await panel.boundingBox();
      expect(panelBox, `query panel has a layout box at ${width}px`).not.toBeNull();
      expect(panelBox!.x + panelBox!.width).toBeLessThanOrEqual(width);
      await expectNoHorizontalScroll(page);
    }
  } finally {
    await closeSession(session);
  }
});

test('pw-issue-79-finance-table-layout C-ISSUE-79-FINANCE-TABLE-LAYOUT OBL-ISSUE-79-FINANCE-TABLES', async () => {
  const session = await openFinance();
  const { page } = session;
  try {
    await expect(page.getByTestId('data-table')).toHaveCount(2);
    const financeTable = page.getByTestId('admin-financial-source-financial-analytics-table');
    const channelTable = page.getByTestId('admin-financial-source-channel-statistics-table');
    await expect(financeTable.getByTestId('admin-financial-source-financial-analytics-row')).toBeVisible();
    await expect(channelTable.getByTestId('admin-financial-source-channel-statistics-row')).toBeVisible();
    await expect(financeTable.locator('thead th')).toHaveCount(9);
    await expect(channelTable.locator('thead th')).toHaveCount(6);
    await expect(financeTable.locator('thead')).toContainText('机构通道源记录计费条数成本收入毛利价格版本刷新');
    await expect(channelTable.locator('thead')).toContainText('通道发送源最终成功成本收入毛利');

    for (const table of [financeTable, channelTable]) {
      const headerStyles = await table.locator('thead th').evaluateAll((headers) => headers.map((header) => {
        const style = getComputedStyle(header);
        return {
          paddingLeft: Number.parseFloat(style.paddingLeft),
          paddingRight: Number.parseFloat(style.paddingRight),
          borderRightWidth: Number.parseFloat(style.borderRightWidth),
        };
      }));
      for (const [index, style] of headerStyles.entries()) {
        expect(style.paddingLeft).toBeGreaterThanOrEqual(12);
        expect(style.paddingRight).toBeGreaterThanOrEqual(12);
        if (index < headerStyles.length - 1) expect(style.borderRightWidth).toBeGreaterThanOrEqual(1);
      }
    }

    await expandQuery(page);
    await page.getByTestId('query-actions').getByTestId('admin-financial-source-financial-analytics-drilldown').click();
    const drilldownTable = page.getByTestId('admin-financial-source-financial-analytics-drilldown-table');
    await expect(drilldownTable.getByTestId('admin-financial-source-financial-analytics-drilldown-row')).toBeVisible();
    const formulaCell = drilldownTable.getByTestId('admin-financial-source-financial-analytics-drilldown-formula');
    await expect(formulaCell).toHaveAttribute('title', source.formula);
    const formulaLayout = await formulaCell.evaluate((cell) => ({
      clientWidth: cell.clientWidth,
      scrollWidth: cell.scrollWidth,
      textOverflow: getComputedStyle(cell).textOverflow,
    }));
    expect(formulaLayout.textOverflow).toBe('ellipsis');
    expect(formulaLayout.scrollWidth).toBeGreaterThan(formulaLayout.clientWidth);

    for (const width of [1440, 1000, 800]) {
      await page.setViewportSize({ width, height: 900 });
      await expectNoHorizontalScroll(page);
    }
  } finally {
    await closeSession(session);
  }
});

test('pw-issue-79-finance-result-states C-ISSUE-79-FINANCE-RESULT-STATES OBL-ISSUE-79-FINANCE-STATES', async () => {
  let releaseFailure: (() => void) | undefined;
  let releaseEmpty: (() => void) | undefined;
  const failureGate = new Promise<void>((resolve) => { releaseFailure = resolve; });
  const emptyGate = new Promise<void>((resolve) => { releaseEmpty = resolve; });
  const session = await openFinance({
    onSummary: async (route, requestIndex) => {
      if (requestIndex === 0) {
        await route.fulfill({ json: response([summary]) });
      } else if (requestIndex === 1) {
        await failureGate;
        await route.fulfill({ status: 503, json: { code: 503, message: 'unavailable' } });
      } else {
        await emptyGate;
        await route.fulfill({ json: response([]) });
      }
    },
  });
  const { page } = session;
  try {
    await expect(page.getByTestId('admin-financial-source-financial-analytics-status')).toContainText('已加载 1 条财务汇总');
    await expect(page.getByTestId('admin-financial-source-channel-statistics-status')).toContainText('已加载 1 条通道统计');
    await expandQuery(page);
    await page.getByTestId('admin-financial-source-financial-analytics-end-date').fill('2026-09-10');
    await page.getByTestId('query-submit').click();
    await expect(page.getByTestId('data-table').nth(0).getByTestId('table-loading')).toBeVisible();
    await expect(page.getByTestId('data-table').nth(1).getByTestId('table-loading')).toBeVisible();

    releaseFailure!();
    await expect(page.getByTestId('data-table').nth(0).getByTestId('table-error')).toBeVisible();
    await expect(page.getByTestId('data-table').nth(1).getByTestId('table-error')).toBeVisible();
    await expect(page.getByTestId('admin-financial-source-financial-analytics-status')).toHaveAttribute('role', 'alert');
    await expect(page.getByTestId('admin-financial-source-channel-statistics-status')).toHaveAttribute('role', 'alert');
    await expect(page.getByTestId('admin-financial-source-financial-analytics-retry')).toBeVisible();
    await expect(page.getByTestId('admin-financial-source-channel-statistics-retry')).toBeVisible();

    await page.getByTestId('admin-financial-source-financial-analytics-retry').click();
    await expect(page.getByTestId('data-table').nth(0).getByTestId('table-loading')).toBeVisible();
    await expect(page.getByTestId('data-table').nth(1).getByTestId('table-loading')).toBeVisible();
    releaseEmpty!();
    await expect(page.getByTestId('data-table').nth(0).getByTestId('table-empty')).toBeVisible();
    await expect(page.getByTestId('data-table').nth(1).getByTestId('table-empty')).toBeVisible();
    await expect(page.getByTestId('admin-financial-source-financial-analytics-table').locator('thead')).toBeVisible();
    await expect(page.getByTestId('admin-financial-source-channel-statistics-table').locator('thead')).toBeVisible();
  } finally {
    await closeSession(session);
  }
});
