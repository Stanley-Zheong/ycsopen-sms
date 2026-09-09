# Phase 16 TODO

- [x] OBL-F-5-1-A — System, tenant, and third-party risk sources are evaluated before route selection and before any sending task exists. Evidence: `EVIDENCE/OBL-F-5-1-A.json`
- [x] OBL-F-5-1-B — Any effective blacklist hit blocks the message and records source-specific category, risk result, and trace reason without task or charge. Evidence: `EVIDENCE/OBL-F-5-1-B.json`
- [x] OBL-F-5-2-A — Cards, filters, and rows expose black or white type, masked phone, reason, source, state, and creation time under scope permission. Evidence: `EVIDENCE/OBL-F-5-2-A.json`
- [x] OBL-F-5-2-B — Authorized users add, remove, import, and request export with validation, partial-failure results, audit, and tenant isolation. Evidence: `EVIDENCE/OBL-F-5-2-B.json`
- [x] OBL-F-5-2-C — Effective whitelist precedence bypasses declared blacklists while expired or out-of-scope white entries do not. Evidence: `EVIDENCE/OBL-F-5-2-C.json`
- [x] OBL-F-5-3-A — Operators configure provider URL, protected credential, basic-intermediate-advanced level, score threshold, timeout, and cache-or-allow fallback. Evidence: `EVIDENCE/OBL-F-5-3-A.json`
- [x] OBL-F-5-3-B — Single and batch provider requests follow the declared signed contract, including a maximum 500-number batch and item-level source outcomes. Evidence: `EVIDENCE/OBL-F-5-3-B.json`
- [x] OBL-F-5-3-C — Provider timeout or error applies exactly the configured allow or fresh-cache decision and records a degradation event. Evidence: `EVIDENCE/OBL-F-5-3-C.json`
- [x] OBL-F-5-4-A — Authorized users inspect interception volume, rate, and system-tenant-provider source distribution from real decisions. Evidence: `EVIDENCE/OBL-F-5-4-A.json`
- [x] OBL-F-5-4-B — Appeal and false-positive marking preserve the original decision and actor and cannot silently mutate historical evidence. Evidence: `EVIDENCE/OBL-F-5-4-B.json`
- [x] OBL-EDGE-RISK-OUTAGE — Third-party risk failure uses only configured allow or fresh-cache fallback and records a reviewable degradation event. Evidence: `EVIDENCE/OBL-EDGE-RISK-OUTAGE.json`
- [x] OBL-DATA-10-5-RISK — Tenant and system lists, provider decisions, sensitive words, and frequency rules preserve protected number, source, scope, action, state, timing, and hit evidence. Evidence: `EVIDENCE/OBL-DATA-10-5-RISK.json`
