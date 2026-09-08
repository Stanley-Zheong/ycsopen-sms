# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-11-001 | ycs.sms.channel-health-pools-candidate-pause.channel_health_observations | channel-health-pools-candidate-pause | V2000 | V1901 | expand | Disable health writes and preserve existing channel status rows while applying forward correction | - |
| SC-11-002 | ycs.sms.channel-health-pools-candidate-pause.channel_pause_events | channel-health-pools-candidate-pause | V2001 | V2000 | expand | Disable pause/maintenance mutation endpoints and preserve audit rows while applying forward correction | - |
| SC-11-003 | ycs.sms.channel-health-pools-candidate-pause.channel_pools | channel-health-pools-candidate-pause | V2002 | V2001 | expand | Disable pool routing consumption and preserve pool rows while applying forward correction | - |
