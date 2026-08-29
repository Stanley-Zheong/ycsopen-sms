# Independent UI and Playwright Contract Review

## Reviewer

- Canonical identity: `/root/audit_ui_test_contract`
- Role: independent UI, selector, source-provenance, and Playwright contract auditor
- Review mode: strict read-only audit; `.planning/reviews/UI-CONTRACT-REVIEW.md` is the only file owned by this reviewer
- Review cycle: 1, post-Claude-cycle-1 corrections
- Attempt: 1
- Review date: 2026-08-30 (Asia/Shanghai)

## Review scope

Reviewed the complete current `.planning/` UI/Playwright contract, with emphasis on:

- The repository-present `validate-ui-contract.rb`, its support code, `validate-phase-entry.rb`, and destructive fixtures.
- All 42 declared UI phases, Phase 2 dependency closure, exact `--ui` entry wiring, and Phase 2 prototype-only evidence.
- Pencil/HTML/React source hierarchy, checksum binding, local uiskill availability, and pinned ycsan provenance.
- All 195 catalog element references, production ownership of direct UI references, and the prohibition on new UI in Phases 51-56.
- The production internal-error UI to observability-assurance trace, and Phase 56 reuse of Phase 45 selectors.
- Secure export artifact parsing, source reconciliation, masking inside the file, protected download, and audit acceptance.
- Whether `UI-ELEMENTS.md` is mechanically required to describe every element's action, state, permission, validation, selector, and Playwright/atomic-obligation trace.

The real phase packages do not yet exist. This review therefore distinguishes contract/validator quality from phase execution evidence; a fixture PASS is never treated as production completion.

## Commands and key raw output

### Validator syntax, current self-test, and catalog

```text
$ ruby -c .planning/tools/planning-validator-support.rb
Syntax OK
$ ruby -c .planning/tools/validate-phase-entry.rb
Syntax OK
$ ruby -c .planning/tools/validate-ui-contract.rb
Syntax OK
$ ruby -c .planning/tools/test-planning-validators.rb
Syntax OK
$ /usr/bin/env ruby .planning/tools/test-planning-validators.rb
planning_validator_self_test=PASS positive=phase_entry+ui+open_current_todo negative=missing_artifact,foreign_obligation,missing_selector,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,schema_conflict
$ /usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --assert-unique --assert-traced
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 selected=522 projects=19
```

The current self-test is executable and its listed negative cases work. Its UI negative coverage is limited to `UI_OWNED_SELECTOR_MISSING`; it has no production-mode fixture and no negative fixture for source type, row completeness, structured TEST-MATRIX linkage, checksum drift, route drift, or Playwright source semantics.

```text
$ rg -n 'mode.*production|evidence_kind.*production|UI_PRODUCTION_SOURCE_NOT_WEB|UI_PLAYWRIGHT_SOURCE_NOT_WEB|CHECKSUM_MISMATCH' .planning/tools/test-planning-validators.rb
(no matches)

$ rg -o 'expected_token: "[A-Z0-9_]+' .planning/tools/test-planning-validators.rb
PHASE_ARTIFACT_MISSING,FOREIGN_OBLIGATION_ID,UI_OWNED_SELECTOR_MISSING,
TODO_OWNED_CHECKBOX_MISSING,TODO_PRECHECKED,SCHEMA_MIGRATION_DUPLICATE
```

### Real Phase 2 fail-closed state

```text
$ /usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 02 --package console-design-system-prototype-foundation
ui_contract=BLOCKED errors=27
- PHASE_DIRECTORY_MISSING: .../.planning/phases/02-console-design-system-prototype-foundation
- UI_ARTIFACT_MISSING: .../02-UI-SPEC.md
- UI_ARTIFACT_MISSING: .../TEST-MATRIX.md
- UI_PENCIL_SOURCE_MISSING: expected at least one .pen
- UI_HTML_PROTOTYPE_MISSING: expected at least one .html
- MISSING_UI_ELEMENTS: .../UI-ELEMENTS.md
- MISSING_UI_CONTRACT_INVENTORY: .../EVIDENCE/ui-contract.json
- UI_MODE_MISMATCH: expected=prototype actual=-
- UI_PLAYWRIGHT_EVIDENCE_KIND_MISMATCH: expected=prototype actual=-
- UI_PROTOTYPE_LABEL_MISSING: TEST-MATRIX.md
- UI_PROTOTYPE_HTML_NOT_TRACED
phase2_ui_exit=1
```

This is correct lifecycle behavior. Phase 2 prototype evidence is explicitly separated in `PROJECT.md`, `EXECUTION-STANDARD.md`, `UI-TEST-CONTRACT.md`, and the artifact template; it cannot close later React production obligations.

### 56-phase and 42-UI-phase dependency/gate regression

An inline Ruby parser read all `### Phase` blocks, expanded dependency ranges, rejected non-prior/missing dependencies, and checked each `**UI hint**: yes` block for a UI contract, `validate-ui-contract.rb`, `validate-phase-entry.rb ... --ui`, and transitive Phase 2 closure.

```text
roadmap_ui=PASS phases=56 ui_phases=42 phase2_closure=41 ui_commands=42 ui_entries=42
```

Actual UI phases were:

```text
2,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,25,26,27,28,29,
32,33,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50
```

### Adversarial production fixture that the gates incorrectly accept

The following command used `Dir.mktmpdir`, wrote no shared-workspace artifact, and invoked the real validators from this repository:

