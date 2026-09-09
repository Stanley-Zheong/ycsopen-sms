# Phase 22 Decisions

- Use integer mil units for all prepaid amounts to avoid floating-point rounding.
- Keep trial quota and prepaid balance ledgers append-oriented; business documents and message references provide idempotency boundaries.
- Keep browser verification scoped to local Chrome only.
- Reuse existing ycsan-style card/table layout and existing tenant qualification status selector instead of introducing a new design system.
