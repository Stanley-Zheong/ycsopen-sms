# Frontend Spirit Workflow

## Definition

A frontend spirit is an independently shippable product slice with its own scope, design contract, quality gate, commit, and merge boundary. A spirit is not a calendar sprint and has no time estimate. It is complete only when its scoped TODO set is empty and the evidence in `QUALITY-GATEWAY.md` proves the requested behavior.

## Required Artifacts

Every spirit directory contains:

| Artifact | Purpose |
|---|---|
| `SPEC.md` | User-visible behavior, owned routes, out-of-scope boundaries, issue/PRD trace, and acceptance rules. |
| `DECISIONS.md` | Durable product, technical, and verification decisions made for the spirit. |
| `SYSTEM-DESIGN.md` | Component ownership, data flow, command flow, API integration, state handling, and failure model. |
| `ITERATIONS.md` | Evidence-backed change log for findings, corrections, and rechecks inside the spirit. |
| `QUALITY-GATEWAY.md` | Commands, evidence, TODO closure, merge gate, and verification boundaries. |

## Common Frontend Contract

- Follow `docs/frontend页面实现规范.md` for layout, QueryPanel, form, table, modal, empty state, and `data-testid` rules.
- Each page declares page goal, primary object, actor role, data source, query behavior, main actions, empty/loading/error states, and audit expectations.
- Every state-changing action declares target object, preconditions, required input, result state, failure feedback, duplicate-submission behavior, and evidence path.
- Every editable field belongs to a query, submit, or explicit async-link behavior. Otherwise render it as read-only text.
- Each changed route has targeted unit or component tests plus Chrome Playwright coverage when user-observable behavior changes.
- Each completed spirit records verification evidence before commit and merge. Claims without fresh evidence are invalid.

## Spirit Sequence

| Spirit | Package | Primary outcome | Representative issues |
|---|---|---|---|
| 01 | `01-foundation-contract` | Shared frontend contract: shell, QueryPanel, form, table, modal, action confirmation, layout baseline. | `#52`, `#57`, `#58`, `#59`, `#76`, `#87`, `#88`, `#89` |
| 02 | `02-admin-operations` | Admin operations pages become action-complete and understandable: alerts, complaints, status actions, reason dialogs. | `#66`, `#88`, `#89`, `#90`, `#91`, `#92` |
| 03 | `03-tenant-commercial-finance` | Tenant lifecycle, trial, contract, recharge, billing, finance, and customer-facing commercial state are coherent. | `#79`, PRD V2 finance TODOs |
| 04 | `04-delivery-data-workbench` | Send/detail/uplink/receipt/error/export/workbench flows expose reliable data lineage and command feedback. | `#88`, `#91`, PRD V2 message-flow TODOs |
| 05 | `05-release-acceptance` | Frontend release evidence, Docker identity, default account, seed data, and acceptance reports become repeatable. | `#60`, release PR follow-ups |

## Commit And Merge Rule

1. Open or reuse one branch per spirit.
2. Update the spirit artifacts before implementation.
3. Implement only the scoped behavior.
4. Run the spirit quality gate and record exact command output boundaries.
5. Commit with the spirit package and changed source/tests together.
6. Open or update a PR that references the spirit package and issue scope.
7. Merge only after the quality gate and review boundary are satisfied.

## Global Remaining TODO

- [ ] Split each open frontend issue into one owning spirit before implementation starts.
- [ ] Add issue links to the relevant spirit `SPEC.md` when the issue is selected for implementation.
- [ ] Record per-spirit verification evidence in `QUALITY-GATEWAY.md` before each spirit merge.
