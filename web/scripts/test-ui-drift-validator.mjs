#!/usr/bin/env node

import { mkdtemp, mkdir, readFile, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(scriptDir, '..');
const fixturePath = path.join(webRoot, 'verification/fixtures/ui-drift-cases.json');
const validatorPath = path.join(scriptDir, 'validate-ui-drift.mjs');
const manifestSchemaPath = path.join(webRoot, 'verification/ui-manifest.schema.json');
const rowSchemaPath = path.join(webRoot, 'verification/row-key-registry.schema.json');
const productionRegistryPath = path.join(webRoot, 'verification/row-key-registry.json');

const fixture = JSON.parse(await readFile(fixturePath, 'utf8'));
let passed = 0;

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function mutate(caseId, graph) {
  if (caseId === 'none') return;
  if (caseId === 'remove-source-route') {
    graph.routesSource = graph.routesSource.replace("      { path: 'route', element: <FixturePage /> },\n", '');
  } else if (caseId === 'add-source-route') {
    graph.routesSource = graph.routesSource.replace(
      "      { path: 'rows/:groupId', element: <RowsPage /> }",
      "      { path: 'rows/:groupId', element: <RowsPage /> },\n      { path: 'stale', element: <FixturePage /> }",
    );
  } else if (caseId === 'remove-source-index-redirect') {
    graph.routesSource = graph.routesSource.replace("      { index: true, element: <Navigate to=\"route\" replace /> },\n", '');
  } else if (caseId === 'add-source-redirect') {
    graph.routesSource = graph.routesSource.replace(
      "      { path: 'rows/:groupId', element: <RowsPage /> }",
      "      { path: 'rows/:groupId', element: <RowsPage /> },\n      { path: 'legacy', element: <Navigate to=\"route\" replace /> }",
    );
  } else if (caseId === 'wrong-source-redirect-target') {
    graph.routesSource = graph.routesSource.replace('to="route" replace', 'to="rows/:groupId" replace');
  } else if (caseId === 'computed-source-route') {
    graph.routesSource = graph.routesSource.replace("path: 'route'", "path: 'route/' + routeName");
  } else if (caseId === 'malformed-route-source') {
    graph.routesSource = graph.routesSource.replace(']);\n', '');
  } else if (caseId === 'route-object-spread') {
    graph.routesSource = graph.routesSource.replace(
      "{ path: 'route', element: <FixturePage /> }",
      "{ path: 'route', element: <FixturePage />, ...extraRoute }",
    );
  } else if (caseId === 'computed-redirect-target') {
    graph.routesSource = graph.routesSource.replace('to="route" replace', 'to={redirectTarget} replace');
  } else if (caseId === 'wrong-manifest-component') {
    graph.manifest.pages[0].component.symbol = 'RowsPage';
    graph.manifest.pages[0].component.source = 'src/RowsPage.tsx';
  } else if (caseId === 'remove-dom-selector') {
    graph.componentSources['src/FixturePage.tsx'] = graph.componentSources['src/FixturePage.tsx'].replace(' data-testid="fixture-page-primary-action"', '');
  } else if (caseId === 'add-dom-selector') {
    graph.componentSources['src/FixturePage.tsx'] = graph.componentSources['src/FixturePage.tsx'].replace(
      '</button>', '</button><span data-testid="fixture-page-stale-status">stale</span>',
    );
  } else if (caseId === 'comment-string-selector-fake') {
    graph.componentSources['src/FixturePage.tsx'] = "const fake = 'data-testid=\"fixture-page-primary-action\"'; // data-testid=\"fixture-page-primary-action\"\nexport default function FixturePage() { return <button>Run</button>; }\n";
  } else if (caseId === 'comment-string-route-fake') {
    graph.routesSource = graph.routesSource.replace("      { path: 'route', element: <FixturePage /> },\n", '');
    graph.routesSource += "\nconst fakeRoute = \"path: '/fixture/route'\"; // path: '/fixture/route'\n";
  } else if (caseId === 'selector-prefix-collision') {
    graph.componentSources['src/FixturePage.tsx'] = graph.componentSources['src/FixturePage.tsx'].replace(
      'fixture-page-primary-action', 'fixture-page-primary-action-extra',
    );
  } else if (caseId === 'computed-test-id') {
    graph.componentSources['src/FixturePage.tsx'] = "const selector = 'fixture-page-primary-action';\nexport default function FixturePage() { return <button data-testid={selector}>Run</button>; }\n";
  } else if (caseId === 'template-test-id') {
    graph.componentSources['src/FixturePage.tsx'] = "export default function FixturePage({ suffix = 'action' }) { return <button data-testid={`fixture-page-primary-${suffix}`}>Run</button>; }\n";
  } else if (caseId === 'jsx-attribute-spread') {
    graph.componentSources['src/FixturePage.tsx'] = "export default function FixturePage(props) { return <button data-testid=\"fixture-page-primary-action\" {...props}>Run</button>; }\n";
  } else if (caseId === 'duplicate-manifest-selector') {
    graph.manifest.pages[0].selectors.push(clone(graph.manifest.pages[0].selectors[0]));
  } else if (caseId === 'dead-component-selector') {
    graph.componentSources['src/DeadPage.tsx'] = graph.componentSources['src/FixturePage.tsx'];
    graph.componentSources['src/FixturePage.tsx'] = 'export default function FixturePage() { return <button>Run</button>; }\n';
    graph.manifest.pages[0].component.symbol = 'DeadPage';
    graph.manifest.pages[0].component.source = 'src/DeadPage.tsx';
    graph.manifest.pages[0].selectors[0].source = 'src/DeadPage.tsx';
  } else if (caseId === 'component-source-outside-root') {
    graph.manifest.pages[0].component.source = '/etc/hosts';
    graph.manifest.pages[0].selectors[0].source = '/etc/hosts';
  } else if (caseId === 'component-source-symlink-outside-root') {
    graph.outsideSymlink = 'src/Outside.tsx';
    graph.manifest.pages[0].component.source = graph.outsideSymlink;
    graph.manifest.pages[0].selectors[0].source = graph.outsideSymlink;
  } else if (caseId === 'remove-playwright-locator') {
    graph.playwrightSources['tests/ui.spec.ts'] = graph.playwrightSources['tests/ui.spec.ts'].replace(
      " await page.getByTestId('fixture-page-primary-action').click();", '',
    );
  } else if (caseId === 'comment-string-playwright-fake') {
    graph.playwrightSources['tests/ui.spec.ts'] = graph.playwrightSources['tests/ui.spec.ts'].replace(
      " await page.getByTestId('fixture-page-primary-action').click();", '',
    );
    graph.playwrightSources['tests/ui.spec.ts'] += "const fakeLocator = \"page.getByTestId('fixture-page-primary-action').click()\"; // page.getByTestId('fixture-page-primary-action').click()\n";
  } else if (caseId === 'add-playwright-locator') {
    graph.playwrightSources['tests/ui.spec.ts'] += "test('PW-STALE CASE-STALE OBL-FOUND-UI-DRIFT-001', async ({ page }) => { await page.goto('/fixture/route'); await page.getByTestId('fixture-page-stale-action').click(); });\n";
  } else if (caseId === 'wrong-playwright-route') {
    graph.playwrightSources['tests/ui.spec.ts'] = graph.playwrightSources['tests/ui.spec.ts'].replace(
      "page.goto('/fixture/route')", "page.goto('/fixture/rows/:groupId')",
    );
  } else if (caseId === 'dead-playwright-locator') {
    graph.playwrightSources['tests/ui.spec.ts'] = graph.playwrightSources['tests/ui.spec.ts'].replace(
      "await page.getByTestId('fixture-page-primary-action').click();",
      "page.getByTestId('fixture-page-primary-action');",
    );
  } else if (caseId === 'computed-playwright-selector') {
    graph.playwrightSources['tests/ui.spec.ts'] = graph.playwrightSources['tests/ui.spec.ts'].replace(
      "page.getByTestId('fixture-page-primary-action')", 'page.getByTestId(selector)',
    );
  } else if (caseId === 'remove-manifest-selector') {
    graph.manifest.pages[0].selectors = [];
  } else if (caseId === 'remove-row-contract') {
    graph.manifest.pages[1].selectors[0].rowContract = null;
  } else if (caseId === 'static-row-source') {
    graph.componentSources['src/RowsPage.tsx'] = "export default function RowsPage() { const row = { reference: 'fixture-order-reference' }; return <ul><li data-testid=\"fixture-list-row\" data-row-key={row.reference}>{row.reference}</li></ul>; }\n";
  } else if (caseId === 'remove-row-key-attribute') {
    graph.componentSources['src/RowsPage.tsx'] = graph.componentSources['src/RowsPage.tsx'].replace(' data-row-key={row.reference}', '');
  } else if (caseId === 'row-key-expression-mismatch') {
    graph.manifest.pages[1].selectors[0].rowContract.keyExpression = 'row.businessKey';
  } else if (caseId === 'row-key-sensitive-source-property') {
    graph.componentSources['src/RowsPage.tsx'] = graph.componentSources['src/RowsPage.tsx'].replace('data-row-key={row.reference}', 'data-row-key={row.phone}');
    graph.manifest.pages[1].selectors[0].rowContract.keyExpression = 'row.phone';
  } else if (caseId === 'row-key-computed-call') {
    graph.componentSources['src/RowsPage.tsx'] = graph.componentSources['src/RowsPage.tsx'].replace('data-row-key={row.reference}', 'data-row-key={deriveKey(row)}');
  } else if (caseId.startsWith('row-class-')) {
    graph.manifest.pages[1].selectors[0].rowContract.keyClass = caseId.slice('row-class-'.length);
  } else {
    throw new Error(`unknown fixture mutation: ${caseId}`);
  }
}

for (const fixtureCase of fixture.cases) {
  const root = await mkdtemp(path.join(tmpdir(), 'ui-drift-validator-'));
  try {
    const graph = clone(fixture.baseline);
    mutate(fixtureCase.mutation, graph);
    await mkdir(path.join(root, 'src'), { recursive: true });
    await mkdir(path.join(root, 'tests'), { recursive: true });
    await writeFile(path.join(root, graph.routesPath), graph.routesSource);
    for (const [relative, source] of Object.entries(graph.componentSources)) {
      await writeFile(path.join(root, relative), source);
    }
    for (const [relative, source] of Object.entries(graph.playwrightSources)) {
      await writeFile(path.join(root, relative), source);
    }
    if (graph.outsideSymlink) await symlink('/etc/hosts', path.join(root, graph.outsideSymlink));
    const manifestPath = path.join(root, 'ui-manifest.json');
    await writeFile(manifestPath, `${JSON.stringify(graph.manifest, null, 2)}\n`);

    const result = spawnSync(process.execPath, [
      validatorPath,
      '--manifest', manifestPath,
      '--routes', path.join(root, graph.routesPath),
      '--manifest-schema', manifestSchemaPath,
      '--row-key-registry', productionRegistryPath,
      '--row-key-schema', rowSchemaPath,
      '--check-static',
    ], { cwd: root, encoding: 'utf8' });
    const output = `${result.stdout}${result.stderr}`;
    const succeeded = result.status === 0;
    const expectedSuccess = fixtureCase.expectedStatus === 'PASS';
    if (succeeded !== expectedSuccess || !output.includes(fixtureCase.expectedDiagnostic)) {
      throw new Error(`${fixtureCase.id}: status=${result.status}\n${output}`);
    }
    passed += 1;
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

console.log(`UI_DRIFT_VALIDATOR_TEST PASS cases=${passed}`);
