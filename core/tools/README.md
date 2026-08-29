# core/tools

本地开发辅助脚本，均为 shell 脚本，非生产部署工具（生产部署见 docs/DEPLOYMENT.md，当前为 TODO）。

| 脚本 | 用途 |
|---|---|
| `init-db.sh` | 创建本地 MySQL 数据库与账号；表结构由 Flyway 在应用启动时按 `src/main/resources/db/migration/` 自动建立 |
| `run-dev.sh` | 依次执行 `init-db.sh` 与 `mvn spring-boot:run`，一键起本地开发环境 |

计划中但尚未实现（见 ../docs/ROADMAP.md）：压测脚本（对应 PRD 2.3 节 1000 TPS 验收）、CMPP 网关联调脚本。
