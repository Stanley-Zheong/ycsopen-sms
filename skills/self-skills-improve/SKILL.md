---
name: self-skills-improve
description: Evaluate a completed ycsopen-sms phase, merged GitHub pull request, review miss, debugging failure, or test failure to decide whether repo-local skills need a durable update. Use only for explicit skill calibration or post-delivery learning, not as a reason to rewrite skills after every change.
---

# Self skill improvement

Treat one completed event as the bounded input:

```text
starting artifact -> expected skill behavior -> actual behavior -> failure mode
-> existing coverage check -> update or no-update decision
```

Use the artifact available before the outcome (request, issue, failing test,
review comment, or phase contract) as the replay input. The final diff and
evidence explain the outcome but must not be leaked into the starting prompt.

Update a skill only for a durable, reusable gap. Prefer a precise correction in
the existing owner skill over duplicate guidance or a new skill. Record
`no_skill_gap`, `not_skill_relevant`, `insufficient_starting_artifact`, or
`covered_by_existing_rule` when no update is justified.

Skill changes remain scoped to `skills/`, receive structural validation and a
diff review, and require the same Git authorization as any other repository
change. Never auto-open a pull request, push, or mutate product code merely
because an evaluation found a skill gap. Redact credentials, customer data,
phone numbers, environment URLs, and raw private artifacts.
