# web/data

前端 mock 数据 / 测试夹具目录。当前为空；`ComplaintRatioPanel` 等组件的开发调试
目前直接连真实的 core 后端（见 vite.config.ts 的 `/api` 代理配置指向 `localhost:8080`）。

后续若要支持"core 未启动也能跑通前端 UI 走查"，建议在这里放 MSW（Mock Service Worker）
的 handler 定义，对应 web/docs/ROADMAP.md 里的 Mock Server 计划。
