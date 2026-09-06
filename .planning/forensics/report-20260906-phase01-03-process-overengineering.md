# Phase 1～3 流程与过度工程复盘

**生成日期：** 2026-09-06
**问题：** 项目启动后 Phase 1～3 推进异常漫长。审计整体方案、阶段拆解、实施、验证与 review 是否存在过度设计，并解释实际时间与工作量消耗。
**证据边界：** 主代理与一个独立 subagent 分别检查远端 Phase 1～3 分支、交付标签、Git 历史、`.planning/`、测试、review、iteration 和 GitHub Actions。复盘只记录已发生时间，不做未来工期估算。

## 结论

存在显著过度工程，而且是系统性的，不是某一个测试跑得慢。

最主要的问题是把“每阶段可验收”扩张成了一套审计级、内容寻址的证据和交付系统：源码全集哈希、逐义务证据文件、证据 schema、自验证 validator、review 文件哈希、annotated tag、远端 PR/check/actor/target-tree 重放。任何小改动都会触发“subject 重封 → 全量验证 → GSD 重审 → Claude 重审 → 摘要与哈希重写”。这套证明系统本身成为了第二个产品。

Phase 3 同时包含数据库字段加密、盲索引、PKCS#11 密钥生命周期、对象存储、上传会话、访问能力、存量迁移、加密快照恢复、安全日志和泄漏扫描。它不是一个聚焦模块，而是至少四个可独立验收的安全子系统。30 个 plan 没有解决这个问题，只是把一个过大的 phase 变成 30 个仍共享状态和最终 seal 的微型阶段。

## 可核证规模与时间

| 项目 | Phase 1 | Phase 2 | Phase 3 |
| --- | ---: | ---: | ---: |
| Phase 内 plan | 13 | 4 | 30 |
| Plan summary | 13 | 4 | 30 |
| Iteration 记录 | 40 | 10 | 47 |
| Phase 提交 | 5 个 PR 提交 | 2 | 95 |
| 变更文件 | 135 | 134 | 324 |
| 净新增行 | 25,215 | 29,731 | 64,762 |
| Review 强度 | 最终 GSD Overall Attempt 8 | 一轮 GSD 修正与一轮 Claude 收敛 | GSD Round 15、Claude Attempt 12 |

前三阶段合计产生 47 份 plan、47 份 plan summary、110 个 task、97 条 iteration 和 279 个 phase 文件。Phase 3 的 95 个提交中有 45 个是文档提交。

Git 只能证明从初始提交 `d620906`（2026-08-29 20:40）到 Phase 3 最终提交 `1b19749`（2026-09-06 08:28）的 7 天 11 小时 48 分。用户感知的约两周还包含 Git 无法记录的停顿与会话等待，因此不能用提交时间伪造精确活跃工时。

可识别的主要窗口如下：

- Phase 1 首个阶段提交到交付提交：9 小时 20 分；更早的规划和 Chrome 方案折返被压入首个大提交，Git 不能完整计时。
- Phase 2 从 Phase 1 交付到完成提交：3 小时 20 分。它不是主要墙钟瓶颈，但留下大量维护资产。
- Phase 3 规划/准入：10 小时 39 分，写产品代码前已经产生 23 个提交。
- Phase 3 授权实施到第一次 root seal：14 小时 9 分。
- Phase 3 两个无提交间隙：2 天 27 分、2 天 9 小时 2 分；`ITERATIONS.md` 的 I-036 明确记录了 token/usage 限制，合计约 4 天 9 小时。
- Phase 3 最终 PR/CI 收口：1 小时 38 分。

排除上述长停顿后，前三阶段仍有接近 40 小时的可见活跃推进窗口。当前目标系统记录还显示累计 token 使用量达到约 367 万，这与文档、review、重封链路的规模一致。

GitHub runner 不是主因。Phase 1 与 Phase 3 的十次 CI 总运行时间约 1,338 秒，即 22 分 18 秒；Phase 3 五次 CI 合计约 8 分钟。真正昂贵的是每次失败后的调查、subject 重封、完整证据重建与两套独立 review。

## 分阶段判断

### Phase 1：基础能力有价值，但证明系统严重超配

应保留：

- Maven、前端、Playwright 的统一可执行入口。
- 一个本机 Chrome 的真实 smoke。
- 真实 MySQL/Redis 的基础集成检查。
- UI `data-testid` 与路由/测试之间的简单漂移检查。

