# ycsopen-sms-core

短信平台后端服务。Spring Boot 3.3 + Java 21 + MySQL 8。

## 目录

```
src/main/java/com/ycsopen/sms/core/
├── config/          Spring 配置（Security、WebMvc 拦截器注册）
├── common/          跨模块工具：异常处理、加密、JWT、HMAC 签名
├── domain/entity/   JPA 实体，字段与 docs/PRD.md 第 10 章数据字典一一对应
├── repository/      Spring Data JPA 仓库
├── service/         业务逻辑，按 PRD 功能模块分包（routing 是实现最完整的一个）
├── web/             controller / dto / interceptor
└── cmpp/            CMPP 协议编解码（当前为空包，见 docs/ROADMAP.md）
```

## 本地运行

前置：JDK 21、MySQL 8（本地或容器均可）、Redis（频控/限流用，routing 单测不需要它，
跑完整应用需要）。

```bash
./tools/init-db.sh                # 建库
mvn test                          # 先确认代码本身没问题（用 H2，不需要 MySQL）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 文档

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) —— 包结构与 PRD 分层的对应关系，
  重点讲解路由引擎（F-5）的内部结构。
- [`docs/API.md`](docs/API.md) —— 已实现接口一览。
- [`docs/ROADMAP.md`](docs/ROADMAP.md) —— **实现进度的诚实清单**：哪些模块真做了、
  哪些是简化版、哪些完全没碰，以及已知的技术债（务必在改动前读一遍）。

## 测试

```bash
mvn test
```

当前 18 个测试全部通过，覆盖：路由引擎的拦截编排逻辑（`RoutingEngineTest`）、
预付费计费的预扣/确认/冲正与幂等性（`BillingServiceTest`）、HTTP API 的 HMAC 签名机制
（`HmacSignatureVerifierTest`）、投诉占比计算（`ComplaintRatioServiceTest`）。

## 已知的安全类技术债（上线前必须处理）

见 [`docs/ROADMAP.md`](docs/ROADMAP.md) 的"已知简化"一节，其中第 3、4、7 条属于安全底线问题
（HMAC 签名未真正比对、nonce 防重放用内存而非 Redis、手机号未真正加密落库），
不要在没有解决这几条之前把本仓库当作可以直接上生产的实现。
