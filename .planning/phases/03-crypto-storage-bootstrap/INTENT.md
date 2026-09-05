# Intent

## Status

Open

## Goal

将数据库保护字段、证据对象、密钥生命周期、存量迁移和日志安全收敛到可执行、可恢复、可审计的单一安全边界，并以真实 MySQL、SoftHSM 和 MinIO 结果证明该边界。

## Deliverables

- 完整且失败关闭的保护数据清单、V1200 owner-range 选择器和 exact-four evidence schema。
- 严格 `YCSE/v1` codec、opaque key/blind-index ports、SunPKCS11 production adapter 和 production startup verifier。
- 当前消息、tenant registration、auth/API-key/tenant analytics/blacklist reader-writer 的显式保护边界。
- V1200 expand-only metadata、签名 writer/snapshot preflight、可恢复 migration runner 和 blind-index cutover/scrub。
- 私有 ciphertext-only S3 adapter、staged registration objects、deny-by-default capability access 和真实 MinIO 验证。
- 类型化日志、跨 DB/object/log/report/evidence 的 canary scanner、rotation/recovery/fault suites 和 exact-four evidence producer。
- 与运行时一致的 `core/docs/API.md`、`docs/使用手册.md`，以及完整的 phase spec/design/decision/iteration/review/evidence 文档。

## Execution slices

Plans `03-01` through `03-23` are the canonical executable decomposition. They proceed from inventory/evidence/service prerequisites through envelope/key/persistence/migration/object/logging/lifecycle implementation, then compose real-boundary verification and independent handoff. No plan may claim another module's business workflow or schema ownership.

## Verification

Planned executable boundary:

```bash
mvn -f core/pom.xml test
mvn -f core/pom.xml -Pphase03-integration test
./scripts/verify-phase-03 --all --result-root core/target/phase03/results
/usr/bin/env ruby .planning/tools/validate-phase-03-crypto-evidence.rb --phase-dir .planning/phases/03-crypto-storage-bootstrap --require-owner crypto-storage-bootstrap
/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 03 --package crypto-storage-bootstrap --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/03-crypto-storage-bootstrap/ENTRY-REVIEW.md --entry-evidence .planning/phases/03-crypto-storage-bootstrap/ENTRY-EVIDENCE.md
```

结果必须区分 deterministic adapter、真实 MySQL、真实 SoftHSM 和真实 MinIO。任一真实边界缺失时，对应 TODO 保持开放。
