# Phase 21 Context — Routing Circuit Policy

## Scope

Phase 21 implements `routing-circuit-policy`: ordered routing rules, routing simulation, circuit state, retry policy mapping, and decision history.

## Owned obligations

- `OBL-F-5-8-A`
- `OBL-F-5-8-B`
- `OBL-F-5-9-A`
- `OBL-F-5-9-B`
- `OBL-F-5-10-A`
- `OBL-FLOW-12-2-ROUTING`
- `OBL-DATA-10-4-ROUTING`

## Boundary

This phase does not replace the final dispatch worker. It provides the reusable policy contract and operator console needed by later dispatch, receipt, billing, and analytics phases.

