# Phase 17 UI Spec

Route: `/admin/content-safety`

The page uses the established dense Admin card/table style from Phase 2 and nearby risk-control pages.

Required regions:

- Statistics cards: total policies, active policies, hit count, intercept count, intercept rate, coverage rate.
- Filter bar: word, category, level, action, status.
- Policy form: word, category, level, replacement, action, scope, scope ref, status, save.
- Import/export controls: textarea, import button, export request button.
- Policy table: word, category, level, replacement, action, scope, status, hit count, created time, delete/disable.
- Final-content scan panel: tenant id, template id, final rendered content, scan result.

Every interactive element has a stable `data-testid` listed in `UI-ELEMENTS.md`.
