---
name: issue-evidence
description: Extract the minimum reproducible evidence from a ycsopen-sms GitHub issue, user report, logs, stack trace, API exchange, SQL error, HAR, screenshot, or protocol capture before implementation. Skip when the trigger, observed failure, owning boundary, and target commit are already clear.
---

# Issue evidence

Do not modify product code until these gates are satisfied:

- issue/report identity and affected commit or version;
- one-sentence claim with expected and actual behavior;
- executable or mechanically checkable trigger;
- owning boundary candidate in `core/`, `web/`, migration, configuration, or
  external SMS protocol/provider integration.

Prefer narrow extraction. Search an API path, exception, table/column,
`data-testid`, message/task ID, request/correlation ID, or first application
stack frame with `rg`. Inspect only the log slice, HAR request, screenshot
region, or protocol frame needed to establish the invariant. Redact credentials,
tokens, phone numbers, message content, tenant data, and provider secrets before
durable writeback.

Record a falsifiable statement in the active phase ITERATIONS or issue notes:

```text
At <commit>, <trigger> reaches <owner> and should satisfy <invariant>, but
<observed evidence> shows <failure>.
```

Stop expanding investigation when the trigger, failure stage, owner, regression
boundary, and next verification are known. If the evidence points outside this
repository or contradicts the requested behavior, report that conflict instead
of inventing a local fix.