过度部分：

- 为 7 个基础义务建立 194-input subject、逐文件角色/模式/哈希、exact-seven evidence、review digest 与远端 target-tree 重算。
- `verification-evidence.rb` 1,438 行、phase runner 1,424 行、delivery attestation 976 行，以及多套 validator 的 destructive self-test；仅 `.planning/tools` 净新增 6,304 行，其他脚本约 3,300 行。
- 为浏览器来源设计 Safari/Edge/Chrome 双版本、Chrome for Testing 下载、driver、archive symlink/TOCTOU 信任链，经过 I-004～I-019 多轮折返后才采用用户要求的本机 Chrome。
- 13 份 plan 配 13 份平均百行以上的 summary，把一次基础设施交付变成 13 次微型闭环。

这是最高置信度的过度工程。最终真正需要的浏览器事实只是一条固定路径、本机版本和一次 Playwright 运行。

### Phase 2：产出速度尚可，但证据粒度过细

应保留：

- 用户明确要求的完整页面、元素、动作、状态、权限和 `data-testid` 清单。
- Pencil 视觉源、HTML 可交互原型和统一 token/component contract。
- 安装版 Chrome 的真实原型交互检查。

过度部分：

- 82 routes、186 selectors 被扩展成 83 个 literal Playwright case、83 个逐义务 runner、80 个独立 JSON 与 3 个 PNG 证据。
- Phase 目录含 96 个 evidence 文件，JSON 共约 13,836 行；Playwright report 本身超过 4,000 行并被长期封存。
- 逐义务文件和校验和没有增加新的用户行为覆盖；一个 machine-readable manifest 加一次真实 Playwright report 已能证明同一事实。

Phase 2 不是耗时主因，但它把未来每次 UI 变更的同步成本预先放大了。

### Phase 3：安全实现有真实价值，但 phase 设计失焦

应保留的高风险实现与测试：

- AEAD/AAD、nonce、tamper 和上下文绑定。
- 密钥与数据分离、目的域隔离、轮换和 retirement race 测试。
- 私有对象存储、授权、租户隔离和无明文验证。
- 存量数据迁移的幂等、恢复、回滚和真实 MySQL 测试。
- 真实 MySQL、MinIO、SoftHSM 集成，以及生产 Spring/ServiceLoader composition smoke。

这些不是形式主义。review 实际抓到了 AAD/header、nonce ownership、snapshot restore、对象分配上限、重复 Spring bean、SQL predecessor guard、key retirement race 等真实缺陷。

但以下部分明显过度或顺序错误：

- 一个 phase 同时新增约 20,574 行 production Java、1,238 行 resources、23,119 行测试、5,272 行 planning validator 和 11,146 行 phase artifacts。
- 新增安全代码横跨 key、migration、object、persistence、logging、envelope、config；最大的 production 类达到约 1,100～1,260 行，与“单一聚焦模块、小单元、人类可读”目标相冲突。
- PRD 要求加密存储、KMS/HSM、轮换和失败安全迁移，但实现进一步扩张到 Ed25519 双 manifest、root-owned MySQL client 路径/ACL/inode/digest 校验、应用内加密 MySQL snapshot 命令、source-locked SoftHSM 构建和 14-lane 内容寻址 evidence。这里混入了高保证部署/供应链系统的要求。
- 第一次 root PASS 后，review 仍发现 routes、migration provider、active-key resolution、S3 ACL、capability consumption、delete/claim serialization 等生产不可达或不完整；随后又新增约 4,894 行 production 和 5,487 行测试。说明计划和单元测试先完成，纵向 production composition 太晚。
- 最后才在真实 Ubuntu/PR ref 上跑交付闭环，连续暴露旧 Phase 1 exact-input 耦合、`/tmp` 权限假设、Ubuntu 缺少 `rg`、synthetic merge ref 与未提交 result JSON。四次失败都是流程/环境问题，不是密码学行为。
- 最终 14 lanes、59 evidence fixtures、109 delivery cases（105 destructive）、316-input subject，再加 GSD Round 15 和 Claude Attempt 12，使小改动的验证成本远高于改动本身。

## 异常检测

### 1. Stuck loop — HIGH

