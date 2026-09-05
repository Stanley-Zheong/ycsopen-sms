---
phase: 03-crypto-storage-bootstrap
plan: "04"
subsystem: cryptographic-envelope
tags: [ycse, aes-gcm, aad, bounded-parsing, java-21]

requires:
  - phase: 03-crypto-storage-bootstrap-01
    provides: Canonical protected-data inventory and database/object capacity limits
provides:
  - Immutable YCSE/v1 envelope and six-field semantic context values
  - Strict 19-byte big-endian parser/serializer with bounded stream reads
  - Exact data/wrap AAD encoders with full authenticated header and domain separation
  - Sanitized fail-closed representation and authentication failure category
affects: [03-05-key-ports, 03-10-pkcs11-provider, protected-persistence, protected-object-storage, encrypted-snapshots]

tech-stack:
  added: []
  patterns:
    - Validate complete headers and checked lengths before length-derived allocation
    - Authenticate one canonical header and context under distinct data and wrap domains
    - Defensively copy all protected binary values and redact their string representations

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/CipherEnvelope.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/EnvelopeCodec.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/ProtectionContext.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/ProtectionFailure.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/envelope/EnvelopeCodecTest.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/envelope/ProtectionContextTest.java
  modified: []

key-decisions:
  - "Keep YCSE/v1 parsing, serialization, capacity selection and AAD construction under one EnvelopeCodec owner."
  - "Expose every malformed, context-mismatched or authentication-invalid value as PROTECTED_DATA_INVALID with no cause, value or oracle detail."
  - "Treat absent stream length as untrusted and consume at most the selected target envelope ceiling plus one detection byte."

patterns-established:
  - "Preallocation gate: fixed fields, unsigned lengths, target bounds, checked sums and complete input size pass before declared-length copies."
  - "Canonical AAD: both AEAD domains bind the same header-auth-v1 and context-v1 bytes, including declared ciphertext length."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
metrics:
  tasks: 1
  files: 6
---

# Phase 03 Plan 04: Strict YCSE Envelope and Canonical AAD Summary

**YCSE/v1 now has a byte-exact, immutable and capacity-bounded Java representation whose full header and six-field row/object context are authenticated under distinct data and wrap domains.**

## Accomplishments

- Implemented the exact 19-byte unsigned big-endian header, fixed `pkcs11` provider, canonical 1-to-32-byte key-reference grammar, 12-byte nonces, 48-byte wrapped DEK, and u32 ciphertext length.
- Added strict byte-array and stream parsing that rejects unknown fields, truncation, trailing bytes, declared/actual mismatch, over-limit inputs, u32 violations and checked-arithmetic failures before length-derived allocation.
- Encoded canonical context as schema byte `1` plus six nonempty u16-length-prefixed strict UTF-8 fields, including exact purpose and tenant-scope rules.
- Produced exact `YCSE-DATA-AAD\0` and `YCSE-WRAP-AAD\0` encodings over the same authenticated header and semantic context, with ciphertext length always included.
- Made envelope arrays and context encodings immutable through defensive copies and reduced all invalid/tampered cases to one cause-free, stackless sanitized category.

## Task Commit

1. **Task 1: Specify and implement strict YCSE/v1 plus canonical AAD** — `63448e5`

## Binary Contract

| Component | Enforced contract |
| --- | --- |
| Fixed header | Exactly 19 bytes; magic/version/data algorithm/wrap algorithm/AAD schema/flags and every length are validated |
| Provider/key | Provider is exactly ASCII `pkcs11`; key reference matches `[a-z0-9][a-z0-9._-]{0,31}` |
| Cryptographic values | Wrap nonce 12 bytes, wrapped DEK 48 bytes, data nonce 12 bytes, ciphertext at least the 16-byte tag |
| Header authentication | Exact serialized header plus provider and key-reference bytes; declared ciphertext length is immutable and authenticated |
| Semantic context | Schema byte plus purpose, owner, class/table, role/field, tenant scope and immutable resource identity |
| Failure surface | One `PROTECTED_DATA_INVALID` category and `protected data is invalid` message; no nested cause or protected value |

The byte-exact golden vector round-trips through the public codec and asserts every header and payload byte independently. The maximum representation overhead is exactly 145 bytes.

## Capacity Proof

