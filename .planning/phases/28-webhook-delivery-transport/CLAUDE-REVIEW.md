# Phase 28 Claude Review

## Runs

- Initial review: `.planning/phases/28-webhook-delivery-transport/EVIDENCE/claude-review.parsed.txt`
- First rerun after fixes: `.planning/phases/28-webhook-delivery-transport/EVIDENCE/claude-review-rerun.parsed.txt`
- Final focused closure: `.planning/phases/28-webhook-delivery-transport/EVIDENCE/claude-review-closure.parsed.txt`

## Closed findings

- SSRF filter expanded from string-prefix checks to resolved-IP validation.
- Redirect-based SSRF bypass closed by disabling redirects in the concrete HTTP request factory.
- Signing moved from source-derived shared seed to generated tenant signing secret.
- External HTTP delivery/test/replay entry points no longer run inside service-level transactions.
- HTTP client now sets explicit connect/read timeouts.
- Payload JSON is generated with Jackson instead of string concatenation.

## Final result

Claude closure output includes `NO BLOCKING OR HIGH FINDINGS`.

Known non-blocking follow-up: DNS can still change between validation and the underlying client connection. That is documented as hardening only, not a Phase 28 blocker.
