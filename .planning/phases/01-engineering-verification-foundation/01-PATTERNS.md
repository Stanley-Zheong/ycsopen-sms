# Phase 1: Engineering verification and drift-control foundation - Pattern Map

**Mapped:** 2026-08-30
**Files analyzed:** 27 new/modified file groups
**Analogs found:** 18 / 27
**Authority boundary:** Only files reachable from the current branch base `origin/main` are implementation authorities. `origin/feature/4-web-test-cases` is unmerged and may be inspected later as a compatibility candidate, but no code, lockfile, evidence, or completion claim should be copied from it without an explicit reviewed decision.

**Browser supersession:** DR-01-016 and DR-01-017 replace the earlier downloaded-browser pattern. Any retained reference to Chrome for Testing, ChromeDriver, 151/152, current/previous majors, two viewports, source admission/probes/attestation, or a four-cell matrix is historical only and is not an implementation authority. Plan 00 uses standard-library Ruby to prove the standard-path Chrome version/brand and direct headless synthetic-page launch in `local-chrome-entry.json`; after independent ENTRY PASS and the 13-plan bootstrap, Plan 06 separately runs the same installed Chrome through Playwright once at 1440x900 and writes `local-chrome-runtime.json`.

## File Classification

| New/Modified File | Role | Data Flow | Closest merged analog | Match quality |
| --- | --- | --- | --- | --- |
| `.planning/tools/verification-evidence.rb` | utility | file-I/O / transform | `.planning/tools/planning-validator-support.rb` | role-match |
| `.planning/tools/validate-verification-evidence.rb` | utility/validator | file-I/O / transform | `.planning/tools/validate-ui-contract.rb` | role-match |
| `.planning/tools/test-repository-verification.rb` | test | process / file-I/O | `.planning/tools/test-planning-validators.rb` | exact |
| `.planning/tools/validate-phase-lifecycle.rb` | utility/validator | file-I/O / request-response | `.planning/tools/validate-phase-entry.rb` | exact |
| `.planning/tools/test-phase-lifecycle.rb` | test | process / Git file-I/O | `.planning/tools/test-bootstrap-phase-01.rb` | role-match |
| `.planning/tools/validate-delivery-attestation.rb` | utility/validator | Git remote request-response | `skills/code-review/scripts/precheck.sh` | partial |
| `scripts/verify-phase-01` | orchestration CLI | batch / process | `skills/code-review/scripts/precheck.sh` | role-match |
| `scripts/lib/phase-01/*` | utility | batch / transform | `.planning/tools/planning-validator-support.rb` | partial |
| `web/scripts/validate-ui-drift.mjs` | utility/validator | AST transform | `web/src/router/routes.tsx` + `.planning/tools/validate-ui-contract.rb` | partial |
| `web/scripts/test-ui-drift-validator.mjs` | test | AST/file-I/O fixtures | `.planning/tools/test-planning-validators.rb` | role-match |
| `web/verification/ui-manifest.json` | config/manifest | declarative graph | Phase UI inventory contract in `.planning/tools/validate-ui-contract.rb` | role-match |
| `web/verification/copy.zh-CN.json` | config/registry | declarative transform | no direct merged analog | none |
| `web/playwright.config.ts` | config | browser request-response | no merged Playwright config | none |
| `web/test/phase01/*.spec.ts` | test | browser request-response | production Playwright contract in `planning-validator-support.rb` | contract-only |
| `web/scripts/probe-local-chrome.mjs` | runtime probe | local process / file-I/O | no merged fixed-path Chrome probe | none |
| `web/scripts/run-local-chrome-smoke.mjs` | browser runner | local process / browser request-response | shared scenario + Playwright contract | contract-only |
| `web/scripts/validate-local-chrome-evidence.mjs` / `test-local-chrome-evidence.mjs` | validator/test | file-I/O / browser evidence | evidence checksum and one-mutation fixture patterns | role-match |
| `web/package.json` / `web/package-lock.json` | config | dependency / command registry | current `web/package.json` | exact |
| `core/src/test/resources/application-integration.yml` | config | database/cache request-response | `application-test.yml` + `application-dev.yml` | role-match |
| `core/src/test/java/**/Phase01MySqlIntegrationTest.java` | test | CRUD / database | `BillingServiceTest.java` | test-style only |
| `core/src/test/java/**/Phase01RedisIntegrationTest.java` | test | CRUD / cache | `BillingServiceTest.java` | test-style only |
| `core/src/test/java/**/Phase01TimezoneIntegrationTest.java` | test | database / transform | current test/config files | partial |
| `core/pom.xml` | config | dependency / build | current `core/pom.xml` | exact |
| `.github/workflows/ci.yml` | config | event-driven / batch | current `.github/workflows/ci.yml` | exact |
| `EVIDENCE/schema/*.json` | schema/config | file-I/O / validation | UI inventory/execution validation in `planning-validator-support.rb` | role-match |
| `EVIDENCE/fixtures/**` | test fixture | file-I/O / transform | temp fixtures in planning validator tests | exact |
| `EVIDENCE/runs/<run-id>/**` and `EVIDENCE/OBL-*.json` | evidence output | batch / file-I/O | UI execution report/checksum contract | role-match |

