# Decisions

- `DELETE` in the console maps to `DISABLED`, not physical deletion, because hit evidence must remain replayable.
- Unicode canonicalization uses Java NFKC plus lowercase before matching. The persisted `message_tasks.content` stores the checker result, so replacements apply to the final task content.
- Product scope uses `RoutingContext.templateId` as the current product-like runtime discriminator until a later product catalog exists.
- Generic alert notification dispatch is out of scope; Phase 17 records alert hits and metrics only.
