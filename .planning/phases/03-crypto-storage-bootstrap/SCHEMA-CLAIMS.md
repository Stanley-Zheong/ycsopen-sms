# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-03-001 | ycs.sms.crypto-storage-bootstrap.* | crypto-storage-bootstrap | V1200 | V1 | expand | Revert application readers/writers to the previous compatible path while leaving V1200 additive objects intact; if migrated data requires recovery, stop new claims and restore the preflight-verified encrypted snapshot, then resume with a forward-fix migration. | - |

V1200 is an umbrella claim for Phase-owned metadata only. It does not claim or modify legacy V1 DDL.