## Pattern Assignments

### Ruby evidence kernel and envelope validators

**Files:** `.planning/tools/verification-evidence.rb`, `.planning/tools/validate-verification-evidence.rb`, `.planning/tools/test-repository-verification.rb`, `EVIDENCE/schema/*.json`, `EVIDENCE/fixtures/**`

**Primary analog:** `.planning/tools/planning-validator-support.rb`

**Imports and module pattern** (`planning-validator-support.rb` lines 1-10, 44-54):

```ruby
#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "pathname"
require "set"

module PlanningValidatorSupport
  module_function

  def read(path, errors, label = "file")
    File.read(path)
  rescue Errno::ENOENT
    errors << "MISSING_#{label.upcase.tr(' ', '_')}: #{path}"
    ""
  rescue Errno::EACCES
    errors << "UNREADABLE_#{label.upcase.tr(' ', '_')}: #{path}"
    ""
  end
end
```

Copy this standard-library-only structure. The evidence helper should return data/errors to callers; executable validators should own terminal output and exit codes.

**Checksum, containment, and fail-closed pattern** (`planning-validator-support.rb` lines 399-422):

```ruby
expanded = File.expand_path(entry["path"], root)
unless expanded.start_with?(File.expand_path(root) + File::SEPARATOR)
  errors << "UI_SOURCE_OUTSIDE_ROOT: section=#{label} path=#{entry['path']}"
  next
end
unless File.file?(expanded)
  errors << "UI_SOURCE_MISSING: section=#{label} path=#{entry['path']}"
  next
end
actual = Digest::SHA256.file(expanded).hexdigest
errors << "UI_SOURCE_CHECKSUM_MISMATCH: section=#{label} path=#{entry['path']}" unless actual == entry["sha256"]
```

Apply the same root-containment, regular-file, SHA-256, and accumulated-error behavior to evidence artifacts. First build one canonical tested-input manifest as stable repository-relative path/file-mode/SHA-256/role entries for every tested implementation, test, config, contract, and validator. Inclusion roles and exact exclusions for the manifest itself, generated evidence, TODO/SUMMARY, reviews, and delivery metadata are schema/code-owned; mutable JSON cannot hide required inputs. Extend each envelope with schema/run/check/phase/obligation/case IDs, allowlisted `argv` array, repository-relative `cwd`, `subject_manifest_path`, `subject_manifest_digest`, `tested_subject_digest`, UTC timestamps, sanitized environment identity, `PASS|FAIL|BLOCKED`, nullable exit code, stable error IDs, diagnostics, and artifact media type. Reject unknown schema versions, stale `running`, wrong/stale subject, missing/extra inputs, content/mode changes, illegal exclusions, malformed timestamps, missing required artifacts, and producer-declared PASS inconsistent with exit status.

**Fixture/process assertion pattern** (`test-planning-validators.rb` lines 16-35):

