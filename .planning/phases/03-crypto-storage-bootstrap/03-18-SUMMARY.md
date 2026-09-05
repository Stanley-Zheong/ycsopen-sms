---
phase: 03-crypto-storage-bootstrap
plan: "18"
subsystem: safe-security-logging
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commits: [be3b3d4, e691bda]
---

# Phase 03 Plan 18: Safe security logging

## Delivered

- Added a typed allowlist for correlation, purpose, hashed locator, enum status and counts.
- Added fixed security events/categories; callers cannot supply arbitrary templates, values or throwables.
- Rewired the global exception handler to log stable event facts only while preserving API responses.
- Applied CR/LF, token, URL, envelope, credential and provider redaction to every configured appender and explicitly suppressed throwable rendering.
- Corrected record termination so the converter emits one physical line without literal `%n` output.

## Verification

- `SafeLogValueTest`, `SecurityRedactionConverterTest`, `GlobalExceptionHandlerLoggingTest`: PASS.
- Captured business, unexpected, nested-provider, token, URL, envelope, credential and CRLF canaries are absent while event/category/correlation remain.
- Plan-owned unsafe log-call source audit, Logback XML validation and appender coverage: PASS.

## Boundary

Plan 19 owns independent multi-surface leak scanning and real canary evidence.
