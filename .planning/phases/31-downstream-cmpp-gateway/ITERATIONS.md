# Phase 31 Iterations

- Iteration 1: Implemented downstream gateway session core, credential lookup, connection registry, shared acceptance port, body codec, adapter, and unit tests.
- Iteration 2: Fixed delivery window behavior so existing inflight reports count against the session window before new DELIVER frames are drained.
