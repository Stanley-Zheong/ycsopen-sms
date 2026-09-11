# Phase 35 Decisions

- Use synchronous evaluation instead of an async worker. Reason: Phase35 can prove rule/state/delivery behavior without adding scheduling infrastructure.
- Store channel and target snapshots on rules/delivery attempts. Reason: alert evidence must remain auditable even if later settings change.
- Do not call external notification providers. Reason: Phase35 owns delivery evidence semantics, not vendor integration.
- Keep all Admin UI in `/admin/alerts`. Reason: the obligations are tightly related and one page is easier to verify without scattering state.
- Use Chrome-only Playwright. Reason: project validation standard was narrowed to local Chrome.
- Keep the Admin sidebar entry role-scoped rather than adding a new alert-engine permission gate. Reason: this phase does not introduce permission seeding, and hiding the menu behind an unseeded permission would reduce operability without adding security value.