```ruby
$ /usr/bin/env ruby - <<'RUBY'
require 'digest'; require 'fileutils'; require 'json'; require 'open3'; require 'tmpdir'; require 'rbconfig'
validator = File.expand_path('.planning/tools/validate-ui-contract.rb', Dir.pwd)
entry = File.expand_path('.planning/tools/validate-phase-entry.rb', Dir.pwd)
def write(path, body) = (FileUtils.mkdir_p(File.dirname(path)); File.write(path, body))
Dir.mktmpdir('ui-adversarial-audit-') do |root|
  phase = File.join(root, '.planning/phases/05-console-identity-platform-rbac')
  write(File.join(root,'.planning/ROADMAP.md'), "### Phase 5: Fixture\n**Package ID**: `console-identity-platform-rbac`\n**Depends on**: Nothing.\n")
  write(File.join(root,'.planning/PRD-OBLIGATIONS.md'), "- OBL-FIXTURE-UI | fixture | PROJECT-UI-CONTRACT | console-identity-platform-rbac | console-identity-platform-rbac-01 | element:admin-demo-home-main-submit | T-FIXTURE-UI:playwright | EVIDENCE/OBL-FIXTURE-UI.json | fixture\n")
  write(File.join(root,'.planning/SCHEMA-OWNERSHIP.md'), "| Schema ID | PRD data domain | Schema object/prefix | Owner package | Migration namespace | Dependencies | Compatibility | Rollback | Cross-owner protocol |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n| S-1 | Fixture | fixture.* | console-identity-platform-rbac | V100-V199 | - | expand-migrate-contract | rollback=forward-fix | Approval in DECISIONS.md |\n")
  write(File.join(phase,'05-SPEC.md'), "OBL-FIXTURE-UI\n")
  write(File.join(phase,'05-CONTEXT.md'), "context\n")
  write(File.join(phase,'INTENT.md'), "intent\n")
  write(File.join(phase,'DESIGN.md'), "Schema migrations: none\n")
  write(File.join(phase,'ITERATIONS.md'), "iterations\n")
  write(File.join(phase,'DECISIONS.md'), "decisions\n")
  write(File.join(phase,'TODO.md'), "- [ ] OBL-FIXTURE-UI\n")
  write(File.join(phase,'TEST-MATRIX.md'), "OBL-FIXTURE-UI admin-demo-home-main-submit\n")
  write(File.join(phase,'CLAUDE-REVIEW.md'), "pending\n")
  write(File.join(phase,'ENTRY-REVIEW.md'), "| Criterion ID | Verdict | Evidence | Command or inspection rule |\n| --- | --- | --- | --- |\n| C-1 | PASS | evidence | command |\n\n## Verdict\n\nPASS\n")
  write(File.join(phase,'05-01-PLAN.md'), '<task><files>x</files><action>x</action><verify>x</verify><done>x</done></task>')
  write(File.join(phase,'05-UI-SPEC.md'), "ui spec\n")
  selector='admin-demo-home-main-submit'; route='/admin/demo'
  write(File.join(phase,'UI-ELEMENTS.md'), "| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | PRD ID | Playwright ID |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n| demo #{route} | - | - | - | - | - | - | #{selector} | - | PW-1 |\n")
  pen=File.join(phase,'screen.pen'); html=File.join(phase,'prototype.html')
  manifest=File.join(phase,'manifest.txt'); fake_react=File.join(root,'web/not-react.txt'); fake_pw=File.join(root,'web/not-playwright.txt')
  write(pen,'pencil'); write(html,"#{route} #{selector}"); write(manifest,"#{route} #{selector}"); write(fake_react,"#{route} #{selector}"); write(fake_pw,selector)
  src=lambda{|p| {'path'=>p.delete_prefix(root+'/'),'sha256'=>Digest::SHA256.file(p).hexdigest}}
  inventory={'mode'=>'production','pencil'=>{'sources'=>[src.call(pen)]},'manifest'=>{'routes'=>[route],'test_ids'=>[selector],'sources'=>[src.call(manifest)]},'prototype'=>{'routes'=>[route],'test_ids'=>[selector],'sources'=>[src.call(html)]},'implementation'=>{'routes'=>[route],'test_ids'=>[selector],'sources'=>[src.call(fake_react)]},'playwright'=>{'test_ids'=>[selector],'evidence_kind'=>'production','sources'=>[src.call(fake_pw)]}}
  write(File.join(phase,'EVIDENCE/ui-contract.json'), JSON.pretty_generate(inventory))
  ui_out,ui_err,ui_status=Open3.capture3(RbConfig.ruby,validator,'--phase','05','--package','console-identity-platform-rbac',chdir:root)
  en_out,en_err,en_status=Open3.capture3(RbConfig.ruby,entry,'--phase','05','--package','console-identity-platform-rbac','--obligations','.planning/PRD-OBLIGATIONS.md','--entry-review','.planning/phases/05-console-identity-platform-rbac/ENTRY-REVIEW.md','--ui',chdir:root)
  puts "adversarial_fixture ui_exit=#{ui_status.exitstatus} entry_exit=#{en_status.exitstatus}"
  puts ui_out+ui_err+en_out+en_err
end
RUBY
```

Key raw output:

```text
adversarial_fixture ui_exit=0 entry_exit=0
ui_contract=PASS phase=05 package=console-identity-platform-rbac mode=production selectors=1 routes=1 owned_elements=1 owned_pages=0
phase_entry=PASS phase=05 package=console-identity-platform-rbac obligations=1 plans=1 ui=true schema_claims=none
```

The accepted fixture is deliberately invalid in four independent ways:

1. `implementation.sources` is `web/not-react.txt`, not React source.
2. `playwright.sources` is `web/not-playwright.txt`, not a Playwright test.
3. `UI-ELEMENTS.md` has `-` for permission, region, element type, data/validation, action/API effect, states/feedback, and PRD ID.
4. `TEST-MATRIX.md` is not the required table and only places the obligation and selector on one unstructured line.

The cause is visible in the validator: it checks only the route, selector format, nonempty Playwright ID, substring presence in `TEST-MATRIX.md`, `web/` path prefix, checksum, and source substring. `exact_obligation_trace` also scans IDs as unstructured text rather than parsing matrix rows and their associations.

### UI-reference production-owner audit

An inline Ruby audit parsed roadmap UI package IDs and all catalog `page:`/`element:` references.

```text
ui_owner_check=BLOCKED refs=373 element_refs=195 page_refs=178 ui_packages=42 non_ui_owner_refs=5
non_ui_owner 223 OBL-F-3-8-A statistics-aggregation-pipeline page:admin-statistics-resources
non_ui_owner 372 OBL-F-11-1-A statistics-aggregation-pipeline page:admin-statistics-channel
non_ui_owner 374 OBL-F-11-2-A statistics-aggregation-pipeline page:admin-statistics-tenant
non_ui_owner 376 OBL-F-11-3-A statistics-aggregation-pipeline page:admin-statistics-resources
non_ui_owner 511 OBL-EDGE-NETWORK-TIMEOUT secure-http-message-acceptance element:shared-secure-http-network-error-retry
late_phase_new_ui=PASS refs=0
```

Phase 34 explicitly excludes dashboards, has no UI contract/hint, and its entry command lacks `--ui`; Phase 23 is an HTTP API acceptance phase with the same absence of UI gating. Since the catalog says `Owner package` is the only implementation owner and `page:`/`element:` denotes a direct UI surface, these five direct UI references have no valid production UI owner/gate.

The previously identified special surfaces are correctly owned:

```text
OBL-IA-ADMIN-SYSTEM-CONFIG -> platform-system-configuration / playwright
OBL-IA-ADMIN-REVIEW-HISTORY -> resource-review-history / playwright
OBL-IA-TENANT-HELP-GUIDE -> tenant-help-center / playwright
OBL-IA-TENANT-HELP-API -> tenant-help-center / playwright
OBL-IA-TENANT-HELP-SERVICE -> tenant-help-center / playwright
OBL-NFR-OBS-HEALTH -> operational-dashboards / playwright
```

### Pencil/HTML/React contract and pinned visual provenance

```text
$ git -C /Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web remote get-url origin
https://github.com/Stanley-Zheong/ycsan.git
$ git -C /Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web rev-parse HEAD
f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b
$ git -C /Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web status --porcelain
(empty; clean)
$ shasum -a 256 <CSS and two screenshot paths>
6931f74f3bbe90f76b972c907b9b519bc93a348fe3e74ba20f7eacfb3ce1fc53  app/globals.css
640074fbfb8015e2e5e6a1cf1ba62f5af4e2064abef130e75165fc00f1fd2258  public/screenshots/homepage.png
080e19fecbffbcc3c84ebb5a55bbffd072201e03d2fe7fd1a1a247ea8a2423a3  public/screenshots/products.png
$ test -f /Users/laosanzheong/Documents/codebases/hengshi-jarvis/projectlogs/907/uiskill/SKILL.md
uiskill=PASS
```

The recorded remote, full SHA, clean tree, and all three SHA-256 values match `PROJECT.md`. Pencil is the visual source, HTML is the clickable interaction source, and React is the declared production source. Checksum mechanics work, but the adversarial fixture proves the current gate does not establish that a production source is actually React or Playwright.

### Internal-error/observability and final-release reuse

```text
OBL-EDGE-INTERNAL-ERROR | owner=console-identity-platform-rbac | element:shared-console-identity-internal-error-message | T-EDGE-INTERNAL-ERROR:playwright
OBL-NFR-OBS-CORRELATION | owner=observability-assurance | ui=- | T-NFR-OBS-CORRELATION:fault
OBL-DOD-09-COMPLAINT-RATIO | owner=final-release-acceptance | ui=- | T-DOD-09-COMPLAINT-RATIO:uat
late_phase_new_ui=PASS refs=0
```

Phase 5 owns the real shared 500 error UI and safe correlation identity; Phase 54 fault evidence links it to backend trace, alert, and full-chain logs without implementing UI. Phase 56 explicitly reuses Phase 45's route and `admin-complaint-ratio-dashboard-*` selector manifest and creates no final-release selector namespace.

### Secure export artifact content and masking

