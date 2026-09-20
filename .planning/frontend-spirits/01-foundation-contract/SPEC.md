# Spirit 01: Frontend Foundation Contract Spec

## Intent

Create the reusable frontend foundation that prevents repeated layout, form, table, and action-context defects across Admin and Tenant pages.

## Scope

### In

- Admin and Tenant shell conventions: sidebar, breadcrumb, page header, content container.
- Shared `QueryPanel`, form field sizing, four-column layout, button placement, empty state table, and modal contracts.
- Shared state-changing action confirmation pattern with target, effect, reason, duplicate-submission lock, and failure retry.
- Login page baseline and stable `data-testid` conventions.

### Out

- Page-specific business implementations for complaints, finance, delivery, or release.
- Backend API changes.
- Tenant commercial or billing policy changes.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-01-QUERY | Every query region uses the shared QueryPanel contract with visible label, stable input test id, search, reset, and optional collapse behavior. | Chrome Playwright can locate `query-panel`, fill a field, run query, reset, and observe result state without horizontal overflow. |
| FE-SPIRIT-01-FORM | Every create/edit form uses a shared modal or drawer contract with validation, cancel confirmation, submit feedback, and unchanged user input after validation failure. | Unit and Playwright coverage prove required-field errors, success toast, refresh, and unsaved-change confirmation. |
| FE-SPIRIT-01-TABLE | Every data table renders headers, loading, empty, error, and result states consistently. | Empty results still show table header and `table-empty`; error state has retry or explanatory feedback. |
| FE-SPIRIT-01-ACTION | State-changing actions never show detached reason inputs and always bind target, effect, reason, latch, audit payload, and retry behavior in a modal. | Existing issue #91 pattern is reusable and no page-level orphan reason field remains. |

## Acceptance

- Layout follows `docs/frontend页面实现规范.md`.
- Existing page-specific selectors are preserved or mapped in the owning spirit.
- No editable input remains without query, submit, or explicit async-link behavior.
- Changed shared components include unit tests and at least one representative Chrome Playwright route check.

## Remaining TODO

- Open item: Assign each open layout/form issue to this spirit or an explicit later spirit.
- Open item: Inventory current shared components and routes before implementation.
- Open item: Record final verification commands in `QUALITY-GATEWAY.md`.
