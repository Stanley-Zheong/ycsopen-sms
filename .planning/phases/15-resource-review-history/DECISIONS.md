# Decisions

- Use a read-only SQL `UNION ALL` projection instead of introducing a new duplicated review-history table.
- Add only `review-history:menu` and `review-history:read` permissions.
- Browser verification remains local installed Google Chrome only.
- Bound list queries with `page` and `pageSize`; keep detail lookup as direct type-specific SQL by history id.