```ruby
def run_validator(root, command, expected_success:, expected_token:)
  stdout, stderr, status = Open3.capture3(*command, chdir: root)
  output = stdout + stderr
  abort "validator status mismatch expected_success=#{expected_success}:\n#{output}" if status.success? != expected_success
  abort "validator output missing #{expected_token}:\n#{output}" unless output.include?(expected_token)
  output
end

Dir.mktmpdir("planning-validator-test-") do |root|
  evidence_dir = File.join(root, ".planning/phases/.../EVIDENCE")
  FileUtils.mkdir_p(evidence_dir)
end
```

Build one known-good fixture, clone/reset it per case, make exactly one mutation, and assert nonzero plus the exact stable diagnostic. Required mutations include missing field, unsupported schema, malformed JSON, checksum mismatch, wrong/stale tested subject, missing/extra input, content/mode mismatch, illegal exclusion, subject-manifest digest mismatch, missing executable, child FAIL, child BLOCKED, interrupted/stale `running`, secret-like diagnostic redaction, and missing artifact. Never let a later child erase an earlier envelope.

**Integration point:** the root orchestrator invokes producers, then independently invokes `validate-verification-evidence.rb`; aggregate PASS is allowed only when every required child validates as PASS. Use fresh run directories or temporary-file-plus-atomic-rename. Committed `OBL-*.json` files are small summaries/checksums; large logs/screenshots/traces remain CI artifacts.

**Anti-patterns:** serializing the full environment; storing shell command strings instead of argv arrays; accepting a producer-supplied PASS; overwriting a failed run; treating missing evidence as skipped; writing absolute local paths, secrets, phone numbers, message bodies, or mutable business values.

---

### Lifecycle, TODO, review, and delivery attestation

**Files:** `.planning/tools/validate-phase-lifecycle.rb`, `.planning/tools/test-phase-lifecycle.rb`, `.planning/tools/validate-delivery-attestation.rb`

**Primary analog:** `.planning/tools/validate-phase-entry.rb`

**CLI and validation aggregation** (`validate-phase-entry.rb` lines 10-24, 133-141):

```ruby
options = { ui: false }
OptionParser.new do |parser|
  parser.banner = "Usage: ruby ..."
  parser.on("--phase NN") { |value| options[:phase] = value }
end.parse!

errors = []
errors << "OPTION_PHASE_REQUIRED: expected numeric --phase" unless options[:phase]&.match?(/\A\d+\z/)

if errors.empty?
  puts "phase_entry=PASS ..."
  exit 0
end
warn "phase_entry=BLOCKED errors=#{errors.length}"
errors.each { |error| warn "- #{error}" }
exit 1
```

Keep stable diagnostic prefixes and accumulated errors. Add explicit modes for entry, exit, pre-push, and post-push; unknown modes/options fail nonzero.

**TODO law** (`planning-validator-support.rb` lines 245-271): parse only real checkbox rows, report exact unchecked/checked line numbers, and compare owned obligation IDs as exact sets. Entry rejects prechecked owned TODOs; exit requires all scoped TODOs checked and all evidence validated. No schedule or percentage field may substitute for this query.

**Current dependency behavior to replace** (`planning-validator-support.rb` lines 144-169): the current code looks for a literal remote SHA and URL in `SUMMARY.md`. Do not extend this impossible self-reference. Replace dependency validation atomically with external delivery-attestation resolution and update every consumer together.

**Remote Git pattern:** `skills/code-review/scripts/precheck.sh` lines 26-42 validates a ref with `git rev-parse`, requires a merge base, and fails when it cannot resolve authority. The delivery validator should similarly validate a strict protected tag/ref namespace, call Git with argv (no eval), parse exactly one `git ls-remote --exit-code` match, peel an annotated tag, and prove branch/tag target equality with the phase commit and PR/check locator.

**Fixture pattern:** `test-bootstrap-phase-01.rb` lines 24-90 constructs a complete temporary phase and corrupts it, while `test-planning-validators.rb` uses `Open3.capture3`. Extend with a local bare Git remote: create a baseline commit, push a branch and annotated attestation tag, then independently mutate missing tag, lightweight tag, moved tag, mismatched branch target, malformed ref, missing PR/check locator, open TODO, missing review, BLOCKER/HIGH review, wrong/stale tested subject, and missing artifact.

