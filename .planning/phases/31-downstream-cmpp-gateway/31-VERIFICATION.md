# Phase 31 Verification

## Scope

Phase 31 implements the downstream CMPP gateway session core: tenant credential authentication, IP/connection/TPS/window/revocation policy, `Service_Id`/template binding, shared acceptance handoff, precise `SUBMIT_RESP` outcomes, and durable requested report delivery.

Boundary: production TCP lifecycle and exact carrier-client body mapping are outside this phase.

## Commands

- `mvn -f core/pom.xml -Dtest='CmppDownstreamGatewaySessionTest,CmppMessageSubmitAcceptanceAdapterTest' test`
  - PASS: 10 tests, 0 failures, 0 errors, 0 skipped.
  - Evidence: `EVIDENCE/mvn-focused.log`
- `mvn -f core/pom.xml test`
  - PASS: 824 tests, 0 failures, 0 errors, 33 skipped.
  - Evidence: `EVIDENCE/mvn-full.log`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner downstream-cmpp-gateway --assert-unique --assert-traced`
  - PASS: selected=5.
  - Evidence: `EVIDENCE/prd-obligations.log`
- `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 31 --package downstream-cmpp-gateway --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/31-downstream-cmpp-gateway/ENTRY-REVIEW.md`
  - PASS.
  - Evidence: `EVIDENCE/phase-entry.log`
- Claude CLI staged diff closure review
  - PASS: all prior blocker/high findings fixed; no remaining blocker/high issue.
  - Evidence: `CLAUDE-REVIEW.md`

## Verdict

PASS
