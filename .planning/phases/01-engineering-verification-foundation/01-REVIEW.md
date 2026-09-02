---
phase: 01-engineering-verification-foundation
reviewed: "2026-08-31T05:59:30Z"
depth: deep
reviewer:
  identity: phase1_gsd_code_review_attempt8
  method: independent-adversarial-deep-review
  attempt: 8
files_reviewed: 60
files_reviewed_list:
  - .github/workflows/ci.yml
  - .planning/tools/bootstrap-phase-01.rb
  - .planning/tools/phase01-chrome-entry-contract.rb
  - .planning/tools/planning-validator-support.rb
  - .planning/tools/produce-phase-01-chrome-entry.rb
  - .planning/tools/produce-phase-01-obligation-evidence.rb
  - .planning/tools/test-bootstrap-phase-01.rb
  - .planning/tools/test-delivery-attestation.rb
  - .planning/tools/test-phase-01-obligation-evidence.rb
  - .planning/tools/test-phase-lifecycle.rb
  - .planning/tools/test-produce-phase-01-chrome-entry.rb
  - .planning/tools/test-repository-verification.rb
  - .planning/tools/test-trace-closure.rb
  - .planning/tools/validate-delivery-attestation.rb
  - .planning/tools/validate-phase-entry.rb
  - .planning/tools/validate-phase-lifecycle.rb
  - .planning/tools/validate-trace-closure.rb
  - .planning/tools/validate-verification-evidence.rb
  - .planning/tools/verification-evidence.rb
  - core/pom.xml
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase01RedisIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase01TimezoneContractTest.java
  - core/src/test/resources/application-phase01-integration.yml
  - core/src/test/resources/verification/timezone-contract.json
  - scripts/lib/phase-01/run_checks.rb
  - scripts/lib/phase-01/service_checks.rb
  - scripts/lib/phase-01/test_run_checks.rb
  - scripts/lib/phase-01/test_service_checks.rb
  - scripts/verify-phase-01
  - web/.eslintrc.cjs
  - web/package.json
  - web/playwright.config.ts
  - web/scripts/probe-local-chrome.mjs
  - web/scripts/run-local-chrome-smoke.mjs
  - web/scripts/serve-browser-scenario.mjs
  - web/scripts/test-browser-scenario-server.mjs
  - web/scripts/test-browser-scenario-validator.mjs
  - web/scripts/test-browser-scenario-visual-local-chrome.mjs
  - web/scripts/test-copy-zh-cn.mjs
  - web/scripts/test-local-chrome-evidence.mjs
  - web/scripts/test-ui-drift-validator.mjs
  - web/scripts/validate-browser-scenario.mjs
  - web/scripts/validate-copy-zh-cn.mjs
  - web/scripts/validate-local-chrome-evidence.mjs
  - web/scripts/validate-ui-drift.mjs
  - web/src/pages/LoginPage.tsx
  - web/src/pages/tenant/send/SendPage.tsx
  - web/test/phase01/chinese-copy.spec.ts
  - web/test/phase01/login-scenario.spec.ts
  - web/verification/browser-scenarios.json
  - web/verification/browser-scenarios.schema.json
  - web/verification/copy.zh-CN.json
  - web/verification/fixtures/ui-drift-cases.json
  - web/verification/fixtures/zh-cn-export.csv
  - web/verification/local-chrome-runtime.schema.json
  - web/verification/row-key-registry.json
  - web/verification/row-key-registry.schema.json
  - web/verification/ui-manifest.json
  - web/verification/ui-manifest.schema.json
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
digests:
  subject_manifest_path: .planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json
  subject_manifest_digest: 94d9fa6dabd318e7e651a622182e357ec11c539ab25505d59eae9da475b5dd53
  tested_subject_digest: c3118bfde865d59b918c3032ae93570fc946d32eda043aed21801405963098e4
  evidence_manifest_sha256: 7174fecb6ac826a1f91b3a0b3f9fb0d9ae8280674c52f4fbdd1cfe5d8c606579
  local_chrome_runtime_sha256: 05535c103fda9308f5dde454a8dbbf311f25292311c6dba399afbb9541d1bfde
---

# Phase 1: Code Review Report — Attempt 8

**Reviewed:** 2026-08-31T05:59:30Z
**Reviewer identity:** `phase1_gsd_code_review_attempt8` (`gsd-code-reviewer`)
**Attempt:** Overall Attempt 8; current 194-input evidence cycle Attempt 1
**Depth:** deep
**Files reviewed:** 60 source, test, configuration, and contract files; the complete five-file CI scenario call graph and the 194-input canonical subject were independently reconciled
**Status:** PASS (`clean`)

