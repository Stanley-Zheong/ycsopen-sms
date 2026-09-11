# Phase 50 Decisions

## D1 — Static versioned content

Decision: bundle the help content in frontend source rather than adding a backend CMS.

Reason: Phase 50 requires truthful current guidance, not runtime authoring.

## D2 — Document only implemented API

Decision: publish `/api/v1/sms/send` and its current HMAC/field/error semantics.

Reason: undocumented future APIs create false contracts.
