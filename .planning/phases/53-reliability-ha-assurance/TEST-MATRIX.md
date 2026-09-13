# Phase 53 Test Matrix

| Obligation | Evidence | Command |
| --- | --- | --- |
| OBL-NFR-AVAILABILITY | `EVIDENCE/OBL-NFR-AVAILABILITY.json` | targeted reliability suite plus full backend suite |
| OBL-NFR-FAILOVER-30S | `EVIDENCE/OBL-NFR-FAILOVER-30S.json` | targeted reliability suite |
| OBL-NFR-STATELESS | `EVIDENCE/OBL-NFR-STATELESS.json` | targeted reliability suite plus PRD/source check |
| OBL-NFR-DATA-HA | `EVIDENCE/OBL-NFR-DATA-HA.json` | targeted reliability suite plus full backend suite |
| OBL-NFR-MULTI-AZ-ROLLBACK | `EVIDENCE/OBL-NFR-MULTI-AZ-ROLLBACK.json` | targeted reliability suite plus PRD/source check |

## Commands

```bash
/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner reliability-ha-assurance --assert-unique --assert-traced
mvn -f core/pom.xml -Dtest=RoutingCircuitPolicyServiceTest,ChannelSelectorTest,ChannelHealthServiceTest,ChannelPoolServiceTest,ChannelCandidateEligibilityServiceTest,DispatchTaskRecoveryServiceTest,MessageAcceptanceIdempotencyServiceTest,ReconciliationSettlementServiceTest,BillingServiceTest,TrialPrepaidLedgerServiceTest,PlatformConfigurationServiceTest,ChannelConfigurationHotReloadTest,ChannelConfigurationActivationFaultTest,ThirdPartyBlacklistClientTest,ChannelOfflineTransitionTest,ChannelDependencyMigrationTest,HttpMessageDeliveryServiceTest,MessageReceiptErrorOperationsServiceTest test
mvn -f core/pom.xml test
git diff --check
```
