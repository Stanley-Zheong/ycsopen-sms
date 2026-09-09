# Phase 27 Context

Package: `message-receipt-error-operations`

Phase27 builds the operations surface for already accepted/delivered message data. It depends on the existing Phase23 acceptance pipeline, Phase24 delivery/receipt closure, Phase25 retry/migration recovery, and Phase20 status taxonomy.

Scope is limited to query/detail/action operations for submissions, send records, receipts, and normalized error groups. Export is a request handoff only; file generation remains owned by `secure-async-export`.