Phase 3 `ITERATIONS.md` 被 28 个提交触碰，`STATE.md` 被 23 个提交触碰，`ROADMAP.md` 被 11 个提交触碰；GSD 到 Round 15、Claude 到 Attempt 12。最后三次简单 CI 修正都重新生成 root/evidence/review/summary 哈希。这是明确的循环性返工。

### 2. Scope drift — HIGH

Phase 1 从验证入口扩张到浏览器供应链与远端内容寻址交付；Phase 3 从“crypto storage bootstrap”扩张成多个安全与运维子系统。Phase 3 的 30 个 plan 是过大 scope 的症状，不是有效拆分。

### 3. Artifact explosion — HIGH

前三阶段 47 plans + 47 summaries + 97 iterations。用户要求每个 phase 记录 spec、intent、design、decision 和过程，并未要求每个 plan 再生成一份百行 summary 和一个 docs-only commit。

### 4. Late integration — HIGH

真实 production composition 和 CI 语义均在大量单元/文档 seal 后验证。I-028、I-035 以及 Phase 3 最后四次 CI 给出了直接证据。

### 5. State inconsistency — HIGH

最终 Phase 3 分支的 `.planning/STATE.md` 仍停在 2026-09-01 的 Phase 3 中途状态；`.planning/ROADMAP.md` 顶部 Phase 1～3 仍未勾选，Phase 1 的 01-10～01-12 仍显示未完成；Phase 3 `TODO.md` 仍保留一个物理 `[ ]`，再由“reserved delivery”协议解释为 effective empty。大量形式化证据没有维护好最基础的项目状态。

### 6. Missing/inconsistent delivery artifacts — MEDIUM

Phase 2 没有根 `SUMMARY.md`、delivery tag、PR 或 CI 运行；Phase 1/3 使用了另一套交付协议。Phase 2 的实现义务虽通过，但三阶段的完成定义不一致。

### 7. Crash/interruption — MEDIUM

两个长停顿有明确 token/usage 限制记录，不属于无解释的代码阻塞；但 367 万级 token 消耗本身是过量上下文、文档和多轮 reviewer 的结果。没有证据表明 Chrome 下载或 CI runner 占据主要时间。

### 8. Partial-plan drift — NOT FOUND

47 个 plan 都有对应 summary；问题不是缺少 plan 闭环，而是闭环数量和粒度本身不合理。

## 根因

1. `.planning/ROADMAP.md` 和 `EXECUTION-STANDARD.md` 把同一套高保证 artifact、entry、hash evidence、review 和 tag 协议强制应用于 56 个 phase，没有风险分级。
2. “TODO 为空”被误解成必须证明 TODO、review、commit、tag 和 proof 本身，最终产生递归的 reserved-delivery 例外；这违反了用户要求的简单物理 TODO 口径。
3. Phase 在架构边界未稳定前就被细化成大量横向 plan，产生了假精确；真正的纵向生产链路和组合测试被后置。
4. Review 被用作持续设计器，而不是 entry 的 scope/contract 检查和 final candidate 的缺陷检查。每次 review 引入新的设计要求，又扩大 subject 和下一轮 review 面积。
5. 没有复杂度熔断器。I-025 已承认 per-plan full-suite、entry gate 和 review ceremony 不是最短路径，但只调整了局部执行节奏，没有删除根协议。
6. “不做时间估算”被错误扩展成“不监控执行效率”。不估算未来工期，不等于不能检查返工次数、文档/代码比例、review 轮次、CI 是否过晚和 TODO 是否真实下降。

## 应停止、合并和保留的内容

### 停止新增

- 普通 phase 的全源码 194/316-input hash seal。
- 每个义务一个独立 JSON、每个 plan 一份独立 summary。
- 每个 phase 的 annotated delivery tag、PR actor/target-tree/remote evidence 自证；里程碑发布时再做。
- 因 docs/review 文件变化而重跑 real-service 和全量 review。
- 为非阻塞 WARNING 无限增加 review attempt。
- 把 generated `target/` 测试结果当长期源码提交。

### 合并

- `INTENT/CONTEXT/DESIGN` 可保留用户要求的章节，但允许一个简短 phase contract 承载；不重复同一事实。
- 30 份 Phase 3 summary 合并为一个 phase summary；历史由 Git 和短 `ITERATIONS.md` 保存。
- GSD/Claude review 文档只保留当前 verdict、未决问题与上一轮修正，不复制完整历史。
- Phase 2 的 83 个证据文件合并为一个 obligation-to-test manifest 和一次 Playwright report。

