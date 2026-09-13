# Phase 51 SPEC — Security Assurance

## Goal

Prove the implemented system's security boundaries with executable local evidence and close the `security-assurance` TODO set.

## Scope

In scope:

- TLS/deployment boundary evidence for external HTTP.
- CMPP authentication, IP allow-list, connection/TPS policy, and acceptance-boundary evidence.
- JWT/RBAC/tenant data-scope authorization evidence.
- HTTP API HMAC, timestamp, nonce, IP allow-list, and rate-limit evidence.
- Domestic-send regulatory fence evidence: tenant eligibility, template/signature binding, blacklist, unsubscribe, routing/frequency.
- Secret/data leak protection evidence.

Out of scope:

- Building a new security product or dashboard.
- Network-heavy dependency scanners that download large vulnerability databases.
- Production DAST against a deployed external environment.

## Completion standard

The phase is complete when:

- The owner obligation validator returns the authoritative `security-assurance` set.
- Each scoped obligation has an evidence JSON file.
- Targeted security JUnit tests pass.
- Full backend tests pass.
- Local secret/static scan passes.
- TODO.md contains no open scoped TODO.
