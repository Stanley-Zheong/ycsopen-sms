# Phase 42 Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
|---|---|---|---|---|---|---|---|
| SCHEMA-P42-RULES | ycs.sms.tenant-risk-auto-pause.tenant_alert_rules | tenant-risk-auto-pause | V5100 | V1 | Expand existing rule table with tenant/status/actor/update metadata. | forward-compatible expand rollback by ignoring added columns | SCHEMA-P42 in `.planning/SCHEMA-OWNERSHIP.md` |
| SCHEMA-P42-EPISODES | ycs.sms.tenant-risk-auto-pause.tenant_risk_episodes | tenant-risk-auto-pause | V5100 | V4400 alert evidence, V1 tenant lifecycle | Create append-only episode evidence table with unique source key. | forward-compatible table removal before dependent code deploy | SCHEMA-P42 in `.planning/SCHEMA-OWNERSHIP.md` |
