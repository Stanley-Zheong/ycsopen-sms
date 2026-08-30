# Decisions

## Current browser-decision precedence

DR-01-016 is the only active browser runtime, entry, viewport, and evidence decision. It revises the browser-specific portions of DR-01-002, DR-01-005, DR-01-009, and DR-01-011, and supersedes DR-01-012 through DR-01-015. Those older sections are retained only as historical rationale and cannot drive bootstrap, lifecycle, TEST-MATRIX, tested-subject membership, plan success criteria, or implementation.

## DR-01-001: Fail closed and preserve diagnostics

### Status

Accepted

### Context

Later phases depend on Phase 1 to distinguish verified behavior from an unavailable tool, missing fixture, or incomplete artifact.

### Decision

Every required check returns nonzero for FAIL or BLOCKED, writes stable diagnostic identifiers, and preserves completed subcheck evidence. Aggregation returns PASS only when every required check passes.

### Consequences

- CI failures remain diagnosable and cannot be converted into silent skips.
- Local environments missing a required service or browser cannot produce completion evidence.

### References

- `.planning/EXECUTION-STANDARD.md`
- OBL-FOUND-TRACE-003
- OBL-FOUND-TRACE-004

## DR-01-002: Browser brand identity is evidence, not an alias

### Status

Superseded by DR-01-016; historical only

### Context

This record preserves the reasoning that browser brand identity must be proven. Its former Chrome for Testing, ChromeDriver, dual-major, and matrix mechanism is historical context only and must not be used as implementation authority.

### Decision

The superseded decision required Chrome for Testing browser/driver source integrity and a supported matrix. DR-01-016 replaces that mechanism completely with the current standard-path local Google Chrome contract.

### Consequences

- No active plan, validator, success criterion, or evidence consumer may require both Chrome majors, a source admission, an archive, a driver, or a matrix because of this record.
- Only DR-01-016 and later accepted decisions define the active browser contract.

### References

- `docs/PRD.md` section 6.3
- OBL-NFR-BROWSER

## DR-01-016: Browser verification uses only the current standard-path local Google Chrome

### Status

Accepted; current browser contract

### Context

The machine already has Google Chrome installed. Downloading two Chrome for Testing browser archives and two ChromeDriver archives, pinning 151/152, maintaining an admission/probe/attestation digest chain, and expanding it into four viewport cells did not improve the selected product support boundary enough to justify the verification machinery. The user explicitly chose the current local Chrome for every Chrome-dependent check.

### Decision

Every Chrome-dependent validation uses `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` or, after entry, Playwright's equivalent `channel: "chrome"` resolution to that standard installation. Gate D is a repeatable pre-entry probe, not a source-admission ceremony: verify the canonical executable, run fixed argv `--version`, require the `Google Chrome <full-version>` identity, derive the major at execution time, and use Chrome's own headless argv with an isolated profile to open a synthetic local page. It has no Playwright dependency and writes `EVIDENCE/local-chrome-entry.json`. An independent reviewer reruns that probe and records the observed facts in `ENTRY-REVIEW.md` before the real bootstrap can authorize downstream execution.

Plan 06 separately launches the same installed browser through Playwright at 1440x900 and writes `EVIDENCE/local-chrome-runtime.json` plus the `/login` smoke-and-visual artifacts. Entry evidence and acceptance runtime evidence are never interchangeable.

The observed planning-time version is `Google Chrome 151.0.7922.174`, but no plan or validator pins it. If the installed version changes, rerun the probe, smoke/visual scenario, evidence sealing, and affected reviews. The canonical tested-input manifest continues to cover source/test/config/contract/validator files; generated local runtime evidence is checksummed and linked from the browser envelope/evidence manifest rather than becoming a self-referential subject input.

No active path downloads Chrome for Testing, downloads or invokes ChromeDriver, selects current/previous versions, creates a version/viewport matrix, validates archive/source checksums, or uses a provider, tunnel, secret, remote host, or VM. Edge, Safari, Firefox, Internet Explorer, Chromium, and other browsers are unsupported and untested. A missing path, non-Google brand, or launch failure is BLOCKED/nonzero.

