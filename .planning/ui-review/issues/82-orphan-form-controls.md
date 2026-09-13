## 现象
使用 Chrome + Playwright 遍历 admin 表单页面，发现部分输入框/下拉框后没有任何可执行动作，无法触发查询、异步联动或提交，控件实际无作用。

重点证据：`/admin/tenant-recharge-review` 有两个约 782px 控件但没有业务按钮；`/admin/templates/review` 有约 1610px 的筛选输入但没有查询动作；`/admin/bulk/details`、`/admin/send/jobs` 也存在控件但无业务按钮。页面不能只渲染输入框而没有行为。

## 需求
为每个有业务用途的输入框/下拉框提供明确动作：查询表单至少提供查询和重置；需要联动的控件输入或选择后触发真实异步请求并反馈加载、成功或错误状态；纯展示字段改为文本，不保留可编辑控件。按钮放在查询组件最后一行右侧。

## Playwright 验收标准（Chrome）
1. 遍历所有 admin 表单路由，每个 `[data-testid="query-panel"]` 都存在 `[data-testid="query-actions"]`，且至少有查询或刷新动作；表单提交区存在提交/保存动作。
2. 对每个筛选 input/select 填值或选择后，点击查询会产生可观测的请求或结果区域变化；页面显示加载完成、结果或空状态。
3. 异步联动控件在请求期间显示加载状态，请求失败显示可定位错误提示，不静默无响应。
4. 不存在既无 label、无动作、无联动行为又可编辑的孤立 input/select；每个控件均有稳定 `data-testid`。
