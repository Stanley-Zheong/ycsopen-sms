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

> **实现程度请先读这句话**：本仓库按 GSD Phase 持续实施，当前不是全功能生产系统。
> 已完成项必须同时有代码、测试和 Phase 验证证据；未进入或未闭环的模块不能按数据库表或页面
> 占位算完成。CMPP 协议、大部分详单查询、计费账务后半段、告警通知等仍在后续范围。完整边界见
> [`core/docs/ROADMAP.md`](core/docs/ROADMAP.md) 与 [`web/docs/ROADMAP.md`](web/docs/ROADMAP.md)。

## 需求依据

- [`docs/PRD.md`](docs/PRD.md) —— 完整产品需求文档（v1.3.0），所有代码注释里的 `F-x.x`
  编号均指向本文档第 5 章的功能编号。
- [`docs/PRD_REVIEW.md`](docs/PRD_REVIEW.md) —— 开工前对 PRD 做的系统性检视记录
  （发现的逻辑缺口、循环依赖、以及本次实现如何应对）。

## 项目结构

```
ycsopen-sms/
├── .planning/       GSD 路线图、原子 PRD obligation、阶段门禁、UI/Playwright 合同与验证工具
├── docs/            全局文档：使用手册、PRD、PRD 检视记录
├── skill/           历史目录规范中的预留占位，见 skill/README.md
├── skills/          当前可调用的仓库工程、UI 与测试工作流技能，见 skills/README.md
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
根目录复数形式的 `skills/` 是实际 repo-local skill 目录，与上述历史占位目录职责不同。
GitHub issue 中的 Jarvis 触发、分支和 PR 交付边界以根目录 `AGENTS.md` 为准。

## 安装、部署与使用

完整的本地安装、配置项、管理员初始化、JAR/静态资源部署、Nginx、systemd、升级和排障说明见
[`docs/使用手册.md`](docs/使用手册.md)。下面是源码开发环境的最短可执行路径。

### 1. 环境要求

- JDK 21、Maven 3.9+
- MySQL 8、Redis 6+
- Node.js 20+、npm 10+
- 仅支持本机当前安装的桌面版 Google Chrome；自动化验证直接使用标准安装路径中的当前版本，不下载额外浏览器。Edge、Safari、Firefox、IE、Chromium 等其他浏览器不在支持范围内
- Git；生成开发密钥时需要 OpenSSL

### 2. 初始化数据库与后端

```bash
git clone https://github.com/Stanley-Zheong/ycsopen-sms.git
cd ycsopen-sms/core

./tools/init-db.sh
export JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"

mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

