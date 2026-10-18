# Phase 41 Decisions

- Use one focused service/controller instead of introducing a generic workflow/task engine.
- Reuse `complaints` and `disposal_records`; add only fields required by the PRD obligation set.
- Treat signature/template disablement as making approved resources unusable by moving review status away from `APPROVED`; this aligns with existing send-time compliance checks and avoids a parallel state model.
- Keep Chrome as the only browser validation target.
- Keep automatic complaint ratio thresholds out of Phase41.
- Use explicit `UNKNOWN` attribution whenever required link fields are missing.
- Treat actor values as server-side audit evidence from the authenticated principal; request-body actor fields are ignored by the controller.
- Do not expose artificial failure controls in production remediation APIs; failed remediation records come only from real execution failures.
- Allow recovery only for the exact latest failed remediation record, as manual compensation evidence with an authorized review id. Issue `#124` revalidates latest failure under the same complaint lock used by remediation.
- Allow FINANCE to read complaint cases and analytics, but not to create, transition, remediate, or recover complaint cases.
- Record remediation as `APPLIED` only when the target resource update affects one existing row.
- Require remediation targets to match the complaint's own attribution before any resource mutation.
- Keep the UI remediation control compact: automatic target/type matching by default, with an explicit type selector for operator override.
- Do not guess recovery record ids in the UI; use persisted remediation
  readback and allow recovery only for the latest `FAILED` record of that case.
- Define the F-9.4 trend as daily complaint counts grouped by the stored case
  `created_at` calendar date and returned in ascending date order.
- The shared `AdminLayout` may further restrict non-complaint navigation by
  platform role; complaint pages remain visible to `ADMIN`, `OPERATOR`, and
  `FINANCE`, preserving this phase's UI element contract while aligning the
  shell with the later role-specific navigation model.
- Issue `#124` supersedes the compact page-level action draft with a server-loaded single-case workspace and action-local dialogs; the original Phase 41 route and business states remain.
- Issue `#124` adds an append-only event table because mutable complaint/disposal snapshots cannot preserve overwritten evidence. Historical backfill stays nullable where evidence is no longer provable.
- Complaint accept, handle, close, and recovery use compare-and-set semantics; remediation locks the complaint state, and real resource failures persist failure evidence only after the failed resource transaction exits.
