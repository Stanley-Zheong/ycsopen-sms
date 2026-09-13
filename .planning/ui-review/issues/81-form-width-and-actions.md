## 现象
使用 Chrome + Playwright 遍历 admin 表单页面，发现多个页面的单个输入框/下拉框占据过宽区域，且部分表单只有控件没有查询或提交动作。

重点证据：`/admin/signatures/review` 输入框约 1610px；`/admin/exemption/policy` 多个控件约 1576px；`/admin/riskcontrol`、`/admin/content-safety`、`/admin/frequency/rules`、`/admin/prefixes` 存在同类宽控件。页面应在同一行容纳约四个普通组件，不能因只有一个字段就铺满整行。

## 需求
统一表单字段宽度：普通输入框按约 30 个汉字预留宽度，单个字段不因所在行为空而拉伸；下拉框按最长 option 宽度设置。响应式不足四个时允许换行，但不改变字段的固定合理宽度。

## Playwright 验收标准（Chrome）
1. 遍历所有 admin 表单路由，普通 input/select 的宽度不超过查询容器可用宽度的 30%，且不超过 420px（文本区按内容需要例外）。
2. 1440px 及以上窗口中，同一查询区最多四列、至少可容纳四个普通字段；字段换行后列起点对齐。
3. 下拉框最长 option 完整可见，不出现截断或横向滚动。
4. 页面 `scrollWidth === clientWidth`，不存在因表单宽度造成的横向滚动。