### 保留

- 物理可见且真正为空的 Phase TODO。
- SPEC、简明 plan、关键 decisions、material iterations、TEST-MATRIX。
- UI 完整 element/action/state/permission/`data-testid` registry。
- 一次真实 Chrome Playwright。
- 高风险安全模块的 tamper、concurrency、recovery 和真实服务测试。
- 一次独立 GSD final review、一次 Claude final review。
- 用户要求的每个 phase 完成后一次 commit/push。

## 后续阶段硬规则

1. **一 phase 一个可独立验收模块。** 出现第二个独立外部系统、超过 5 个 cohesive task、超过约 25 个 production 文件，或需要两套不同故障恢复模型时，先拆 phase，不增加 plan 数量掩盖问题。
2. **风险分级。** 普通 CRUD/UI 只用 unit/API/Playwright；支付、安全、迁移才增加 threat model、并发、恢复和 real-service。禁止所有 phase 套用 crypto-grade evidence。
3. **Plan 上限。** 默认 1～5 个可交付 slice；不得再出现 13/30 个 plan。超过上限必须重新划分 phase，而不是继续分 plan。
4. **Review 上限。** Entry 只做一次 scope/API/dependency/acceptance 检查；完整 candidate 各做一次 GSD 和 Claude。只为 BLOCKER/HIGH 做一次修正复核。第二次复核仍增加 BLOCKER/HIGH 时停止并重新切 scope。
5. **先纵向、后横向。** 第一个 slice 必须贯通 production composition 和最小真实服务 smoke；不能只凭 unit test、fixture 或静态 validator 标记 plan 完成。
6. **CI 前置。** 第一个可编译/可启动骨架就建立 draft PR 并跑真实 Ubuntu；CI PASS 后才生成最终 evidence。禁止完成所有本地 seal 后才验证 runner、工具和 PR ref。
7. **证据复用标准工具。** Maven/JUnit、Playwright report、GitHub check 本身就是主要证据；只保留一个 obligation → test/result manifest，不再 hash 全部源码与 review 文档。
8. **TODO 无例外。** Phase 完成时文件中不得存在 `- [ ]`；PR/tag 等会形成自引用的外部动作不放进 Phase TODO，也不允许 reserved/effective-empty 语义。
9. **完成阶段不阻塞后续代码。** 已交付 phase 的 source seal 只在其交付点有意义；后续分支只运行其可复用 contract tests，不验证旧文件全集仍存在。
10. **文档只记录新信息。** 每个 phase 只维护一套当前 spec/design/decision/iteration/summary；禁止每个 plan docs-only commit。完成时一次性同步 `STATE.md` 与 `ROADMAP.md`。
11. **复杂度熔断。** 任一条件出现立即停止并做 scope/process review：review attempt 超过 2；同一 meta artifact 被触碰超过 3 次；docs/review commit 接近或超过 product commit；尚未跑真实 CI 就开始 seal；root PASS 后仍新增大块 production wiring。
12. **集成基线一致。** 每个完成 phase 进入明确的 integration/main 基线，或明确声明 stacked PR base；禁止长期保留 Phase 1/3 OPEN PR、Phase 2 无 PR/tag 的混合状态继续堆叠。

## 最终判断

56 个 roadmap phase 本身不是核心问题；如果每个 phase 都复制 Phase 1/3 的协议，项目必然失控。前三阶段实际形成了 47 个内部 plan 闭环，这已经接近用户担心的“50 个 phase”。

下一步不应直接沿用当前 `EXECUTION-STANDARD.md` 开始 Phase 4。应先精简全局 gate、取消递归证据/交付自证、修正 Phase 1～3 的状态记录，并按上述规则重新检查 Phase 4 及后续 phase 的边界。

## 独立审计一致性

独立 subagent 在不知道主代理最终判断的前提下，从远端 Phase 1～3 分支和 delivery tags 重新取证，得到同一结论：最大浪费依次是内容寻址 evidence/重封、Phase 3 多子系统捆绑、review 放大、文档爆炸、浏览器方案折返和 CI 后置。两份审计对关键计数、时间窗口和应保留的安全测试结论一致。

---

本报告由 `$gsd-forensics` 生成。仓库路径已使用相对路径，未记录凭据或运行时敏感值。
