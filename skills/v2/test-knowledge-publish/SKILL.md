---
name: test-knowledge-publish
description: "Publish or correct durable ycsopen-sms project knowledge only when the user explicitly requests a repository documentation/planning mutation and supplies or authorizes source-backed evidence. Requires target ownership, independent review, validation, and readback; do not publish raw notes, logs, inferred behavior, or secrets."
---

# Project knowledge publish

Mutation authority is explicit and separate from query. Freeze the proposed
claims, source paths/commits, target file owner, duplicate/conflict search, and
review input before editing. Choose an existing canonical owner such as the
active phase DECISIONS/ITERATIONS, `.planning/intel/`, API/architecture docs, or
user manual; do not create a competing source of truth.

Every published claim records authority, scope, status, evidence, and
supersession when applicable. A fresh reviewer checks correctness, conflicts,
secrets/PII, and destination ownership over unchanged inputs. Run affected
planning/doc/skill validators and query the result back through
`test-knowledge-query`.

Stop on unresolved authority, stale or mutable evidence, changed review input,
duplicate/conflicting truth, missing explicit mutation authority, or failed
readback. Never publish credentials, phone numbers, message content, tenant
records, private URLs, or raw customer artifacts.
