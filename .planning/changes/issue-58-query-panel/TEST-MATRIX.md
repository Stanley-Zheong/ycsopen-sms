# Issue 58 Query Panel Test Matrix

This matrix covers the cross-page production query-panel contract. Existing
phase tests continue to own page-specific permissions, data isolation, and API
semantics.

The focused acceptance run uses the repository's `local-google-chrome` project
with an explicit Playwright Chromium 151.0.7922.34 headless-shell executable.
It exercises the rendered browser UI and intercepts only the page APIs needed
to make each assertion deterministic. The JSON report was written outside the
repository and had SHA-256
`4a4358ec92a71060621290b8137dc7c7ab71cbd1b9f833fc1889bdd32d4c30b3`.
Its normalized, readable execution record is stored at
`EVIDENCE/playwright-query-panel-report.json`, including the exact command,
browser identity, source checksums, individual cases, and acceptance boundary.
This is not evidence of a real-service integration run: that boundary requires
the repository's Docker/Ruby harness, which is unavailable in this runner.

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OBL-ISSUE-58-ADMIN-TENANTS | PROJECT-UI-CONTRACT | issue-58-admin-tenants | T-ISSUE-58-QUERY-PANEL:playwright | pw-issue-58-admin-tenants | admin-tenants `/admin/tenants` | query-panel | C-ISSUE-58-ADMIN-TENANTS | Chromium opens the collapsed panel, verifies every label/control pair, applies all three client-side tenant filters, advances to page two, then reset clears every control, returns to page one and restores the initial rows; collapse and reload are also verified. | Use the exact 3-case command in `EVIDENCE/playwright-query-panel-report.json` with `--grep 'pw-issue-58-admin-tenants'` for an isolated replay. | MOCKED BROWSER PASS; checksum-bound record above. Real-service acceptance remains blocked by the recorded runner limitations. |
| OBL-ISSUE-58-OPERATION-AUDIT | PROJECT-UI-CONTRACT | issue-58-operation-audit | T-ISSUE-58-QUERY-PANEL:playwright | pw-issue-58-operation-audit | admin-operation-audit `/admin/system/logs` | query-panel | C-ISSUE-58-OPERATION-AUDIT | Chromium opens the collapsed panel, verifies every label/control pair, populates all five fields, observes every serialized query parameter and the filtered result, then reset clears every control, restores page one and the initial row; collapse and reload are also verified. | Use the exact 3-case command in `EVIDENCE/playwright-query-panel-report.json` with `--grep 'pw-issue-58-operation-audit'` for an isolated replay. | MOCKED BROWSER PASS; checksum-bound record above. Real-service acceptance remains blocked by the recorded runner limitations. |
| OBL-ISSUE-58-ADMIN-UPLINKS | PROJECT-UI-CONTRACT | issue-58-admin-uplinks | T-ISSUE-58-QUERY-PANEL:playwright | pw-issue-58-admin-uplinks | admin-uplinks `/admin/uplink` | query-panel | C-ISSUE-58-ADMIN-UPLINKS | Chromium scopes the first of two panels, verifies every label/control pair, populates all seven fields, observes every serialized query parameter and the filtered result, then reset clears every control and restores the initial rows; collapse and reload are also verified. | Use the exact 3-case command in `EVIDENCE/playwright-query-panel-report.json` with `--grep 'pw-issue-58-admin-uplinks'` for an isolated replay. | MOCKED BROWSER PASS; checksum-bound record above. Real-service acceptance remains blocked by the recorded runner limitations. |
| OBL-ISSUE-58-ADMIN-UNSUBSCRIBES | PROJECT-UI-CONTRACT | issue-58-admin-unsubscribes | T-ISSUE-58-QUERY-PANEL:playwright | pw-issue-58-admin-unsubscribes | admin-unsubscribes `/admin/unsubscribes` | query-panel | C-ISSUE-58-ADMIN-UNSUBSCRIBES | Existing phase browser regression expands the migrated query panel, applies a keyword in the request, resets it, and then verifies the statistics workflow remains available. | Use the exact 2-case command in `EVIDENCE/playwright-supplemental-report.json` with `--grep 'pw-issue-58-admin-unsubscribes'` for an isolated replay. | Supplemental MOCKED BROWSER PASS; exact command, source checksums, raw report SHA, and case set are in `EVIDENCE/playwright-supplemental-report.json`. |
| OBL-ISSUE-58-TENANT-UPLINKS | PROJECT-UI-CONTRACT | issue-58-tenant-uplinks | T-ISSUE-58-QUERY-PANEL:playwright | pw-issue-58-tenant-uplinks | tenant-uplinks `/tenant/uplink` | query-panel | C-ISSUE-58-TENANT-UPLINKS | Existing phase browser regression expands the migrated query panel, applies a keyword in the tenant-scoped request, resets it, and then verifies the independent auto-reply workflow remains available. | Use the exact 2-case command in `EVIDENCE/playwright-supplemental-report.json` with `--grep 'pw-issue-58-tenant-uplinks'` for an isolated replay. | Supplemental MOCKED BROWSER PASS; exact command, source checksums, raw report SHA, and case set are in `EVIDENCE/playwright-supplemental-report.json`. |