字段加密和私有对象存储默认关闭，因此上述开发启动不需要字段密钥。这个模式只适合查看和开发
不涉及受保护数据的功能；短信提交中的受保护手机号处理，以及机构注册材料上传/读取会失败关闭。
启用受保护流程不能使用单个环境变量密钥，必须按
[`使用手册的生产加密存储配置`](docs/使用手册.md#启用生产加密存储)准备 PKCS#11、五种用途密钥、
数据库引用、加密快照目录和私有对象存储。

`init-db.sh` 使用本机 MySQL root 免密或 socket 登录方式，创建 `ycsopen_sms` 数据库、
`ycsopen/ycsopen` 应用账号和 `ycsopen_migrator/ycsopen_migrator` Flyway 账号。应用账号没有
DDL 权限；Flyway 迁移后按表授予运行权限，审计表仅授予 `SELECT/INSERT`。root 需要密码或
MySQL 位于其他主机时，请使用手册中的手工 SQL。
MySQL 开启 binary log 时，执行 V1500 前必须由 DBA 启用 `log_bin_trust_function_creators`；
脚本和 Flyway callback 会预检该条件，完整命令见使用手册。

后端启动后验证：

```bash
curl -fsS http://localhost:8080/actuator/health
```

### 3. 本地管理员

Flyway 在首次以 `dev` profile 启动时会自动创建 Web 控制台管理员，开发登录凭据如下：

| 用途 | 用户名 | 密码 | 说明 |
|---|---|---|---|
| MySQL 应用账号 | `ycsopen` | `ycsopen` | 仅限本地开发，来自 `application-dev.yml` |
| MySQL 迁移账号 | `ycsopen_migrator` | `ycsopen_migrator` | 仅限本地开发，只由 Flyway 使用 |
| Web 控制台管理员 | `admin` | `Admin@123456` | 仅 `dev` profile 自动创建，角色为 `ADMIN` |

以上默认密码严禁直接用于对外环境。非 `dev` profile 不会自动创建默认账号。

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

部署时使用外部 MySQL/Redis、PKCS#11 密钥设备和私有对象存储，将后端 JAR 作为受限系统服务运行，
并由 Nginx 托管 `web/dist/`、把 `/api/` 同源代理到后端。可直接复制的配置示例见
[`docs/使用手册.md`](docs/使用手册.md#部署后端-jar)。

### 6. Docker Compose 开发发布

仓库根目录的 `compose.yaml` 会从当前检出的源码构建 Core 和 Web，不依赖预先存在的应用镜像。
首次启动会创建 MySQL 卷、迁移账号并执行全部 Flyway migration 和 `dev` 种子：

```bash
export BUILD_COMMIT="$(git rev-parse HEAD)"
docker compose up -d --build
```

默认访问地址为 <http://localhost:5173/login>，Core 健康检查为
<http://localhost:8080/actuator/health>。可通过 `YCSOPEN_WEB_PORT` 和
`YCSOPEN_CORE_PORT` 修改宿主机端口。服务默认只绑定 `127.0.0.1`；确需其他地址时，
必须显式设置 `YCSOPEN_BIND_ADDRESS` 并自行配置防火墙和非开发凭据。例如：

```bash
YCSOPEN_WEB_PORT=15173 YCSOPEN_CORE_PORT=18080 docker compose up -d --build
```

例如，经过风险评估后显式监听所有 IPv4 网卡：

```bash
YCSOPEN_BIND_ADDRESS=0.0.0.0 docker compose up -d --build
```

Web 页面中的 `meta[name="ycsopen-build-commit"]` 与 Core 的 `/actuator/info`
都会返回 `BUILD_COMMIT`。同一 Compose 项目再次执行构建会复用命名卷，Flyway
只增量升级，并保留已有账号和业务数据。默认数据库、JWT 和管理员凭据只供本机开发，
不得用于共享或公网环境；结束后可用 `docker compose down` 保留数据，或明确执行
`docker compose down -v` 删除本项目卷。

发布验收要求本机标准路径中已有 Google Chrome，并会创建和清理两个不可由调用者改名的
独立 Compose 项目：一个验证全新卷，另一个先创建 Issue #60 之前的 Flyway location 基线，
再强制重建当前 Core/Web 验证增量升级、业务数据保留和重复启动：

```bash
BUILD_COMMIT="$(git rev-parse HEAD)" ./scripts/verify-docker-release
```

### 7. 当前可用范围

登录后可查看已有真实页面和占位导航。控制台登录、机构注册/试用、通道基础管理、HTTP 单条发送、
路由/预付费核心服务、投诉占比看板，以及带类型校验、版本历史、热加载和回滚的系统配置已有实现；
管理员可在 `/admin/system/configuration` 操作当前登记的三项运行时配置，权限和运维边界见
[`使用手册的系统配置章节`](docs/使用手册.md#系统配置管理)。其他模块的真实完成边界以
[`core/docs/ROADMAP.md`](core/docs/ROADMAP.md)和
[`web/docs/ROADMAP.md`](web/docs/ROADMAP.md)为准。

> 当前控制台 JWT 过滤器与开放 API HMAC 验签仍有明确未完成项。不要把当前代码直接暴露到生产网络。

## 验证状态

| 检查 | 结果 |
|---|---|
| `mvn -f core/pom.xml test` | ✅ 499/499 通过，另有 22 项环境条件跳过 |
| `npm --prefix web test -- --run` | ✅ 37/37 通过 |
| `npm --prefix web run lint` | ✅ 通过 |
| `npm --prefix web run build` | ✅ 通过 |
| 本机 Google Chrome Phase 07 真实服务 Playwright | ✅ 3/3 通过，无平台 API 请求替身 |
| Phase 07 真实 MySQL | ✅ V1600/V1601、历史保护、版本并发和回滚通过 |

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
- [`docs/项目规划索引.md`](docs/项目规划索引.md)：GSD 路线图、各 Phase 计划、TODO、决策、迭代、检查与证据的可见入口。
- [`core/docs/API.md`](core/docs/API.md)：当前后端 API。
- [`core/docs/ARCHITECTURE.md`](core/docs/ARCHITECTURE.md)：后端结构与核心设计。
- [`core/docs/ROADMAP.md`](core/docs/ROADMAP.md)：后端真实实现边界。
- [`web/docs/ROADMAP.md`](web/docs/ROADMAP.md)：前端真实页面与占位页面边界。
- [`docs/PRD.md`](docs/PRD.md)：产品需求。
- [`skills/README.md`](skills/README.md)：仓库工程、UI 与测试技能目录及迁移审计结论。

## License

本项目采用带有 Commons Clause License Condition v1.0 附加条款的 Apache License 2.0。
允许学习、修改和内部免费自用，禁止未经书面授权的第三方商业交付、销售、收费外包或商业代运营。
详见 [`LICENSE.md`](LICENSE.md)。
