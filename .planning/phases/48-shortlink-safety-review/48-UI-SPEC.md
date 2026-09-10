# Phase 48 UI SPEC

- Tenant `/tenant/shortlink`: form fields for original URL, short domain and validity; create action; list with original URL, short URL, expiry, state and click count; analytics cards for unique clicks, region and device.
- Admin `/admin/shortlinks/review`: table shows tenant, immutable target version, domain/screenshot evidence, automated result and risk; approve/reject/inspect actions use one opinion field.
- Public `/s/:code`: safe page states for pending, rejected, expired and offline; no target jump link is rendered.
