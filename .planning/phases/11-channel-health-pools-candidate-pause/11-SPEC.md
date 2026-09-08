# channel-health-pools-candidate-pause: Channel health, pools, and candidate pause

## Intent

Operators can see channel health evidence, maintain valid channel pools, and
remove unhealthy or paused channels from new route candidates without claiming
durable in-flight task migration.

## Scope

### In

- Heartbeat/test-message health observations with connection, timeout, failure,
  latency, and source-event evidence.
- Sustained health failure moves a channel to maintenance or candidate
  ineligible state and emits one source-backed alert event.
- Weighted and primary-backup channel pools with deterministic membership,
  primary, weight, disabled-member, and concurrent-edit validation.
- Authorized pause records for manual, health, complaint, and ratio triggers.
- Candidate eligibility API/fence that excludes paused, maintenance, abnormal,
  offline, disabled-member, and non-effective channels from new assignments.
- Admin production UI for `/admin/channel/health` and `/admin/channel/pools`.

### Out

- Already-owned in-flight durable task migration and recovery.
- Real CMPP/SGIP/SMGP/HTTP provider sessions.
- Provider status taxonomy normalization.
- Complaint and ratio calculation.
- Mobile UI and non-Chrome browser validation.

## External behavior

### channel-health-pools-candidate-pause-01

Where a channel is checked by heartbeat or test-message, when the adapter
reports connection, timeout, failure, and latency values, the system records the
observation, displays the latest health state, and emits a source event for
sustained failure.

### channel-health-pools-candidate-pause-02

Where an operator configures a channel pool, when members, weights, primary
choice, disabled members, and expected version are submitted, the system accepts
only valid weighted or primary-backup semantics and rejects stale or invalid
updates deterministically.

### channel-health-pools-candidate-pause-03

Where an authorized manual, health, complaint, or ratio pause is requested, the
system records trigger, actor or system principal, reason, and time, changes the
channel to paused or maintenance as appropriate, and excludes it from new route
candidates immediately.

### channel-health-pools-candidate-pause-04

Where planned maintenance begins or ends, the system records the maintenance
evidence, makes the channel route-ineligible while maintenance is active, and
restores normal status only after a successful health validation.

## Error boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| Health sample lacks required metrics | Reject sample and preserve previous health state | channel-health-pools-candidate-pause-01 |
| Sustained failure threshold reached twice for same episode | Emit one source event for the episode, not duplicate alerts | channel-health-pools-candidate-pause-01 |
| Weighted pool has zero/negative weights or total outside accepted range | Reject pool mutation | channel-health-pools-candidate-pause-02 |
| Primary-backup pool has zero or multiple primary members | Reject pool mutation | channel-health-pools-candidate-pause-02 |
| Pool references missing, offline, paused, maintenance, abnormal, or non-effective channel | Reject enabled membership or mark disabled member ineligible | channel-health-pools-candidate-pause-02 |
| Pause is requested without trigger, actor/system principal, or reason | Reject mutation | channel-health-pools-candidate-pause-03 |
| Channel is paused or in maintenance | Candidate API and selector exclude the channel from new assignments | channel-health-pools-candidate-pause-03 |
| Maintenance end is requested without successful health validation | Reject transition to normal | channel-health-pools-candidate-pause-04 |

## Verification

| Verification ID | Evidence |
| --- | --- |
| T-P11-HEALTH | Unit/integration tests record metrics, state transitions, and single source event for sustained failure. |
| T-P11-POOL | Unit/integration tests validate weighted/primary-backup pools, disabled members, and optimistic concurrency. |
| T-P11-CANDIDATE | Component/API tests prove paused and maintenance channels are excluded from new candidates. |
| T-P11-UI-CHROME | Installed local Chrome exercises health page, pause/maintenance actions, pool editor, and visible feedback. |
| T-P11-TRACE | PRD owner trace, production UI contract, evidence files, and empty scoped TODO prove closure. |

## Requirement trace

| PRD requirement | Behavior IDs | Verification IDs |
| --- | --- | --- |
| REQ-F-4-3 | channel-health-pools-candidate-pause-01 | T-P11-HEALTH, T-P11-UI-CHROME |
| REQ-F-4-6 | channel-health-pools-candidate-pause-02 | T-P11-POOL, T-P11-UI-CHROME |
| REQ-F-4-7 | channel-health-pools-candidate-pause-03, channel-health-pools-candidate-pause-04 | T-P11-CANDIDATE, T-P11-UI-CHROME |
| PROJECT-STATE-MACHINE | channel-health-pools-candidate-pause-03, channel-health-pools-candidate-pause-04 | T-P11-CANDIDATE, T-P11-HEALTH |

## Owned obligations

- OBL-F-4-3-A
- OBL-F-4-3-B
- OBL-F-4-6-A
- OBL-F-4-6-B
- OBL-F-4-7-A
- OBL-F-4-7-B
- OBL-STATE-CHANNEL-PAUSE
- OBL-STATE-CHANNEL-MAINTAIN
- OBL-STATE-CHANNEL-MAINTAIN-END
