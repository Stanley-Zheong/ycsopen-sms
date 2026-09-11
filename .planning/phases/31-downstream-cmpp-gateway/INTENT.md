# Phase 31 Intent

Build the downstream CMPP gateway as a verifiable protocol/session core, not as a premature socket service.

The user-visible product truth is that tenant CMPP traffic is not a compliance bypass. Authentication and connection policy happen before acceptance; accepted submits enter the same message pipeline as HTTP; requested reports are delivered only to the right tenant session and stay pending until acked.