## Summary

This is a fresh adversarial review of the post-Ubuntu-correction seal. Older reports and digest bindings were not used as completion authority. In addition to retaining the exact-source CI/browser separation, the current implementation isolates live-Chrome version fixtures from the host, gives the moved-tag Git fixture a local deterministic identity, and validates both classic and containerd-backed Docker image-store identities against the captured index → platform child → config chain.

The previous obfuscation bypasses were independently replayed and rejected by the exact-source binding. Registry mutation adding `--run-playwright` to the CI server was rejected by the exact argv contract, and the server itself rejects every argv other than no arguments or the sole explicit local `--run-playwright` flag before creating runtime state. CI contains neither browser-layer checks nor either browser-enabling flag; local visual execution retains the exact sole `--run-local-chrome` argument.

Portable subject/evidence binding, path and descriptor integrity, bounded JSON/base64/PNG handling, Java/Ruby process cleanup, exact-seven reduction, and the current-installed-Chrome-only scope were also re-reviewed. No BLOCKER, HIGH, WARNING, security vulnerability, correctness defect, or test-reliability defect was proven in the reviewed scope.

## Seal Binding

| Bound object | Expected | Independently observed | Status |
| --- | --- | --- | --- |
| Subject manifest path | `.planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json` | same durable path; 194 live-validated inputs | MATCH |
| Subject manifest digest | `94d9fa6dabd318e7e651a622182e357ec11c539ab25505d59eae9da475b5dd53` | independently recomputed through `durable_subject_binding` | MATCH |
| Tested subject digest | `c3118bfde865d59b918c3032ae93570fc946d32eda043aed21801405963098e4` | independently recomputed from live path/mode/content-bound inputs | MATCH |
| Formal evidence manifest SHA-256 | `7174fecb6ac826a1f91b3a0b3f9fb0d9ae8280674c52f4fbdd1cfe5d8c606579` | raw-file SHA-256 and exact-seven validation | MATCH |
| Local Chrome runtime SHA-256 | `05535c103fda9308f5dde454a8dbbf311f25292311c6dba399afbb9541d1bfde` | raw-file SHA-256 and formal-manifest runtime binding | MATCH |

## Targeted Deep Review

