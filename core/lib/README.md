# core/lib

存放**不通过 Maven 中央仓库分发的第三方/供应商库**（vendored jars），例如：

- 运营商或通道商私有提供的 CMPP SDK（若采购的通道商要求使用其专有客户端而非自研编解码器）。
- 内部积累但尚未发布到私有 Maven 仓库的工具库。

**当前为空**——本项目的 CMPP 协议编解码（对应 PRD F-6.7/F-6.8/F-6.9）计划作为
`com.ycsopen.sms.core.cmpp` 包内的普通 Java 代码实现（见 `../src/main/java/.../core/cmpp/`），
不依赖此目录下的第三方 jar；只有当确实需要引入无法从 Maven Central 获取的二进制依赖时，
才把 jar 放在这里，并在 `pom.xml` 中用 `system` scope 或安装到本地 Maven 仓库后引用
（`../tools/install-local-libs.sh`，当前为 TODO，见 ../docs/ROADMAP.md）。
