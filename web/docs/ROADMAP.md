# web 实现进度与路线图

对应 core/docs/ROADMAP.md 的前端版本，同样原则：诚实记录，方便接手。

## 已实现并有验证

| 内容 | PRD 编号 | 位置 | 验证方式 |
|---|---|---|---|
| 路由树（完整 IA，含占位页） | 第 8 章 Web IA | `src/router/routes.tsx` | `npx tsc -b` 通过；`npm run build` 通过 |
| 角色守卫布局（Admin / Tenant） | 3.1/3.2 节 | `src/components/layout/*Layout.tsx` | 同上 |
| 登录页 + 会话状态 | F-1.4 | `src/pages/LoginPage.tsx`、`src/store/authStore.ts` | 同上（未接后端联调测试） |
| **通道/机构月度投诉占比看板** | **F-11.9（本次新增需求）** | `src/pages/admin/dashboard/ComplaintRatioPanel.tsx` | `npm run build` 通过；格式化逻辑有 `test/unit/format.test.ts`（4 项全绿） |
| 机构列表 + 审核通过并开通试用 | F-2.1/F-2.2/F-2.8 | `src/pages/admin/tenants/TenantListPage.tsx` | 构建通过，未接后端联调测试 |
| 通道列表 + 暂停/恢复 | F-4.1/F-4.7 | `src/pages/admin/channels/ChannelListPage.tsx` | 构建通过，未接后端联调测试 |
| 手工发送（对接 F-6.1 接口） | F-6.10 | `src/pages/tenant/send/SendPage.tsx` | 构建通过；**注意**：真实调用会被 core 的 HMAC 拦截器拒绝，因为控制台会话不等于 HTTP API 签名，见下方"已知问题" |

2026-08-29：`npx tsc -b`、`npm run build`、`npx vitest run` 均通过（4/4 测试）。

## 已知问题 / 简化

1. **`SendPage` 直接调用 `/api/v1/sms/send`，但该接口要求 HMAC 签名头**（见 core 的
   `HmacAuthInterceptor`），控制台的 JWT 会话不满足这个要求——实际点击会收到 401。
   正确做法是 core 另开一个走会话鉴权的"控制台内发送"接口，代理到
   `MessageSubmitService`；这个接口在 core 里还没有，是前后端一起要补的缺口。
2. **登录后没有实际的 JWT 校验拦截**——`authStore` 存了 token，但因为 core 的
   `SecurityConfig` 当前 `anyRequest().permitAll()`（见 core/docs/ROADMAP.md），
   没有 token 也能调所有接口，鉴权目前形同虚设。
3. 除仪表盘投诉占比、机构列表、通道列表、手工发送外，其余全部页面是 `PlaceholderPage`
   占位（见 `src/router/routes.tsx` 里挂载的组件），不是真实功能。
4. 没有做样式系统统一（用的是一份手写的极简 CSS，`src/styles/index.css`），生产化时
   建议引入 Ant Design / shadcn-ui 之类的组件库替换。
5. E2E 测试目录 `test/e2e/` 只建了目录，没有写用例（建议用 Playwright，覆盖 4.4 节
   Task Flow A/B/C 的关键路径）。

## 建议的下一步顺序

1. 和 core 一起补上"控制台内发送"接口 + 前端联调，把 `SendPage` 的调用改对。
2. 接入真实的路由守卫（未登录跳转登录页，token 过期自动登出——`authStore.logout()`
   已经有了，只差 core 侧真正返回 401 的场景）。
3. 按 `PlaceholderPage` 列表逐个"认领"实现，优先级参照 PRD 2.5 节路线图。