```text
$ /usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner secure-async-export --assert-unique --assert-traced
validation=PASS ... selected=9 ...
```

Phase 46 and `OBL-F-7-8-C` require each supported producer/format artifact to be decrypted and parsed, with headers, row count, typed values, ordering, immutable authorized source snapshot, and masked protected fields reconciled before encrypted/password-protected, expiring, re-authorized, audited download. The owner set also covers launch handoffs, async/fault behavior, unsubscribe/finance export reuse, and persistent job metadata.

## Findings

| Criterion ID | Verdict | Evidence | Command or inspection rule |
| --- | --- | --- | --- |
| UI-VAL-01 | BLOCKER | The adversarial production fixture uses `web/not-react.txt` as implementation and `web/not-playwright.txt` as Playwright evidence, yet both UI and phase-entry validators return exit 0. Current validation proves path prefix/checksum/string containment, not React DOM or Playwright source identity. | Run the recorded adversarial fixture. Production implementation must include approved React source types under `web/` and real route/DOM selector declarations; Playwright evidence must include approved `*.spec.*` sources and executable selector assertions. Add destructive fixtures for false source types and prototype-as-production claims. |
| UI-TRACE-02 | BLOCKER | The same fixture has `-` for permission, region, element type, data/validation, action/API effect, states/feedback, and PRD ID, plus a non-table TEST-MATRIX with no row-level association; both validators still PASS. `validate-ui-contract.rb:65-77,98-100` checks only three UI cells and substring presence; `exact_obligation_trace` scans unstructured IDs. | Parse and validate every required `UI-ELEMENTS.md` cell, using an explicit reasoned N/A form only where meaningful. Parse the required TEST-MATRIX table; require every owned atomic obligation and linked requirement to have row-level tests/evidence, every UI row's Playwright ID to resolve to a matrix Test ID, and every selector/obligation association to be bidirectional. Add broken fixtures for empty/placeholder cells, flat text, missing and mismatched row links. |
| UI-FIXTURE-03 | BLOCKER | Current self-test reports PASS but has only a prototype positive UI fixture and one selector-corruption UI negative. It does not exercise production mode, React/Playwright source semantics, checksum/route drift, complete UI columns, or atomic row linkage, so it cannot guard the two false-PASS paths above. | Extend `test-planning-validators.rb` with a valid production fixture and one destructive mutation per enforced invariant; require specific failure tokens for each mutation. |
| UI-OWNER-04 | BLOCKER | Five direct UI references are uniquely owned by non-UI phases: four `page:admin-statistics-*` obligations in Phase 34, which explicitly excludes dashboards, and one `shared-secure-http-network-error-retry` element in Phase 23. Neither phase has a UI contract or `--ui` gate. | Reassign/split direct UI presentation to a semantic production UI owner, or change backend-only aggregate/API obligations to `ui=-` and add separate UI obligations under the correct owner. Every package retaining `page:`/`element:` implementation ownership must be a UI phase and run `--ui`. Add this owner-vs-UI-phase invariant to the catalog validator. |
| UI-ROADMAP-05 | PASS | All 56 phases parse; all 42 UI phases have UI contracts, exact Ruby UI commands, `--ui` entry commands, valid prior dependencies, and Phase 2 transitive closure. | Run the recorded roadmap UI parser and require `phases=56 ui_phases=42 phase2_closure=41 ui_commands=42 ui_entries=42`. |
| UI-PROTOTYPE-06 | PASS | Phase 2 is explicitly prototype-only, binds Pencil/HTML/prototype Playwright, excludes React production, and its absent real package fails closed. Later UI phases declare production mode and cannot close through Phase 2 narrative evidence. | Inspect PROJECT/UI-TEST-CONTRACT/EXECUTION-STANDARD/template and run the real Phase 2 UI command. This contract PASS does not waive UI-VAL-01's machine-enforcement blocker. |
| UI-PROVENANCE-07 | PASS | Local uiskill exists; ycsan remote, full SHA, clean working tree, CSS checksum, and both screenshot checksums exactly match the pinned project contract. | Run the recorded git and `shasum -a 256` commands. |
| UI-CATALOG-08 | PASS | Catalog validator reports 522 records, 108/108 requirements, 56/56 owners, 195 element references, zero invalid element formats, and zero duplicate obligation/test/evidence identities. | Run `validate-prd-obligations.rb --assert-unique --assert-traced`. |
| UI-LATE-09 | PASS | Packages for Phases 51-56 own zero `page:` or `element:` references; their roadmap contracts add no UI. | Parse phase package IDs 51-56 and filter the catalog UI-reference field; require `late_phase_new_ui=PASS refs=0`. |
| UI-OWNER-FIXES-10 | PASS | System configuration, review history, all three Tenant help surfaces, and Admin API status now have focused production UI owners and Playwright traces in Phases 7, 15, 50, and 44. | Query the six recorded obligation IDs and compare to roadmap UI phases. |
| UI-500-11 | PASS | Phase 5 owns production safe-500 UI/correlation identity; Phase 54 owns injected-failure UI-ID-to-trace-to-alert/log assurance with no UI reference. | Query `OBL-EDGE-INTERNAL-ERROR` and `OBL-NFR-OBS-CORRELATION`; inspect Phases 5 and 54. |
| UI-FINAL-12 | PASS | Phase 56 has no UI reference/selector namespace and explicitly reuses Phase 45 production routes and `admin-complaint-ratio-dashboard-*` selectors for release UAT. | Query `OBL-DOD-09-COMPLAINT-RATIO`, scan for `element:*final-release`, and inspect Phase 56 primary surfaces. |
| UI-EXPORT-13 | PASS | Phase 46 and its nine owned obligations include real artifact parsing, source-to-file reconciliation, masking inside the artifact, authorization snapshot, protected expiring download, audit, and UI/fault/data acceptance layers. | Run the secure-export owner query and inspect Phase 46 success criterion 3 plus `OBL-F-7-8-C`. |

## Required corrections before recheck

1. Close `UI-VAL-01` and `UI-TRACE-02` in the validators, not only in prose.
2. Add destructive production fixtures that fail on every corrected invariant and preserve their exact failure tokens.
3. Resolve all five non-UI-owner direct UI references and enforce the invariant mechanically.
4. Rerun this same adversarial fixture; it must return nonzero from both UI and phase-entry validators for the intended reasons.
5. Rerun the catalog, 42-phase closure, late-phase UI, provenance, 500-flow, Phase 56, and export regressions unchanged.

## Verdict

BLOCKER

The planning prose now covers the requested UI/Playwright behaviors well, and the named prior UI ownership/observability/final-release/export issues are closed. Execution is still not authorized because the executable UI gate admits structurally incomplete UI contracts and non-React/non-Playwright production evidence, its fixture suite does not protect those invariants, and five catalog UI references remain owned by phases that do not run the UI gate.

---

# Cycle 1 — Attempt 2 recheck

## Reviewer

- Canonical identity: `/root/audit_ui_test_contract`
- Role: independent UI, selector, source-provenance, and Playwright contract auditor
- Review mode: strict read-only recheck; this report remains the only file owned by this reviewer
- Review cycle: 1, post-Claude-cycle-1 corrections
- Attempt: 2
- Review date: 2026-08-30 (Asia/Shanghai)

## Attempt 2 scope

Rechecked every Attempt 1 finding and reran the unchanged regressions. Additional adversarial coverage tested whether the new 12-column `UI-ELEMENTS.md` and 11-column `TEST-MATRIX.md` links reach the actual React route/component and the actual Playwright test block rather than stopping at declarative tables.

## Attempt 2 commands and key raw output

### Updated validator and fixture suite

