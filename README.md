# ycsopen-sms

优创硕安短信平台系统（YCSAN-SMS）的开放源码实现（重新起草，非对内部 `ycsan-sms` 代码库的直接复制）。
多租户、多通道、可运营的短信中台——上游对接多家运营商/通道商（CMPP/SGIP/SMGP/HTTP），
下游为企业客户提供 HTTP API 与 CMPP 两种接入方式；支持预付费/后付费计费、签名模板审核、
黑名单与内容审核路由、机构全生命周期管理。

## ⚠️ 商业授权须知

本项目开放源码，但采用的是 **Apache License 2.0 with Commons Clause License Condition v1.0**，
属于源码可用（Source-Available）许可，并非不受商业限制的开源协议。

- **允许**：个人学习、技术交流、修改，以及个人或企业内部非销售性质的免费部署与使用。
- **禁止**：将本项目或其衍生版本向第三方交付、打包销售、有偿外包开发，或作为收费商业代运营服务的主要组成部分。

如需用于任何第三方商业交付，必须先取得项目版权方的书面商业授权。完整条款见
[`LICENSE.md`](LICENSE.md)。

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
├── docs/            全局文档：使用手册、PRD、PRD 检视记录
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

## 安装、部署与使用

完整的本地安装、配置项、管理员初始化、JAR/静态资源部署、Nginx、systemd、升级和排障说明见
[`docs/使用手册.md`](docs/使用手册.md)。下面是源码开发环境的最短可执行路径。

### 1. 环境要求

- JDK 21、Maven 3.9+
- MySQL 8、Redis 6+
- Node.js 20+、npm 10+
- Git；生成开发密钥时需要 OpenSSL

### 2. 初始化数据库与后端

```bash
git clone https://github.com/Stanley-Zheong/ycsopen-sms.git
cd ycsopen-sms/core

./tools/init-db.sh
export JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
export FIELD_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"

mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`init-db.sh` 使用本机 MySQL root 免密或 socket 登录方式，创建 `ycsopen_sms` 数据库及
`ycsopen/ycsopen` 开发账号。root 需要密码或 MySQL 位于其他主机时，请使用手册中的手工 SQL。

后端启动后验证：

```bash
curl -fsS http://localhost:8080/actuator/health
```

### 3. 初始化本地管理员

Flyway 当前只建表，不会自动植入应用账号。首次本地启动后，按
[`使用手册的管理员初始化步骤`](docs/使用手册.md#初始化本地管理员账号)执行一次 SQL，得到以下开发登录凭据：

| 用途 | 用户名 | 密码 | 说明 |
|---|---|---|---|
| MySQL 应用账号 | `ycsopen` | `ycsopen` | 仅限本地开发，来自 `application-dev.yml` |
| Web 控制台管理员 | `admin` | `Admin@123456` | 仅在执行手册中的初始化 SQL 后存在 |

以上密码严禁用于对外环境。仓库没有生产默认账号，也没有自动植入生产密码。

### 4. 启动前端

另开终端：

```bash
cd ycsopen-sms/web
npm ci
npm test
npm run dev
```

访问 <http://localhost:5173/login>。Vite 会把 `/api` 代理到
<http://localhost:8080>。平台角色进入 `/admin`，机构角色进入 `/tenant`。

### 5. 构建部署产物

```bash
cd core
mvn clean package
# 后端产物：core/target/ycsopen-sms-core.jar

cd ../web
npm ci
npm run build
# 前端产物：web/dist/
```

部署时使用外部 MySQL/Redis/KMS、显式设置所有密钥，将后端 JAR 作为受限系统服务运行，
并由 Nginx 托管 `web/dist/`、把 `/api/` 同源代理到后端。可直接复制的配置示例见
[`docs/使用手册.md`](docs/使用手册.md#部署后端-jar)。

### 6. 当前可用范围

登录后可查看已有真实页面和占位导航。控制台登录、机构注册/试用、通道基础管理、HTTP 单条发送、
路由/预付费核心服务及投诉占比看板已有实现；其他模块的真实完成边界以
[`core/docs/ROADMAP.md`](core/docs/ROADMAP.md)和
[`web/docs/ROADMAP.md`](web/docs/ROADMAP.md)为准。

> 当前控制台 JWT 过滤器与开放 API HMAC 验签仍有明确未完成项。不要把当前代码直接暴露到生产网络。

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

## 文档

- [`docs/使用手册.md`](docs/使用手册.md)：安装、部署、配置、默认开发凭据、使用、升级与排障。
- [`core/docs/API.md`](core/docs/API.md)：当前后端 API。
- [`core/docs/ARCHITECTURE.md`](core/docs/ARCHITECTURE.md)：后端结构与核心设计。
- [`core/docs/ROADMAP.md`](core/docs/ROADMAP.md)：后端真实实现边界。
- [`web/docs/ROADMAP.md`](web/docs/ROADMAP.md)：前端真实页面与占位页面边界。
- [`docs/PRD.md`](docs/PRD.md)：产品需求。

## License

本项目采用带有 Commons Clause License Condition v1.0 附加条款的 Apache License 2.0。
允许学习、修改和内部免费自用，禁止未经书面授权的第三方商业交付、销售、收费外包或商业代运营。
详见 [`LICENSE.md`](LICENSE.md)。
