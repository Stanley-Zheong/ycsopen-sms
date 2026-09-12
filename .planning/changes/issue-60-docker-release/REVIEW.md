# Issue 60 Docker Release Review

## Independent review

The first read-only review reported four HIGH findings:

1. A caller-supplied Compose project name could redirect destructive cleanup.
2. The repeated build did not force a restart or model a pre-change volume.
3. Build identity propagation was not tied to the checked-out branch head.
4. Published ports and the Nginx Actuator proxy exposed more surface than the
   acceptance contract required.

The verifier now owns unused fresh and upgrade project names, creates a
pre-Issue-60 Flyway-location baseline, force-recreates Core and Web twice,
preserves an operator-owned row, and rejects a commit other than `HEAD`. The PR
job checks out the pull-request head. Compose binds to localhost by default and
Nginx forwards only Actuator health and info.

The focused read-only re-review of those corrections returned:

`NO BLOCKER OR HIGH FINDINGS`

## First pull-request runtime finding

The first Docker release job built both images from commit `b383c2e`, applied
all 45 migrations to a new MySQL volume, and then stopped at Hibernate schema
validation because `blacklist_entries.mobile_hash` is `CHAR(64)` in the
versioned schema but its JPA mapping defaulted to `VARCHAR(255)`. A complete
audit of fixed-width columns owned by JPA entities found the same latent
contract mismatch on `message_tasks.mobile_hash`,
`complaint_ratio_stats.stat_month`, and `tenants.inspection_credit_code`.

The entity mappings now state the exact existing MySQL widths and `CHAR` types,
with a focused regression test covering all four fields. No versioned Flyway
file was edited and no new migration is needed because the database schema is
already the intended source of truth. Fresh-volume runtime evidence will be
re-run on the corrected pull-request head.

The follow-up schema review found three additional native-type gaps that the
first failing column had hidden: tenant inspection confidence was mapped as a
floating-point value instead of `DECIMAL(5,4)`, tenant customer level defaulted
to `INTEGER` instead of `TINYINT`, and the protocol credential's protocol and
status fields defaulted to `VARCHAR` instead of their native MySQL enums. The
entity persistence types now match the immutable migrations while preserving
the existing public confidence accessors. The compatibility test covers these
types too.

The final independent review of the complete corrected diff returned:

`NO BLOCKER OR HIGH FINDINGS`

## Tooling boundaries

- Repository precheck whitespace checks pass. Its whole-tree Java output scan
  still finds eight diagnostic prints already present on `origin/main`; none is
  in an Issue 60 changed Java file.
- The external Claude CLI is installed but has no authenticated session or
  configured token in this run. Its invocation returned `Not logged in`; this
  is an unavailable review boundary, not a passing review.
- Docker Compose and Google Chrome are unavailable in the managed local
  container. The pull-request Docker release job owns that runtime evidence.