### Consequences

- OBL-NFR-BROWSER contains exactly one real `/login` smoke-and-visual execution at 1440x900.
- Plan 00 owns pre-entry path/version/brand/headless synthetic-page evidence and independent entry review. Plan 05 configures the repository scenario after entry; Plan 06 probes through Playwright, executes, and validates durable runtime/smoke evidence.
- `browser-source-admission.json`, `browser-source-entry-attestation.json`, and `browser-source-probes/chrome-151.json` / `chrome-152.json` remain superseded Attempt 3 history only.
- Repository tooling must remove those historical files from bootstrap, lifecycle, delivery, tested-subject, and fixture requirements before entry can pass.
- Every phase still finishes through TODO-empty, blocking-free GSD/Claude review, one atomic commit, PR, and remote delivery attestation.

### References

- D-01, D-02, D-04, D-06, D-08
- OBL-NFR-BROWSER
- `EVIDENCE/local-chrome-runtime.json`
- `ENTRY-REVIEW.md`

## DR-01-003: Stable semantic selectors are separate from row identity

### Status

Accepted

### Context

Repeated rows require stable automation hooks without leaking or encoding mutable business data.

### Decision

The repeated component exposes a stable semantic `data-testid`; the row's non-sensitive business key is carried separately. Validators reject selectors derived from phone numbers, database IDs, localized labels, or other mutable values.

### Consequences

- Playwright locators remain stable across data changes.
- Dynamic rows are still addressable through an explicit secondary key contract.

### References

- `.planning/UI-TEST-CONTRACT.md`
- OBL-FOUND-UI-DRIFT-002

## DR-01-004: Remote delivery uses an external annotated attestation

### Status

Accepted

### Context

A Git commit cannot contain its own final SHA because changing `SUMMARY.md` changes the tree and therefore the commit object ID. The project still requires one atomic Phase commit and verifiable remote visibility.

### Decision

The atomic Phase commit records its configured remote, full branch ref, and deterministic annotated delivery-tag ref. After pushing that commit, delivery creates and pushes an annotated tag targeting the exact commit. The tag payload records phase, branch, commit, tree, `tested_subject_digest`, `subject_manifest_digest`, `evidence_manifest_digest`, PR/check locator, and PASS status. The post-push validator resolves the remote branch and annotated tag, requires both to target the same commit, reads the canonical subject manifest from that target tree, and recomputes its allowed input set. No second implementation commit is created.

`SUMMARY.md` records the delivery-tag locator rather than claiming an impossible literal self-SHA. Dependency gates resolve the live remote attestation and reject missing, lightweight, moved, malformed, or mismatched tags.

### Consequences

- The implementation remains one atomic commit while remote visibility is proved after the commit exists.
- Delivery attestation becomes external Git state and must use a protected, deterministic tag namespace.

### References

- Git content-addressed object model
- OBL-FOUND-TRACE-004
- GitHub issue #13

## DR-01-005: Supported desktop viewports are explicit

### Status

Accepted as revised by DR-01-016

### Context

PRD 6.3 defines `1366x768` as the minimum supported desktop size but does not name a representative wide desktop.

### Decision

Compatibility evidence executes every required browser/version cell at `1366x768` and `1920x1080`. Future viewport changes require a recorded decision and a full matrix rerun.

### Consequences

- The matrix is finite and reproducible.
- Phase 1 does not claim mobile responsiveness.

### References

- `docs/PRD.md` section 6.3
- OBL-NFR-BROWSER

## DR-01-006: UI drift compares normalized relation tuples

### Status

Accepted

### Context

Set membership alone can pass when a selector exists only on the wrong route or in dead source.

### Decision

The validator normalizes navigable leaf route, rendered component, selector, row-key contract, and Playwright action/assertion into relation tuples. Index routes and redirects declare their target; parameterized routes preserve the route pattern; unknown dynamic route construction fails as unsupported rather than being guessed.

