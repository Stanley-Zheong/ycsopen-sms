# Phase 24 Test Matrix

| Obligation | Evidence |
| --- | --- |
| OBL-ACCEPT-UPSTREAM-HTTP-REAL | `HttpSmsUpstreamProviderClientTest.postsIdempotentHttpPayloadToSandboxProvider` |
| OBL-HTTP-UPSTREAM-001 | `HttpMessageDeliveryServiceTest.dispatchClaimsOneReadyOutboxAndSendsExactlyOnce` |
| OBL-HTTP-UPSTREAM-002 | `HttpMessageDeliveryServiceTest.unknownProviderOutcomeIsQuarantinedAndNotRetriedAutomatically` |
| OBL-HTTP-UPSTREAM-003 | receipt tests in `HttpMessageDeliveryServiceTest` |
| OBL-STATE-MESSAGE-SENT | accepted provider response test |
| OBL-STATE-MESSAGE-DELIVERED | delivered receipt test |
| OBL-STATE-MESSAGE-FAILED-PRE | provider rejection test |
| OBL-STATE-MESSAGE-FAILED-REPORT | failed receipt test |
