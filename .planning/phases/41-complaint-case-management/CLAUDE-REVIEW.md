# Phase 41 Claude Review

Status: final blocker-only review passed.

Review target:

- Backend complaint case service/controller/migration/tests.
- Frontend complaint API/pages/routes/navigation/tests.
- Phase41 planning and UI contract artifacts.

Review boundary:

- Claude CLI was executed in diff-review mode with `claude -p --output-format json --disable-slash-commands --tools ""`.
- Base diff: `origin/phase/40-fee-warning-credit-enforcement`.
- Final blocker-only review result: `NO CRITICAL OR IMPORTANT FINDINGS.`

Claude findings fixed:

- `CARRIER` complaint source was rejected even though carrier complaints are in scope.
- Frontend row actions operated on stale/first-row state instead of the selected complaint row.
- Controller accepted client-supplied actor values for state, remediation, and recovery mutations.
- Production remediation API exposed a client-controlled failure path.
- Remediation and recovery lacked state boundaries; remediation now requires `PROCESSED`, recovery now requires the referenced remediation to be `FAILED`.
- Actor evidence was written but not returned; case rows now expose accepted/handled/closed actors.
- `BLACKLIST_MOBILE` now strips `mobile:` target refs before calling the blacklist port.
- `BLACKLIST_MOBILE` now requires tenant attribution before creating a blacklist entry.
- Mutating complaint endpoints are narrowed to `ADMIN` and `OPERATOR`; read/analytics endpoints still allow `FINANCE`.
- Remediation now verifies that tenant/channel/signature/template updates affect exactly one target row before recording `APPLIED`.
- Frontend remediation now sends a disposal type that matches the selected/derived target reference.
- Frontend state/remediation/recovery evidence values are editable page inputs rather than unchangeable request literals.
- Frontend recovery no longer submits an arbitrary fallback disposal record id; recovery is disabled until the current case action returns a remediation record id.
- Backend remediation now verifies the target belongs to the complaint attribution before mutating tenant/channel/signature/template/mobile resources.

Second-pass Claude findings:

- Critical: nonexistent remediation target could be recorded as `APPLIED`.
- Critical: frontend target derivation and hardcoded `SUSPEND_CHANNEL` could disagree.
- Important: evidence fields were hardcoded in the UI.
- Important: recovery submitted fallback disposal id `1`.
- Important: migration assumptions needed to be checked against the actual repository schema.
- Critical: remediation target existence checks still allowed an unrelated existing target to be mutated under another complaint.

Second-pass fixes:

- Added RED/GREEN backend coverage for nonexistent targets and rows-affected enforcement.
- Added RED/GREEN frontend coverage for signature-target disposal type derivation and disabled recovery.
- Confirmed repository V1 migration already defines `complaints` and `disposal_records`; V5000 extends those existing tables and keeps standalone H2 test bootstrap only for isolated migration tests.
- Added RED/GREEN backend coverage that rejects unrelated tenant/channel/signature/template/mobile targets and leaves those resources unchanged.

Residual scope decision:

- Recovery is scoped as manual compensation evidence for failed remediation records. Phase41 does not attempt automatic reversal of tenant/channel/signature/template effects because successful remediation cannot be recovered by this API.

No Critical or Important Claude findings remain for Phase41.
