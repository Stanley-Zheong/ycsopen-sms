# Claude Review

Claude CLI review was run against the staged Phase22 diff. It identified actionable blockers:

- Expiry/exhaustion freeze could be rolled back when `consumeTrial` throws.
- Prepaid reserve needed account-level serialization/version guarding to prevent over-freeze.
- Tenant-facing controller methods needed tenant ownership scoping.
- The production `credit-test` HTTP endpoint was a financial backdoor and needed removal.

Fixes applied:

- `consumeTrial` now uses `noRollbackFor = BusinessException.class`, locks the trial row before consuming, checks update count, and `freezeTrial` only records the freeze ledger when status actually changes.
- `reservePrepaid`, `confirmPrepaid`, and `reversePrepaid` lock the prepaid account row and update with version guard.
- `TrialPrepaidLedgerController` now scopes tenant requests to the authenticated user's tenant and restricts platform-only financial mutations to platform roles.
- `credit-test` was removed from the HTTP controller; balance seeding remains package-private test fixture code only.

Follow-up verification:

- `mvn -q -f core/pom.xml -Dtest=TrialPrepaidLedgerServiceTest,TrialPrepaidLedgerControllerSecurityContractTest test` — PASS.
- `mvn -f core/pom.xml test` — PASS, logged in `EVIDENCE/mvn-test.log`.

Second Claude review confirmed the four original blockers were resolved and found one remaining hard blocker:

- `confirmPrepaid` and `reversePrepaid` read ledger state before account lock and could double-apply under concurrent duplicate receipts.

Second fix applied:

- `confirmPrepaid` and `reversePrepaid` now acquire the account lock, conditionally transition the ledger row with `WHERE state='RESERVED'`, and only mutate balance/frozen amount when that transition affects exactly one row.
- Sequential duplicate confirm/reverse tests assert only one audit mutation is recorded.

Final Claude code review result:

- NO HARD BLOCKERS.
