# UI Spec

## Tenant route

Route: `/tenant/templates`

Required elements:

- Template application form: name, content, type, signature id, parameter rule, rationale, submit.
- Variable preview panel with stable preview action and variable input.
- Template list with status, version, audit comment, and resubmit action.

## Admin route

Route: `/admin/templates/review`

Required elements:

- Review stats.
- Keyword filter.
- Review table with template content, tenant, type, signature, variables, status.
- Decision modal with approve, reject, and amendment-required outcomes.

## Browser

Production acceptance uses only local Google Chrome through Playwright.
