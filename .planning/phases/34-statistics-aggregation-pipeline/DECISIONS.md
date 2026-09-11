# Phase 34 Decisions

- Use Java aggregation logic instead of one large SQL aggregate statement. Reason: keeps MySQL/H2 verification compatible and makes final-state/correction rules explicit.
- Use final delivery report state over task send status. Reason: receipt is the final external state when present.
- Use confirmed billing amount over task cost. Reason: billing record is the auditable consumption source when present.
- Keep scheduler/dashboard/custom reports out of scope. Reason: later phases own presentation and report authoring.
- Keep this phase backend-only. Reason: roadmap primary surfaces are aggregation jobs/tables, metric registry/API, and reconciliation reports; no production UI contract is required here.
