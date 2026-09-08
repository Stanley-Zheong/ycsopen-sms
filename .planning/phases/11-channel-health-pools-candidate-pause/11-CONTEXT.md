# Phase 11 Context

## Repository state

- Phase10 delivered channel configuration and immutable version activation.
- `Channel.status` already has `NORMAL`, `MAINTENANCE`, `ABNORMAL`, `PAUSED`,
  and `OFFLINE`.
- The existing `/api/v1/console/channels` pause/resume endpoints are thin
  repository writes and are not enough for Phase11 because they do not own
  health evidence, pool validation, or candidate eligibility as a reusable
  routing fence.
- `ChannelSelector` already filters by `Channel.isRoutable()`. Phase11 will
  keep that simple boundary and make the eligibility reason explicit for later
  routing phases.

## Scope fence

This phase owns channel health observations, channel pools, planned
maintenance, pause records, and the current candidate eligibility fence. It
does not own durable task migration, already-owned in-flight message transfer,
provider status taxonomy, protocol sessions, complaint-ratio calculation, or
non-Chrome browser support.

## Reuse

- Reuse Phase10 channel configuration rows and status values.
- Reuse Phase2 console tokens and dense table style.
- Reuse installed local Google Chrome only for browser verification.
