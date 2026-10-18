# Phase 41 UI Elements

Issue `#124` supersedes page-level mutation drafts with a selected-case workspace and contextual dialog. The primary Phase 41 selectors remain compatibility anchors; `.planning/changes/issue-124-complaint-case-context/UI-ELEMENTS.md` is the definitive inventory for new detail, timeline, modal, retry, stale, input, and feedback selectors.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
|---|---|---|---|---|---|---|---|---|---|---|---|
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR/FINANCE | complaint workspace | page | source, summary, tenant, channel, signature, template, message, mobile, content type, requirement | GET/POST `/console/complaints` | loading/error/registered/list | admin-complaint-case-complaints-page | OBL-F-9-1-A; REQ-F-9-1 | complaint-case-management-01 | T-F-9-1-A:playwright | pw-p41-intake |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR/FINANCE | attribution | table cell | COMPLETE or UNKNOWN | read-only data-quality indicator | explicit quality | admin-complaint-case-complaints-attribution-quality | OBL-F-9-1-B; REQ-F-9-1 | complaint-case-management-01 | T-F-9-1-B:integration | pw-p41-attribution |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR mutate; FINANCE read | state flow | selected-case action group | pending, processing, handled, closed | accept, handle, close complaint state endpoints from server-loaded case context | eligibility, pending, stale/error feedback | admin-complaint-case-complaints-state-action | OBL-F-9-2-A; REQ-F-9-2 | complaint-case-management-02 | T-F-9-2-A:playwright | pw-p41-state |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR mutate; FINANCE read | remediation | selected-case dialog action | disposal type, exact target ref, server actor, authorized review, reason | POST `/console/complaints/{id}/remediations` | applied/failed/idempotent/pending | admin-complaint-case-complaints-remediation | OBL-F-9-3-A; REQ-F-9-3 | complaint-case-management-03 | T-F-9-3-A:integration | pw-p41-remediation |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR mutate; FINANCE read | remediation recovery | selected-case dialog action | latest exact failed disposal record, authorized review, server actor, resume condition | GET case detail; POST `/console/complaints/{id}/recoveries` | failed/recovered/error/stale; enabled only for exact failed record in PROCESSED/CLOSED | admin-complaint-case-complaints-remediation-recovery | OBL-F-9-3-B; REQ-F-9-3 | complaint-case-management-03 | T-F-9-3-B:fault | pw-p41-recovery |
| admin-complaint-analytics `/admin/complaint/analytics` | ADMIN/OPERATOR/FINANCE | analytics workspace | page | daily trend, total, unknown attribution, tenant/signature/content-type dimensions | GET `/console/complaint-analytics` | loading/error/empty/data | admin-complaint-case-analytics-page | OBL-F-9-4-A; REQ-F-9-4 | complaint-case-management-04 | T-F-9-4-A:playwright | pw-p41-analytics |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR/FINANCE | remediation resource | table cell | mobile, tenant, signature, template, channel target refs | read exact target before remediation | complete/unknown resource refs | admin-complaint-case-complaints-remediation-resource | OBL-STATE-RESOURCE-DISABLE; PROJECT-STATE-MACHINE | complaint-case-management-05 | T-STATE-RESOURCE-DISABLE:integration | pw-p41-resource |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR mutate; FINANCE read | accept | button/action | complaint id, server actor, required opinion | POST `/console/complaints/{id}/accept` | accepted/error/stale | admin-complaint-case-complaints-accept | OBL-STATE-COMPLAINT-PROCESS; PROJECT-STATE-MACHINE | complaint-case-management-06 | T-STATE-COMPLAINT-PROCESS:playwright | pw-p41-accept |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR mutate; FINANCE read | handle | button/action | required opinion, remediation, requirement, server actor | POST `/console/complaints/{id}/handle` | handled/error/stale | admin-complaint-case-complaints-handle | OBL-STATE-COMPLAINT-HANDLED; PROJECT-STATE-MACHINE | complaint-case-management-06 | T-STATE-COMPLAINT-HANDLED:integration | pw-p41-handle |
| admin-complaints `/admin/complaints` | ADMIN/OPERATOR mutate; FINANCE read | close | button/action | required close confirmation and server actor | POST `/console/complaints/{id}/close` | closed/error/stale | admin-complaint-case-complaints-close | OBL-STATE-COMPLAINT-CLOSE; PROJECT-STATE-MACHINE | complaint-case-management-06 | T-STATE-COMPLAINT-CLOSE:playwright | pw-p41-close |

Implementation-only selectors (Issue `#124` scopes action fields inside the selected-case dialog):

- `admin-complaint-case-complaints-message`: success message.
- `admin-complaint-case-complaints-error`: mutation error message.
- `admin-complaint-case-complaints-refresh`: refresh action.
- `admin-complaint-case-complaints-table`: complaint table.
- `admin-complaint-case-complaints-row`: complaint row.
- `admin-complaint-case-complaints-opinion`: operator handling opinion input.
- `admin-complaint-case-complaints-remediation-note`: operator handling remediation text input.
- `admin-complaint-case-complaints-state-requirement`: operator handling requirement input.
- `admin-complaint-case-complaints-close-note`: operator close note input.
- `admin-complaint-case-complaints-disposal-type`: remediation type selector, including automatic target matching.
- `admin-complaint-case-complaints-review-id`: remediation authorization review id input.
- `admin-complaint-case-complaints-remediation-reason`: remediation reason input.
- `admin-complaint-case-complaints-remediation-status`: persisted remediation
  loading/error/empty status or persisted status and failure reason displayed
  with each complaint row.
- `admin-complaint-case-complaints-remediation-load-error`: persisted
  remediation readback failure; recovery remains unavailable.
- `admin-complaint-case-complaints-recovery-review-id`: recovery authorization review id input.
- `admin-complaint-case-complaints-recovery-condition`: recovery condition input.
- `admin-complaint-case-analytics-quality`: unknown attribution analytics.
- `admin-complaint-case-analytics-trend`: ordered daily complaint trend and
  explicit empty state.
- `admin-complaint-case-analytics-tenant`: tenant distribution.
- `admin-complaint-case-analytics-signature`: signature distribution.
- `admin-complaint-case-analytics-content-type`: content-type distribution.