```text
$ ruby -c .planning/tools/planning-validator-support.rb
Syntax OK
$ ruby -c .planning/tools/validate-ui-contract.rb
Syntax OK
$ ruby -c .planning/tools/validate-phase-entry.rb
Syntax OK
$ ruby -c .planning/tools/test-planning-validators.rb
Syntax OK
$ ruby -c .planning/tools/validate-prd-obligations.rb
Syntax OK

$ /usr/bin/env ruby .planning/tools/test-planning-validators.rb
planning_validator_self_test=PASS positive=phase_entry+prototype_ui+production_ui+open_current_todo negative=missing_artifact,foreign_obligation,missing_selector,ui_placeholder,free_text_test_matrix,missing_atomic_row,missing_atomic_link,wrong_behavior,wrong_requirement,wrong_catalog_test,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,prototype_as_production,fake_react_txt,fake_playwright_txt,comment_only_react,string_only_react,schema_conflict

$ /usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --assert-unique --assert-traced
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 ui_owners=42 non_ui_owner_refs=0 selected=522 projects=19
```

The updated suite now contains positive prototype and production fixtures and rejects placeholders, free text, missing/mismatched atomic links, prototype relabelling, fake source extensions, and comment/string-only React evidence.

### Exact Attempt 1 adversarial fixture replay

The Attempt 1 temporary fixture was rerun unchanged: old 10-column UI inventory, free-text matrix, `web/not-react.txt`, and `web/not-playwright.txt`.

```text
attempt1_fixture_replay ui_exit=1 entry_exit=1
BAD_UI_ELEMENTS_HEADER=PRESENT
BAD_UI_TEST_MATRIX_HEADER=PRESENT
UI_PRODUCTION_SOURCE_BAD_PATH=PRESENT
UI_PLAYWRIGHT_SOURCE_BAD_PATH=PRESENT
UI_CONTRACT_BLOCKED=PRESENT
ui_contract=BLOCKED errors=18
- BAD_UI_ELEMENTS_HEADER: ... expected=... 12 exact columns ...
- BAD_UI_TEST_MATRIX_HEADER: ... expected=... 11 exact columns ...
- UI_PRODUCTION_SOURCE_BAD_PATH: web/not-react.txt
- UI_PLAYWRIGHT_SOURCE_BAD_PATH: web/not-playwright.txt
phase_entry=BLOCKED errors=20
- UI_CONTRACT_BLOCKED
```

This closes Attempt 1 `UI-VAL-01` for the exact original reproduction.

### Updated 56-phase/UI-owner regression

```text
attempt2_structure=PASS phases=56 ui_phases=42 phase2_closure=41 ui_commands=42 ui_entries=42
attempt2_ui_owners=PASS refs=370 element_refs=195 non_ui_owner_refs=0 late_refs=0
OBL-F-3-8-A owner=statistics-aggregation-pipeline ui=- test=T-F-3-8-A:integration
OBL-F-11-1-A owner=statistics-aggregation-pipeline ui=- test=T-F-11-1-A:integration
OBL-F-11-2-A owner=statistics-aggregation-pipeline ui=- test=T-F-11-2-A:integration
OBL-F-11-3-A owner=operational-dashboards ui=page:admin-statistics-resources test=T-F-11-3-A:playwright
OBL-EDGE-NETWORK-TIMEOUT owner=tenant-console-send ui=element:shared-tenant-console-send-network-error-retry test=T-EDGE-NETWORK-TIMEOUT:playwright
```

The four backend aggregate obligations either have `ui=-` or moved to Phase 44 production presentation, and the network timeout/retry UI moved to Phase 26. `validate-prd-obligations.rb` now independently enforces `UI_REFERENCE_NON_UI_OWNER` and reports `non_ui_owner_refs=0`.

### New disconnected Playwright/React trace adversarial fixture

An inline `Dir.mktmpdir` fixture used the real validators and otherwise-valid artifacts:

- Exact 12-column UI row with obligation, requirement, behavior, catalog test, selector, and `PW-FIXTURE-ATOMIC-01`.
- Exact 11-column matrix row with the same IDs and unique `CASE-ATOMIC-01`.
- Checksum-valid `web/src/demo.tsx` containing `routes=[{path:'/admin/demo', element:null}]`; the selector occurs only in an exported dead component.
- Checksum-valid `web/e2e/demo.spec.ts` containing only `test('unrelated smoke', async ({page}) => { page.getByTestId('admin-demo-home-main-submit'); });`.
- The Playwright source deliberately contains no Playwright ID, obligation ID, Case ID, route navigation, action, `await`, or assertion.

Executed command:

```text
$ /usr/bin/env ruby - <<'RUBY'
# Build the valid 12/11-column production fixture above in Dir.mktmpdir;
# invoke the repository's validate-ui-contract.rb and validate-phase-entry.rb;
# print exit codes and whether the Playwright source contains PW/OBL/Case IDs.
RUBY
```

Key raw output:

```text
disconnected_trace_fixture ui_exit=0 entry_exit=0
ui_contract=PASS phase=05 package=console-identity-platform-rbac mode=production selectors=1 routes=1 owned_elements=1 owned_pages=0
phase_entry=PASS phase=05 package=console-identity-platform-rbac obligations=1 plans=1 ui=true schema_claims=none
playwright_source_contains_pwid=false obligation=false case_id=false
```

The matrix-to-source trace therefore remains declarative only. `validate_playwright_sources` separately finds any `test(`/`it(` and any selector getter anywhere in all listed files; it does not require the matrix Playwright ID/Case ID/obligation to identify the test, nor require the selector, action, and assertion inside that test block. The implementation check likewise permits a route rendering `null` while the selector lives in unrelated dead code.

### Artifact template contradiction

The current template JSON example was compared to the updated path rules:

```text
$ rg -n 'implementation|playwright|web/e2e|web/src|web/path' .planning/PHASE-ARTIFACT-TEMPLATE.md
225:  "implementation": {
228:    "sources": [{"path": "web/e2e/module/page.spec.ts", "sha256": "<64-hex>"}]
230:  "playwright": {
233:    "sources": [{"path": "web/path", "sha256": "<64-hex>"}]

$ ruby -e '<apply the validator path regexes to both example paths>'
template_implementation_path=web/e2e/module/page.spec.ts validator_accepts=false
template_playwright_path=web/path validator_accepts=false
```

The prose immediately below the JSON correctly requires implementation under `web/src/**/*.{tsx,ts,jsx,js}` and Playwright under `web/**/*.{spec,test}.*`; the copyable JSON example states the opposite and will deterministically fail the gate.

### Unchanged provenance, export, 500, Phase 56, and late-phase regressions

```text
brand_remote=https://github.com/Stanley-Zheong/ycsan.git
brand_head=f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b
brand_tree=CLEAN
6931f74f3bbe90f76b972c907b9b519bc93a348fe3e74ba20f7eacfb3ce1fc53  app/globals.css
640074fbfb8015e2e5e6a1cf1ba62f5af4e2064abef130e75165fc00f1fd2258  public/screenshots/homepage.png
080e19fecbffbcc3c84ebb5a55bbffd072201e03d2fe7fd1a1a247ea8a2423a3  public/screenshots/products.png
uiskill=PASS
secure_async_export selected=9
late_refs=0
```

- Phase 46 still requires parsed producer/format artifact headers, rows, typed values, ordering, source snapshot, in-file masking, encrypted/expiring re-authorized download, and audit.
- Phase 5 production safe-500 UI still links through Phase 54 fault correlation with no Phase 54 UI.
- Phase 56 still has `ui=-` and reuses Phase 45's production route/selector manifest.
- Phases 51-56 still introduce no direct UI references.

## Attempt 2 findings

