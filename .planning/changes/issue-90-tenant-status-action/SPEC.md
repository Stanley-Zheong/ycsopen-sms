# Issue 90 Release Tenant Status Action

GitHub issue #90 restores account-status management for the signed release
acceptance tenant. The existing tenant list exposes the `账户状态` action only
when an account exists, and account-status mutations require that account and
its revision. The release fixture must therefore preserve the same
tenant-to-account invariant established by normal approval.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-90-release-tenant-account | The repeatable development-release seed creates one normal, zero-balance account for `DEV-TENANT` when that tenant has no account. Reapplying the seed neither duplicates the account nor overwrites an existing account status. | A fresh seed produces exactly one `tenant_accounts` row for `DEV-TENANT` with status `NORMAL` and version `0`. After changing that row to `DISABLED`, a second seed execution leaves exactly one row with status `DISABLED`. In the Docker release acceptance environment, the authorized admin sees the existing `账户状态` action on the `DEV-TENANT` row. |

## Scope

- Repair the release-only repeatable fixture for `DEV-TENANT`.
- Keep the existing tenant-list permission and account-existence guard.
- Preserve balances, frozen amounts, status, and version on any existing
  account row.
- Cover fresh and repeated execution in the owning SQL migration test.
- Extend the existing Docker release SQL and Chrome acceptance checks with the
  tenant-account and visible row-action assertions.

Creating accounts for pending or unapproved tenants, changing production
tenant approval, changing account-status authorization, and modifying the
tenant-list UI are outside this change.

## Verification boundary

The migration test executes the production repeatable SQL against an H2 schema
that includes the tables touched by the release fixture. It proves the missing
account repair and repeatability contract. The Docker release acceptance runs
the same SQL on MySQL and checks the existing row action in Google Chrome. A
host without Docker or Chrome can prove only the H2 and frontend-unit layers;
the pull-request CI owns the release acceptance in that environment.
