# Phase 52 Design

## Test design

`MessageSubmitServiceTest.syntheticPeakLoadAcceptsOneThousandSubmissionsWithLatencyBoundaryAndNoInvariantLoss` submits 1,000 unique requests through the current `MessageSubmitService` orchestration.

Assertions:

- every response is accepted into `PENDING`;
- every response has a unique message id;
- measured P95 submit latency is below 200 ms;
- accepted throughput is at least 1,000 submissions/second on the local in-process harness;
- billing reserve is invoked exactly 1,000 times;
- send intent enqueue is invoked exactly 1,000 times;
- idempotency accepted marking is invoked exactly 1,000 times.

## Reused evidence

The targeted suite also runs existing tests for:

- statistics aggregation;
- channel health and candidate eligibility;
- API key rate limiting;
- controller rate-limit enforcement.

## Schema migrations

None.

## UI

None.

## Deployment

None. The PRD’s horizontal-scaling requirement is recorded as a capacity-planning boundary and covered by architecture/source evidence only in this phase.
