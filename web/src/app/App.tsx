import { RouterProvider } from 'react-router-dom';
import { router } from '@/router/routes';

/**
 * 应用入口。路由树按 PRD ycsansms.md 第 8 章 Web 管理端信息架构拆成 admin（平台管理后台）
 * 与 tenant（机构端）两棵子树，登录后按 user_type 决定进入哪一棵——见 router/routes.tsx。
 */
export default function App() {
  return <RouterProvider router={router} />;
}
