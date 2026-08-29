import { useQuery, useQueryClient } from '@tanstack/react-query';
import { listChannels, pauseChannel, resumeChannel } from '@/api/channels';

/** F-4.1/F-4.7：通道列表 + 暂停/恢复操作，对应 4.4 节 Task Flow C 的最后一步。 */
export default function ChannelListPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['channels'], queryFn: listChannels });

  if (isLoading) return <div className="card">加载中…</div>;

  return (
    <div className="card">
      <h2>通道管理</h2>
      <table className="ratio-table">
        <thead>
          <tr>
            <th>通道名称</th>
            <th>协议</th>
            <th>运营商</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {(data ?? []).map((c) => (
            <tr key={c.id}>
              <td>{c.channelName}</td>
              <td>{c.protocol}</td>
              <td>{c.operator}</td>
              <td>{c.status}</td>
              <td>
                {c.status === 'NORMAL' ? (
                  <button
                    onClick={async () => {
                      const reason = window.prompt('暂停原因？') ?? '运营手工暂停';
                      await pauseChannel(c.id, reason, 'operator-demo');
                      queryClient.invalidateQueries({ queryKey: ['channels'] });
                    }}
                  >
                    暂停
                  </button>
                ) : (
                  <button
                    onClick={async () => {
                      await resumeChannel(c.id);
                      queryClient.invalidateQueries({ queryKey: ['channels'] });
                    }}
                  >
                    恢复
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
