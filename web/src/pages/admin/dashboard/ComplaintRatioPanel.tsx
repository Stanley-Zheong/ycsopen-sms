import { useQuery } from '@tanstack/react-query';
import { fetchComplaintRatio } from '@/api/dashboard';
import { formatRatioAsPerMille } from '@lib/format';

interface ComplaintRatioPanelProps {
  dimension: 'channel' | 'tenant';
  title: string;
}

/**
 * F-11.9 [新增]：每通道 / 每机构当月投诉占比排行榜。
 * 占比 ≥ 千分之三（0.3%，默认阈值，见 core application.yml
 * ycsopen.routing.complaint-ratio-threshold，可在 F-11.10 仪表盘配置里调整）的行标红置顶——
 * 排序与"超阈值"判定完全交给后端（ComplaintRatioService），前端只负责渲染，
 * 避免前后端各算一套阈值口径不一致。
 */
export default function ComplaintRatioPanel({ dimension, title }: ComplaintRatioPanelProps) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['complaint-ratio', dimension],
    queryFn: () => fetchComplaintRatio(dimension),
  });

  if (isLoading) return <div className="card">加载中…</div>;
  if (isError) return <div className="card">加载失败，请稍后重试（网络异常，见 PRD 5.15 节异常流规范）。</div>;

  const rows = data ?? [];
  const overThresholdCount = rows.filter((r) => r.overThreshold).length;

  return (
    <div className="card">
      <h3>
        {title}
        {overThresholdCount > 0 && <span className="badge-over">{overThresholdCount} 项超阈值</span>}
      </h3>
      {rows.length === 0 ? (
        <p style={{ color: '#888' }}>暂无数据</p>
      ) : (
        <table className="ratio-table">
          <thead>
            <tr>
              <th>{dimension === 'channel' ? '通道 ID' : '机构 ID'}</th>
              <th>当月发送量</th>
              <th>当月投诉量</th>
              <th>投诉占比</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.dimensionId} className={row.overThreshold ? 'over-threshold' : undefined}>
                <td>{row.dimensionName ?? row.dimensionId}</td>
                <td>{row.sendCount.toLocaleString()}</td>
                <td>{row.complaintCount.toLocaleString()}</td>
                <td>{formatRatioAsPerMille(row.ratio)}</td>
                <td>{row.overThreshold ? '超阈值' : '正常'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