| Target | Exact plaintext ceiling | Exact complete-envelope ceiling | One-byte-over result |
| --- | ---: | ---: | --- |
| V1 protected database field | 110 | 255 | Rejected |
| Business license | 10,485,760 | 10,485,905 | Rejected |
| Representative ID front | 5,242,880 | 5,243,025 | Rejected |
| Representative ID back | 5,242,880 | 5,243,025 | Rejected |
| Short-link-domain proof | 10,485,760 | 10,485,905 | Rejected |
| Trademark proof | 10,485,760 | 10,485,905 | Rejected |
| MySQL encrypted-snapshot chunk | 10,485,760 | 10,485,905 | Rejected |

The database tests additionally prove the paired 110/111-byte plaintext and 255/256-byte complete-envelope boundaries. Trusted declared lengths are checked before allocating the exact buffer; absent lengths use a bounded reader capped at target maximum plus one detection byte.

## Mutation Results

- Every fixed-header field rejects an unsupported or noncanonical value, including under/oversized provider and key-reference declarations and an unsigned `0xffffffff` ciphertext length.
- Provider content and every key-reference byte class (uppercase, leading punctuation, separators outside the grammar, whitespace, non-ASCII and overlength) fail closed.
- Separate AES-GCM checks prove wrap nonce, wrapped DEK, data nonce and ciphertext mutations fail authentication.
- Mutations to each of the six semantic fields change the authenticated bytes; wrong context fails both unwrap/data authentication rather than falling back.
- Both domain prefixes are asserted byte-exact and independently mutated; cross-domain or mutated-domain authentication fails with the same sanitized category.
- Truncated, trailing, declared-short, declared-long and unknown-length-overrun inputs all fail without a partial envelope.

## Verification

- `mvn -f core/pom.xml -Dtest='EnvelopeCodecTest,ProtectionContextTest' test` — PASS, 44 tests with zero failures/errors/skips.
- `mvn -f core/pom.xml test` — PASS, 80 tests with zero failures/errors; seven existing opt-in environment tests skipped by their configured gates.
- `git diff --check` — PASS.

## Decisions Made

- `EnvelopeCodec` is the only YCSE/v1 wire, target-capacity and AAD owner. Immutable value classes validate their local shape but do not define alternate encodings.
- The pre-encryption AAD overload accepts the known plaintext size so the authenticated header can include the final ciphertext length before either AEAD operation; the decoded-envelope overload reconstructs the same bytes.
- Purpose and target must agree (`database-field`, `protected-object`, or `mysql-encrypted-snapshot-chunk`), preventing a caller from applying a larger capacity class or a different semantic domain silently.

## Deviations from Plan

None — the implementation stayed within the six declared production/test files and followed `ENVELOPE-CONTRACT.md` as the sole wire, AAD and capacity authority.

## Issues Encountered

- The first behavior-test run failed at compile time as expected because the four production types did not yet exist.
- One draft test attempted to exceed the 6,147-byte combined context ceiling while all five caller-supplied fields remained individually bounded at 1,024 bytes; the fixed purpose value makes that combination smaller than the aggregate ceiling. The test was corrected to assert the maximum legal six-field encoding remains within the contract instead of inventing an impossible input.

## Known Stubs

None. The codec deliberately does not own key-provider operations, persistence, object storage or migration orchestration; those are concrete later-plan boundaries rather than placeholders in this implementation.

## Threat Surface Review

- **Tampering:** full header/context AAD and mutation tests cover row/tenant/field/object substitution and both AEAD domains.
- **Denial of service:** target ceilings, unsigned conversion, `Math.addExact`, exact declared length and maximum-plus-one bounded reads prevent untrusted length allocation.
- **Information disclosure:** immutable redacted values and the single cause-free failure category expose no plaintext, ciphertext, context, key reference or provider exception detail.
- **Spoofing:** provider and key-reference syntax are fixed and byte-validated before any payload allocation.

No network endpoint, authentication path, file-system path or database schema was added beyond the plan's declared cryptographic representation surface.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan supplies the strict representation/AAD prerequisite only; it does not claim production key-provider, persistence, object-storage, migration, leak-proof, independent-review or delivery evidence and therefore completes no obligation.

## Self-Check: PASSED

- All six declared source/test artifacts and this summary exist.
- Task commit `63448e5` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- The focused 44-test suite and complete 80-test backend suite pass against the committed implementation.
- No placeholder/TODO pattern, secret, production data, generated output or runtime state is present in the committed files.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; no progress, percentage, duration or estimate field is added.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
