# Phase 19 Decisions

## D1 — Local provider boundary

The phase implements a `PortabilityProvider` interface but defaults to no external call. This keeps Phase19 deterministic and avoids unowned credentials/network integration. Provider failure is still executable through `forceProviderFailure`.

## D2 — Protected portability storage

`mobile_portability` stores `mobile_hash` and `masked_mobile`, not plaintext phone numbers. Prefix mappings store only numeric prefixes.

## D3 — One React page for three routes

`/admin/number-attribution`, `/admin/number-portability`, and `/admin/prefixes` render one focused page with three sections. This satisfies the page contract without creating three duplicated screens.