### Consequences

- Missing and stale references fail in both directions with source locations.
- Comment, string, dead-component, and unrelated-smoke occurrences cannot satisfy the contract.

### References

- OBL-FOUND-UI-DRIFT-001
- `.planning/UI-TEST-CONTRACT.md`

## DR-01-007: Evidence retention separates committed facts from diagnostic artifacts

### Status

Accepted

### Context

Small machine-readable facts must survive repository review, while screenshots, traces, and videos can be large and may accidentally contain sensitive data.

### Decision

Commit only redacted JSON summaries, schemas, source/checksum manifests, and small deterministic fixtures. Store screenshots, traces, videos, raw service logs, and browser runtime reports as access-controlled CI artifacts; committed summaries record their run/artifact locator and checksum. Synthetic data is mandatory in either location.

### Consequences

- Git history stays reviewable without losing evidence linkage.
- CI retention/access policy is part of the recorded environment identity.

### References

- `AGENTS.md`
- OBL-FOUND-TRACE-003

## DR-01-008: Compatibility verifier ownership is separate from product acceptance

### Status

Accepted

### Context

Phase 1 is a verification-only foundation and cannot truthfully prove future first-release exports or international-message persistence that do not yet exist. Synthetic copy/export or timezone DTO fixtures are necessary for validator behavior, but they are not production acceptance evidence.

### Decision

Phase 1 owns the versioned fail-closed simplified-Chinese copy/export contract validator and UTC+8/IANA-timezone verifier under OBL-FOUND-TRACE-003. OBL-NFR-CHINESE and OBL-NFR-TIMEZONE retain their IDs, requirement links, catalog tests, evidence targets, and product semantics but are owned by `final-release-acceptance`, whose Phase 56 execution must rerun the foundation contracts against all delivered production UI, errors, exports, storage, APIs, displays, and international-message persistence.

### Consequences

- Phase 1 has exactly seven owned obligations and never closes either product-level compatibility TODO.
- A synthetic fixture can prove the reusable contract fails closed, but cannot prove a future product surface exists or conforms.
- Phase 56 cannot substitute foundation fixture evidence for executed production acceptance.

### References

- OBL-FOUND-TRACE-003
- OBL-NFR-CHINESE
- OBL-NFR-TIMEZONE
- REQ-NFR-COMPATIBILITY
- GitHub issue #13

## DR-01-009: Browser smoke uses one local-direct real-HTTP responder

### Status

Accepted as revised by DR-01-016

### Context

Playwright request interception does not prove that an actual Google Chrome session reached the same deterministic failure path.

### Decision

`LOGIN-SMOKE-V1` is served by one repository-owned Node HTTP server that serves `web/dist` with SPA fallback, exposes tested-subject/scenario identity through `GET /__phase01/health`, and returns the fixed safe 401 contract from canonical `POST /console/auth/login` and the existing `/api/v1` client-prefix mapping. The code-owned local Chrome runner reaches its base URL directly. Health subject-manifest/tested-subject/scenario digests, exact 401/body/marker, and the actual Chrome transcript are independently validated; a runner-side request cannot substitute.

### Consequences

- Structural diagnostics and both actual Google Chrome major runs execute the same HTTP scenario without interception.
- No provider, credential, remote host, tunnel, or VM capability is required or inferred.
- Missing or mismatched reachability evidence is BLOCKED/nonzero.

### References

- LOGIN-SMOKE-V1
- OBL-NFR-BROWSER
- D-02, D-04, D-06

## DR-01-010: Visual acceptance requires full unclipped and unobstructed element evidence

### Status

Accepted

### Context

A positive viewport rectangle does not detect an ancestor that clips an element, a clip-path or mask, zero visibility/opacity, or an overlay covering an actionable corner.

### Decision

`LOGIN-CARD-IN-VIEWPORT-V2` binds one canonical JSON digest and requires each registered element rectangle inside the viewport, full coverage through every applicable ancestor overflow intersection, rejection of computed hidden/clip-path/mask state, and center-plus-four-inset-corner hit-tests that resolve to the element or its descendants. IntersectionObserver exact-full-visibility output is supplemental only. The evaluator checks registered border boxes and DOM ancestors, not internal input-content descendants.

