# Iterations

| Iteration ID | Trigger or finding | Evidence | Change made | Affected behavior/decision | Recheck |
| --- | --- | --- | --- | --- | --- |
| I-001 | Planning baseline found Phase 08 summary still says its atomic delivery is pending | `.planning/phases/08-tenant-qualification-status/08-VERIFICATION.md`, `git ls-remote origin refs/heads/phase/08-tenant-qualification-status` | Confirmed executable final PASS, empty TODO, and remote SHA `0468fe5c9fd229c43fc748b8760a8c273527f8bd`; treated stale summary prose as historical wording | D-09-002 | Phase 09 entry validator rerun |
| I-002 | Existing credential tables contain most required columns but entities expose only a subset | `core/src/main/resources/db/migration/V1__init_schema.sql`, entity inspection | Planned additive V1800/V1801 columns and safe management projections instead of duplicate stores | D-09-002, D-09-003 | Schema claim and MySQL integration tests |
| I-003 | Phase 02 defines exact tenant API/CMPP/admin page routes and prototype IDs | `.planning/phases/02-console-design-system-prototype-foundation/UI-ELEMENTS.md` | Reused `/tenant/administrators`, `/tenant/api/keys`, `/tenant/cmpp/access` and mapped production IDs | D-09-001 | Design UI validator and production Chrome evidence |
# Execution notes

- Implemented the first vertical backend slice with tenant-derived scope for
  subaccounts, HTTP API keys, and CMPP metadata. No request DTO accepts a
  tenant selector.
- Kept the credential boundary small: Phase 03 `ProtectedFieldCodec`, one
  redacted in-process revoke event, and existing operation audit storage; no
  protocol session, event bus, mobile surface, or package install was added.
- Added a production security-route rule so authenticated tenant roles can
  reach the three new APIs; service methods still recheck the mutable user
  type and tenant row.
- The real MySQL harness exposed a stale Phase 08 test assertion that froze
  Flyway at `1701`; it was updated to assert the latest Phase 09 migration
  `1801`, preserving the intended latest-schema regression check.
