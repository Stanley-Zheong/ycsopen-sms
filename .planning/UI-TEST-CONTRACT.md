# UI and Playwright Contract

## Selector law

- Every interactive or asserted UI element has a documented stable `data-testid` before implementation.
- IDs use `<portal>-<module>-<page>-<region>-<element>[-<action>]` in kebab case.
- A test ID describes business purpose, not visual position, CSS class, translated text, generated database ID, or component implementation.
- Shared shell/component IDs are stable across pages; page-specific IDs are namespaced by portal, module, and page.
- Repeated rows expose a stable row contract and a separate business-key value; tests do not bake mutable user data into selector names.
- Hidden and disabled behavior is asserted from the permission/state contract rather than worked around in tests.

## Required inventory

Before UI code starts, `UI-ELEMENTS.md` inventories:

- Routes, menu entries, breadcrumb, page title, global actions, notifications, and profile popover.
- Cards, KPIs, charts, legends, accessible data-table alternatives, freshness, source, and error indicators.
- Filters, search, sort, column control, pagination, selection, bulk actions, import, export, and async job feedback.
- Forms, labels, required/optional state, defaults, formats, validation, dependency fields, draft behavior, and concurrent-edit behavior.
- Tables, every column, row state, row action, expanded content, detail drawer, empty state, loading state, and error/retry state.
- Tabs, accordions, steps, timelines, modals, drawers, popovers, tooltips, toasts, floating controls, and confirmation consequences.
- Page, region, row, and action authorization; state-based visibility; hidden-versus-disabled policy.
- Default, hover, focus, active, selected, disabled, loading, empty, partial, success, warning, error, permission-denied, stale-data, and business-state variants.

`UI-ELEMENTS.md` is a machine table with this exact header:

```markdown
| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
```

Every cell is required. Empty values and placeholders such as `-`, `n/a`, `tbd`, or `todo` fail validation. A static element uses an explicit contract such as `read-only`, `no-action`, and its asserted states; it does not use a placeholder. Each row lists comma-separated machine IDs. For every owned catalog row whose UI reference is `page:` or `element:`, the matching page/selector row links the exact `OBL-*`, every catalog `Requirement IDs` value, planned behavior ID, catalog test/layer, and Playwright ID. The linked requirement, behavior, and catalog-test sets may contain neither omissions nor unrelated IDs.

## Machine-readable test matrix

Every UI phase uses this exact `TEST-MATRIX.md` header:

```markdown
| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
```

There is exactly one row for every atomic obligation owned by the package. Additional cases must first be split into additional stable atomic obligations; free text and duplicate obligation rows do not satisfy this contract. Each row repeats the catalog's exact requirement set, planned behavior ID, and test ID/layer. All cells contain explicit values. For an obligation without a direct UI reference, UI-only columns use a reason-bearing value such as `not-applicable: lower-layer-only`, never `-`. For a direct `page:` or `element:` obligation, Page ID/route, `data-testid`, and Playwright ID exactly match its linked `UI-ELEMENTS.md` row. Case ID is unique; command and evidence are reproducible and obligation-specific.

## Playwright derivation

For every owned atomic PRD obligation and every top-level integration requirement linked through that obligation's machine-readable `Requirement IDs` field:

1. Query the exact atomic rows with `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner <package-id> --assert-unique --assert-traced`; source-text inference is not a substitute for the `Requirement IDs` field.
2. Trace each obligation ID and each linked `REQ-*` or registered `PROJECT-*` ID to phase behavior IDs.
3. Enumerate roles, valid states, invalid states, boundary values, exception flows, and destructive or async actions for that atomic obligation.
4. Inspect the implemented routes, APIs, persistence effects, events, logs, and UI feedback.
5. Create Playwright cases for user-visible paths and lower-layer tests for behavior that cannot be truthfully proven through the browser alone.
6. Record the obligation's exact machine row in `TEST-MATRIX.md` and link it to its full-form `data-testid` row in `UI-ELEMENTS.md`; no obligation may be represented only by free text or the broader top-level requirement.
7. Run against real application services for acceptance; network substitution is allowed only for an explicitly external system and must preserve the platform's real boundary behavior.

The UI gate fails if an owned atomic obligation has no test/evidence row, if a top-level linked requirement has no atomic coverage, or if an element reference does not match `<portal>-<module>-<page>-<region>-<element>[-<action>]`.

