# Phase 11 Design

## Module shape

Phase11 adds a small channel-health package beside Phase10 channel
configuration:

- `ChannelHealthService` owns observations, sustained-failure state changes,
  planned maintenance, and pause records.
- `ChannelPoolService` owns pool validation and optimistic versioning.
- `ChannelCandidateEligibilityService` owns one reusable eligibility decision
  used by pool validation and `ChannelSelector`.
- `ChannelHealthController` exposes monitor, pause, maintenance, and pool APIs.
- React pages `ChannelHealthPage` and `ChannelPoolsPage` provide the production
  Admin UI.

## Persistence

Schema migrations: declared

- `channel_health_observations` stores latest and historical metric samples.
- `channel_pause_events` stores pause/maintenance/restore episode evidence.
- `channel_pools` and `channel_pool_members` store pool mode, optimistic
  version, member weights, primary flag, and disabled-member state.

All changes are additive under SCHEMA-P11. Existing Phase10 channel rows remain
compatible.

## Candidate fence

A channel is eligible for new route candidates only when:

1. `status == NORMAL`
2. `availability == AVAILABLE`
3. `effectiveVersionId` is present
4. the channel is not disabled in the selected pool

The fence returns a reason code, not just a boolean, so UI and later routing
phases can explain exclusions without duplicating rules.

## State handling

- Sustained health failures move the channel to `MAINTENANCE` and create one
  source event for the current episode.
- Manual, health, complaint, and ratio pauses move the channel to `PAUSED` and
  record trigger, actor/system principal, reason, and time.
- Planned maintenance moves `NORMAL` to `MAINTENANCE`.
- Ending maintenance requires a successful health validation and then returns to
  `NORMAL`.

## UI design

The pages follow Phase2 dense Admin style and Phase10 channel table patterns:

- concise KPI cards above the table;
- stable row actions;
- modal dialogs for required reasons;
- no mobile-specific layout;
- no hidden unsupported browser matrix.
