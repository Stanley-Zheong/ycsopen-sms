# Phase 08 UI specification

## Visual and route contract

Reuse the Phase 02 ycsan-inspired desktop console baseline: blue navigation, white content cards, compact 8 px spacing rhythm, semantic status tags, blue primary actions, teal success, amber supplement/freeze, and red rejection/error. The target is the locally installed Google Chrome at 1440×900 with a minimum supported desktop width of 1100 px. Mobile and other browsers are outside scope.

Exactly three routes exist:

- `/tenant/register`: public registration card with initial administrator credentials and the shared qualification form.
- `/tenant/qualification`: tenant-administrator status and qualification workspace.
- `/admin/tenants`: platform tenant table; review, evidence, edit, and operating-status work stays in drawers/dialogs.

The local Pencil tool is unavailable in this execution environment. The checked-in `.pen` file therefore reuses the approved Phase 02 visual baseline unchanged; this specification, `UI-ELEMENTS.md`, and the clickable HTML prototype own the Phase 08 screen-specific design. No claim is made that Pencil rendered new Phase 08 frames.

## Interaction contract

The qualification form is one React component used by both tenant routes. It groups company, license, legal representative, contact, and conditional proof fields. Field errors are adjacent and summarized; the first invalid control receives focus. Uploads show allowed type/size, progress, completion, failure, and expired-session recovery. Contact verification has explicit send, input, verify, expiry, and confirmed states. Submission disables repeat actions and preserves input on safe failures.

The tenant status card distinguishes unverified, pending, verified, rejected, and supplement-required, including update/certification timestamps and safe reviewer feedback. Pending is read-only. Rejected/supplement-required may resubmit. A verified tenant must confirm recertification before certification-bound edits invalidate current eligibility.

The Admin table supports keyword, certification status, operating status, refresh/reset, deterministic pagination, and explicit empty/loading/error/denied states. Review uses a right drawer and separate evidence dialog. Approve, reject, and supplement-required use one confirmation dialog with a required reason; approve additionally requires completed OCR assistance and human confirmation. Edit and enable/disable/arrears-freeze have their own focused drawer/dialog and retain values after validation or stale-revision errors.

## Permission contract

- `tenant:menu` and `tenant:read`: list and status metadata.
- `tenant:qualification:review`: open review and decide.
- `tenant:update`: maintain allowed profile fields.
- `tenant:status:update`: change the existing account operating status.
- `tenant:evidence:read`: request application-mediated protected evidence.
- `TENANT_ADMIN`: own-tenant qualification read/submit/recertify, with tenant ID derived only from authentication.

Buttons are hidden or disabled consistently with server-side checks. Missing permission returns a safe 403 state. Evidence responses are no-store/nosniff and never reveal object IDs, capability tokens, or storage locators.

## Accessibility and state contract

Every input has a visible label and described constraints. Dialogs reuse `ModalDialog` focus trapping, Escape handling, and focus restoration. Status is never conveyed by color alone. Mutation results use an `aria-live` region. Destructive or eligibility-changing actions require consequence text and confirmation. Tables remain keyboard reachable; drawers have a labeled close action. No tooltip-only information, floating action button, business popover, or animation dependency is introduced.
