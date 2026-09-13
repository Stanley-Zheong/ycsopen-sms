# Phase 41 Iterations

1. Backend RED: tests failed because complaint case service/controller/migration did not exist.
2. Backend GREEN pass 1: implemented service/controller/migration.
3. Backend fix: generated-key handling was narrowed to `id`.
4. Backend fix: attribution-quality rule changed from partial inference to all-core-link completeness.
5. Backend fix: analytics SQL spacing and validation messages corrected.
6. Frontend RED: tests failed because complaint API and pages did not exist.
7. Frontend GREEN pass 1: implemented API/pages/routes/navigation.
8. Frontend test fix: analytics unit assertion waits for async query content.
9. Local review RED: added carrier-source and mobile-target test; it failed because carrier source was rejected.
10. Backend fix: added CARRIER source support, parsed `mobile:` target refs before blacklist calls, and changed case lookup to direct id query.
11. Claude review fix: row actions now operate on the selected complaint row, not the first loaded row.
12. Claude review fix: controller now derives mutation actor from authenticated principal and ignores forged request-body actors.
13. Claude review fix: removed client-controlled remediation failure input and added explicit remediation/recovery state boundaries.
14. TDD RED: mobile blacklist remediation without tenant attribution failed to throw.
15. Backend fix: blacklisting now requires tenant attribution before calling the blacklist port.
16. Backend authorization fix: mutating complaint endpoints are limited to ADMIN/OPERATOR while read endpoints retain FINANCE access.
17. Claude blocker RED: nonexistent remediation target was recorded as APPLIED.
18. Backend fix: tenant/channel/signature/template remediation now requires exactly one updated target row before recording APPLIED.
19. Claude blocker RED: frontend signature-target remediation still sent SUSPEND_CHANNEL.
20. Frontend fix: remediation type is derived from the actual target unless the operator explicitly selects a type.
21. Frontend fix: handling, remediation, and recovery evidence fields are editable inputs; recovery stays disabled until a remediation record id exists.
22. Claude blocker RED: existing but unrelated remediation targets could be mutated under another complaint.
23. Backend fix: remediation now rejects targets that do not match the complaint's own tenant/channel/signature/template/mobile attribution.