| Criterion ID | Attempt 2 verdict | Evidence | Command or inspection rule |
| --- | --- | --- | --- |
| UI-VAL-01 | PASS | The exact old fake `.txt` fixture now returns UI exit 1 and entry exit 1 with source-path and table-header failures. Production-mode positive and negative fixtures exist. | Rerun the Attempt 1 fixture and updated self-test. |
| UI-TRACE-02 | BLOCKER | Structural row trace is fixed, but the disconnected fixture proves both gates PASS when the actual Playwright source contains none of its declared Playwright ID, obligation ID, or Case ID and performs no navigation/action/assertion. React route and selector can also live in disconnected dead code. | Require each applicable matrix Playwright ID and Case ID, plus its obligation identity, in the actual `test`/`it` block; require the linked selector and an awaited action/assertion in that same block. Bind route navigation and rendered selector evidence rather than accepting route syntax and a dead selector independently. Add a destructive disconnected-trace fixture. |
| UI-FIXTURE-03 | BLOCKER | The suite expanded substantially and closes all original mutations, but it has no negative fixture for a correct 12/11 table whose Playwright ID/Case/OBL are absent from source or whose selector is only referenced by an unrelated no-op test. | Add the disconnected fixture above and require a deterministic source-trace failure token. |
| UI-OWNER-04 | PASS | Catalog/UI ownership invariant is machine-enforced; the five prior non-UI references are corrected and global output is `ui_owners=42 non_ui_owner_refs=0`. | Run the global catalog validator and owner parser. |
| UI-TEMPLATE-14 | BLOCKER | The artifact template's copyable JSON maps `implementation` to a Playwright file and `playwright` to `web/path`; both are rejected by the new validator and contradict the template's own prose. | Change implementation example to `web/src/module/page.tsx` and Playwright example to a valid `web/e2e/module/page.spec.ts`, then validate the example paths with the production path rules. |
| UI-ROADMAP-05 | PASS | 56 phases, 42 UI phases, 41 later Phase 2 closures, 42 UI commands, and 42 `--ui` entries remain valid. | Run the Attempt 2 roadmap parser. |
| UI-PROTOTYPE-06 | PASS | Phase 2 remains explicit prototype mode and cannot close production evidence; prototype-as-production fixture fails. | Run updated self-test and real Phase 2 fail-closed command. |
| UI-PROVENANCE-07 | PASS | ycsan remote/full SHA/clean tree/checksums and uiskill path remain reproducible. | Run the recorded git/hash/path commands. |
| UI-CATALOG-08 | PASS | 522/108/56 validation, 195 element formats, 42 UI owners, and zero non-UI UI-reference owners pass. | Run global catalog validator. |
| UI-LATE-09 | PASS | Phases 51-56 own no direct UI reference. | Require `late_refs=0`. |
| UI-OWNER-FIXES-10 | PASS | Previously fixed system configuration, review history, Tenant help, API status, statistics presentation, and network retry surfaces have semantic production UI owners. | Query their atomic records and roadmap phases. |
| UI-500-11 | PASS | Phase 5 production UI and Phase 54 fault-correlation assurance remain separated and linked. | Query the two atomic records and inspect Phase 54 exclusions. |
| UI-FINAL-12 | PASS | Phase 56 reuses Phase 45 without introducing a selector/DOM namespace. | Query final UAT obligation and scan final-release UI refs. |
| UI-EXPORT-13 | PASS | Nine Phase 46 obligations retain artifact-content parsing, reconciliation, masking, protection, authorization, audit, UI, fault, and data coverage. | Run secure-export owner query. |

## Required corrections before Attempt 3

1. Extend matrix-to-source validation so a direct UI row cannot be closed by an unrelated Playwright smoke test and a dead selector reference.
2. Add a destructive fixture for missing Playwright ID/Case ID/obligation identity and disconnected/no-op selector use.
3. Correct the `PHASE-ARTIFACT-TEMPLATE.md` JSON source examples to match the executable validator.
4. Rerun the exact Attempt 1 and Attempt 2 adversarial fixtures plus all unchanged regressions.

## Verdict

BLOCKER

Attempt 2 closes the four originally reported implementation defects at their original reproduction boundaries. Execution remains unauthorized because the claimed atomic Playwright trace does not yet reach the actual test block/behavior, and the canonical artifact template still supplies source paths that the corrected validator rejects.

---

# Cycle 1 — Attempt 3 final recheck

## Reviewer

- Canonical identity: `/root/audit_ui_test_contract`
- Role: independent UI, selector, source-provenance, and Playwright contract auditor
- Review mode: strict read-only recheck; `.planning/reviews/UI-CONTRACT-REVIEW.md` is the only workspace file owned by this reviewer
- Review cycle: 1, post-Claude-cycle-1 corrections
- Attempt: 3
- Review date: 2026-08-30 (Asia/Shanghai)

## Attempt 3 scope

Replayed the Attempt 2 disconnected React/Playwright adversarial case against the new explicit design/production lifecycle. Rechecked same-block PW ID/Case ID/OBL ID binding, exact route navigation, linked-selector awaited action/assertion, dead-component-without-browser-closure rejection, missing execution evidence, the positive production fixture, the 42-UI-phase lifecycle, Phase 2 transitive closure, template source paths, all 522 catalog obligations/195 element references, Phases 51-56, ycsan provenance, uiskill, safe-500 observability handoff, Phase 56 reuse, and secure export content/masking acceptance.

## Attempt 3 commands and key raw output

### Syntax, destructive suite, and catalog

```text
$ for f in .planning/tools/planning-validator-support.rb .planning/tools/validate-ui-contract.rb .planning/tools/validate-phase-entry.rb .planning/tools/test-planning-validators.rb .planning/tools/validate-prd-obligations.rb; do ruby -c "$f" || exit 1; done
Syntax OK
Syntax OK
Syntax OK
Syntax OK
Syntax OK

$ /usr/bin/env ruby .planning/tools/test-planning-validators.rb
planning_validator_self_test=PASS positive=design_ui+production_ui+phase_entry_design+open_current_todo negative=missing_stage,missing_artifact,foreign_obligation,missing_selector,ui_placeholder,free_text_test_matrix,missing_atomic_row,missing_atomic_link,wrong_behavior,wrong_requirement,wrong_catalog_test,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,prototype_as_production,missing_pw_id,missing_case_id,missing_obl_id,unrelated_smoke,no_goto,no_action_or_assertion,dead_component_without_browser_closure,execution_missing,execution_fail,execution_checksum,fake_react_txt,fake_playwright_txt,comment_only_react,string_only_react,schema_conflict,template_path_regression

$ /usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --assert-unique --assert-traced
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 ui_owners=42 non_ui_owner_refs=0 selected=522 projects=19
```

The updated suite now exercises explicit stage selection, design-entry and production-exit positives, missing/mismatched same-block IDs, unrelated smoke, missing/wrong navigation, no awaited action/assertion, dead component without browser closure, missing/failed/checksum-invalid execution evidence, fake source types, comment/string-only React, and template path regression.

### Attempt 2 disconnected fixture replay under the new lifecycle

The same temporary production Phase 5 fixture now contained exact 12/11-column contracts, checksum-bound Pencil/HTML/manifest/prototype Playwright, a React route whose selector was in an unrendered component, and an unrelated production smoke test. The entry command used `--ui`; the exit command used explicit `--stage production`.

