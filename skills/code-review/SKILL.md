---
name: code-review
description: Review ycsopen-sms diffs or commits for correctness, security, tenant isolation, API/schema compatibility, UI contract coverage, tests, documentation, and GSD phase completeness. Use for read-only review requests; do not implement fixes unless the user separately authorizes changes.
---

# ycsopen-sms code review

Read `skills/SKILL.md`, the active phase artifacts, and the complete diff.

When `origin/main` exists, refresh it and pass it explicitly:

```bash
git fetch origin main
bash skills/code-review/scripts/precheck.sh --base origin/main
```

In a local-only repository, omit the fetch and `--base`; the script prints the
auto-detected `origin/HEAD`, `origin/main`, or `HEAD^` base and fails if none is
safe. `REVIEW_BASE_REF=<ref>` is the non-argument equivalent.

Review findings first, ordered BLOCKER, HIGH, MEDIUM, LOW. Each actionable
finding identifies the file/line, trigger, impact, violated contract, and a
specific correction. Do not report formatting preferences as correctness bugs.

Check at least:

- requirement/behavior/TODO trace and whether the diff stays in one phase;
- authentication, authorization, cross-tenant access, secret/PII handling, and
  audit logging;
- SMS task idempotency, routing, frequency/blacklist/content checks, channel
  transitions, receipts, billing, complaints, retries, and async terminal state;
- API request/response/error compatibility and documentation fallout;
- Flyway immutability, schema ownership, rollback/compensation, and mixed-version
  reader/writer safety;
- React route and state behavior, accessibility, complete UI-ELEMENTS inventory,
  stable `data-testid`, and one-to-one Playwright trace;
- tests that prove behavior rather than implementation details, plus executable
  evidence for the exact changed scope;
- human-readable names, bounded methods/components, useful comments, and no
  duplicate semantic owner.

An empty finding list means only that this review found no actionable defect.
Completion still requires the project gates, blocking-free Claude review, and
an empty scoped TODO set.