**Anti-patterns:** trusting local tracking refs as remote truth; accepting a lightweight or movable unprotected tag without policy; literal same-commit SHA in `SUMMARY.md`; self-attestation from the artifact being verified; direct push to `main`; treating an unpushed local commit as delivered.

---

### Root verification command and evidence-preserving orchestration

**Files:** `scripts/verify-phase-01`, `scripts/lib/phase-01/*`

**Primary analog:** `skills/code-review/scripts/precheck.sh` lines 1-23, 44-69.

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$REPO_ROOT"

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --base) ... ;;
    *) echo "ERROR: unknown argument: $1" >&2; exit 2 ;;
  esac
done
```

Preserve strict mode, root discovery, explicit option parsing, and fail-closed dependency probes. Unlike a simple `set -e` script, the orchestrator must deliberately execute every selected independent check, capture each exit status, write its envelope immediately, and reduce only after all runnable checks finish. `FAIL` dominates; otherwise `BLOCKED` dominates; only all required PASS returns 0. The public command is exactly the root-level command declared in validation: `./scripts/verify-phase-01 --all --evidence-dir .../EVIDENCE` plus focused allowlisted flags such as `--timezone`.

Use fixed check identifiers and fixed argv builders in code. Never execute command strings supplied by JSON. Terminal output names every check and its evidence path; machine output is under the requested phase evidence directory.

---

### TypeScript AST UI drift validator and manifest

**Files:** `web/scripts/validate-ui-drift.mjs`, `web/scripts/test-ui-drift-validator.mjs`, `web/verification/ui-manifest.json`

**Input syntax analog:** `web/src/router/routes.tsx` lines 17-60 uses `createBrowserRouter` with nested object routes, index redirects, absolute parents, relative children, JSX component elements, and `Navigate` redirects. The AST walker must concatenate parent/child route paths, distinguish navigable leaves from redirects/index routes according to the recorded decision, and link routed JSX identifiers to imported component files.

```tsx
export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: <DashboardPage /> },
    ],
  },
]);
```

**Contract analog:** `validate-ui-contract.rb` lines 93-163 normalizes each UI row into page, selector, obligation, requirement, behavior, catalog test, and Playwright IDs, then checks duplicates and both missing/foreign directions. Preserve that relation-set model, but implement parsing with the TypeScript Compiler API rather than regex.

**Runtime closure analog:** `planning-validator-support.rb` lines 610-645 requires the same Playwright block to contain exact metadata tokens, exact route navigation, and an action/assertion against the selector. New AST output should feed the same normalized tuple:

```text
page-id <-> full route <-> routed component <-> rendered data-testid
full route <-> Playwright block <-> selector action/assertion
semantic row data-testid <-> row metadata <-> separate data-row-key
obligation <-> behavior <-> catalog test <-> case <-> Playwright ID <-> evidence
```

Manifest rows must be page-scoped, versioned, and contain row-contract metadata. A repeated row uses a constant semantic `data-testid` and a separate synthetic, stable, non-sensitive `data-row-key`; reject phone number, database ID, localized/mutable label, and data-derived/template-literal test IDs.

**Fixture style:** use a known-good temporary TS/TSX/manifest/spec graph and one mutation per case. Cover missing and stale route/page/component/DOM selector/Playwright locator in both directions, duplicate tuple, comment/string-only fake, prefix collision, nonliteral/dynamic unsupported syntax, dead component, dead locator, route without runtime closure, absent row metadata, absent separate key, and sensitive/mutable key classification.

**Anti-patterns:** grep-only presence scans; treating comments or strings as code; global selector arrays without page relation; accepting computed syntax silently; assuming a locator variable is an executed assertion; encoding row values in `data-testid`.

---

### Playwright configuration, tests, and browser evidence

**Files:** `web/playwright.config.ts`, `web/test/phase01/*.spec.ts`, `web/package.json`, `web/package-lock.json`, browser evidence schemas/summaries.

There is no merged Playwright implementation to copy. Use repository conventions from `web/package.json` lines 6-13: ESM, non-watch scripts, and commands runnable through `npm --prefix web ...`. Pin the researched `@playwright/test` version and regenerate the lockfile on this branch; do not copy the unmerged candidate lockfile.

Follow the production execution contract in `planning-validator-support.rb` lines 648-705 for command/config/PASS/report/checksum/case validation, but replace its commit-bound identity with DR-01-011: evidence must include a validated canonical subject manifest path/digest and tested-subject digest, report inside the phase `EVIDENCE/` root, matching SHA-256, and exact nonduplicate expected case IDs. The target-tree delivery validator, not a committed envelope, binds final commit identity after push.

Configure one local Google Chrome Playwright project at 1440x900. Runtime evidence—not the project name—must record the canonical standard path, actual Google Chrome brand, full/major version, command/session, launch result, scenario/visual result, screenshot, console/page errors, and artifact checksums. A missing path, non-Google brand, or launch failure is BLOCKED/nonzero. No downloaded, driver-backed, or other-browser project exists.

Structural/mocked `page.route().fulfill()` tests may prove frontend structure only and must be labeled as such. They cannot close backend/MySQL/Redis integration obligations.

---

### Maven MySQL, Redis, and timezone integration

**Files:** `core/pom.xml`, `core/src/test/resources/application-integration.yml`, integration test classes.

**Dependency analog:** `core/pom.xml` lines 24-63 already fixes Java 21 and includes JPA, MySQL Connector, Flyway core/MySQL, and Spring Data Redis. Lines 111-120 show test-scope conventions. Preserve these production dependencies; add only test infrastructure necessary for isolated integration execution and keep H2 unit tests separate.

**Configuration contrast:** `application-test.yml` lines 1-9 uses H2, `ddl-auto: create-drop`, and disables Flyway; it is explicitly not an integration analog. `application-dev.yml` lines 1-6 shows MySQL URL/env placeholders and `serverTimezone=Asia/Shanghai`, but development defaults are not acceptable test credentials or host-independent timezone proof. Create a separate integration profile driven by test-only environment values, with Flyway enabled, no persistent shared state, and explicit application/JDBC timezone policy.

**Test style analog:** `BillingServiceTest.java` lines 23-38 uses a requirement-oriented class comment, package-local test class, JUnit 5 lifecycle, AssertJ, and descriptive behavior names; lines 49-69 and 117-126 make direct state/idempotency assertions. Retain these style conventions, but integration tests must use real Spring context and real MySQL/Redis rather than Mockito.

MySQL evidence must prove authenticated `SELECT 1`, server version, real `V1__init_schema.sql` Flyway history/checksum, Spring startup, transactional repository round trip, UTF-8 Chinese round trip, session timezone, and temporal round trip. Redis evidence must prove isolated synthetic prefix, PING, SET, TTL, GET, TTL assertion, DEL, and one Spring `StringRedisTemplate` path. Never use `FLUSHALL`, production keys, persistent volumes, or readiness alone as integration proof.

Timezone tests must independently prove JVM/application policy, JDBC/MySQL session, API serialization, and browser display under `Asia/Shanghai`, while preserving IANA zone identity for international fixtures. Do not rely on host default timezone or `LocalDateTime.now()` as proof.

---

### CI integration

**File:** `.github/workflows/ci.yml`

**Exact analog:** current CI lines 9-48 uses least-privilege `contents: read`, checkout/setup actions, Java 21 with Maven cache, Node 20 with npm cache, deterministic `npm ci`, test, and build lanes.

Keep the portable existing lanes and invoke the same code-owned commands used locally. Add planning/evidence, MySQL/Redis service, UI AST, copy, timezone, and browser-evidence schema/fixture jobs without moving verification logic into YAML. Pin service image versions/digests, configure health checks as readiness only, use synthetic test credentials, and upload diagnostics on failure. The real standard-path Chrome launch remains a local pre-push command; CI downloads no browser and cannot manufacture browser PASS.

**Anti-patterns:** duplicated business logic in workflow YAML; mutable service tags; secrets in argv/logs; calling a skipped job PASS; replacing root commands with different CI-only behavior.

## Shared Patterns

### Stable diagnostics and fail-closed exit

**Sources:** `validate-prd-obligations.rb` lines 54-57 and 254-278; `validate-phase-entry.rb` lines 133-141.

All validators accumulate stable uppercase diagnostic IDs, print a concise PASS summary only when `errors.empty?`, print all failures to stderr, and return nonzero. Missing, malformed, contradictory, stale, unexecuted, or unavailable required evidence never passes.

### Exact-set reconciliation

**Sources:** `planning-validator-support.rb` lines 198-209; `validate-ui-contract.rb` lines 119-163.

Normalize IDs/relations first, then compute missing, foreign/stale, unknown, and duplicate sets separately. Diagnostics identify direction and exact IDs; substring presence is insufficient.

### Repository-relative file trust

**Source:** `planning-validator-support.rb` lines 399-422.

Resolve paths against the repository root, reject escape, require a regular file, and independently verify SHA-256. Evidence paths live below the active phase `EVIDENCE/` directory.

### Test isolation

**Sources:** `test-planning-validators.rb` lines 16-35; `test-bootstrap-phase-01.rb` lines 24-90.

Use `Dir.mktmpdir`, synthetic content, `Open3.capture3` argv calls, and one mutation per baseline. Do not touch production data, local runtime state, or private repositories.

### Delivery boundary

**Sources:** `AGENTS.md` lines 8-18 and 20-25.

Implementation goes through a branch and PR, never directly to `main`. PR evidence includes issue reference, exact verification commands, known limitations, and external post-push attestation. README/roadmap/state claims follow verified evidence only.

## No Direct Analog Found

| File/capability | Reason and planner instruction |
| --- | --- |
| `web/playwright.config.ts` | No Playwright dependency/config exists on merged main. Use the research contract and official API; treat the unmerged feature branch only as non-authoritative compatibility input. |
| TypeScript Compiler API relation extractor | Current merged UI validator is Ruby/regex-oriented. Reuse its normalized sets and diagnostics, not its parsing technique. |
| Standard-path local Google Chrome probe/runner | No merged runner exists. Implement one code-owned path/version/brand/Playwright-launch probe and one 1440x900 scenario; preserve Gate D BLOCKED until an independent reviewer reruns it. |
| `copy.zh-CN.json` and export scanner | No reviewed copy registry/export implementation exists. Build a narrow versioned registry and do not claim nonexistent exports as executed product evidence. |
| Real MySQL/Redis Spring integration tests | Current backend tests are H2 or Mockito. Use project JUnit/AssertJ style, but integration semantics come from the phase research. |
| Annotated delivery-tag validator | Current dependency validator expects an impossible literal same-commit SHA. Replace the whole contract and its consumers rather than copying it. |

## Planner Integration Checklist

- Put evidence schema/writer/reducer and destructive fixtures before orchestration consumers.
- Put lifecycle/delivery protocol replacement in one atomic plan slice so no consumer retains literal self-SHA semantics.
- Put TypeScript AST extractor/manifest/fixtures before Playwright evidence closure.
- Put Playwright installation/config/scenario tests before the browser claim; preserve missing/non-Google/unlaunchable standard-path Chrome as BLOCKED until the single runtime/smoke evidence passes.
- Put MySQL/Redis services, integration profile, functional tests, and CI invocation in the same verifiable slice.
- Make every task name its exact focused command, stable diagnostic fixture, evidence target, and TODO closure rule.
- Do not include a Flyway migration: Phase 1 changes no business schema.
- Do not describe feature-branch prototypes, mocks, engine approximations, readiness probes, or unavailable environments as completed product evidence.

## Metadata

**Analog search scope:** `.planning/tools/`, `scripts/`, `skills/code-review/scripts/`, `web/`, `core/`, `.github/workflows/`

**Merged files inspected:** 15 primary analog/config/test files plus phase context, research, spec, design, and validation contracts.

**Pattern extraction date:** 2026-08-30
