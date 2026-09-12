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

## Tooling boundaries

- Repository precheck whitespace checks pass. Its whole-tree Java output scan
  still finds eight diagnostic prints already present on `origin/main`; none is
  in an Issue 60 changed Java file.
- The external Claude CLI is installed but has no authenticated session or
  configured token in this run. Its invocation returned `Not logged in`; this
  is an unavailable review boundary, not a passing review.
- Docker Compose and Google Chrome are unavailable in the managed local
  container. The pull-request Docker release job owns that runtime evidence.
