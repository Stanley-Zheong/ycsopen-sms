# Design

## Context and constraints

Phase 1 is the trust root for later completion claims. A green build alone is insufficient: every check must identify its scope, environment, inputs, command, result, and diagnostic artifact. Missing tools or environments are blocking states, not implicit skips.

## Architecture and ownership

- Planning validation remains standard-library Ruby under `.planning/tools/`.
- Web source reconciliation and browser execution use repository-local Node/Playwright tooling under `web/`.
- A repository-root verification entry orchestrates independent checks without swallowing their exit status.
- Every check writes a versioned JSON evidence envelope under the active phase `EVIDENCE/` path.
- Every committed envelope, browser health/cell record, aggregate, obligation summary, and review binds one canonical `tested-inputs.json` subject; final commit identity is external delivery metadata, not a self-contained evidence field.
- Phase 1 owns verification contracts only; later phases supply their own manifests, selectors, tests, and execution reports.
- Phase 56 reruns the copy/export and timezone contracts against complete production surfaces and owns the two product-level compatibility obligations; synthetic foundation fixtures cannot close them.

## Data model and migrations

The canonical tested-input manifest is a stable path-sorted set of repository-relative path, file mode, SHA-256, and code-owned role entries covering tested implementation, tests, configs, contracts, and validators. It excludes itself, generated execution-evidence summaries/artifacts (including `local-chrome-entry.json` and `local-chrome-runtime.json`), TODO/SUMMARY, later review records, and delivery metadata through schema/code-owned rules. Bootstrap/lifecycle separately validates entry evidence and `ENTRY-REVIEW.md`; the browser envelope separately checksums and links Plan 06 runtime/smoke evidence rather than hashing generated artifacts into the source subject. Evidence JSON contains schema version, run/check/phase/obligation/case IDs, argv, working directory, timestamps, sanitized environment identity, result, exit code, stable errors, diagnostics, artifact checksums, `subject_manifest_path`, `subject_manifest_digest`, and `tested_subject_digest`. It contains no final self commit identity, credentials, or private payloads.

Gate D is Plan 00's repeatable fixed-path pre-entry probe against `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`: verify a regular executable, execute fixed argv `--version`, require the `Google Chrome <full-version>` form, then launch the same browser directly in headless mode with a fresh private profile against a synthetic local page. It writes entry-only `EVIDENCE/local-chrome-entry.json` and has no Playwright dependency. A reviewer distinct from the executor reruns the probe, records the exact four-column verdict in `ENTRY-REVIEW.md`, and requires the real bootstrap to validate all 13 plans. Plan 06 later launches the same installed browser through Playwright at 1440x900 and writes separate `EVIDENCE/local-chrome-runtime.json` plus `/login` smoke/visual evidence. The artifacts are repeatable rather than immutable: if Chrome updates, rerun both probes, browser scenario, evidence sealing, and affected reviews. Missing path, non-Google brand, or either launch failure is BLOCKED/nonzero. No download, archive/source checksum chain, ChromeDriver, provider, tunnel, secret, VM, version pair, or viewport matrix exists.

Schema migrations: none

## State machines

Each check transitions `declared -> running -> PASS|FAIL|BLOCKED`. Only `PASS` closes its linked TODO. `FAIL` means an executed assertion failed; `BLOCKED` means a required environment or trustworthy identity was unavailable. Aggregate status is PASS only when all required checks are PASS.

## API or protocol contracts

Commands accept explicit scope/evidence arguments, print concise terminal output, write JSON, and return `0` only for PASS. Unknown arguments, malformed manifests, unsupported evidence versions, or missing required files return nonzero with stable error identifiers.

## Authorization and tenant isolation

Foundation checks use synthetic fixtures and must not require production credentials or tenant data. The Chrome-only local runner uses the already installed standard-path Google Chrome and no browser secret.

## UI and interaction model

The UI validator reconciles declared route/page/selector sets bidirectionally across manifest, React route source, JSX `data-testid` syntax, semantic repeated-row metadata, and Playwright locators. Phase 1 browser and copy scenarios reuse the existing `/login` route and stable `shared-auth-login-*` selectors; no synthetic verification route is added. It does not approve visual quality or business behavior.

## Async, idempotency, retry, and concurrency

Verification retries are explicit and recorded. A retry cannot erase earlier failures. Evidence writes use a fresh run directory or atomic replacement so interrupted runs cannot masquerade as a complete PASS. Any tested input membership, content, or mode change invalidates the subject and every affected evidence/review record.

## Security and privacy

Evidence forbids secrets, access tokens, passwords, full phone numbers, message bodies, and mutable business values in selectors. Paths are repository-relative where possible. Chrome identity is obtained from the canonical local executable's fixed-argv version output and real launch facts, not user-supplied labels or an archive/source checksum chain.

## Observability and audit

Terminal output names every check and evidence path. JSON preserves stable error codes, subject digests, and artifact hashes. CI uploads diagnostic artifacts for failed checks where available; non-committed CI diagnostics may additionally record runtime commit identity.

## Failure, rollback, and recovery

All Phase 1 additions are tooling/configuration changes with no schema mutation. Rollback is a code revert. Interrupted or partial evidence is non-PASS and may be replaced only by a new complete run whose identity is recorded.

## Alternatives rejected

- Treating a missing standard-path Google Chrome or a launch failure as skipped PASS: rejected because it overstates supported-browser compatibility.
- Using grep-only one-way selector checks: rejected because stale manifest/test entries must also fail.
- Embedding all logic in CI YAML: rejected because checks must reproduce locally.
- Using build success as PRD completion evidence: rejected because it does not prove atomic behavior or trace closure.
