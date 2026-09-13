# Phase 48 SPEC

Package: `shortlink-safety-review`

Goal: short links are created only from valid public URLs and approved domains; unsafe, pending, rejected, expired, or offline links never redirect.

Owned obligations: `OBL-F-13-1-A`, `OBL-F-13-1-B`, `OBL-F-13-1-C`, `OBL-F-13-2-A`, `OBL-F-13-2-B`, `OBL-F-13-2-C`, `OBL-F-13-2-D`, `OBL-FIELD-SHORTLINK-URL`, `OBL-FIELD-SHORTLINK-DOMAIN`, `OBL-FIELD-SHORTLINK-VALIDITY`, `OBL-STATE-SHORTLINK-APPROVE`, `OBL-STATE-SHORTLINK-REJECT`, `OBL-STATE-SHORTLINK-EXPIRE`, `OBL-STATE-SHORTLINK-OFFLINE`, `OBL-DATA-10-10-SHORTLINK`.

Scope fence: no marketing automation, no live external screenshot crawler, no new browser matrix, no real threat-intelligence subscription.