```text
$ /usr/bin/env ruby - <<'RUBY'
# Build the complete temporary fixture described above; run validate-phase-entry.rb --ui,
# then validate-ui-contract.rb --stage production. Mutate the production source/evidence
# for dead-locator, missing-execution, and complete-positive cases.
RUBY
attempt2_fixture_replay entry_exit=0 production_exit=1
phase_entry=PASS phase=05 package=console-identity-platform-rbac obligations=1 plans=1 ui_stage=design schema_claims=none
ui_contract=BLOCKED errors=4
- UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING: stage=production id=PW-FIXTURE-ATOMIC-01
- UI_PW_BLOCK_CASE_ID_MISSING: stage=production id=CASE-ATOMIC-01
- UI_PW_BLOCK_OBLIGATION_ID_MISSING: stage=production id=OBL-FIXTURE-ATOMIC
- UI_PW_BLOCK_ID_COMBINATION_MISSING: stage=production obligation=OBL-FIXTURE-ATOMIC
dead_component_without_browser_closure production_exit=1
ui_contract=BLOCKED errors=1
- UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING: stage=production obligation=OBL-FIXTURE-ATOMIC selector=admin-demo-home-main-submit
missing_execution production_exit=1
ui_contract=BLOCKED errors=1
- UI_PRODUCTION_EXECUTION_MISSING
complete_production_fixture production_exit=0
ui_contract=PASS phase=05 package=console-identity-platform-rbac stage=production mode=production selectors=1 routes=1 owned_elements=1 owned_pages=0
```

This correctly proves that `validate-phase-entry.rb --ui` is design-only, while the production exit independently rejects an unrelated smoke test, absent same-block identifiers, a dead locator/no browser closure, and missing execution evidence. A structurally complete production fixture passes.

### New exact-identifier boundary adversarial fixture

The same-block matcher was then tested for exact machine identity rather than substring containment. The full production fixture was otherwise valid: exact 12/11 tables, checksum-bound Pencil/HTML/React/prototype and production Playwright sources, linked route/action, and checksum-bound PASS execution report. The matrix required `PW-FIXTURE-ATOMIC-01`, `CASE-ATOMIC-01`, and `OBL-FIXTURE-ATOMIC`; the actual production test title contained only different, longer identifiers.

```text
$ /usr/bin/env ruby - <<'RUBY'
# Build a complete production fixture as above. Its production test title is:
# PW-FIXTURE-ATOMIC-010 CASE-ATOMIC-010 OBL-FIXTURE-ATOMIC-SUFFIX
# Invoke validate-ui-contract.rb --phase 05 --package console-identity-platform-rbac --stage production.
RUBY
token_boundary_full_gate production_exit=0
ui_contract=PASS phase=05 package=console-identity-platform-rbac stage=production mode=production selectors=1 routes=1 owned_elements=1 owned_pages=0
required=PW-FIXTURE-ATOMIC-01,CASE-ATOMIC-01,OBL-FIXTURE-ATOMIC
actual_title=PW-FIXTURE-ATOMIC-010 CASE-ATOMIC-010 OBL-FIXTURE-ATOMIC-SUFFIX
```

The direct helper reproduction isolates the cause:

```ruby
$ /usr/bin/env ruby - <<'RUBY'
require 'tmpdir'
require_relative '.planning/tools/planning-validator-support'
Dir.mktmpdir('pw-token-boundary-') do |dir|
  path = File.join(dir, 'collision.spec.ts')
  File.write(path, <<~TS)
    test("PW-FIXTURE-ATOMIC-010 CASE-ATOMIC-010 OBL-FIXTURE-ATOMIC-SUFFIX", async ({ page }) => {
      await page.goto("/admin/demo");
      await page.getByTestId("admin-demo-home-main-submit").click();
    });
  TS
  errors = []
  PlanningValidatorSupport.validate_playwright_matrix_blocks(
    [path],
    [{ playwright_id: 'PW-FIXTURE-ATOMIC-01', case_id: 'CASE-ATOMIC-01',
       obligation_id: 'OBL-FIXTURE-ATOMIC', route: '/admin/demo',
       selector: 'admin-demo-home-main-submit' }],
    errors,
    label: 'production'
  )
  puts "token_boundary_fixture=#{errors.empty? ? 'FALSE_PASS' : 'BLOCKED'} errors=#{errors.length}"
end
RUBY
token_boundary_fixture=FALSE_PASS errors=0
```

`planning-validator-support.rb:609-611` selects blocks with `metadata[block].include?(expected_id)`. It therefore treats a prefix of a different ID as the exact matrix identity. This violates the one-to-one atomic trace and lets an adjacent/suffixed PW ID, Case ID, and OBL ID jointly impersonate the required triple.

### 56-phase lifecycle, Phase 2 closure, and template paths

```text
$ ruby - <<'RUBY'
# Parse every phase block and exact lifecycle command.
RUBY
ui_lifecycle=PASS phases=56 ui=42 design_entries=42 production_exits=41 phase2_production=0 non_ui_production=0 phases_51_56_ui=0
ui_phase_numbers=2,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,25,26,27,28,29,32,33,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50

$ ruby - <<'RUBY'
# Parse roadmap dependencies with PlanningValidatorSupport and compute transitive ancestors.
RUBY
dependency_closure=PASS phases=56 ui=42 phase2_transitive=41 non_prior=0

$ ruby - <<'RUBY'
# Assert the template implementation and Playwright paths plus their prose rules.
RUBY
template_paths=PASS implementation=web/src/module/Page.tsx playwright=web/e2e/module/page.spec.ts
```

This closes Attempt 2's lifecycle and template blockers: there are exactly 42 UI phases, exactly 41 production exits, Phase 2 has no production stage, every later UI phase reaches Phase 2 transitively, Phases 51-56 add no UI, and the copyable template uses validator-accepted source locations.

### Provenance and unchanged special-surface regressions

```text
$ git -C /Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web remote get-url origin
https://github.com/Stanley-Zheong/ycsan.git
$ git -C /Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web rev-parse HEAD
f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b
$ git -C /Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web status --porcelain
(empty; clean)
$ shasum -a 256 <pinned CSS and screenshot paths>
6931f74f3bbe90f76b972c907b9b519bc93a348fe3e74ba20f7eacfb3ce1fc53  app/globals.css
640074fbfb8015e2e5e6a1cf1ba62f5af4e2064abef130e75165fc00f1fd2258  public/screenshots/homepage.png
080e19fecbffbcc3c84ebb5a55bbffd072201e03d2fe7fd1a1a247ea8a2423a3  public/screenshots/products.png
$ test -f /Users/laosanzheong/Documents/codebases/hengshi-jarvis/projectlogs/907/uiskill/SKILL.md && echo uiskill=PASS
uiskill=PASS

$ /usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner secure-async-export --assert-unique --assert-traced
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 ui_owners=42 non_ui_owner_refs=0 selected=9 projects=19

late_phase_new_ui=PASS phases=51-56 packages=6 refs=0
secure_export_contract=PASS parsed,headers,rows,typed,ordering,snapshot,masking,encrypted,expiry,reauth,audit
```

The catalog still assigns the real safe-500 UI to Phase 5 (`OBL-EDGE-INTERNAL-ERROR`) and the cross-stack injected-fault correlation to non-UI Phase 54 (`OBL-NFR-OBS-CORRELATION`). Phase 56's `OBL-DOD-09-COMPLAINT-RATIO` still has `ui=-` and explicitly reuses Phase 45's `admin-complaint-ratio-dashboard-*` production selectors. Phase 46 still requires artifact parsing and source reconciliation, masked protected fields inside the artifact, protected/expiring/re-authorized download, and audit.

## Attempt 3 findings

