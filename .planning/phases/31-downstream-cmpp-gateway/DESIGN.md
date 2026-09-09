# Phase 31 Design

- `CmppDownstreamGatewaySession`: per-client session state machine for CONNECT/SUBMIT/DELIVER/ACTIVE_TEST/TERMINATE.
- `CmppDownstreamCredentialStore`: active tenant credential lookup by CMPP account.
- `CmppDownstreamConnectionRegistry`: active connection counter by credential.
- `CmppDownstreamAcceptancePort`: policy boundary that shared HTTP/CMPP acceptance must pass through.
- `CmppMessageSubmitAcceptanceAdapter`: production adapter from CMPP submit to `MessageSubmitService`.
- `CmppDownstreamSessionRegistry`: active tenant session and pending report registry, so reconnecting clients can receive retained reports without relying on the old socket object.
- `CmppDownstreamBodyCodec`: compact Phase31 test body codec for submit binding fields.

Precise response codes are constants on `CmppDownstreamGatewaySession` so tests can assert each reject class without relying on prose.