### Consequences

- Ancestor clipping, clip-path, mask, hidden/zero-opacity state, and transparent or opaque overlays have isolated stable failure diagnostics.
- Normal internal input-content clipping does not produce a false failure.
- Structural diagnostics and the local Chrome runner must record the same rule ID and digest.

### References

- LOGIN-CARD-IN-VIEWPORT-V2
- OBL-NFR-BROWSER
- DR-01-005

## DR-01-011: Evidence subject uses a canonical tested-input manifest

### Status

Accepted as revised by DR-01-016 for browser runtime evidence

### Context

A committed evidence envelope cannot require the final commit ID that contains that envelope: adding the ID changes the commit object. Evidence still needs a deterministic, independently recomputable identity for the implementation, tests, configuration, contracts, and validators that actually ran.

### Decision

Committed evidence does not contain or require final commit identity. It records `subject_manifest_path`, `subject_manifest_digest`, and `tested_subject_digest`. The canonical `tested-inputs.json` manifest is a stable path-sorted list of repository-relative entries; every entry contains path, file mode, SHA-256, and a code-owned role from implementation, test, config, contract, or validator.

The schema and code-owned per-check registries derive the required input union. They explicitly exclude the manifest itself, generated execution `EVIDENCE` summaries/artifacts, TODO/SUMMARY, later review records, and delivery metadata so the subject never hashes itself. The exact Gate D browser admission, referenced probe artifacts, independent entry attestation, and `ENTRY-REVIEW.md` are explicit config/contract exceptions and remain inside the tested subject. Producer-controlled JSON cannot exclude or conceal source, test, config, contract, or validator inputs. Missing/extra inputs, content or mode changes, illegal exclusions, manifest-digest mismatch, and wrong/stale tested subjects fail closed.

Every child, aggregate, browser health response, browser cell, obligation summary, evidence manifest, GSD review, and Claude review binds the same subject manifest and digests. CI or another external runtime may record its runtime commit SHA only in a non-committed artifact; it is not a required field of committed evidence.

The final single Phase commit contains the tested inputs, canonical subject manifest, evidence, and delivery documents. The annotated delivery tag externally records commit, tree, tested-subject digest, subject-manifest digest, evidence-manifest digest, branch, PR/check locator, and PASS. The post-push validator reads the subject manifest from the tag target commit/tree, recomputes allowed path/mode/content entries, and compares every evidence/review/tag digest. It never amends the commit or creates a second implementation commit.

### Consequences

- Evidence remains deterministic and detects source/test/config/contract/validator drift without a self-reference.
- Any post-test input change invalidates evidence and reviews until the canonical subject and affected checks are regenerated.
- Delivery commit identity is established externally by the annotated tag after the single atomic commit exists.

### References

- D-01, D-02, D-06
- DR-01-004
- OBL-FOUND-TRACE-003
- OBL-FOUND-TRACE-004

## DR-01-012: Gate D browser admission is pinned by an independent digest chain

### Status

Superseded by DR-01-016; historical only

### Context

Moving `browser-source-admission.json` out of Plan 06 removes the lifecycle cycle, but a structurally valid replacement after entry review could otherwise select a different runner or adapter. Execution-time schema validation alone does not prove byte identity with the source accepted at Gate D.

### Decision

Before implementation, an allowed developer runs the repository-owned `.planning/tools/produce-phase-01-chrome-entry.rb` with explicit destination, cache, and platform arguments. Its fixed-argv/no-shell implementation resolves allowlisted official Chrome for Testing metadata and browser/driver URLs, computes content and executable SHA-256s, safely extracts the artifacts, validates Google Chrome for Testing and matching ChromeDriver CLI identities, launches a real isolated WebDriver session, validates returned capabilities, and writes the admission candidate plus exactly two successful identity-only probe artifacts: `chrome-151` and `chrome-152` for the 2026-08-30 baseline. Gate D deliberately proves source and runtime identity only; the two required viewports per major remain Plan 01-06 execution evidence. The independent entry reviewer creates `browser-source-entry-attestation.json`, which records the exact repository-relative admission path, file mode, SHA-256, and the exact path/mode/SHA-256 set for those two probes. `ENTRY-REVIEW.md` records the accepted admission and attestation SHA-256 values.

