# Phase 41 Decisions

- Use one focused service/controller instead of introducing a generic workflow/task engine.
- Reuse `complaints` and `disposal_records`; add only fields required by the PRD obligation set.
- Treat signature/template disablement as making approved resources unusable by moving review status away from `APPROVED`; this aligns with existing send-time compliance checks and avoids a parallel state model.
- Keep Chrome as the only browser validation target.
- Keep automatic complaint ratio thresholds out of Phase41.
- Use explicit `UNKNOWN` attribution whenever required link fields are missing.
- Treat actor values as server-side audit evidence from the authenticated principal; request-body actor fields are ignored by the controller.
- Do not expose artificial failure controls in production remediation APIs; failed remediation records come only from real execution failures.
- Allow recovery only for failed remediation records, as manual compensation evidence with an authorized review id.
- Allow FINANCE to read complaint cases and analytics, but not to create, transition, remediate, or recover complaint cases.
- Record remediation as `APPLIED` only when the target resource update affects one existing row.
- Require remediation targets to match the complaint's own attribution before any resource mutation.
- Keep the UI remediation control compact: automatic target/type matching by default, with an explicit type selector for operator override.
- Do not guess recovery record ids in the UI; require the page to have a remediation record id from the current case action.
