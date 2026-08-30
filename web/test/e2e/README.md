# web/test/e2e

Playwright 自动化已迁移到 `../scripts/`，测试契约和 fixture 计划位于 `../cases/`。

执行：

```bash
npm run test:e2e
```

当前套件通过逐用例 API mock 验证 Web 页面行为，不证明真实后端联调；单元测试见 `../unit/`。
