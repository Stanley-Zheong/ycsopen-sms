# Phase 30 Spec

Phase 30 delivers a CMPP upstream connector core that can interoperate with an authoritative in-process simulator.

The executable boundary is the Java client/session core, 12-byte PDU header framing, simulator contract, outcome normalization, and upstream SPI adapter. Production TCP lifecycle and exact carrier CMPP 2.0/3.0 body mapping are outside this phase.

The phase must prove:

- OBL-ACCEPT-UPSTREAM-CMPP-REAL: CMPP simulator interop proves connect, submit accepted/rejected outcomes, response, receipt, uplink, final state, and billing-facing provider outcome.
- OBL-UPSTREAM-CMPP-001: CMPP PDU header encode/decode, CONNECT authentication success/rejection, ACTIVE_TEST heartbeat, SUBMIT/SUBMIT_RESP sequence correlation, window state, and TERMINATE.
- OBL-UPSTREAM-CMPP-002: Fragmented frame reassembly, long-message segmentation, sequence/window backpressure, slow peer, disconnect, and no-duplicate client-send outcome preservation.
- OBL-UPSTREAM-CMPP-003: Submit responses, deterministic validation rejects, delivery reports, and uplinks normalize through shared provider taxonomy/upstream SPI boundaries.
- OBL-EDGE-CMPP-DISCONNECT: Disconnect handling preserves claimed uncertain work and exposes reconnect backoff without duplicate client send.

Non-goals:

- No downstream CMPP server.
- No SGIP/SMGP implementation.
- No production TCP socket lifecycle or external carrier credential.
