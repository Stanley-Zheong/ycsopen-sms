# Test Matrix

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OBL-PLATFORM-MESSAGE-001 | PRD F-2.1 | platform-system-message-bootstrap-01 | T-PLATFORM-MESSAGE-001:integration | n/a | n/a | n/a | TC-04-001 | Registration/operational message reaches provider sandbox and records one durable result | `mvn -f core/pom.xml -Dtest='*PlatformMessage*Test,*PlatformNotification*Test' test` | `.planning/phases/04-platform-system-message-bootstrap/EVIDENCE/evidence-manifest.json` |
| OBL-PLATFORM-MESSAGE-002 | REQ-F-12-2 | platform-system-message-bootstrap-02 | T-PLATFORM-MESSAGE-002:fault | n/a | n/a | n/a | TC-04-002 | Recursion guard and retry classification block/allow concrete scenarios | `mvn -f core/pom.xml -Dtest='*PlatformMessage*Test' test` | `.planning/phases/04-platform-system-message-bootstrap/EVIDENCE/evidence-manifest.json` |
| OBL-PLATFORM-MESSAGE-003 | PROJECT-SYSTEM-MESSAGING; PRD 7.1 | platform-system-message-bootstrap-03 | T-PLATFORM-MESSAGE-003:security | n/a | n/a | n/a | TC-04-003 | Audit evidence captures redacted metadata and provider result mapping | `mvn -f core/pom.xml -Dtest='*PlatformNotification*Test,*NotificationAudit*Test' test` | `.planning/phases/04-platform-system-message-bootstrap/EVIDENCE/evidence-manifest.json` |

