# Phase 12 Context

## Dependencies consumed

- Phase 08 supplies `TenantEligibilityPolicy`; signature application must call it instead of duplicating tenant qualification/account rules.
- Phase 10 supplies configured channel rows.
- Phase 11 supplies `ChannelCandidateEligibilityService`; usable-channel calculation must reuse it instead of inventing a second channel eligibility rule.
- Phase 02 supplies console layout/style/test-id discipline.

## Existing implementation

- `signatures` and `signature_channel_registrations` exist in `V1__init_schema.sql`.
- `Signature` and `SignatureRepository` exist but only cover the older minimal status model.
- Tenant signature and admin audit routes currently point to placeholders.

## Lean implementation decision

Use one focused service and one controller for the Phase 12 module. Avoid a generic review workflow engine, generic filing bus, future provider SDK abstraction, async retry scheduler, template checks, and new browser matrix.
