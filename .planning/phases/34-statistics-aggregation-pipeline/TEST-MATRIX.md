# Phase 34 Test Matrix

| Obligation | Verification |
| --- | --- |
| OBL-F-3-8-A | `StatisticsAggregationServiceTest.rebuildAggregatesResourceChannelAndTenantMetricsFromFinalSources` asserts resource usage counts by signature/template include accepted, rejected, success, and failure. |
| OBL-F-11-1-A | `StatisticsAggregationServiceTest.rebuildAggregatesResourceChannelAndTenantMetricsFromFinalSources` asserts channel aggregates use latest delivery report and confirmed billing amount; `rebuildIsIdempotentAndLateCorrectionReplacesFinalState` asserts late correction replacement. |
| OBL-F-11-2-A | `StatisticsAggregationServiceTest.rebuildAggregatesResourceChannelAndTenantMetricsFromFinalSources` asserts tenant-scoped behavior and consumption aggregates. |
| OBL-DATA-10-11-AGGREGATES | `StatisticsAggregationMigrationTest.phase34MigrationCreatesMetricRegistryAggregateAndCorrectionTables` asserts required tables/columns; service tests assert bucket/date/dimensions/drilldown/correction identity. |
