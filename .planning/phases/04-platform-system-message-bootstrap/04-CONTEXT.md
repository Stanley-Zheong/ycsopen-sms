# Phase 4 context

## Dependency evidence

- Phase 1 verification foundation: `.planning/phases/01-engineering-verification-foundation/SUMMARY.md`, `.planning/phases/01-engineering-verification-foundation/01-VERIFICATION.md`, `.planning/phases/01-engineering-verification-foundation/TODO.md`.
- Phase 3 crypto bootstrap/migration: `.planning/phases/03-crypto-storage-bootstrap/03-VERIFICATION.md`, `.planning/phases/03-crypto-storage-bootstrap/SUMMARY.md`, `.planning/phases/03-crypto-storage-bootstrap/TODO.md`.

## Current implementation facts

- No dedicated Phase 4 platform bootstrap module exists yet.
- Delivery and provider-authorization infrastructure is present at application-level foundations (Spring/JPA/Jackson/validation stacks), but no owner-specific platform bootstrap SPI.
- Existing tenant notification pages/flows are in Phase 2 prototype scope only and are not implementation-complete.

## Constraints and exclusions

- This phase must not depend on tenant-owned channel/routing/billing implementations.
- No mobile channel behavior is in scope.
- The first runnable bootstrap proof uses real browser evidence only after a provider sandbox endpoint is wired.
- Every decision must map to one of the three owned obligations.

