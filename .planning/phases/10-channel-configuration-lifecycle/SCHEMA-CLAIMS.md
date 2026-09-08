# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-10-001 | ycs.sms.channel-configuration-lifecycle.channels | channel-configuration-lifecycle | V1900 | V1801 | expand | Disable new readers and restore the prior compatible channel snapshot; retain existing rows | - |
| SC-10-002 | ycs.sms.channel-configuration-lifecycle.channel_configuration_versions | channel-configuration-lifecycle | V1901 | V1900 | expand | Stop version activation and restore the previous effective pointer from a verified snapshot; retain immutable history | - |
