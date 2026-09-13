# Phase 40 Design

`FeeWarningCreditService` owns rule evaluation, episode dedupe, alert delivery evidence, and ingress enforcement. Message submission calls a single fence after idempotency so duplicate submissions keep returning their original response.

The UI has two routes: finance/admin manages rules and approvals; tenants view their own warning evidence.
