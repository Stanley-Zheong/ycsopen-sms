# Phase 30 Decisions

- Use an authoritative in-process simulator for protocol interoperability evidence; do not require external carrier credentials in this phase.
- Keep the first implementation as protocol core plus SPI adapter, not a full TCP/Netty runtime.
- Preserve no-duplicate client send semantics with idempotency keys, accepted-segment tracking, and retained inflight claims on uncertain CMPP outcomes.
- Normalize CMPP receipts through the existing `ProviderStatusTaxonomyPort` shape.
- Treat the simulator body codec as a Phase30 test contract, not the final carrier wire body. The implemented PDU header, sequence correlation, auth flow, window/backpressure, reconnect state, submit outcome handling, receipt/uplink normalization, and SPI adapter are reusable; future production socket/carrier work must replace or extend body field mapping against exact CMPP 2.0/3.0 provider specs.
- Keep browser verification out of this phase because there is no UI.
