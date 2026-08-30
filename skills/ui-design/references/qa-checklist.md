# UI design review checklist

## Consistency and usability

- [ ] Every screen uses the ycsan token file; no unexplained hard-coded color.
- [ ] Shared shell, table, form, state, overlay, feedback, and icon patterns are
  structurally reused.
- [ ] Visual hierarchy, density, spacing, radius, and button priority are
  consistent; blue/teal gradients are accents rather than noise.
- [ ] All interactive/asserted prototype elements have final documented
  `data-testid` values and semantic accessible roles/names.
- [ ] Focus order, visible focus, dialog focus containment/return, keyboard
  operation, status announcements, contrast, chart alternatives, and reduced
  motion are defined.
- [ ] Loading, empty, error/retry, stale/partial, disabled, denied, destructive,
  success, and async terminal states are represented where applicable.
- [ ] Permissions and tenant boundaries specify visible/hidden/disabled and
  server-denied behavior, not only an icon annotation.
- [ ] Responsive behavior matches the declared viewport contract.
- [ ] Pencil, HTML, manifest, UI-ELEMENTS, and TEST-MATRIX routes/selectors agree.

## PRD and test trace

- [ ] Every owned atomic obligation maps to exact requirement IDs, behavior IDs,
  page/route, element/state, catalog layer, Playwright ID, and Case ID.
- [ ] Roles, state transitions, validations, boundaries, API effects, async
  outcomes, destructive consequences, billing/audit effects, and errors match
  current PRD/SPEC authority.
- [ ] Every table column, row action, modal, drawer, popover, tooltip, toast, and
  floating control is present in UI-ELEMENTS.
- [ ] Every chosen implicit requirement is implemented; rejected ones have a
  recorded reason.
- [ ] No required item remains partially covered. Fix it or keep its TODO open
  and block design acceptance.
