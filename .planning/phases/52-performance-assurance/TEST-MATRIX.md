# Phase 52 Test Matrix

| Obligation | Evidence | Command |
| --- | --- | --- |
| OBL-NFR-PERF-TPS | `EVIDENCE/OBL-NFR-PERF-TPS.json` | `mvn -f core/pom.xml -Dtest=MessageSubmitServiceTest,StatisticsAggregationServiceTest,ChannelHealthServiceTest,ChannelPoolServiceTest,ChannelCandidateEligibilityServiceTest,ApiKeyRateLimitServiceTest,MessageControllerRateLimitTest,OperationalDashboardServiceTest test` |
| OBL-NFR-PERF-DAILY | `EVIDENCE/OBL-NFR-PERF-DAILY.json` | PRD baseline static check plus targeted/full backend test |
| OBL-NFR-PERF-LATENCY | `EVIDENCE/OBL-NFR-PERF-LATENCY.json` | targeted Maven performance suite |
| OBL-NFR-PERF-HEALTH | `EVIDENCE/OBL-NFR-PERF-HEALTH.json` | targeted Maven performance suite |
| OBL-DOD-05-PERFORMANCE | `EVIDENCE/OBL-DOD-05-PERFORMANCE.json` | owner validator, targeted Maven suite, full backend Maven suite |

## Commands

```bash
/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner performance-assurance --assert-unique --assert-traced
mvn -f core/pom.xml -Dtest=MessageSubmitServiceTest,StatisticsAggregationServiceTest,ChannelHealthServiceTest,ChannelPoolServiceTest,ChannelCandidateEligibilityServiceTest,ApiKeyRateLimitServiceTest,MessageControllerRateLimitTest,OperationalDashboardServiceTest test
mvn -f core/pom.xml test
git diff --check
```