| Review area | Independent result |
| --- | --- |
| Claude CL-01/CL-02 correction | The CI validator is a separate flag-free file; the CI server is registered with exact no-flag argv; local visual execution is a distinct `all`/browser command with the sole `--run-local-chrome` argument. `ci=20`, `all=19`, CI browser-layer count is zero, and CI argv contains neither `--run-playwright` nor `--run-local-chrome`. |
| Ubuntu Git fixture identity | The moved-tag fixture supplies repository-local `user.name` and `user.email` only to `git commit-tree`; no global or repository config is mutated. The complete delivery suite passed 42 cases while `HOME`/`XDG_CONFIG_HOME` were inaccessible, system config was disabled, and all author/committer/email environment variables were removed. |
| Portable Chrome version fixture | The version mutation now updates the copied review's evidence SHA, proves the mutation does not also create `ENTRY_REVIEW_EVIDENCE_DIGEST_MISSING`, and injects a deterministic live-file/version probe. A separate throwing probe proves missing Chrome remains fail-closed. Repository verification passed 19 mutations, 10 redaction cases, and 12 path cases on the host-independent path. |
| Exact-source drift and obfuscation | Exact SHA-256 binding covers validator `0f557d...`, validator dependency `026d21...`, server `ff6f1d...`, probe `9f0179...`, and serve helper `a67c65...`; every live hash matched. Replayed unreviewed static import, computed `createRequire`/Playwright/launch, and direct `child_process` Chrome launch mutations all returned `CHECK_SCENARIO_SOURCE_SHA256_MISMATCH`. A server registry argv append returned `CHECK_SCENARIO_SERVER_BINDING_INVALID`. |
| CI call-graph closure | Direct import tracing produced validator → `validate-browser-scenario.mjs`; server → `probe-local-chrome.mjs` and `serve-browser-scenario.mjs`; serve → probe and validate. All repository-local nodes are present in the five-file SHA map. Built-in Node modules and the package-lock-bound Playwright dependency are outside the repository-local source graph. |
| Strict server arguments and default path | `parseRunPlaywrightArgument` accepts exactly no args or the sole `--run-playwright` flag and runs before temporary-state creation. An unexpected argument returned `BROWSER_SCENARIO_SERVER_ARGUMENT_INVALID`. The sealed ordered server check reports `local_google_chrome=not-run viewport=not-run`; its no-arg path does not call `runPlaywright` or `observeStandardLocalChrome`. |
| Portable and denial evidence | The imported portable validator returned `portable_runtime_validation=PASS live_browser_launched=false` and `portable_chrome_artifact=PASS runtime_claim=false live_browser_launched=false`. The sealed normal portable matrix is 20/20; the independently recorded sandbox control denies read/execute access to the standard Chrome path while the structural validator/server and portable artifact pass. No browser was launched during this review. |
| TRACE-003 and exact-seven evidence | Formal manifest validation returned `verification_evidence=PASS`; obligation generation tests passed 21 mutations, seven summaries, legacy-v1 rejection/compatibility boundaries, runtime binding, and bound source content/mode. TRACE-003 contains the complete local obligation result set, including exact local visual and copy browser argv, without claiming the CI selector or future product acceptance. |
| Path/descriptor and NOFOLLOW | Repository reads retain component lstat, link/containment checks, stable `O_NOFOLLOW` descriptor identity, pre/post state checks, bounded same-descriptor reads, and content hashing. Explicit missing-`File::NOFOLLOW` injection returns `*_NOFOLLOW_UNAVAILABLE`; repository self-test passed 19 evidence mutations, 10 redaction cases, and 12 path/descriptor/bounded-read cases. |
| PNG and resource bounds | Runtime/scenario/schema/subject JSON files are bounded before allocation and parse. Screenshot base64/declared/raw limits precede decode/re-encode/hash/PNG work. PNG verifies type alphabet/reserved bit, unknown critical chunks, CRC, IHDR/PLTE/IDAT/IEND state, contiguous IDAT, palette legality, full bounded zlib consumption, scanline length/filter layout, and invalid-header short-circuit. Probe 21, evidence 81, and predecode 5 fixture cases passed. |
| Java/Ruby descendants and resources | Ruby service checks retain capped concurrent drains, process-group ownership, bounded TERM/KILL and joins; bounded-spawn passed 14 assertions. The sealed Java focused process suite covers timeout, output caps, reparented/setsid descendant rediscovery, cleanup on success/failure, and cleanup-before-result publication. |
| OCI provenance and Docker identity | Embedded index/child/config documents are byte/digest/platform-linked captured immutable trust roots, not a live-registry freshness claim. Runtime-contract passed 48 assertions. Descriptor-backed stores must match child digest, platform, reference, and child/config image ID. Classic stores may have a null descriptor only when `.Config.Image` equals the approved child-digest reference and `.Image` equals the already-bound config digest; malformed descriptors and reference/config substitutions fail closed. |
| Current Chrome scope | Evidence is limited to the installed standard-path Google Chrome and viewport `1440x900`; no other browser, browser matrix, driver, download, remote provider, or compatibility claim is present. |

The structural server's direct standalone replay after cleanup correctly stopped at missing transient `web/dist`; the registered matrix orders `web-build` before the server, and the sealed ordered no-flag result is PASS with Chrome `not-run`. No full `--all` run, browser/driver launch or download, evidence generation, other-browser inspection, repository Git mutation, network access, or remote operation was performed by this review. Git commands ran only inside disposable delivery-test repositories.

## Narrative Findings (AI reviewer)

All 60 reviewed files meet the Phase 1 correctness, security, portability, and test-reliability standards at deep review depth. No issues found.

## Final verdict

PASS

Overall Attempt 8 has **0 BLOCKER and 0 HIGH**. The current 194-input evidence cycle Attempt 1 is clear for the next independent gate; this report does not itself mark TODO items complete or perform delivery/commit operations.

---

_Reviewed: 2026-08-31T05:59:30Z_
_Reviewer: `phase1_gsd_code_review_attempt8` (`gsd-code-reviewer`)_
_Depth: deep_

## Review Binding

This final 194-input seal starts a fresh bounded review cycle. Only the current manifest is authoritative in this table. This protocol table is intentionally the final table in the document so target-tree delivery parsing has one unambiguous row set.

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | ---: | ---: | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json | 94d9fa6dabd318e7e651a622182e357ec11c539ab25505d59eae9da475b5dd53 | c3118bfde865d59b918c3032ae93570fc946d32eda043aed21801405963098e4 | PASS |
