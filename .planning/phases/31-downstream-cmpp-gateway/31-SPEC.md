# Phase 31 Spec

Phase 31 delivers the downstream CMPP gateway session core. It authenticates tenant clients, enforces credential/IP/connection/TPS/window policy, maps `Service_Id`/product/template binding to the shared acceptance boundary, returns precise protocol outcomes, and keeps requested reports in a durable session queue until acknowledged.

Boundary: the executable contract is Java session logic and PDU/header/body test codecs. A production TCP listener can wrap this session later without changing the acceptance/security semantics.

## Owned PRD obligations

- OBL-F-6-7-A: tenant-facing CMPP server session implements CONNECT, SUBMIT, DELIVER, ACTIVE_TEST, TERMINATE, window, and sequence behavior.
- OBL-F-6-7-B: account, password, IP, max connection, TPS, window, and revocation policy are enforced per tenant credential.
- OBL-F-6-8-A: Service_Id/product/template binding is carried into the same shared acceptance contract used by HTTP.
- OBL-F-6-8-B: malformed, mismatched, or noncompliant product-template data receives precise SUBMIT_RESP errors and creates no accepted submission.
- OBL-F-6-9-A: registered delivery reports are emitted as CMPP_DELIVER to the correct tenant session and retained until acknowledged.

## Non-goals

- No production TCP socket service.
- No separate routing, billing, or compliance path.
- No UI.
