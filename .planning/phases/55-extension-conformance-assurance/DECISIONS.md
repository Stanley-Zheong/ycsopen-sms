# Phase 55 Decisions

## DEC-55-001 — Registry over plugin framework

Decision: add a static Java registry of extension seams and conformance tests.

Reason: the PRD asks for extensibility conformance, not a runtime plugin marketplace. A static registry gives executable coverage with lower risk.

## DEC-55-002 — Keep policy extensions as versioned configuration

Decision: routing, provider taxonomy, pricing, and review extension points are represented as versioned configuration contracts.

Reason: existing services already store and test policy versions; hard-coded branching is not needed.

## DEC-55-003 — Declare independent scale only where ownership is durable

Decision: upstream provider, platform notification, qualification inspection, and dispatch-worker seams are marked independently scalable; pure policy configurations are not.

Reason: connector/worker boundaries can scale by replica/consumer count, while policy records are shared configuration state.
