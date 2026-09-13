import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { getDashboardConfiguration, saveDashboardConfiguration } from '@/api/operationalDashboardApi';
import { QueryField, QueryPanel } from '@/components/common/QueryPanel';

export default function DashboardConfigurationPage() {
  const [draftRole, setDraftRole] = useState('ADMIN');
  const [role, setRole] = useState('ADMIN');
  const [message, setMessage] = useState('');
  const config = useQuery({ queryKey: ['operational-dashboard-configuration', role], queryFn: () => getDashboardConfiguration(role), retry: false });
  const save = useMutation({
    mutationFn: saveDashboardConfiguration,
    onSuccess: (result) => setMessage(`已保存：${result.role} / ${result.refreshMode}`),
  });
  const data = config.data;
  return (
    <section className="card" data-testid="admin-operational-dashboards-dashboard-configuration-page">
      <h1>仪表盘配置</h1>
      <QueryPanel
        onSubmit={() => setRole(draftRole)}
        onReset={() => {
          setDraftRole('ADMIN');
          setRole('ADMIN');
        }}
        result={(
          <>
            {config.isError && <p role="alert">仪表盘配置加载失败。</p>}
            {data && (
              <dl className="form-grid">
                <div><dt>全局卡片</dt><dd>{String(data.globalCards)}</dd></div>
                <div><dt>租户卡片</dt><dd>{String(data.tenantCards)}</dd></div>
                <div><dt>刷新</dt><dd>{data.refreshMode} / {data.pollingSeconds}</dd></div>
                <div><dt>投诉阈值</dt><dd>{data.complaintThreshold}</dd></div>
              </dl>
            )}
          </>
        )}
      >
        <QueryField name="dashboard-role" label="角色">
          <select
            data-testid="admin-operational-dashboards-dashboard-configuration-role"
            value={draftRole}
            onChange={(event) => setDraftRole(event.target.value)}
          >
            {['ADMIN', 'OPERATOR', 'FINANCE', 'TENANT_ADMIN', 'TENANT_DEV', 'TENANT_USER'].map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </QueryField>
      </QueryPanel>
      <button
        type="button"
        data-testid="admin-operational-dashboards-dashboard-configuration-save"
        onClick={() => save.mutate({
          role,
          globalCards: data?.globalCards ?? true,
          tenantCards: data?.tenantCards ?? true,
          refreshMode: data?.refreshMode ?? 'MANUAL',
          pollingSeconds: data?.pollingSeconds ?? 300,
          complaintThreshold: data?.complaintThreshold ?? '0.0030',
        })}
      >
        保存配置
      </button>
      {message && <p role="status" data-testid="admin-operational-dashboards-dashboard-configuration-message">{message}</p>}
    </section>
  );
}
