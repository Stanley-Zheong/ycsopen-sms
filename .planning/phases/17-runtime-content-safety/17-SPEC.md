# Phase 17 Spec: Runtime Final-Content Safety

## Goal

Content introduced only through variables is scanned in the final render before routing can create a send task.

## Owned obligations

- `OBL-F-5-5-A`: content policy cards and rows expose word, category, level, replacement, action, scope, state, creation time, total, intercept, rate, and coverage metrics.
- `OBL-F-5-5-B`: authorized users create, edit, delete/disable, import, and request export of word policies with validation, audit, and hot update.
- `OBL-F-5-5-C`: scanning operates on canonical final rendered content, including values introduced only by variables and Unicode normalization.
- `OBL-F-5-5-D`: block, replace, or alert behavior follows deterministic scope and severity precedence and records matched policy and resulting final content.

## Scope fence

- No free-text domestic send expansion.
- No Phase 18 frequency counters.
- No Phase 35 alert dispatch.
- Delete is implemented as soft disable to preserve hit evidence replay.
