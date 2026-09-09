# Phase 18 Spec

## Objective

Per-key multi-window limits and runtime frequency rules are isolated, observable, and return a standard 429 contract before any task or charge is created.

## Owned obligations

- OBL-F-5-6-A: Frequency rule cards and rows expose rule name, dimension, count/window, action, scope, state, and hit metrics.
- OBL-F-5-6-B: Authorized users create, update, import, export-request, enable, and disable rules with partial-failure evidence.
- OBL-F-5-6-C: Multi-instance Redis counters enforce exact fixed windows and scoped exemptions without race or clock-boundary bypass.
- OBL-F-6-5-A: API keys enforce independent second/minute/hour/day limits across instances.
- OBL-F-6-5-B: Exceeded API key limits return HTTP 429 with retry guidance and do not create task/charge.
- OBL-EDGE-HIGH-CONCURRENCY: API excess returns 429; console excess has explicit queued/delayed feedback copy.

## Non-goals

- No channel-window implementation.
- No final export file generation.
- No worker queue implementation beyond visible queued/delayed contract handoff.
