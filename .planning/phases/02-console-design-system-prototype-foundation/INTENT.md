# Intent

## Status
Open

## Goal
Establish the console design-system baseline: shared shell, route registry, and admin/tenant prototypes with complete obligation traceability.

## Deliverables
- [x] `02-SPEC.md` includes all owned obligations and trace table.
- [x] `02-CONTEXT.md`, `DESIGN.md`, `ITERATIONS.md`, `DECISIONS.md`, `TODO.md` are present and consistent with the focused prototype scope.
- [x] `02-UI-SPEC.md`, `UI-ELEMENTS.md`, `TEST-MATRIX.md`, and real Pencil/clickable HTML artifacts are present.
- [x] `EVIDENCE/ui-contract.json` is mode `prototype` and checksum-bound.
- [x] Plan files `02-01-PLAN.md` to `02-04-PLAN.md` exist with explicit obligation slices.

## Tasks
1. Record the owned obligation set and lane boundary for design-system and IA responsibilities.
2. Produce one prototype route/selector registry and one matrix row per owned obligation.
3. Produce checksum-bound `UI contract` artifacts for `design`/`prototype` validation.
4. Keep production implementation TODOs out of Phase 2.

## Verification
Executable:
- `ruby .planning/tools/validate-phase-entry.rb --phase 02 --package console-design-system-prototype-foundation --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/02-console-design-system-prototype-foundation/ENTRY-REVIEW.md --ui`
- `ruby .planning/tools/validate-ui-contract.rb --phase 02 --package console-design-system-prototype-foundation --stage design`
- `ruby .planning/tools/validate-phase-02-obligation-evidence.rb`
- `ruby .planning/tools/test-phase2-ui-evidence.rb`
