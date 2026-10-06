# Issue 121 Tenant Onboarding And API Access Review

## Entry review

The first independent review found four blocking ambiguities: page-lifecycle
disposal of the one-time secret, the exact audit HTTP/read model, placeholder
permission language, and missing Phase 08/09/22 obligation ownership. The
contracts now define volatile secret disposal, the bounded newest-first audit
response, exact permissions, and mappings to OBL-F-2-2-B, OBL-F-2-6-A,
OBL-F-2-6-B, and OBL-F-2-8-A. A corrected-package readback passed those four
findings. A second independent review then identified shared table selectors,
modal interaction shielding, unknown create outcomes, and an explicit
TENANT_ADMIN happy path. The contracts now cover each item; a final entry
readback passed with no remaining blocker.

## Superseded pre-push review

Three independent pre-push reviews covered the final backend, frontend, and
planning slices. Backend review confirmed tenant predicates, role boundaries,
create-only secret separation, redaction, the six-field audit projection, and
the newest-100 ordering oracle. Planning review confirmed obligation trace,
state/selector coverage, and evidence freshness.

Frontend review first found that a single unresolved-name value could forget
an earlier unknown create after a second name also became unknown. The page now
retains every unresolved name for its mounted lifetime, and both Vitest and
Playwright prove the A-to-B-to-A sequence does not send the final request. The
changed slice was rerun and independently reread. All three final reviews
reported no remaining blocker, high, or medium finding. Later owner review
invalidated that conclusion with concrete CIDR, timezone, 403, index, CI,
dialog, real-service, evidence-validator, and last-use findings.

## Reopened final review

The owner findings are being repaired on the rebased PR branch. Completion
remains open until fresh local gates, provider MySQL/Google Chrome gates, the
change-package production evidence validator, and a new independent review all
pass on the repaired diff.

The first repaired-diff rereview found a remaining CSS-specificity collision,
late mutation completion after a concurrent 403, and a missing writer for the
displayed last-use field. The implementation now explicitly excludes shared
dialogs from the legacy selector, uses a synchronous denied latch before and
after mutation awaits, and records successful HMAC use through a throttled
atomic update. The next independent rereview confirmed no remaining blocker,
high, or medium implementation finding, including the ref-backed authorization
read counter for non-visual post-mutation refreshes. Provider evidence remains
open.

The first provider run of that repaired diff passed the complete Web job and
all five deterministic Issue 121 Chrome cases. Its wider Docker suite exposed
a shared-modal integration regression: the complaint action dialog had both a
legacy and a shared backdrop. The same run's real MySQL lane exposed fixture
leakage because API-key audit rows survived between Phase09 test methods. The
implementation now has one configurable shared backdrop with direct-click and
pending guards, and Phase09 clears only its owned tenant/resource audit rows in
`@BeforeEach`. Focused tests, build, diff hygiene, and a further independent
rereview found no blocker, high, or medium issue. Fresh provider proof remains
the completion boundary.
