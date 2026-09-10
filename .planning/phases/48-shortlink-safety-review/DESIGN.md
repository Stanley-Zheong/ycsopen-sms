# Phase 48 DESIGN

- Tenant page: create short link, view original URL, short URL, expiry, state, click count and analytics.
- Admin page: review tenant, immutable target version, domain/screenshot evidence, automated result and approve/reject/inspect actions.
- Public safe page: pending/rejected/expired/offline states show a platform warning without target link.
- Backend service: deterministic sandbox checks reject private/local targets, unapproved domains, malicious URL signals, too-new or unfiled domains.