The bootstrap and Plan 06 validators recompute this chain before Chrome source resolution or browser launch. The canonical tested-input union includes the admission, two probe artifacts, attestation, and `ENTRY-REVIEW.md` as code-owned config/contract inputs despite the general generated-evidence and later-review exclusions. Admission content replacement, file-mode drift, missing or extra probes, accepted-digest mismatch, probe-artifact mismatch, dynamic command data, provider/tunnel/secret/VM fields, or developer-specific absolute paths returns BLOCKED/nonzero.

### Consequences

- Plans 01 through 12 consume an independently accepted immutable input and cannot manufacture their own authorization.
- Any Gate D input change invalidates bootstrap, the tested subject, affected evidence, and reviews until a new independent entry review accepts the new digest chain.
- Executable command strings remain outside committed admission data; the code-owned pre-entry producer and local Chrome runner define executable argv and use no browser secret.

### References

- D-01, D-02, D-04, D-06
- DR-01-002, DR-01-011
- OBL-FOUND-TRACE-003
- OBL-NFR-BROWSER

## DR-01-013: Product support is desktop Google Chrome only

### Status

Superseded by DR-01-016; historical only

### Context

Maintaining executable current/previous coverage for Chrome, Edge, and Safari required provider, tunnel, secret, runner-image, and VM work that did not improve the product's chosen support contract proportionally. The product owner selected a narrower browser target for better implementation and verification ROI.

### Decision

Only desktop Google Chrome is supported. Acceptance executes the current stable major and previous stable major at 1366x768 and 1920x1080. The 2026-08-30 Gate D baseline is Chrome 152/151, sourced from official Chrome for Testing browser and driver artifacts and executed through a local code-owned runner. Edge, Safari, Firefox, Internet Explorer, and every other browser are explicitly unsupported and outside source admission, implementation, testing, evidence, and acceptance. Phase 1 does not add a browser-blocking or warning UI.

### Consequences

- Gate D requires exactly two identity probes and four acceptance cells.
- Provider, tunnel, browser-secret, remote-host, and VM paths are deleted from the current plan.
- Bundled Chromium may remain a clearly labelled structural diagnostic but cannot satisfy OBL-NFR-BROWSER.
- Users and deployment documentation may require supported desktop Google Chrome and make no compatibility guarantee for other browsers.

### References

- D-04, D-08
- OBL-NFR-BROWSER
- REQ-NFR-COMPATIBILITY

## DR-01-014: Gate D Chrome producer never trusts reusable cache contents

### Status

Superseded by DR-01-016; historical only

### Context

The first Chrome-only producer design reused predictable archive and extraction paths under a caller-supplied cache. A current-user process or stale prior run could prepopulate those paths, and the producer could execute content before a trusted digest chain existed. Symlinkable cache/destination roots and unbounded archive expansion added avoidable pre-entry trust gaps.

### Decision

The `--cache` argument is only a verified current-user-owned, non-symlink `0700` workspace root. Each invocation creates a new unpredictable exclusive `0700` run directory and atomically downloads fresh official Chrome for Testing archives through allowlisted HTTPS hosts; there is no cache-hit or reuse path. The repository Phase 1 `EVIDENCE` destination is fixed and revalidated for ownership, symlinks, and unsafe modes. Archive inspection and extraction are bounded by entry count, individual/total uncompressed size, and compression ratio; ambiguous paths, duplicate/conflicting paths, hardlinks, special entries, and all links outside DR-01-015's narrow macOS Chrome framework policy fail closed. Only canonical regular non-symlink Chrome/driver executables with matching CLI/WebDriver identities may run, with fixed argv and a fresh isolated profile. Cleanup removes only a verified marked run directory under the verified cache root.