| Criterion ID | Attempt 3 verdict | Evidence | Command or inspection rule |
| --- | --- | --- | --- |
| UI-VAL-01 | PASS | Source extensions/locations, executable route/JSX syntax, prototype-versus-production evidence, checksums, and execution metadata are fail-closed in the suite. | Run syntax, destructive suite, and the complete positive production fixture. |
| UI-TRACE-02 | BLOCKER | The original missing-ID/unrelated-smoke/dead-locator reproduction is fixed, but the full production gate accepts three different longer identifiers as the required PW ID/Case ID/OBL ID because matching uses substring inclusion. Exact one-to-one atomic identity is therefore not enforced. | Match machine IDs as exact tokens, not substrings; e.g. use escaped identifier boundaries that reject adjacent `[A-Z0-9-]`, or parse title/annotations into exact tokens. Rerun `token_boundary_full_gate`; it must return nonzero with an exact-ID failure. |
| UI-FIXTURE-03 | BLOCKER | The expanded suite covers missing IDs but not adjacent/suffixed ID collisions, so it reports PASS while the complete token-boundary fixture false-passes. | Add a destructive `identifier_superstring_collision` case for PW ID, Case ID, and OBL ID and require the corresponding missing/exact-token error. |
| UI-OWNER-04 | PASS | Global catalog remains 522/108/56 with 195 valid element refs, 42 UI owners, and `non_ui_owner_refs=0`. | Run global catalog validation. |
| UI-TEMPLATE-14 | PASS | Copyable JSON and prose now use `web/src/module/Page.tsx` for implementation and `web/e2e/module/page.spec.ts` for Playwright. | Run template path regression. |
| UI-ROADMAP-05 | PASS | Exactly 42 design entries and 41 production exits exist; Phase 2 is design-only; all other UI phases transitively depend on Phase 2; Phases 51-56 have no UI. | Run lifecycle and transitive-dependency parsers. |
| UI-PROTOTYPE-06 | PASS | Entry reports `ui_stage=design`; production obligations require separate production stage; prototype-as-production remains a negative fixture. | Replay the disconnected fixture and run the destructive suite. |
| UI-PROVENANCE-07 | PASS | uiskill exists; ycsan remote/full SHA/clean tree and all three SHA-256 values reproduce the project pin. | Run recorded path/git/hash commands. |
| UI-CATALOG-08 | PASS | Exact atomic/requirement/behavior/catalog-test table links and all element formats pass global validation. | Run catalog and suite regressions. |
| UI-LATE-09 | PASS | Phases 51-56 own zero direct UI references and have no UI lifecycle commands. | Require `late_phase_new_ui=PASS refs=0`. |
| UI-500-11 | PASS | Production safe-500 UI remains in Phase 5 and Phase 54 remains non-UI fault assurance linking UI correlation ID to traces/alerts/logs. | Query the two catalog obligations. |
| UI-FINAL-12 | PASS | Phase 56 creates no DOM/UI and reuses Phase 45's production selector manifest. | Query `OBL-DOD-09-COMPLAINT-RATIO` and scan late-phase refs. |
| UI-EXPORT-13 | PASS | Phase 46's nine obligations and roadmap contract retain parsed artifact content, source reconciliation, in-file masking, protected delivery, expiry, re-authorization, and audit. | Run owner query and secure-export contract scan. |
| UI-EXECUTION-15 | PASS | Missing, failed, mismatched, stale, or checksum-invalid execution evidence fails; the report must repeat command, 40-hex commit, config, PASS, and exact direct-UI Case IDs. | Run the execution negative fixtures and `missing_execution` replay. |

## Required correction after Attempt 3

1. Replace substring ID matching in `validate_playwright_matrix_blocks` with exact machine-token matching for PW ID, Case ID, and OBL ID.
2. Add a full destructive fixture whose source contains only adjacent/suffixed variants of all three required IDs; it must fail production validation deterministically.
3. Rerun the complete token-boundary fixture, the repository self-test, catalog validation, lifecycle/closure parser, and unchanged regressions.

## Verdict

BLOCKER

Attempt 3 closes the prior lifecycle separation, missing-ID/unrelated-smoke/dead-locator, execution-evidence, and template-path blockers at their original boundaries. Execution remains unauthorized because the production gate still accepts non-exact PW ID/Case ID/OBL ID superstrings as the required atomic trace, and the destructive suite does not protect that boundary.

---

# Cycle 2 — Attempt 1 independent recheck

## Reviewer

- Canonical identity: `/root/audit_ui_test_contract`
- Role: independent UI, selector, source-provenance, and Playwright contract auditor
- Review mode: strict read-only recheck; `.planning/reviews/UI-CONTRACT-REVIEW.md` is the only workspace file owned by this reviewer
- Review cycle: 2, opened by the complete Cycle 1 Attempt 3 production false-PASS evidence and its implementation correction
- Attempt: 1
- Review date: 2026-08-30 (Asia/Shanghai)

## Cycle 2 Attempt 1 scope

Replayed the complete `token_boundary_full_gate` fixture recorded in Cycle 1 Attempt 3. Audited delimiter-bounded exact PW ID, Case ID, and OBL ID matching, the permanent destructive collision fixture, Attempt 2's unrelated-smoke/dead-locator/wrong-route cases, execution evidence, design/production lifecycle separation, all 42 UI phases and 41 production exits, Phase 2 transitive closure, canonical template paths, all 522 catalog obligations/195 element references, and Phases 51-56's no-new-UI constraint.

## Cycle 2 Attempt 1 commands and key raw output

### Validator implementation, syntax, full destructive suite, and catalog

```text
$ nl -ba .planning/tools/planning-validator-support.rb | sed -n '590,655p'
598  def exact_metadata_token?(metadata, token)
599    escaped = Regexp.escape(token)
600    metadata.match?(/(?:\A|[^A-Za-z0-9_-])#{escaped}(?:\z|[^A-Za-z0-9_-])/) 
601  end
614  playwright_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:playwright_id)) }
615  case_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:case_id)) }
616  obligation_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:obligation_id)) }

$ for f in .planning/tools/planning-validator-support.rb .planning/tools/validate-ui-contract.rb .planning/tools/validate-phase-entry.rb .planning/tools/test-planning-validators.rb .planning/tools/validate-prd-obligations.rb; do ruby -c "$f" || exit 1; done
Syntax OK
Syntax OK
Syntax OK
Syntax OK
Syntax OK

$ /usr/bin/env ruby .planning/tools/test-planning-validators.rb
planning_validator_self_test=PASS positive=design_ui+production_ui+phase_entry_design+open_current_todo negative=missing_stage,missing_artifact,foreign_obligation,missing_selector,ui_placeholder,free_text_test_matrix,missing_atomic_row,missing_atomic_link,wrong_behavior,wrong_requirement,wrong_catalog_test,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,prototype_as_production,missing_pw_id,missing_case_id,missing_obl_id,metadata_token_boundary,unrelated_smoke,no_goto,no_action_or_assertion,dead_component_without_browser_closure,execution_missing,execution_fail,execution_checksum,fake_react_txt,fake_playwright_txt,comment_only_react,string_only_react,schema_conflict,template_path_regression

$ /usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --assert-unique --assert-traced
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 ui_owners=42 non_ui_owner_refs=0 selected=522 projects=19
```

The permanent fixture at `test-planning-validators.rb:365-376` changes all three identifiers to adjacent/suffixed variants and requires `UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING`. The suite's PASS therefore protects the exact failure boundary that was absent in Cycle 1.

### Complete Cycle 1 false-PASS fixture replay

Executed the same complete checksum-bound temporary production fixture recorded in Cycle 1 Attempt 3, unchanged except that it invoked the corrected validator. It contains exact 12/11 tables, Pencil/HTML/React sources, prototype Playwright, linked production route/action, and PASS execution metadata; only its actual production title uses longer, different identifiers.

