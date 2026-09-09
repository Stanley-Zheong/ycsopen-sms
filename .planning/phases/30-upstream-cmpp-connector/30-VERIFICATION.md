# Phase 30 Verification

## Scope

Phase 30 implements the upstream CMPP connector core: PDU header framing, CONNECT auth, heartbeat, submit response handling, segmentation, window/backpressure, uncertain outcome retention, receipt/uplink normalization, authoritative simulator, and upstream SPI adapter.

Boundary: production TCP lifecycle and exact carrier CMPP body field mapping are outside this phase.

## Commands

- `mvn -f core/pom.xml -Dtest='CmppProtocolCodecTest,CmppClientSessionTest' test`
  - PASS: 10 tests, 0 failures, 0 errors, 0 skipped.
  - Evidence: `EVIDENCE/mvn-focused.log`
- `mvn -f core/pom.xml test`
  - PASS: 814 tests, 0 failures, 0 errors, 33 skipped.
  - Evidence: `EVIDENCE/mvn-full.log`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner upstream-cmpp-connector --assert-unique --assert-traced`
  - PASS: selected=5.
  - Evidence: `EVIDENCE/prd-obligations.log`
- `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 30 --package upstream-cmpp-connector --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/30-upstream-cmpp-connector/ENTRY-REVIEW.md`
  - PASS.
  - Evidence: `EVIDENCE/phase-entry.log`
- Claude CLI staged diff closure review
  - PASS: all five prior blocker/high findings fixed; no remaining blocker/high issue.
  - Evidence: `CLAUDE-REVIEW.md`

## Verdict

PASS