### Consequences

- Caller-prepopulated predictable archives are ignored and never executed.
- Deterministic offline adversarial tests cover cache/destination symlinks, owner/mode seams, ZIP path/type/size/count/ratio limits, and redirect allowlisting.
- `/private/tmp/ycsopen-sms-phase01-chrome-cache` is the canonical macOS command path so `/tmp` symlink ambiguity cannot weaken the path-component check.
- Gate D remains blocked until live Chrome probes and independent attestation exist; security fixtures cannot substitute for live evidence.

### References

- DR-01-012, DR-01-013
- OBL-NFR-BROWSER
- Plan 01-06

## DR-01-015: Permit only the standard macOS Chrome framework link graph

### Status

Superseded by DR-01-016; historical only

### Context

Official Chrome for Testing macOS application archives use five internal symbolic links in `Google Chrome for Testing Framework.framework`: four public framework entries route through `Versions/Current`, and `Versions/Current` selects the full version directory. Blanket symlink rejection made the documented Apple Silicon Gate D producer unable to extract an otherwise official archive. Broad symlink acceptance would instead reopen traversal, link-following write, dangling-target, cycle, and type-confusion risks. A live 152.0.7977.64 archive probe corrected the earlier synthetic-fixture name `Google Chrome Framework.framework`; the allowlist and positive fixture must use the actual Chrome for Testing bundle identity so offline tests cannot validate a different product layout.

### Decision

For `chrome-mac-arm64` and `chrome-mac-x64` archives only, the producer permits exactly these bundle-relative link positions: `Google Chrome for Testing Framework`, `Helpers`, `Libraries`, `Resources`, and `Versions/Current` under `Google Chrome for Testing.app/Contents/Frameworks/Google Chrome for Testing Framework.framework`. The first four targets are fixed relative paths through `Versions/Current`; the `Current` target is a syntactically valid four-part Chrome version directory and is not pinned to one patch version. Every link target must be relative UTF-8 without NUL, backslash, absolute or drive-prefix form, must normalize inside the same framework root, and must resolve through a complete acyclic allowlisted graph to an already-extracted regular file or directory of the required type.

Extraction is two-pass into a fresh verified root: regular files/directories are extracted while all five link entries are excluded, the complete graph is read and validated after targets exist, links are created only at absent allowlisted leaves under non-symlink parents, and the final tree is traversed without following directory links and revalidated against the same path/target/containment/type graph. Every other symlink and all hardlinks or special entries remain rejected. The existing entry-count, individual/total uncompressed size, compression-ratio, canonical-path, duplicate/conflict, ownership, and fresh-run limits are unchanged.

### Consequences

- The official macOS Chrome for Testing framework layout is runnable without weakening general archive link policy.
- Deterministic offline coverage includes the five-link positive layout plus non-allowlisted, absolute, escaping, dangling, cyclic, link-to-link escaping, and target-type conflict cases.
- The browser and ChromeDriver executable paths themselves must still be regular non-symlink files before fixed-argv identity checks and execution.
- No offline archive fixture is evidence that the live Chrome 151/152 Gate D probes passed.

### References

- DR-01-012, DR-01-014
- OBL-NFR-BROWSER
- Plan 01-06

## DR-01-017: Wave 0 is the sole pre-entry remediation exception

### Status

Accepted; current entry sequencing contract

### Context

The corrected browser entry contract could not be implemented while every plan required an already-passing entry gate. The independent checker also found that entry evidence and final Playwright smoke evidence had been conflated, the plan catalog still described 12 plans, and the interrupted Plan 05 work lacked a strict resumption boundary.

### Decision

