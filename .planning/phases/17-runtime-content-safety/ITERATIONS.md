# Iterations

## Iteration 1

- Added runtime policy management service/controller, migration, permissions, Admin UI, API client, unit tests, and Chrome Playwright test.
- Replaced the simplified checker path with canonical final-content scanning and deterministic action precedence.

## Iteration 2

- Fixed frontend test ambiguity by waiting for the populated policy row instead of querying duplicate option/table text.
- Fixed CSS token drift by using existing design tokens.

## Iteration 3

- Closed Claude review BLOCKER by splitting real routing scans from console dry-run scans.
- Added regression coverage so trial scans cannot persist hit counts or `content_safety_hits` rows.

## Iteration 4

- Closed Claude review BLOCKER by preventing canonical scan text from leaking into returned/sent content.
- Added regression coverage for pass-through case preservation, canonical replacement with original text preservation, and expanded Unicode normalization replacement boundaries.
- Re-ran targeted and full verification after the fix.
