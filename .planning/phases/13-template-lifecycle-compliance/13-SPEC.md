# Phase 13 Spec

## Goal

Template variable and content rules produce precise client/server outcomes.

## Scope

- Tenant template application with name, content, type, approved signature binding, variable extraction, parameter rules, and rationale.
- Tenant preview and rejected/amendment-required resubmission as a new pending version.
- Admin template review queue, filters, content/signature/variable inspection, and approve/reject/amendment decisions.
- Shared domestic pre-send validator for approved template, approved signature, tenant ownership, binding, and exact variables.

## Out of scope

- Runtime sensitive-word library authoring.
- Routing policy and provider delivery.
- Batch/CMPP transports that do not yet have production ingress in this repository; Phase 13 provides the shared validator they must call when introduced.
