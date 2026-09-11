# Phase 28 Iterations

- Iteration 1: Backend service and migration implemented; focused tests exposed replay attempt numbering and H2 migration compatibility issues.
- Iteration 2: Replay attempt numbering changed to max persisted attempt + 1; migration split into single-column ALTER statements.
- Iteration 3: Frontend implementation added; unit test race fixed by waiting for async config hydration before mutating the form.
- Iteration 4: Claude review found blocking/high issues in SSRF coverage, source-derived signing keys, transactional HTTP calls, unbounded HTTP timeouts, and hand-written JSON. The implementation now uses resolved-IP SSRF rejection, tenant random signing secrets, no-redirect timeout-bound HTTP client behavior, non-transactional delivery entry points, and Jackson payload serialization.
