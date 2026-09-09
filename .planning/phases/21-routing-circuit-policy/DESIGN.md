# Design

## Backend

- `routing_policy_versions` and `routing_policy_rules` store effective ordered rules.
- `routing_circuit_states` records circuit status and history.
- `routing_retry_rules` maps normalized provider status category to retry behavior.
- `routing_decision_history` stores simulation/selection evidence with version and snapshots.
- `RoutingCircuitPolicyService` is the contract later dispatch can consume.

## Frontend

- `/admin/routing-policy` exposes import, simulation, circuit state, retry rule, current rule, and version history sections.