`01-00-PLAN.md` is the only plan that may run while Phase 1 ENTRY is BLOCKED. It owns only the five entry-tool/test files, entry-only `EVIDENCE/local-chrome-entry.json`, `ENTRY-REVIEW.md`, and `01-00-SUMMARY.md`. It executes the standard-path Chrome fixed-argv version and isolated-profile headless synthetic-page probe without downloads, drivers, Playwright, or a fixed version. A reviewer distinct from the executor must independently rerun it and populate the exact four-column entry table; only that PASS plus a zero-exit bootstrap validating all 13 plans authorizes every remaining plan.

`EVIDENCE/local-chrome-entry.json` proves entry readiness only. Plan 06 remains solely responsible for Playwright `EVIDENCE/local-chrome-runtime.json` and one `/login` smoke-and-visual run at 1440x900. Plan 05 resumes its already-landed but unaccepted work inside its existing 14-file implementation ownership plus the already-authorized `SendPage.tsx` Rule 3 repair; it does not create another formal phase or expand its file set. Completed Plans 01/02/03/04/08 are not reimplemented, but later root/evidence plans regression-check their outputs against the revised tested-subject contract.

### Consequences

- The circular entry dependency has one explicit, bounded remediation path.
- The active Phase 1 catalog contains exactly 13 plans in waves 0 through 8.
- The executor cannot self-authorize, and Plan 06 cannot manufacture its own entry permission.
- Phase 1 still ends with one final atomic commit and push; Plan 00 creates no separate commit.

### References

- DR-01-016
- `01-00-PLAN.md`
- `ENTRY-REVIEW.md`
- `01-05-PLAN.md`
- `01-06-PLAN.md`

## DR-01-018: Active entry consumers belong to the pre-entry remediation boundary

### Status

Accepted; revision of DR-01-017 ownership only

### Context

The first independent Plan 00 entry review produced `6 PASS / 2 BLOCKER`. The fixed-path Chrome identity, version, direct headless launch, 13-plan set, and reviewer independence passed. ENTRY-04 found that six active evidence/lifecycle/runner/delivery consumers still required the superseded browser-source admission, dual probes, attestation, legacy digests, or removed contract APIs; their failure necessarily kept ENTRY-07 bootstrap blocked. Deferring those consumers until after entry would make the entry gate circular.

### Decision

Plan 00 retains its sole Wave 0 exception and expands to exactly 14 owned files: the existing five entry tools/tests, the six active consumers `.planning/tools/verification-evidence.rb`, `.planning/tools/test-repository-verification.rb`, `.planning/tools/test-phase-lifecycle.rb`, `scripts/lib/phase-01/test_run_checks.rb`, `.planning/tools/validate-delivery-attestation.rb`, `.planning/tools/test-delivery-attestation.rb`, plus `EVIDENCE/local-chrome-entry.json`, `ENTRY-REVIEW.md`, and `01-00-SUMMARY.md`. This is a hard cap and may not expand into application or business code.

The six consumers migrate to the current `local-chrome-entry.json` and independent-review boundary before entry authorization. Historical `EVIDENCE/browser-source-*` JSON remains on disk as superseded audit history but cannot be copied, required, hashed, validated, or accepted as current subject/evidence. Generic tested-subject, subject/evidence manifest digests, target-tree recomputation, branch/tag/commit/tree, PR/check, and delivery fail-closed rules remain unchanged.

The first `6 PASS / 2 BLOCKER` review is preserved as revision input. Only a new independent reviewer that did not implement the consumer migration may change ENTRY-04 and ENTRY-07 after rerunning repository-verification, phase-lifecycle, delivery-attestation, root-runner, producer/bootstrap self-tests, planning/catalog validators, and the real bootstrap.

### Consequences

- No plan, phase, wave, browser lane, or Plan 05 business scope is added.
- ENTRY-04 closes only when all six active consumers and their destructive fixtures pass without legacy membership.
- ENTRY-07 closes only after the updated independent review has no BLOCKER and the real 13-plan bootstrap exits zero.
- Phase 1 still creates one final atomic commit and push; this revision creates none.

### References

- DR-01-016
- DR-01-017
- `01-00-PLAN.md`
- `ENTRY-REVIEW.md` first local-Chrome review
