---
phase: 03-crypto-storage-bootstrap
plan: "15"
subsystem: private-object-storage
tags: [aws-sdk-v2, s3, minio, ciphertext, checksum, bounded-streaming]

requires:
  - phase: 03-crypto-storage-bootstrap-03
    provides: Digest-locked MinIO fixture and AWS SDK v2.54.7 admission
  - phase: 03-crypto-storage-bootstrap-04
    provides: Strict YCSE/v1 envelope parser and purpose-specific capacity bounds
provides:
  - Ciphertext-only private object port with no bucket, caller key, original name, URL, or public ACL surface
  - Purpose-bounded S3 adapter with private bucket admission, exact metadata, SHA-256 verification, and bounded reads
  - Deterministic boundary/fault coverage plus a real digest-locked MinIO lifecycle and anonymous-denial check
affects: [03-16-object-capability, 03-17-registration-object-api, 03-19-leak-proof, 03-22-evidence-production]

tech-stack:
  added: [AWS SDK for Java S3 2.54.7 production scope, AWS URLConnection client 2.54.7 production scope]
  patterns:
    - Exact-purpose ciphertext admission before object transfer
    - HEAD and metadata validation before opening a response body
    - Stable sanitized storage failures without provider causes

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/PrivateObjectStorePort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/StoredObjectMetadata.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectStoreProperties.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/S3PrivateObjectStoreAdapter.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/object/S3PrivateObjectStoreAdapterTest.java
  modified:
    - core/pom.xml
    - core/src/main/resources/application.yml

key-decisions:
  - "Object keys are generated inside the adapter as a closed opaque grammar; callers can never supply a bucket, original name, ACL, policy, or URL."
  - "The admitted bucket state permits canonical-owner ACL grants and no bucket policy; any group grant or policy fails closed before object access."
  - "User metadata is normalized case-insensitively for S3 compatibility, then validated as exactly purpose, envelope-length, and sha256 with duplicate or unknown keys rejected."
  - "AWS credentials are selected only through default-chain, container, or instance-profile provider indirection; access-key and secret-key configuration fields do not exist."

patterns-established:
  - "Bounded object read: private admission -> HEAD validation -> GET header validation -> exact-size read plus one-byte lookahead -> YCSE parse -> SHA-256 comparison."
  - "Split-write containment: an uncertain or checksum-rejected put triggers a best-effort delete, leaving higher-level reconciliation as the final lifecycle owner."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
metrics:
  tasks: 1
  files: 7
---

# Phase 03 Plan 15: Private Ciphertext Object Store Summary

**A private AWS SDK v2 object boundary now accepts only canonical YCSE/v1 ciphertext, enforces five purpose-specific envelope ceilings, and verifies exact metadata and checksums before exposing bytes.**

## Accomplishments

- Added a closed `PrivateObjectStorePort` with only purpose-bound `put`, `get`, `head`, and `delete`; its API has no caller bucket, original filename, ACL, policy, direct-link, or raw endpoint output.
- Added internally generated 256-bit opaque storage keys and immutable metadata/body values whose string representations redact storage keys, checksums, and ciphertext.
- Reused the canonical envelope parser to reject malformed, over-limit, short, extra, declared-versus-actual, and u32-sized input before S3 body transfer.
- Added private bucket admission before each object access: canonical-owner ACL only and no bucket policy. Group grants and every policy fail closed.
- Added exact S3 user metadata, service/custom SHA-256 verification, HEAD-before-GET size checks, response-header revalidation, exact bounded reads, extra-byte detection, and response abort on failure.
- Promoted only the approved AWS SDK v2 S3 and URLConnection modules to production scope at version `2.54.7`; excluded the unselected Apache5 and Netty HTTP runtimes.
- Proved the adapter against the admitted MinIO image `minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e`, including authenticated lifecycle, raw ciphertext equality, anonymous HTTP 403, and owner-scoped cleanup.

## Task Commit

1. **Task 1: Implement ciphertext-only private object-store adapter** — `2f5904c`

## Boundary Contract

| Operation | Admitted input | Fail-closed checks | Returned value |
| --- | --- | --- | --- |
| `put` | five-value purpose, admitted media type, YCSE/v1 ciphertext stream, optional declared length | purpose ceiling before read, exact declared/actual length, canonical envelope, private bucket, SHA-256 response match | opaque key plus size/checksum/media facts |
| `head` | opaque generated key and expected purpose | key grammar, private bucket, purpose ceiling, exact three-field metadata, media allowlist, optional service checksum | size/checksum/media facts |
| `get` | opaque generated key and expected purpose | complete `head` gate before GET, repeated headers, exact bounded stream, short/extra byte, envelope parse, SHA-256 | copied ciphertext and immutable facts |
| `delete` | opaque generated key and expected purpose | complete `head` gate before delete | no object body or locator output |

The five exact complete-envelope ceilings are `10,485,905` bytes for business license, short-link-domain proof, and trademark proof, and `5,243,025` bytes for representative ID front and back. One byte over each ceiling is rejected before the input stream is read or an object body is transferred.

## Verification

