#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"
require_relative "planning-validator-support"

tool_dir = __dir__
phase_validator = File.join(tool_dir, "validate-phase-entry.rb")
ui_validator = File.join(tool_dir, "validate-ui-contract.rb")

def run_validator(root, command, expected_success:, expected_token:)
  stdout, stderr, status = Open3.capture3(*command, chdir: root)
  output = stdout + stderr
  if status.success? != expected_success
    abort "validator status mismatch expected_success=#{expected_success}:\n#{output}"
  end
  abort "validator output missing #{expected_token}:\n#{output}" unless output.include?(expected_token)
  output
end

def write(path, content)
  FileUtils.mkdir_p(File.dirname(path))
  File.write(path, content)
end

Dir.mktmpdir("planning-validator-test-") do |root|
  planning = File.join(root, ".planning")
  phase_dir = File.join(planning, "phases/02-console-design-system-prototype-foundation")
  evidence_dir = File.join(phase_dir, "EVIDENCE")
  FileUtils.mkdir_p(evidence_dir)

  write(
    File.join(planning, "ROADMAP.md"),
    <<~MARKDOWN
      # Fixture Roadmap

      ### Phase 2: Prototype UI
      **Package ID**: `console-design-system-prototype-foundation`
      **Depends on**: Nothing.

      ### Phase 3: Foreign domain
      **Package ID**: `crypto-storage-bootstrap`
      **Depends on**: Phase 2.
    MARKDOWN
  )
  write(
    File.join(planning, "PRD-OBLIGATIONS.md"),
    <<~MARKDOWN
      - OBL-FIXTURE-PAGE | fixture | PROJECT-UI-CONTRACT | console-design-system-prototype-foundation | console-design-system-prototype-foundation-01 | page:admin-demo-home | T-FIXTURE-PAGE:static | EVIDENCE/OBL-FIXTURE-PAGE.json | The page exists.
      - OBL-FIXTURE-ELEMENT | fixture | PROJECT-UI-CONTRACT | console-design-system-prototype-foundation | console-design-system-prototype-foundation-02 | element:admin-demo-home-main-submit | T-FIXTURE-ELEMENT:playwright | EVIDENCE/OBL-FIXTURE-ELEMENT.json | The action exists.
      - OBL-FIXTURE-FOREIGN | fixture | PROJECT-DATA-MODEL | crypto-storage-bootstrap | crypto-storage-bootstrap-01 | element:tenant-crypto-key-main-rotate | T-FIXTURE-FOREIGN:playwright | EVIDENCE/OBL-FIXTURE-FOREIGN.json | Foreign obligation.
    MARKDOWN
  )
  write(
    File.join(planning, "SCHEMA-OWNERSHIP.md"),
    <<~MARKDOWN
      # Fixture Schema Registry

      | Schema ID | PRD data domain | Schema object/prefix | Owner package | Migration namespace | Dependencies | Compatibility | Rollback | Cross-owner protocol |
      | --- | --- | --- | --- | --- | --- | --- | --- | --- |
      | SCHEMA-P02 | Prototype registry | ycs.sms.console-design-system-prototype-foundation.* | console-design-system-prototype-foundation | V1100-V1199 | - | expand-migrate-contract | rollback=forward-fix | Approval is recorded in DECISIONS.md. |
      | SCHEMA-P03 | Crypto | ycs.sms.crypto-storage-bootstrap.* | crypto-storage-bootstrap | V1200-V1299 | console-design-system-prototype-foundation | expand-migrate-contract | rollback=forward-fix | Approval is recorded in DECISIONS.md. |
    MARKDOWN
  )

  owned_trace = "OBL-FIXTURE-PAGE\nOBL-FIXTURE-ELEMENT\n"
  write(File.join(phase_dir, "02-SPEC.md"), "# Spec\n#{owned_trace}")
  write(File.join(phase_dir, "02-CONTEXT.md"), "# Context\nFixture context.\n")
  write(File.join(phase_dir, "INTENT.md"), "# Intent\nFixture.\n")
  write(File.join(phase_dir, "DESIGN.md"), "# Design\nSchema migrations: declared\n")
  write(File.join(phase_dir, "ITERATIONS.md"), "# Iterations\nFixture.\n")
  write(File.join(phase_dir, "DECISIONS.md"), "# Decisions\nFixture.\n")
  write(File.join(phase_dir, "TODO.md"), "# TODO\n- [ ] OBL-FIXTURE-PAGE\n- [ ] OBL-FIXTURE-ELEMENT\n")
  write(
    File.join(phase_dir, "TEST-MATRIX.md"),
    <<~MARKDOWN
      # Test Matrix

      | Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
      | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
      | OBL-FIXTURE-PAGE | PROJECT-UI-CONTRACT | console-design-system-prototype-foundation-01 | T-FIXTURE-PAGE:static | PW-FIXTURE-PAGE | admin-demo-home /admin/demo | admin-demo-home-main-submit | CASE-PROTOTYPE-PAGE | prototype page renders | playwright test prototype.spec.ts | EVIDENCE/prototype-page.json |
      | OBL-FIXTURE-ELEMENT | PROJECT-UI-CONTRACT | console-design-system-prototype-foundation-02 | T-FIXTURE-ELEMENT:playwright | PW-FIXTURE-ELEMENT | admin-demo-home /admin/demo | admin-demo-home-main-submit | CASE-PROTOTYPE-ELEMENT | prototype submit action | playwright test prototype.spec.ts | EVIDENCE/prototype-element.json |
    MARKDOWN
  )
  write(File.join(phase_dir, "CLAUDE-REVIEW.md"), "# Claude Review\nPending execution review.\n")
  write(
    File.join(phase_dir, "ENTRY-REVIEW.md"),
    <<~MARKDOWN
      # Entry Review

      | Criterion ID | Verdict | Evidence | Command or inspection rule |
      | --- | --- | --- | --- |
      | ENTRY-01 | PASS | EVIDENCE/entry.json | ruby validator |

      ## Verdict

      PASS
    MARKDOWN
  )
  write(
    File.join(phase_dir, "02-01-PLAN.md"),
    <<~PLAN
      ---
      phase: 02-console-design-system-prototype-foundation
      plan: "01"
      wave: 0
      depends_on: []
      files_modified:
        - web/src/fixture.tsx
        - web/src/generated-contract.json
      ---

      <task type="auto">
        <files>web/src/fixture.tsx</files>
        <action>Implement fixture behavior.</action>
        <verify>Run fixture verification.</verify>
        <done>Fixture evidence is deterministic.</done>
      </task>
    PLAN
  )
  write(
    File.join(phase_dir, "02-02-PLAN.md"),
    <<~PLAN
      ---
      phase: 02-console-design-system-prototype-foundation
      plan: "02"
      wave: 1
      depends_on: [02-01]
      files_modified:
        - web/src/fixture.css
      ---

      <task type="auto">
        <files>web/src/fixture.css</files>
        <read_first>
          - web/src/generated-contract.json
        </read_first>
        <action>Style fixture behavior.</action>
        <verify>Run fixture style verification.</verify>
        <done>Fixture style evidence is deterministic.</done>
      </task>
    PLAN
  )
  write(
    File.join(phase_dir, "02-03-PLAN.md"),
    <<~PLAN
      ---
      phase: 02-console-design-system-prototype-foundation
      plan: "03"
      wave: 2
      depends_on: [02-02]
      files_modified:
        - web/src/fixture.test.tsx
      ---

      <task type="auto">
        <files>web/src/fixture.test.tsx</files>
        <action>Verify fixture behavior.</action>
        <verify>Run fixture regression verification.</verify>
        <done>Fixture regression evidence is deterministic.</done>
      </task>
    PLAN
  )
  write(
    File.join(phase_dir, "SCHEMA-CLAIMS.md"),
    <<~MARKDOWN
      # Schema Claims

      | Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
      | --- | --- | --- | --- | --- | --- | --- | --- |
      | SC-02-001 | ycs.sms.console-design-system-prototype-foundation.registry | console-design-system-prototype-foundation | V1100 | - | expand | forward-compatible drop of unused registry | - |
    MARKDOWN
  )
  write(File.join(phase_dir, "02-UI-SPEC.md"), "# UI Spec\nadmin-demo-home /admin/demo\n")
  pen_path = File.join(phase_dir, "prototype.pen")
  write(pen_path, "pencil fixture")

  selector = "admin-demo-home-main-submit"
  route = "/admin/demo"
  ui_elements = <<~MARKDOWN
    # UI Elements

    | Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
    | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
    | admin-demo-home /admin/demo | admin-only | main | submit button | required text; UTF-8 | submit prototype; no production API | default,focus,disabled,success,error | admin-demo-home-main-submit | OBL-FIXTURE-PAGE,OBL-FIXTURE-ELEMENT,PROJECT-UI-CONTRACT | console-design-system-prototype-foundation-01,console-design-system-prototype-foundation-02 | T-FIXTURE-PAGE:static,T-FIXTURE-ELEMENT:playwright | PW-FIXTURE-PAGE,PW-FIXTURE-ELEMENT |
  MARKDOWN
  write(File.join(phase_dir, "UI-ELEMENTS.md"), ui_elements)

  manifest_path = File.join(phase_dir, "selector-registry.json")
  html_path = File.join(phase_dir, "design-output/prototype.html")
  playwright_path = File.join(phase_dir, "prototype.spec.ts")
  write(manifest_path, JSON.generate({ routes: [route], test_ids: [selector] }))
  write(html_path, %(<main data-route="#{route}"><button data-testid="#{selector}">Submit</button></main>))
  write(
    playwright_path,
    <<~TYPESCRIPT
      test("PW-FIXTURE-PAGE CASE-PROTOTYPE-PAGE OBL-FIXTURE-PAGE", async ({ page }) => {
        await page.goto("#{route}");
        await expect(page.getByTestId("#{selector}")).toBeVisible();
      });
      test("PW-FIXTURE-ELEMENT CASE-PROTOTYPE-ELEMENT OBL-FIXTURE-ELEMENT", async ({ page }) => {
        await page.goto("#{route}");
        await page.getByTestId("#{selector}").click();
      });
    TYPESCRIPT
  )

  relative = ->(path) { path.delete_prefix(root + File::SEPARATOR) }
  source = lambda do |path|
    { "path" => relative.call(path), "sha256" => Digest::SHA256.file(path).hexdigest }
  end
  inventory = {
    "mode" => "prototype",
    "pencil" => { "sources" => [source.call(pen_path)] },
    "manifest" => { "routes" => [route], "test_ids" => [selector], "sources" => [source.call(manifest_path)] },
    "prototype" => { "routes" => [route], "test_ids" => [selector], "sources" => [source.call(html_path)] },
    "prototype_playwright" => { "test_ids" => [selector], "evidence_kind" => "prototype", "sources" => [source.call(playwright_path)] }
  }
  write(File.join(evidence_dir, "ui-contract.json"), JSON.pretty_generate(inventory))

  phase_command = [
    RbConfig.ruby, phase_validator,
    "--phase", "02",
    "--package", "console-design-system-prototype-foundation",
    "--obligations", ".planning/PRD-OBLIGATIONS.md",
    "--entry-review", ".planning/phases/02-console-design-system-prototype-foundation/ENTRY-REVIEW.md",
    "--ui"
  ]
  ui_command = [RbConfig.ruby, ui_validator, "--phase", "02", "--package", "console-design-system-prototype-foundation", "--stage", "design"]

  run_validator(root, ui_command, expected_success: true, expected_token: "ui_contract=PASS")
  run_validator(root, phase_command, expected_success: true, expected_token: "phase_entry=PASS")

  entry_evidence_path = File.join(phase_dir, "ENTRY-EVIDENCE.md")
  entry_evidence_body = <<~MARKDOWN
    # Entry Evidence

    Review subject commit: `#{'c' * 40}`
    Evidence recorder identity: fixture-independent-reviewer
    Tool boundary: read-only repository and local command execution; no implementation edits

    Exit status: `0`
    Exit status: `0`
    Exit status: `0`
    Exit status: `0`
  MARKDOWN
  write(entry_evidence_path, entry_evidence_body)
  entry_evidence_digest = Digest::SHA256.file(entry_evidence_path).hexdigest
  entry_review_path = File.join(phase_dir, "ENTRY-REVIEW.md")
  entry_review_body = File.read(entry_review_path)
  write(entry_review_path, entry_review_body.sub("# Entry Review", "# Entry Review\n\nENTRY-EVIDENCE-SHA256: #{entry_evidence_digest}"))
  evidence_phase_command = phase_command + ["--entry-evidence", relative.call(entry_evidence_path)]
  run_validator(root, evidence_phase_command, expected_success: true, expected_token: "phase_entry=PASS")
  write(entry_review_path, File.read(entry_review_path).sub(entry_evidence_digest, "0" * 64))
  run_validator(root, evidence_phase_command, expected_success: false, expected_token: "ENTRY_EVIDENCE_DIGEST_MISSING")
  write(entry_review_path, entry_review_body)

  plan_01_path = File.join(phase_dir, "02-01-PLAN.md")
  plan_02_path = File.join(phase_dir, "02-02-PLAN.md")
  plan_03_path = File.join(phase_dir, "02-03-PLAN.md")
  plan_01_body = File.read(plan_01_path)
  plan_02_body = File.read(plan_02_path)
  plan_03_body = File.read(plan_03_path)

  write(plan_02_path, plan_02_body.sub("depends_on: [02-01]", "depends_on: [02-99]"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_DEPENDENCY_UNKNOWN")
  write(plan_02_path, plan_02_body.sub("depends_on: [02-01]", "depends_on: [02-02]"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_DEPENDENCY_SELF")
  write(plan_02_path, plan_02_body)

  write(plan_02_path, plan_02_body.sub("depends_on: [02-01]", "depends_on: []"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_ARTIFACT_DEPENDENCY_MISSING")
  write(plan_02_path, plan_02_body)
  run_validator(root, phase_command, expected_success: true, expected_token: "phase_entry=PASS")

  shared_file = File.join(root, "web/src/shared-existing.ts")
  write(shared_file, "export const sharedExisting = true;\n")
  plan_01_shared_body = plan_01_body.sub(
    "  - web/src/generated-contract.json",
    "  - web/src/generated-contract.json\n  - web/src/shared-existing.ts"
  )
  plan_03_shared_body = plan_03_body.sub(
    "  - web/src/fixture.test.tsx",
    "  - web/src/fixture.test.tsx\n  - web/src/shared-existing.ts"
  )
  write(plan_01_path, plan_01_shared_body)
  write(plan_03_path, plan_03_shared_body)
  run_validator(root, phase_command, expected_success: true, expected_token: "phase_entry=PASS")
  write(plan_03_path, plan_03_shared_body.sub("depends_on: [02-02]", "depends_on: []"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_SHARED_FILE_DEPENDENCY_MISSING")
  write(plan_01_path, plan_01_body)
  write(plan_03_path, plan_03_body)

  write(plan_01_path, plan_01_body.sub("depends_on: []", "depends_on: [02-02]"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_DEPENDENCY_CYCLE")
  write(plan_01_path, plan_01_body)

  write(plan_02_path, plan_02_body.sub("wave: 1", "wave: 0"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_DEPENDENCY_WAVE_NOT_EARLIER")
  write(
    plan_02_path,
    plan_02_body
      .sub("wave: 1", "wave: 0")
      .sub("depends_on: [02-01]", "depends_on: []")
      .sub("web/src/fixture.css", "web/src/fixture.tsx")
  )
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_SAME_WAVE_FILE_OVERLAP")
  write(plan_02_path, plan_02_body)

  write(plan_02_path, plan_02_body.sub("depends_on: [02-01]", "depends_on: [02-01"))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_FRONTMATTER_YAML_INVALID")
  write(plan_02_path, plan_02_body.sub('plan: "02"', 'plan: "09"'))
  run_validator(root, phase_command, expected_success: false, expected_token: "PLAN_ID_FILENAME_MISMATCH")
  write(plan_02_path, plan_02_body)

  run_validator(
    root,
    [RbConfig.ruby, ui_validator, "--phase", "02", "--package", "console-design-system-prototype-foundation"],
    expected_success: false,
    expected_token: "OPTION_STAGE_REQUIRED"
  )

  context_path = File.join(phase_dir, "02-CONTEXT.md")
  context_body = File.read(context_path)
  File.delete(context_path)
  run_validator(root, phase_command, expected_success: false, expected_token: "PHASE_ARTIFACT_MISSING")
  write(context_path, context_body)

  spec_path = File.join(phase_dir, "02-SPEC.md")
  spec_body = File.read(spec_path)
  write(spec_path, spec_body + "OBL-FIXTURE-FOREIGN\n")
  run_validator(root, phase_command, expected_success: false, expected_token: "FOREIGN_OBLIGATION_ID")
  write(spec_path, spec_body)

  ui_elements_path = File.join(phase_dir, "UI-ELEMENTS.md")
  write(ui_elements_path, ui_elements.sub(selector, "admin-demo-home-main-cancel"))
  run_validator(root, phase_command, expected_success: false, expected_token: "UI_OWNED_SELECTOR_MISSING")
  write(ui_elements_path, ui_elements)

  write(ui_elements_path, ui_elements.sub("| admin-only |", "| - |"))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_ELEMENT_PLACEHOLDER")
  write(ui_elements_path, ui_elements.sub("OBL-FIXTURE-ELEMENT,", ""))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_DIRECT_OBLIGATION_LINK_MISSING")
  write(ui_elements_path, ui_elements.sub("console-design-system-prototype-foundation-01,console-design-system-prototype-foundation-02", "console-design-system-prototype-foundation-01,wrong-behavior-99"))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_ROW_BEHAVIOR_LINK_MISMATCH")
  write(ui_elements_path, ui_elements)

  test_matrix_path = File.join(phase_dir, "TEST-MATRIX.md")
  test_matrix_body = File.read(test_matrix_path)
  write(test_matrix_path, "# Test Matrix\nprototype free text OBL-FIXTURE-PAGE\n")
  run_validator(root, ui_command, expected_success: false, expected_token: "BAD_UI_TEST_MATRIX_HEADER")
  page_matrix_row = test_matrix_body.lines.find { |line| line.include?("| OBL-FIXTURE-PAGE |") }
  write(test_matrix_path, test_matrix_body.sub(page_matrix_row, ""))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_TEST_MATRIX_OWNED_MISSING")
  write(test_matrix_path, test_matrix_body.sub("| PROJECT-UI-CONTRACT |", "| PROJECT-WRONG |"))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_TEST_MATRIX_REQUIREMENT_MISMATCH")
  write(test_matrix_path, test_matrix_body.sub("| console-design-system-prototype-foundation-01 |", "| wrong-behavior-01 |"))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_TEST_MATRIX_BEHAVIOR_MISMATCH")
  write(test_matrix_path, test_matrix_body.sub("| T-FIXTURE-PAGE:static |", "| T-WRONG:static |"))
  run_validator(root, ui_command, expected_success: false, expected_token: "UI_TEST_MATRIX_CATALOG_TEST_MISMATCH")
  write(test_matrix_path, test_matrix_body)

  todo_path = File.join(phase_dir, "TODO.md")
  todo_body = File.read(todo_path)
  write(todo_path, todo_body.sub("- [ ] OBL-FIXTURE-PAGE", "OBL-FIXTURE-PAGE"))
  run_validator(root, phase_command, expected_success: false, expected_token: "TODO_OWNED_CHECKBOX_MISSING")
  write(todo_path, todo_body.sub("- [ ] OBL-FIXTURE-PAGE", "- [x] OBL-FIXTURE-PAGE"))
  run_validator(root, phase_command, expected_success: false, expected_token: "TODO_PRECHECKED")
  write(todo_path, todo_body)

  write(
    File.join(phase_dir, "SUMMARY.md"),
    "# Summary\nRemote URL: https://github.com/example/fixture.git\nRemote SHA: `#{'a' * 40}`\n"
  )
  write(File.join(phase_dir, "02-VERIFICATION.md"), "# Verification\n\n## Verdict\n\nPASS\n")
  dependency_errors = []
  phases = PlanningValidatorSupport.roadmap_packages(File.join(planning, "ROADMAP.md"), dependency_errors)
  dependencies = PlanningValidatorSupport.roadmap_dependencies(File.join(planning, "ROADMAP.md"), dependency_errors)
  PlanningValidatorSupport.validate_dependency_evidence(root, 3, phases, dependencies, dependency_errors)
  abort "dependency unchecked TODO was not rejected" unless dependency_errors.any? { |error| error.start_with?("TODO_UNCHECKED:") }

  foreign_claim_dir = File.join(planning, "phases/03-crypto-storage-bootstrap")
  production_evidence_dir = File.join(foreign_claim_dir, "EVIDENCE")
  FileUtils.mkdir_p(production_evidence_dir)
  production_selector = "tenant-crypto-key-main-rotate"
  production_route = "/tenant/crypto"
  production_page = "tenant-crypto-key /tenant/crypto"
  write(File.join(foreign_claim_dir, "03-UI-SPEC.md"), "# UI Spec\ntenant-crypto-key #{production_route}\n")
  production_pen = File.join(foreign_claim_dir, "production.pen")
  production_html = File.join(foreign_claim_dir, "design-output/production.html")
  production_manifest = File.join(foreign_claim_dir, "selector-registry.json")
  production_prototype_playwright = File.join(foreign_claim_dir, "design-output/prototype.spec.ts")
  production_implementation = File.join(root, "web/src/fixture-production.tsx")
  production_playwright = File.join(root, "web/e2e/fixture-production.spec.ts")
  write(production_pen, "production pencil fixture")
  write(production_html, %(<main data-route="#{production_route}"><button data-testid="#{production_selector}">Rotate</button></main>))
  write(production_manifest, JSON.generate({ routes: [production_route], test_ids: [production_selector] }))
  write(
    production_prototype_playwright,
    <<~TYPESCRIPT
      test("PW-FIXTURE-PRODUCTION-01 CASE-PRODUCTION-ROTATE OBL-FIXTURE-FOREIGN", async ({ page }) => {
        await page.goto("#{production_route}");
        await page.getByTestId("#{production_selector}").click();
      });
    TYPESCRIPT
  )
  write(
    production_implementation,
    <<~TSX
      export const routes = [
        { path: "#{production_route}", element: <button data-testid="#{production_selector}">Rotate</button> }
      ];
    TSX
  )
  write(
    production_playwright,
    <<~TYPESCRIPT
      test("PW-FIXTURE-PRODUCTION-01 CASE-PRODUCTION-ROTATE OBL-FIXTURE-FOREIGN", async ({ page }) => {
        await page.goto("#{production_route}");
        await page.getByTestId("#{production_selector}").click();
      });
    TYPESCRIPT
  )
  write(
    File.join(foreign_claim_dir, "UI-ELEMENTS.md"),
    <<~MARKDOWN
      # UI Elements

      | Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
      | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
      | #{production_page} | tenant-admin-only | main | rotate key button | active key required | POST key rotation; audit event | default,focus,loading,success,error | #{production_selector} | OBL-FIXTURE-FOREIGN,PROJECT-DATA-MODEL | crypto-storage-bootstrap-01 | T-FIXTURE-FOREIGN:playwright | PW-FIXTURE-PRODUCTION-01 |
    MARKDOWN
  )
  write(
    File.join(foreign_claim_dir, "TEST-MATRIX.md"),
    <<~MARKDOWN
      # Test Matrix

      | Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
      | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
      | OBL-FIXTURE-FOREIGN | PROJECT-DATA-MODEL | crypto-storage-bootstrap-01 | T-FIXTURE-FOREIGN:playwright | PW-FIXTURE-PRODUCTION-01 | #{production_page} | #{production_selector} | CASE-PRODUCTION-ROTATE | production key rotation | npx playwright test web/e2e/fixture-production.spec.ts | EVIDENCE/production-rotate.json |
    MARKDOWN
  )
  production_inventory_path = File.join(production_evidence_dir, "ui-contract.json")
  execution_command = "npx playwright test web/e2e/fixture-production.spec.ts"
  execution_commit = "b" * 40
  execution_config = "playwright.config.ts fixture=production"
  execution_report_path = File.join(production_evidence_dir, "production-playwright-report.json")
  write(
    execution_report_path,
    JSON.pretty_generate(
      {
        "command" => execution_command,
        "commit" => execution_commit,
        "config" => execution_config,
        "result" => "PASS",
        "cases" => ["CASE-PRODUCTION-ROTATE"]
      }
    )
  )
  write_production_inventory = lambda do |implementation_path, playwright_source_path|
    production_inventory = {
      "mode" => "production",
      "pencil" => { "sources" => [source.call(production_pen)] },
      "manifest" => { "routes" => [production_route], "test_ids" => [production_selector], "sources" => [source.call(production_manifest)] },
      "prototype" => { "routes" => [production_route], "test_ids" => [production_selector], "sources" => [source.call(production_html)] },
      "prototype_playwright" => { "test_ids" => [production_selector], "evidence_kind" => "prototype", "sources" => [source.call(production_prototype_playwright)] },
      "implementation" => { "routes" => [production_route], "test_ids" => [production_selector], "sources" => [source.call(implementation_path)] },
      "playwright" => { "test_ids" => [production_selector], "evidence_kind" => "production", "sources" => [source.call(playwright_source_path)] },
      "execution" => {
        "command" => execution_command,
        "commit" => execution_commit,
        "config" => execution_config,
        "result" => "PASS",
        "report" => source.call(execution_report_path)
      }
    }
    write(production_inventory_path, JSON.pretty_generate(production_inventory))
  end
  write_production_inventory.call(production_implementation, production_playwright)
  production_design_command = [RbConfig.ruby, ui_validator, "--phase", "03", "--package", "crypto-storage-bootstrap", "--stage", "design"]
  production_ui_command = [RbConfig.ruby, ui_validator, "--phase", "03", "--package", "crypto-storage-bootstrap", "--stage", "production"]
  run_validator(root, production_design_command, expected_success: true, expected_token: "stage=design")
  run_validator(root, production_ui_command, expected_success: true, expected_token: "ui_contract=PASS")

  production_playwright_body = File.read(production_playwright)
  {
    "PW-FIXTURE-PRODUCTION-01" => "UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING",
    "CASE-PRODUCTION-ROTATE" => "UI_PW_BLOCK_CASE_ID_MISSING",
    "OBL-FIXTURE-FOREIGN" => "UI_PW_BLOCK_OBLIGATION_ID_MISSING"
  }.each do |identifier, expected_error|
    write(production_playwright, production_playwright_body.sub(identifier, "REMOVED-IDENTIFIER"))
    write_production_inventory.call(production_implementation, production_playwright)
    run_validator(root, production_ui_command, expected_success: false, expected_token: expected_error)
  end
  token_boundary_collision = production_playwright_body
    .sub("PW-FIXTURE-PRODUCTION-01", "PW-FIXTURE-PRODUCTION-010")
    .sub("CASE-PRODUCTION-ROTATE", "CASE-PRODUCTION-ROTATE-SUFFIX")
    .sub("OBL-FIXTURE-FOREIGN", "OBL-FIXTURE-FOREIGN-SUFFIX")
  write(production_playwright, token_boundary_collision)
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(
    root,
    production_ui_command,
    expected_success: false,
    expected_token: "UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING"
  )
  write(
    production_playwright,
    production_playwright_body.sub("page.goto(\"#{production_route}\")", "page.goto(\"/unrelated-smoke\")")
  )
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PW_BLOCK_GOTO_MISSING")
  write(production_playwright, production_playwright_body.lines.reject { |line| line.include?("page.goto") }.join)
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PW_BLOCK_GOTO_MISSING")
  write(
    production_playwright,
    production_playwright_body.sub(
      "await page.getByTestId(\"#{production_selector}\").click();",
      "const deadLocator = page.getByTestId(\"#{production_selector}\");"
    )
  )
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING")
  write(production_playwright, production_playwright_body)
  write_production_inventory.call(production_implementation, production_playwright)

  mislabeled_inventory = JSON.parse(File.read(production_inventory_path))
  mislabeled_inventory["mode"] = "prototype"
  write(production_inventory_path, JSON.pretty_generate(mislabeled_inventory))
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_MODE_MISMATCH")
  write_production_inventory.call(production_implementation, production_playwright)

  fake_react_txt = File.join(root, "web/src/fake-react.txt")
  write(fake_react_txt, %(<Route path="#{production_route}" /> <button data-testid="#{production_selector}">fake</button>))
  write_production_inventory.call(fake_react_txt, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PRODUCTION_SOURCE_BAD_PATH")

  fake_playwright_txt = File.join(root, "web/e2e/fake-playwright.txt")
  write(fake_playwright_txt, %(test("fake", async ({ page }) => page.getByTestId("#{production_selector}"));))
  write_production_inventory.call(production_implementation, fake_playwright_txt)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PLAYWRIGHT_SOURCE_BAD_PATH")

  implementation_body = File.read(production_implementation)
  write(production_implementation, %(// <Route path="#{production_route}" />\n// <button data-testid="#{production_selector}">fake</button>\n))
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PRODUCTION_ROUTE_SYNTAX_MISSING")
  write(production_implementation, %(export const fake = `<Route path="#{production_route}" /><button data-testid="#{production_selector}">fake</button>`;\n))
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PRODUCTION_ROUTE_SYNTAX_MISSING")
  write(production_implementation, implementation_body)
  write_production_inventory.call(production_implementation, production_playwright)

  dead_component_body = <<~TSX
    const DeadRotateButton = () => <button data-testid="#{production_selector}">Rotate</button>;
    export const routes = [{ path: "#{production_route}", element: <div>Unrelated route</div> }];
  TSX
  dead_locator_body = production_playwright_body.sub(
    "await page.getByTestId(\"#{production_selector}\").click();",
    "const deadLocator = page.getByTestId(\"#{production_selector}\");"
  )
  write(production_implementation, dead_component_body)
  write(production_playwright, dead_locator_body)
  write_production_inventory.call(production_implementation, production_playwright)
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING")
  write(production_implementation, implementation_body)
  write(production_playwright, production_playwright_body)
  write_production_inventory.call(production_implementation, production_playwright)

  valid_inventory = JSON.parse(File.read(production_inventory_path))
  missing_execution_inventory = Marshal.load(Marshal.dump(valid_inventory))
  missing_execution_inventory.delete("execution")
  write(production_inventory_path, JSON.pretty_generate(missing_execution_inventory))
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PRODUCTION_EXECUTION_MISSING")
  failed_execution_inventory = Marshal.load(Marshal.dump(valid_inventory))
  failed_execution_inventory["execution"]["result"] = "FAIL"
  write(production_inventory_path, JSON.pretty_generate(failed_execution_inventory))
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PRODUCTION_EXECUTION_NOT_PASS")
  bad_checksum_inventory = Marshal.load(Marshal.dump(valid_inventory))
  bad_checksum_inventory["execution"]["report"]["sha256"] = "0" * 64
  write(production_inventory_path, JSON.pretty_generate(bad_checksum_inventory))
  run_validator(root, production_ui_command, expected_success: false, expected_token: "UI_PRODUCTION_EXECUTION_REPORT_CHECKSUM_MISMATCH")
  write(production_inventory_path, JSON.pretty_generate(valid_inventory))

  write(
    File.join(foreign_claim_dir, "SCHEMA-CLAIMS.md"),
    <<~MARKDOWN
      | Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
      | --- | --- | --- | --- | --- | --- | --- | --- |
      | SC-03-001 | ycs.sms.crypto-storage-bootstrap.keyring | crypto-storage-bootstrap | V1100 | - | expand | forward fix | - |
    MARKDOWN
  )
  run_validator(root, phase_command, expected_success: false, expected_token: "SCHEMA_MIGRATION_DUPLICATE")

  template = File.read(File.expand_path("../PHASE-ARTIFACT-TEMPLATE.md", tool_dir))
  implementation_example = '"implementation"' + template.split('"implementation"', 2).fetch(1).split('"playwright"', 2).fetch(0)
  playwright_example = '"playwright"' + template.split('"playwright"', 2).fetch(1).split("}\n}", 2).fetch(0)
  abort "template implementation source regression" unless implementation_example.include?("web/src/") && !implementation_example.include?("web/e2e/")
  abort "template Playwright source regression" unless playwright_example.include?("web/e2e/") && playwright_example.match?(/\.spec\.(?:ts|tsx|js|jsx)/)

  puts "planning_validator_self_test=PASS positive=design_ui+production_ui+phase_entry_design+entry_evidence_binding+open_current_todo+deterministic_plan_graph+planned_artifact_dependency+shared_file_dependency negative=entry_evidence_digest_mismatch,plan_unknown_dependency,plan_self_dependency,plan_cycle,plan_same_wave_dependency,plan_same_wave_file_overlap,plan_artifact_dependency_missing,plan_shared_file_dependency_missing,plan_bad_yaml,plan_id_mismatch,missing_stage,missing_artifact,foreign_obligation,missing_selector,ui_placeholder,free_text_test_matrix,missing_atomic_row,missing_atomic_link,wrong_behavior,wrong_requirement,wrong_catalog_test,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,prototype_as_production,missing_pw_id,missing_case_id,missing_obl_id,metadata_token_boundary,unrelated_smoke,no_goto,no_action_or_assertion,dead_component_without_browser_closure,execution_missing,execution_fail,execution_checksum,fake_react_txt,fake_playwright_txt,comment_only_react,string_only_react,schema_conflict,template_path_regression"
end
