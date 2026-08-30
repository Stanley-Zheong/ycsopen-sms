---
name: ui-design
description: Convert ycsopen-sms PRD/SPEC requirements into a complete GSD UI design contract using local Pencil, ycsan-web visual language, clickable HTML, exhaustive element/action/state/permission inventory, stable data-testid values, and Playwright traceability. Use before implementing any new or materially changed React page, modal, drawer, popover, table, chart, or shared console component.
---

# ycsopen-sms UI design

This skill supplements `.planning/EXECUTION-STANDARD.md` and
`.planning/UI-TEST-CONTRACT.md`; those project contracts win on conflict.

Track every design obligation in the active phase TODO. Do not create schedule,
duration, completion-date, or percentage estimates. Design is accepted only
when all scoped TODOs and blocking review findings are closed with evidence.

## 1. Understand the phase contract

Read the full PRD obligation rows, active phase SPEC/CONTEXT/INTENT/DESIGN,
DECISIONS, TEST-MATRIX, and relevant current React/API code. Extract roles,
tenant boundary, entities, state machines, validations, errors, async behavior,
and external dependencies. Record unresolved business-critical contradictions;
do not invent permissions, terminal states, billing meaning, or data ownership.

## 2. Information architecture and complete inventory

Read `references/implicit-requirements-checklist.md`. Define menu, routes,
page hierarchy, user flows, permission matrix, shared shell, and every state.

Populate phase `UI-ELEMENTS.md` with the exact 12-column header required by
`.planning/UI-TEST-CONTRACT.md`:

```markdown
| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
```

Inventory every page, region, field, button, link, card, KPI, chart, legend,
table column, row action, tab, modal, drawer, popover, tooltip, toast, floating
control, loading/empty/error state, permission, and action consequence. No cell
may use `-`, `n/a`, `tbd`, or `todo`. IDs follow
`<portal>-<module>-<page>-<region>-<element>[-<action>]` and describe business
purpose rather than position, CSS, translated text, or database IDs.

## 3. Design system and shared components

Read `references/design-system.md` and `references/component-patterns.md`.
Use ycsan-web's blue/teal brand language and the provided tokens as the primary
baseline; use Arco only for interaction clarity and component behavior. Reuse
the provided assets, then adapt them to the phase. Do not create page-local
colors, spacing scales, icon families, or duplicated shell components.

Design desktop-first console density. Specify responsive behavior when the PRD
requires other viewports. Cover keyboard focus, accessible names, dialog focus,
status announcements, chart alternatives, contrast, and reduced motion.

## 4. Pencil and clickable prototype

Use local Pencil for high-fidelity screens and interactions. Access `.pen`
files only through Pencil tools. Every route and meaningful state in
UI-ELEMENTS must map to a Pencil node/screen and to the selector/route manifest.

Create clickable HTML under the active phase `EVIDENCE/ui/prototype/`, reusing
one tokens file and shared components. Use realistic, sanitized Chinese SMS
operations data. Every interactive/asserted prototype element carries its final
`data-testid`; permissions and hidden/disabled rules are visible in design
annotations. The prototype proves design behavior only, never React production
completion.

Use `python3 skills/ui-design/scripts/scaffold.py <phase-dir>` when a skeleton
is needed, then run:

```bash
python3 skills/ui-design/scripts/check_consistency.py <phase-dir>/EVIDENCE/ui/prototype
```

## 5. Two independent design reviews

Read `references/qa-checklist.md`.

1. Design consistency: ycsan brand/tokens, component reuse, hierarchy, density,
   states, responsive/accessibility behavior, and selector consistency.
2. PRD/spec compliance: map every owned atomic obligation and behavior to the
   exact page/element/state and planned Playwright case.

Fix findings and rerun affected checks. A report that merely leaves required
items partially covered is not acceptance.

## 6. GSD UI gates and handoff

Store design decisions and review evidence in the active phase, update
`EVIDENCE/ui-contract.json`, and run the roadmap's exact design command:

```bash
/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase <NN> --package <package-id> --stage design
```

No React implementation starts until this gate and the phase entry review pass.
If the repository still lacks Playwright dependencies/configuration, the owning
production UI phase must bootstrap and prove that harness (including CI) before
spec generation or `--stage production`; a missing harness is an open blocking
TODO, not evidence that browser acceptance is optional.
After implementation, reconcile any drift back into DESIGN, DECISIONS,
UI-ELEMENTS, TEST-MATRIX, Pencil, and prototype. Production completion requires
the `--stage production` gate, actual React route/JSX `data-testid` declarations,
and checksum-bound executed Playwright evidence. Phase 2 uses the project-defined
prototype exception and remains design-only.
