---
name: test-fixture-provision
description: "Execute an explicitly authorized ycsopen-sms fixture plan only in a resettable local or designated test environment, recording owned resources, independent readback, cleanup, and failures. Do not mutate production, shared/unowned resources, or persist secrets."
---

# Fixture provision

Provisioning requires the approved plan, exact environment identity, explicit
mutation authority, runtime secret names, and a unique run ID. Store the
manifest under the active phase evidence:

```yaml
case_id:
run_id:
status: verified | failed-retained | cleanup-verified | blocked
environment:
resources: []
ownership_ledger:
readback_results: []
cleanup:
failures: []
```

Create only resources owned by the run and record each returned ID before later
configuration. Verify tenant ancestry, role, data shape, message/channel state,
and other business preconditions through independent readback; HTTP success is
not enough. On failure, stop and preserve the earliest error and owned IDs.

Cleanup deletes/restores only ledger-proven resources and verifies the final
state. Never print secrets or perform hard delete, truncate, drop, grant/revoke,
or overwrite existing data unless the user explicitly expands authority and the
approved plan includes recovery. Only a verified manifest proceeds to spec
generation; blocked/failed results go to triage.
