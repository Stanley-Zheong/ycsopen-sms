# Phase 42 Decisions

- Use existing tenant lifecycle `FROZEN` instead of adding another send-blocking subsystem.
- Keep one episode table with source snapshot evidence instead of introducing a generic episode framework.
- Treat source numerator/denominator/window/registry/source-key as the source-backed boundary for this phase; creating aggregate jobs remains out of scope.
- Browser verification target is local Chrome only.
- Do not expand mobile or non-web scope.