```text
$ /usr/bin/env ruby - <<'RUBY'
# Rebuild the complete token_boundary_full_gate Dir.mktmpdir fixture from Cycle 1
# Attempt 3 and invoke validate-ui-contract.rb --phase 05
# --package console-identity-platform-rbac --stage production.
RUBY
token_boundary_full_gate production_exit=1
ui_contract=BLOCKED errors=4
- UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING: stage=production id=PW-FIXTURE-ATOMIC-01
- UI_PW_BLOCK_CASE_ID_MISSING: stage=production id=CASE-ATOMIC-01
- UI_PW_BLOCK_OBLIGATION_ID_MISSING: stage=production id=OBL-FIXTURE-ATOMIC
- UI_PW_BLOCK_ID_COMBINATION_MISSING: stage=production obligation=OBL-FIXTURE-ATOMIC
UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING=PRESENT
UI_PW_BLOCK_CASE_ID_MISSING=PRESENT
UI_PW_BLOCK_OBLIGATION_ID_MISSING=PRESENT
UI_PW_BLOCK_ID_COMBINATION_MISSING=PRESENT
required=PW-FIXTURE-ATOMIC-01,CASE-ATOMIC-01,OBL-FIXTURE-ATOMIC
actual_title=PW-FIXTURE-ATOMIC-010 CASE-ATOMIC-010 OBL-FIXTURE-ATOMIC-SUFFIX
```

The exact original false-PASS is closed at the full production gate, with all three required exact-ID failure tokens present.

### Delimiter-boundary positive, prefix-collision, and suffix-collision checks

```text
$ /usr/bin/env ruby - <<'RUBY'
# Call validate_playwright_matrix_blocks with one exact punctuation-delimited title,
# one title containing only suffixed IDs, and one containing only prefixed IDs.
RUBY
exact_delimited=PASS errors=0 tokens=
suffix_collision=BLOCKED errors=4 tokens=UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING,UI_PW_BLOCK_CASE_ID_MISSING,UI_PW_BLOCK_OBLIGATION_ID_MISSING,UI_PW_BLOCK_ID_COMBINATION_MISSING
prefix_collision=BLOCKED errors=4 tokens=UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING,UI_PW_BLOCK_CASE_ID_MISSING,UI_PW_BLOCK_OBLIGATION_ID_MISSING,UI_PW_BLOCK_ID_COMBINATION_MISSING
```

Normal punctuation delimiters remain valid. Adjacent alphanumeric, hyphen, or underscore characters prevent a false exact match on either side.

### Attempt 2 disconnected/dead/unrelated and execution-evidence replay

The complete temporary production fixture was mutated one condition at a time and its checksum-bound inventory was regenerated after every source mutation.

```text
$ /usr/bin/env ruby - <<'RUBY'
# Build a complete production fixture; run unrelated-smoke, dead-locator,
# wrong-goto, missing-execution, complete-positive, and design-stage cases.
RUBY
unrelated_smoke production_exit=1 expected_token=UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING=PRESENT
ui_contract=BLOCKED errors=4
- UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING: stage=production id=PW-FIXTURE-ATOMIC-01
- UI_PW_BLOCK_CASE_ID_MISSING: stage=production id=CASE-ATOMIC-01
- UI_PW_BLOCK_OBLIGATION_ID_MISSING: stage=production id=OBL-FIXTURE-ATOMIC
- UI_PW_BLOCK_ID_COMBINATION_MISSING: stage=production obligation=OBL-FIXTURE-ATOMIC
dead_locator production_exit=1 expected_token=UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING=PRESENT
ui_contract=BLOCKED errors=1
- UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING: stage=production obligation=OBL-FIXTURE-ATOMIC selector=admin-demo-home-main-submit
wrong_goto production_exit=1 expected_token=UI_PW_BLOCK_GOTO_MISSING=PRESENT
ui_contract=BLOCKED errors=1
- UI_PW_BLOCK_GOTO_MISSING: stage=production obligation=OBL-FIXTURE-ATOMIC route=/admin/demo
missing_execution production_exit=1 expected_token=UI_PRODUCTION_EXECUTION_MISSING=PRESENT
ui_contract=BLOCKED errors=1
- UI_PRODUCTION_EXECUTION_MISSING
complete_production production_exit=0 expected_token=ui_contract=PASS=PRESENT
ui_contract=PASS phase=05 package=console-identity-platform-rbac stage=production mode=production selectors=1 routes=1 owned_elements=1 owned_pages=0
design_stage_ignores_unimplemented_production production_exit_source=unrelated design_exit=0
ui_contract=PASS phase=05 package=console-identity-platform-rbac stage=design mode=production selectors=1 routes=1 owned_elements=1 owned_pages=0
```

This confirms the intended lifecycle: the design gate evaluates Pencil/HTML/prototype evidence and does not require not-yet-written React production behavior; the production gate independently rejects unrelated, dead, disconnected, wrong-route, and missing-execution evidence. The permanent suite additionally rejects failed and checksum-mismatched execution reports.

### Lifecycle coverage, Phase 2 closure, template paths, and late phases

```text
$ ruby - <<'RUBY'
# Parse every roadmap phase/lifecycle command and compute Phase 2 ancestors.
RUBY
ui_lifecycle=PASS phases=56 ui=42 design_entries=42 production_exits=41 phase2_production=0 phase2_transitive=41 non_ui_production=0 phases_51_56_ui=0

$ ruby - <<'RUBY'
# Validate copyable template paths and validate-phase-entry.rb --ui wiring.
RUBY
template_paths=PASS implementation=web/src/module/Page.tsx playwright=web/e2e/module/page.spec.ts
phase_entry_ui_stage=PASS --ui=>--stage design

$ ruby - <<'RUBY'
# Resolve Phase 51-56 packages and filter catalog page:/element: ownership.
RUBY
late_phase_new_ui=PASS phases=51-56 refs=0
```

There are exactly 42 design entries and 41 production exits. Phase 2 is the sole design-only UI phase, every other UI phase reaches it transitively, no non-UI phase carries a production UI exit, and Phases 51-56 introduce no UI.

## Cycle 2 Attempt 1 findings

| Criterion ID | Verdict | Evidence | Command or inspection rule |
| --- | --- | --- | --- |
| C2-UI-TRACE-01 | PASS | The unchanged complete production collision fixture now exits 1 and reports exact PW ID, Case ID, OBL ID, and combination failures. Prefix and suffix variants are both rejected; punctuation-delimited exact IDs pass. | Replay `token_boundary_full_gate` and the delimiter helper. |
| C2-UI-FIXTURE-02 | PASS | The permanent destructive suite contains `metadata_token_boundary` and passes alongside missing-ID, unrelated, navigation, action/assertion, execution, fake-source, and template-path mutations. | Run `test-planning-validators.rb` and require its full PASS token list. |
| C2-UI-DISCONNECTED-03 | PASS | Unrelated smoke, dead locator, and wrong linked route each fail production with their deterministic token; the complete production fixture passes. | Run the Attempt 2 replay fixture. |
| C2-UI-EXECUTION-04 | PASS | Missing execution blocks the full fixture; permanent tests also reject FAIL and checksum mismatch, while validator code verifies metadata/report equality and exact Case-ID set. | Run execution mutations and inspect `validate_production_execution`. |
| C2-UI-LIFECYCLE-05 | PASS | `validate-phase-entry.rb --ui` invokes design only; there are 42 design entries, 41 production exits, no Phase 2 production exit, and transitive Phase 2 closure for all 41 production UI phases. | Run lifecycle parser and entry-wiring check. |
| C2-UI-TEMPLATE-06 | PASS | Canonical template paths remain `web/src/module/Page.tsx` and `web/e2e/module/page.spec.ts`, matching executable validator path laws. | Run template path check and permanent regression. |
| C2-UI-CATALOG-07 | PASS | Catalog remains 522 obligations, 108/108 requirements, 56/56 owners, 195 valid element refs, 42 UI owners, and zero non-UI-owner direct refs. | Run global catalog validation. |
| C2-UI-LATE-08 | PASS | Phases 51-56 own zero direct UI references and carry no UI lifecycle. | Run late-phase owner/reference scan. |

## Verdict

PASS

No BLOCKER. No WARNING. Cycle 1 Attempt 3's complete production false-PASS is closed at its exact reproduction boundary, the correction is protected by a permanent destructive fixture, and all requested UI/Playwright lifecycle, execution, roadmap, template, and catalog regressions pass.
