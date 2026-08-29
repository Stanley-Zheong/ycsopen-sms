# ycsopen-sms

优创硕安短信平台系统（YCSAN-SMS）的开源实现（重新起草，非对内部 `ycsan-sms` 代码库的直接复制）。
多租户、多通道、可运营的短信中台——上游对接多家运营商/通道商（CMPP/SGIP/SMGP/HTTP），
下游为企业客户提供 HTTP API 与 CMPP 两种接入方式；支持预付费/后付费计费、签名模板审核、
黑名单与内容审核路由、机构全生命周期管理。

> **实现程度请先读这句话**：本仓库是一次"完整实现"尝试的**第一阶段成果**，不是全功能
> 生产系统。核心的路由引擎（黑名单/内容审核/频控/通道选择）、预付费计费、机构注册/试用、
> HTTP 发送 API、以及本次新增的"通道/机构月度投诉占比看板"是**真实实现并有测试覆盖**的
> （见下方"验证状态"）；CMPP 协议、大部分详单查询、计费账务的后半段、告警引擎等模块
> **仅有数据库表结构和骨架，业务逻辑未写**。完整的、诚实的进度清单见
> [`core/docs/ROADMAP.md`](core/docs/ROADMAP.md) 与 [`web/docs/ROADMAP.md`](web/docs/ROADMAP.md)。

## 需求依据

- [`docs/PRD.md`](docs/PRD.md) —— 完整产品需求文档（v1.3.0），所有代码注释里的 `F-x.x`
  编号均指向本文档第 5 章的功能编号。
- [`docs/PRD_REVIEW.md`](docs/PRD_REVIEW.md) —— 开工前对 PRD 做的系统性检视记录
  （发现的逻辑缺口、循环依赖、以及本次实现如何应对）。

## 项目结构

```
ycsopen-sms/
├── docs/            全局文档：PRD、PRD 检视记录
├── skill/           预留给 jarvis（本仓库所有者的自动编程框架）的技能定义，见 skill/README.md
├── core/            后端：Spring Boot 3 + Java 21 + MySQL 8
│   ├── src/main     业务代码（按 F 模块分包，见 core/docs/ARCHITECTURE.md）
│   ├── src/test     单元测试（JUnit 5 + Mockito + AssertJ）
│   ├── test/        集成/压测脚本存放处（当前为空目录，占位）
│   ├── docs/        ARCHITECTURE.md / API.md / ROADMAP.md
│   ├── data/        种子/参考数据 CSV（号段库、敏感词库样例等）
│   ├── tools/       本地开发脚本（建库、起服务）
│   └── lib/         第三方/供应商 jar 存放处（当前为空，见 lib/README.md）
└── web/             前端：React 18 + TypeScript + Vite（平台管理后台 + 机构端一套应用）
    ├── src/         页面/组件/路由/状态管理
    ├── test/unit    Vitest 单元测试；test/e2e 预留（当前为空）
    ├── docs/        ROADMAP.md
    ├── data/        mock 数据存放处（当前为空）
    ├── tools/       前端配套脚本存放处（当前为空）
    └── lib/         跨组件纯函数工具库（`@lib/*` 别名）
```

`core` 与 `web` 都在各自目录下有完整的 `src/test/docs/skill/data/tools/lib` 七件套——
这是应用方明确要求的目录规范，其中部分目录（如两边的 `skill/`、`web/tools/`、`web/data/`）
目前只有说明性 README，因为暂无实际内容可填，没有为了"看起来完整"塞入无意义的占位文件。

## 快速开始

### core（需要 JDK 21 + MySQL 8 + Redis）

```bash
cd core
./tools/init-db.sh          # 建库建账号
mvn test                    # 跑单元测试，验证环境正常（不需要 MySQL/Redis）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### web（需要 Node 20+）

```bash
cd web
npm install
npm run build   # 类型检查 + 生产构建
npm test        # Vitest 单元测试
npm run dev     # 本地开发服务器，代理 /api 到 http://localhost:8080
```

## 验证状态（截至 2026-08-29，本仓库首次提交）

| 检查 | 结果 |
|---|---|
| `cd core && mvn compile` | ✅ 通过 |
| `cd core && mvn test` | ✅ 18/18 测试通过 |
| `cd web && npx tsc -b` | ✅ 通过 |
| `cd web && npm run build` | ✅ 通过（生产构建产物 ~104KB gzip） |
| `cd web && npx vitest run` | ✅ 4/4 测试通过 |

以上是实际执行过的命令结果，不是"应该能跑"的推测。

## 技术栈

- **core**：Spring Boot 3.3 / Java 21 / MySQL 8（Flyway 管理 schema）/ Redis（限流与频控计数）/
  JWT + HMAC-SHA256（控制台会话 / 开放 API 双轨鉴权）
- **web**：React 18 / TypeScript / Vite / React Router / TanStack Query / Zustand / Axios

## 参考

- 内部原型实现：`Documents/codebases/ycsan-sms/`（Spring Boot 3 + Java 21 后端，
  React 双前端）——本仓库的技术栈选型继承自该实现，但代码是本次重新编写，非直接复制。
- 开源参考项目（仅参考功能模块结构，未直接复用代码）：
  [itcastopen/itcast-sms-web](https://github.com/itcastopen/itcast-sms-web)、
  [liuyanning/sms_platform](https://github.com/liuyanning/sms_platform)

## License

暂未附加开源许可证（版权保留），如需开源分发协议请后续按需补充 `LICENSE` 文件。
