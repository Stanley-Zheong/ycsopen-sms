import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { ApiResponse, Tenant } from '@/types/api';
import { approveAndActivateTrial } from '@/api/tenants';
import { useQueryClient } from '@tanstack/react-query';

/** F-2.1/F-2.2/F-2.8：机构列表 + 一键"审核通过并开通试用"，对应 PRD 场景四。 */
export default function TenantListPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Tenant[]>>('/console/tenants');
      return res.data.data;
    },
  });

  if (isLoading) return <div className="card">加载中…</div>;

  return (
    <div className="card">
      <h2>机构列表</h2>
      <table className="ratio-table">
        <thead>
          <tr>
            <th>机构编号</th>
            <th>简称</th>
            <th>认证状态</th>
            <th>生命周期状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {(data ?? []).map((t) => (
            <tr key={t.id}>
              <td>{t.tenantNo}</td>
              <td>{t.shortName}</td>
              <td>{t.verificationStatus}</td>
              <td>{t.lifecycleStatus}</td>
              <td>
                {t.verificationStatus === 'PENDING' && (
                  <button
                    onClick={async () => {
                      await approveAndActivateTrial(t.id, 'operator-demo');
                      queryClient.invalidateQueries({ queryKey: ['tenants'] });
                    }}
                  >
                    审核通过并开通试用
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