- `mvn -f core/pom.xml -Dtest=S3PrivateObjectStoreAdapterTest test` — PASS: 21 discovered, 20 ordinary boundary/fault cases passed, and the opt-in real MinIO case was skipped outside its profile as designed.
- `mvn -f core/pom.xml -Pphase03-integration -Dtest=S3PrivateObjectStoreAdapterTest test` — PASS: all 21 cases, including the real digest-locked MinIO adapter lifecycle and anonymous denial.
- `mvn -f core/pom.xml test` — PASS: 102 tests, no failure or error; nine opt-in service tests skipped outside their profiles.
- `mvn -f core/pom.xml dependency:tree -Dincludes=software.amazon.awssdk` — PASS: every resolved AWS SDK module is version `2.54.7`; direct production modules are S3 and URLConnection, and the unselected Apache5/Netty HTTP runtimes are absent after exclusions.
- `! rg -n 'Presigner|presign|PUBLIC_READ|public-read|getUrl' core/src/main/java/com/ycsopen/sms/core/common/security/object` — PASS.
- `! rg -n 'readAllBytes\(' core/src/main/java/com/ycsopen/sms/core/common/security/object` — PASS.
- `/usr/bin/env ruby scripts/lib/phase-03/service_checks.rb assert-clean --all` — PASS after real MinIO verification.
- `git diff --check` — PASS before the task commit.

## Fault Results

- Every purpose accepts its exact complete-envelope boundary and rejects one byte over without reading the input.
- Unknown-length input stops at the first excess byte; declared-short, declared-long, u32 overflow, short response, extra response, malformed envelope, checksum mismatch, and over-limit HEAD all fail closed.
- Traversal, original-name-shaped and URL-shaped keys, unapproved or credential-bearing endpoints, disallowed media types, unexpected metadata, group ACLs, and bucket policies are rejected.
- Provider failures retain no cause or arbitrary provider text; endpoint, bucket, key, credential, provider detail, and object-body canaries are absent from returned messages.
- An uncertain put and a checksum-rejected put both attempt deletion without exposing the generated storage key.

## Decisions Made

- Used the existing `EnvelopeCodec` as the sole envelope grammar/capacity owner instead of reproducing header offsets or accepting arbitrary ciphertext blobs.
- Kept configuration immutable and disabled by default. A configured endpoint must be exact-allowlisted; cleartext transport is accepted only for explicitly enabled loopback fixture use.
- Rejected every nonempty bucket policy rather than implementing a partial policy-language evaluator. Application identity policy supplies access, and the bucket remains deny-by-default.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - S3 compatibility] Normalized metadata names before exact validation**

- **Found during:** Task 1 real MinIO verification.
- **Issue:** MinIO returned the three user metadata names in title case even though the AWS SDK request used lowercase names. A case-sensitive map comparison rejected the otherwise exact private object.
- **Fix:** Normalize metadata names with `Locale.ROOT`, reject duplicate normalized names, then require the exact closed three-name set and exact values.
- **Files modified:** `S3PrivateObjectStoreAdapter.java`, `S3PrivateObjectStoreAdapterTest.java`.
- **Verification:** Focused unit suite and real MinIO profile both pass after the correction.
- **Committed in:** `2f5904c`.

**2. [Rule 2 - Missing critical containment] Deleted uncertain writes on provider failure**

- **Found during:** Task 1 fault review.
- **Issue:** A provider exception can occur after the remote store accepted bytes, leaving a ciphertext orphan whose generated key is unavailable to the caller.
- **Fix:** Attempt an internal delete for every uncertain or checksum-rejected put; the later object service remains responsible for operation-level reconciliation.
- **Files modified:** `S3PrivateObjectStoreAdapter.java`, `S3PrivateObjectStoreAdapterTest.java`.
- **Verification:** The focused test proves the delete call occurs and the provider diagnostic remains redacted.
- **Committed in:** `2f5904c`.

**Total deviations:** Two auto-fixed correctness/security items. Both preserve the planned ciphertext-only private boundary without adding an endpoint, schema, public access path, or alternate SDK.

## Issues Encountered

- Context7 MCP and the `ctx7` CLI were unavailable. Version-specific stream behavior was verified against official AWS SDK for Java documentation before implementation.
- The first real test harness attempt resolved the service script relative to Maven's module working directory. The test now walks only ancestor directories for the exact regular non-symlink script and reports stable errors without exposing paths.

## Known Stubs

None. The ordinary suite intentionally skips the real MinIO test outside `phase03-integration`; the enabled profile reaches the real locked service and cannot substitute a mock.

## Threat Surface Review

The planned production network boundary is limited to the injected S3 client. Endpoint canonicalization/allowlisting, credential-provider indirection, private ACL/policy admission, opaque internal keys, bounded ciphertext allocation, exact metadata and SHA-256 verification, stable failures, response abort, and public/direct-link API absence implement the plan threat mitigations. The test-only process reaches the existing run-owned MinIO harness and proves cleanup; it adds no production endpoint, credential value, persistent service data, or public access method.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This adapter and its real MinIO proof do not yet implement the protected object service, capability lifecycle, MySQL object metadata, tenant registration composition, leak evidence, or the canonical exact-four obligation evidence.

## Self-Check: PASSED

- All seven planned implementation/configuration files and this summary exist.
- Task commit `2f5904c` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused, real MinIO, full backend, dependency, public/direct-link scan, bounded-read scan, cleanup, and diff checks pass against the committed implementation.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; its canonical frontmatter shape is unchanged.
- Every Phase 03 obligation TODO remains open and `requirements-completed` remains empty.
