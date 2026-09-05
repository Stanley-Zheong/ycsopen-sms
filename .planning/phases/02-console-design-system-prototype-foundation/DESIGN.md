# Phase 2 Design

## Context and constraints
- This phase is design/registry only, no production React implementation.
- Runtime acceptance uses only the currently installed standard-path Google Chrome at `1440x900`; no browser download or compatibility matrix is in scope.

## Architecture and ownership
- The Pencil source defines reusable components, Admin/Tenant reference screens, global states/overlays, and the complete 83-obligation route map. The checked-in HTML implements the same registry as a clickable single-page prototype.
- The contract is owned by `console-design-system-prototype-foundation` and validated through planning validators.

## Data model and migrations
- No migrations and no database schema changes.
- Schema migrations: none

## State machines
- Reusable states are 16 separately addressable contracts: loading, empty, partial success, success, warning, error, retry, stale data, permission denied, destructive confirmation, toast, modal, drawer, popover, tooltip and floating control.

## UI and interaction model
- Stable route map and `data-testid` registry drive future production phases.
- Every owned obligation has one canonical matrix row, one route selector binding, one literal Playwright block and its exact catalog evidence target.
- `UI-ELEMENTS.md` separately freezes all shared controls, table columns, row actions, dialogs, drawers, popovers, floating feedback and boundary-state actions.
- The visual baseline is pinned to ycsan commit `f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b`; its CSS and real-Chrome screenshot checksums are recorded in `design-output/ycsan-style-snapshot.json`. Navy/text/primary tokens are adopted directly, while teal is explicitly a Phase 2 adaptation.

## API or protocol contracts
- No API implementation occurs in Phase 2.
- Playwright IDs bind matrix rows to UI rows and to literal tests that serve the checked-in HTML through a deterministic local HTTP server.

## Security and privacy
- No user data storage or API payload handling in phase artifacts.

## Observability and audit
- One canonical reporter JSON, nine runtime source seals (including tokens and pinned ycsan provenance), 80 obligation JSON files, three report-attached Playwright screenshots and an 83-entry manifest form the fail-closed evidence set.

## Alternatives rejected
- Requiring full React production pages in Phase 2.
- Deferring all UI contract setup to later phases.
