# Phase 33 Decisions

- Use existing `message_tasks` final statuses `SENT` and `DELIVERED` as the unsubscribe-rate denominator.
- Keep product code only on unsubscribe evidence because `message_tasks` has no product field.
- Do not implement export file generation; create `export_tasks` only.
- Do not implement alert delivery; create alert source event evidence only.
- Do not add mobile scope.
- Use local Chrome only for browser verification.
