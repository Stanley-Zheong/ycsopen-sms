---
name: flyway-migration
description: Use when creating, reviewing, renaming, cherry-picking, or backporting Flyway SQL in ycsopen-sms under core/src/main/resources/db/migration. Enforces immutable applied history, schema ownership, deterministic version selection, compatibility, and rollback or compensation evidence.
---

# Flyway migration

The current repository uses sequential files such as
`core/src/main/resources/db/migration/V1__init_schema.sql`.

Classify the change before editing:

- New migration: derive the next unused version with
  `python3 skills/flyway-migration/scripts/next_flyway_version.py` from the
  repository root (or pass `--repo <repository-root>`).
- Explicit rename: allowed only before application in every shared environment
  and with a recorded maintainer decision.
- Cherry-pick/backport: preserve path, name, and content. A target branch not
  containing the file does not make it a new migration.
- Already applied migration: never edit it; add a higher-version corrective
  migration.

The project intentionally uses integer versioned names
`V<integer>__<description>.sql`; repeatable `R__<description>.sql` is recognized
but does not change the next integer. Dotted/underscored versions, undo `U*`, or
other SQL names are rejected instead of silently mixed into this namespace.
Changing that policy requires an explicit schema decision and a matching update
to the version tool before such a file is added.

Before implementation, satisfy `.planning/SCHEMA-OWNERSHIP.md` and the active
phase `SCHEMA-CLAIMS.md`: owner, unique migration ID, dependencies, one
expand/migrate/contract step, and executable rollback or compensation.

Check MySQL 8 syntax, charset/collation, null/default behavior, indexes and
constraints, backfill safety, transaction/lock implications, repeat deployment,
existing-row preservation, and mixed-version application compatibility. Never
delete `flyway_schema_history` or disable validation to force progress.

Verify on a fresh schema and, when the change affects existing data, on a
representative pre-migration schema. Record the migration command, schema
state, assertions, and rollback/compensation result in phase evidence.
