# UI Direction and Delivery Contract

## Reference sources

- Brand reference local checkout: `/Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web`.
- Brand reference Git remote: `https://github.com/Stanley-Zheong/ycsan.git`.
- Pinned full commit SHA: `f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b`.
- Adopted CSS source: `/Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web/app/globals.css`.
- Reference screenshots: `/Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web/public/screenshots/homepage.png` and `/Users/laosanzheong/Documents/codebases/ycsan/ycsan-aisms-web/public/screenshots/products.png`.
- Verified CSS SHA-256: `6931f74f3bbe90f76b972c907b9b519bc93a348fe3e74ba20f7eacfb3ce1fc53`.
- Verified homepage screenshot SHA-256: `640074fbfb8015e2e5e6a1cf1ba62f5af4e2064abef130e75165fc00f1fd2258`.
- Verified products screenshot SHA-256: `080e19fecbffbcc3c84ebb5a55bbffd072201e03d2fe7fd1a1a247ea8a2423a3`.
- UI workflow reference: `/Users/laosanzheong/Documents/codebases/hengshi-jarvis/projectlogs/907/uiskill`.
- Design tool: local Pencil MCP; `.pen` files must be accessed through Pencil tools.
- Product source: `docs/PRD.md`, especially roles, workflows, exception rules, and the admin/tenant information architecture.

## Brand translation

The public ycsan site uses a pale blue canvas, deep navy text, clear blue primary actions, teal accents, blue-to-teal gradients, white rounded cards, restrained borders, and soft shadows.
The management console must preserve that identity while increasing information density and reducing decorative radius and shadow.

Initial token direction:

| Token | Direction |
| --- | --- |
| canvas | `#F6FAFF` / `#F7F8FA` |
| surface | `#FFFFFF` |
| primary | `#0C85E8` |
| primary active | `#0B61AA` |
| accent | `#31C9B6` |
| warning | `#FF9C00` |
| text | `#123250` |
| muted text | `#5F7389` |
| border | `#DBE5EF` |
| danger | accessible red aligned with the component library |

Final values are decided in the design-system phase after contrast and state checks.

The hashes above were verified from the pinned checkout. Phase 2 must rerun `shasum -a 256` and record the adopted CSS file and both reference screenshot results together with the full Git SHA in `DESIGN.md`, `DECISIONS.md`, `TEST-MATRIX.md`, and the evidence index. Execution fails closed when the checkout SHA or a checksum differs from the accepted provenance without a recorded design decision and renewed visual review.

## Layout and interaction baseline

- Desktop-first admin shell with collapsible left navigation, header, breadcrumb, notification access, user profile popover, and dense main content.
- Admin and tenant portals share tokens and primitives but have separate navigation, permissions, dashboards, and task flows.
- Shared primitives are defined before business screens: navigation, header, table toolbar, selection bar, pagination, form validation, modal, drawer, popover, toast, empty state, skeleton, error state, confirmation, import/export feedback, and permission-denied state.
- Every destructive, irreversible, approval, rejection, pause, termination, refund, credential revocation, and bulk action declares confirmation copy and post-action feedback.
- Every async operation declares queued/running/succeeded/partially-succeeded/failed/cancelled presentation where the business action supports those states.

## Per-page documentation contract

`UI-ELEMENTS.md` must record each page and all visible or interactive elements with these fields:

| Field | Required content |
| --- | --- |
| page ID and route | Stable page identifier and actual route |
| role and permission | Page, region, row, and action visibility rules |
| region | Header, filter bar, card, form section, table, tab, drawer, modal, popover, floating control, footer |
| element | Label, type, data source, validation, default value, format, and empty value |
| action | Trigger, precondition, API effect, loading behavior, success effect, failure effect, retry behavior |
| states | Default, hover/focus, disabled, loading, empty, error, permission denied, and relevant business states |
| test ID | Unique stable `data-testid`; selectors must not depend on translated copy or CSS |
| PRD trace | One or more requirement IDs |
| test trace | Playwright scenario ID and lower-level test IDs |

Naming convention:

`<portal>-<module>-<page>-<region>-<element>[-<action>]`

Dynamic rows use a stable business identifier through a documented attribute pattern, while tests locate the row by its stable row test ID and assert its business key separately.

## Design and QA gates

1. Read and reconcile the full module spec.
2. Produce module information architecture, page inventory, permissions, implicit-requirement decisions, and user flows.
3. Define or reuse tokens and shared components.
4. Create Pencil high-fidelity screens and clickable prototype states.
5. Run design consistency review and fix every finding.
6. Run PRD coverage review and fix every missing or partial item.
7. Freeze `UI-SPEC.md` and `UI-ELEMENTS.md` before implementation selectors and Playwright cases are authored.
8. After implementation, compare actual pages to Pencil outputs and record visual review evidence.
