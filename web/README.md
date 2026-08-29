# ycsopen-sms-web

短信平台前端。React 18 + TypeScript + Vite，平台管理后台（Admin Portal）与机构端
（Tenant Portal）在同一套应用内，登录后按角色分流到不同布局/导航（见 `src/router/routes.tsx`）。

## 目录

```
src/
├── app/           应用根组件
├── router/        路由树，严格对齐 docs/PRD.md 第 8 章 Web IA
├── components/    layout（Admin/Tenant 布局）、common（PlaceholderPage 等）、charts（预留）
├── pages/         admin/* 与 tenant/* 两棵页面树
├── api/           后端接口封装（axios）
├── store/         Zustand 全局状态（当前只有登录会话）
├── types/         与后端 DTO 对应的 TS 类型
└── styles/        全局样式（手写极简 CSS，未接入组件库）
```

## 本地运行

```bash
npm install
npm run dev      # http://localhost:5173，/api 代理到 http://localhost:8080（core）
npm run build    # 类型检查 + 生产构建
npm test         # Vitest 单元测试
```

## 本次重点功能：投诉占比看板

`src/pages/admin/dashboard/ComplaintRatioPanel.tsx` 实现了本次新增需求——每通道 / 每机构
当月投诉占比排行，超过阈值（默认千分之三，由后端 `ComplaintRatioService` 计算并返回
`overThreshold` 字段）的行标红置顶。格式化逻辑抽到 `lib/format.ts`，有独立单测
（`test/unit/format.test.ts`）。

## 文档

[`docs/ROADMAP.md`](docs/ROADMAP.md) —— 哪些页面是真实现、哪些是 `PlaceholderPage` 占位，
以及已知问题（如手工发送页当前会因为鉴权方式不匹配而调用失败）。**改动前建议先读一遍**，
避免误以为某个占位页面是"坏了的真实现"。
