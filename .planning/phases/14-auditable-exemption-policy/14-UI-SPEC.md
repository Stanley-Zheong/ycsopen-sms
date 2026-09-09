# Phase 14 UI Spec

## Page

`admin-exemption-policy /admin/exemption/policy`

## Required regions and selectors

- `admin-auditable-exemption-exemption-policy-page`: page shell, breadcrumb, heading, filters, form, list, preview, and usage history.
- `admin-auditable-exemption-exemption-effective-preview`: effective-decision preview panel showing active/denied result, matched policy, precedence reason, and non-exemptable denial.
- `admin-auditable-exemption-exemption-usage-history`: usage table listing policy use and decision audit fields.

## Interaction contract

- Create policy: fill tenant, type, resource, product, scope, approval, validity, reason; submit to API.
- Preview policy: fill subject and control, then request effective preview.
- Revoke policy: row action records actor/reason and removes future effectiveness.
- Usage history: refreshes after create/preview/revoke and exposes actor, subject, version, reason, result.
