# Phase 33 Context

Package: `unsubscribe-compliance`

Phase 33 depends on Phase 32 normalized uplink records and Phase 16 protected blacklist writes. The phase does not introduce a new uplink transport, export-file generator, alert delivery worker, or mobile surface.

Owned obligations:

- OBL-F-7-9-A
- OBL-F-10-2-A
- OBL-F-10-2-B
- OBL-F-10-2-C
- OBL-F-10-2-D
- OBL-F-10-2-E
- OBL-F-10-3-A
- OBL-F-10-3-B
- OBL-DATA-10-7-UNSUBSCRIBE

Implementation boundary:

- Unsubscribe keyword matching runs after normalized uplink creation.
- Tenant blacklist insertion reuses the protected blacklist adapter.
- Unsubscribe evidence stores masked/hash/protected digest fields and never exposes plaintext phone.
- Statistics use `unsubscribe_records` numerator and final `message_tasks` statuses `SENT`/`DELIVERED` denominator.
- Export requests create `export_tasks`; file generation remains out of scope.