Phase 2 browser and accessibility evidence runs only against the pinned clickable HTML prototype and is labelled `prototype` in `TEST-MATRIX.md` and evidence metadata. It cannot close any React production obligation. Every later production UI owner must execute its own Playwright/accessibility/visual evidence against the real React DOM and services while reusing the stable selector registry.

## Lifecycle gates

`validate-ui-contract.rb` always requires an explicit stage:

- `--stage design` is the implementation-entry gate. It validates UI-SPEC, the strict 12-column UI-ELEMENTS table, Pencil, clickable HTML, selector/route manifest, phase-local prototype interaction Playwright, and the strict 11-column TEST-MATRIX trace. It never requires unimplemented React or production test results. `validate-phase-entry.rb --ui` invokes exactly this stage.
- `--stage production` is the exit gate for every production UI phase. It reruns the complete design gate, then validates React route/DOM sources, production Playwright blocks, and checksum-bound executed PASS evidence. It must pass before TODO-empty, review completion, commit, or push.

Phase 2 is explicitly a prototype/design-foundation module, so `--stage production` rejects it; its exit proves its scoped design deliverables through `--stage design`. This exception does not authorize Phase 2 to close any later React obligation.

## Production source proof

Production implementation evidence is accepted only from checksum-bound `web/src/**` files ending in `.tsx`, `.ts`, `.jsx`, or `.js`. Every declared route must occur in executable route syntax such as a JSX `<Route path=...>` or route-object `path: ...`; every selector must occur as an actual JSX `data-testid=...` attribute. A `.txt` file, comment, arbitrary string literal, selector registry, or mere substring does not prove a production route or DOM selector.

Production Playwright evidence is accepted only from checksum-bound `web/**` files named `*.spec.ts`, `*.spec.tsx`, `*.spec.js`, `*.spec.jsx`, `*.test.ts`, `*.test.tsx`, `*.test.js`, or `*.test.jsx`.

For every direct UI matrix row, one actual `test(`/`it(` block must contain its exact, delimiter-bounded Playwright ID, Case ID, and OBL ID in the title or annotations; a longer identifier that merely contains the required ID is not a match. That same block must execute `await page.goto(<linked route>)` and perform an awaited action or assertion against the linked selector, such as click, fill, check, select, press, or `expect(...).toBeVisible()`. Case IDs and Playwright IDs are unique and form a one-to-one mapping; a UI-ELEMENTS row that links several obligations lists the corresponding Playwright IDs in the same order as its OBL IDs. An unrelated smoke test, dead locator, missing route navigation, comment, arbitrary selector string, or prefix-collision identifier fails closed.

Production route and selector declarations may live in different React files only when the same production Playwright block closes the route→rendered-selector chain and its executed report passes. Otherwise the implementation must statically expose the route component/import/render chain. The current validator always requires the browser closure for direct UI obligations, so dead or unrendered components cannot satisfy production by substring.

`ui-contract.json.execution` records the exact Playwright command, a 40-hex application commit, configuration/environment identity, `PASS`, and a report path/SHA-256 under the phase EVIDENCE directory. The checksum-bound JSON report repeats command, commit, config, PASS, and the exact direct-UI Case-ID set. Missing, failed, mismatched, stale, or checksum-invalid execution evidence blocks production.

Phase 2 remains `prototype` mode. Its checksum-bound `.pen`, HTML, selector registry, and prototype Playwright sources prove only prototype behavior and cannot be relabelled or reused as production implementation evidence.

## Minimum browser case set for a UI module

- Authorized happy path and final persisted result.
- Each relevant role's allow/deny matrix and cross-tenant denial.
- Client and server validation, including boundary values and duplicate submission.
- Loading, empty, service error, retry, and stale/partial data states.
- Destructive confirmation, cancellation, server rejection, success feedback, and audit effect.
- Async queued/running/succeeded/partially-succeeded/failed/cancelled states when supported.
- List search/filter/sort/pagination/selection/bulk/import/export behavior when present.
- Accessibility checks for labels, keyboard focus, dialog focus management, status announcements, contrast-sensitive states, and chart alternatives.
- Visual comparison against approved Pencil screens at declared viewports.

## Evidence

Acceptance evidence includes the executed command, application commit, test data identity, environment/config identity, pass/fail result, trace and screenshot/video paths when captured, request or correlation IDs, and any database/protocol facts used to establish the outcome.
