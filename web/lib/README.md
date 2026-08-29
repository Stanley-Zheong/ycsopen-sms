# web/lib

跨页面/跨组件复用、且不依赖具体渲染框架的纯函数工具库（通过 `@lib/*` 路径别名引用，
见 `vite.config.ts` / `tsconfig.json`），与 `src/` 的区别：

- `src/` 是"应用"——页面、组件、路由、状态管理，与本产品的业务强绑定。
- `lib/` 是"工具"——不关心自己被用在哪个页面，可以脱离渲染直接单测（见 `../test/unit/format.test.ts`）。

当前内容：

| 文件 | 用途 |
|---|---|
| `format.ts` | F-11.9 投诉占比的百分比/千分比格式化（`ComplaintRatioPanel` 使用） |
