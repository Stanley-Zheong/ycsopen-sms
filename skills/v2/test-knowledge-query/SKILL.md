---
name: test-knowledge-query
description: "Read-only lookup of ycsopen-sms product and engineering knowledge across PRD, GSD planning/intel/phase artifacts, code, migrations, tests, docs, and Git history with source authority, freshness, conflicts, and limitations. Do not edit or publish knowledge."
---

# Project knowledge query

Search exact requirement, obligation, behavior, case, route, selector, API,
entity, table, class, or commit terms with `rg` before broad concepts. Use
authority from `skills/SKILL.md`; derived summaries support but do not override
current PRD/SPEC/code/runtime evidence.

Return only relevant results:

```yaml
query:
results:
  - path:
    line_or_commit:
    authority:
    status: current | historical | derived | conflicting
    excerpt_or_summary:
    last_verified:
limitations: []
```

Label conflicts and stale/history-only results. Redact secrets and private
tenant/message data. Current behavioral claims require code/test/runtime
cross-check; a planning prose hit alone is not an oracle. Return results to the
requesting lifecycle owner and stop without mutation.
