# Context

## Reused foundations

- Phase 02 supplies the three route IDs, Admin/Tenant shells, desktop tokens, accessible dialog behavior, and ycsan visual baseline.
- Phase 03 supplies protected field envelopes, private object sessions, purpose/media/magic/size validation, atomic object claim, one-time capabilities, and no-store reads.
- Phase 04 supplies controlled registration notification templates and the provider SPI; Phase 08 adds only the configured HTTP adapter needed by contact verification.
- Phase 05 supplies current database-revalidated JWT/RBAC and disabled-account rejection.
- Phase 06 supplies structural operation audit for every authenticated console mutation and sensitive evidence read.
- Phase 07 full-service harness pattern supplies real Spring/Vite/MySQL/installed-Chrome orchestration without browser API substitution.

## Existing gaps

The current public/tenant routes do not exist; the existing `TenantController` is platform-role-only; required profile fields and check-digit validation are absent; contact verification, supplement decisions, review history, recertification, and operating actions are absent; `approvedBy` is client-controlled and rejection reasons are discarded; legal-ID uploads are limited to 5 MiB instead of 10 MiB; protected evidence cannot be reviewed; and `MessageSubmitService` never checks qualification or tenant-account status.

## Fixed constraints

- Exactly three production routes: `/tenant/register`, `/tenant/qualification`, `/admin/tenants`.
- One shared qualification form; the Admin route uses drawers/dialogs rather than new detail routes.
- Browser acceptance uses only the locally installed Google Chrome at 1440×900.
- Current supported new-work ingress is HTTP `MessageSubmitService`. Future ingresses must reuse the same policy when their owning phase creates them.
- The user-owned Phase 02 PNG remains untouched and excluded from every diff/review/commit.
