import ComplaintRatioPanel from './ComplaintRatioPanel';

/** F-11.6/F-11.9 关键指标概览仪表盘（本次 scaffold 重点还原投诉占比看板，其余 KPI 卡片见 ROADMAP）。 */
export default function DashboardPage() {
  return (
    <div>
      <h1>关键指标概览</h1>
      <ComplaintRatioPanel dimension="channel" title="每通道当月投诉占比" />
      <ComplaintRatioPanel dimension="tenant" title="每机构当月投诉占比" />
    </div>
  );
}
