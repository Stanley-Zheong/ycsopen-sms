# Phase 29 UI Spec

The UI follows existing YCS Open SMS console layout and table style.

- `/tenant/bulk/send`: tenant bulk import form, preview table, create action, validation feedback.
- `/tenant/scheduled/tasks`: tenant task table, state cell, pause/resume/cancel/restart controls.
- `/admin/bulk/details`: admin aggregate cards, task table, item detail table.
- `/admin/send/jobs`: operations search filters and state controls.

Every interactive element used by automation has a stable `data-testid` in `UI-ELEMENTS.md`.
