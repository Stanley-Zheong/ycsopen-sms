# Issue 120 Decisions

## DR-120-001: Project Tenant Identity at Read Time

Use a left join to the tenant master for uplink and push-monitor reads. This keeps tenant naming under the tenant owner, avoids stale copied names and schema changes, and preserves rows whose tenant metadata is unavailable.

## DR-120-002: Keep `tenantId` as the Filter Value

The browser may search tenant options by number or name, but list and monitor requests send only the resolved `tenantId`. Ambiguous display text does not select the first match.

## DR-120-003: Reuse the Existing Phase 32 Visual Contract

The change adds identity text and searchable tenant controls to the existing `/admin/uplink` table, drawer, and QueryPanel. It introduces no new route, overlay, layout system, or visual component family. The delta is inventoried in `UI-ELEMENTS.md` and verified in production Chrome.
