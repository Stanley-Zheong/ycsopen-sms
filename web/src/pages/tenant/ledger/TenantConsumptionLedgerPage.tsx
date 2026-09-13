import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { listConsumption } from '@/api/trialPrepaidApi';
import { protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/trial-prepaid.css';

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function TenantConsumptionLedgerPage() {
  const tenantId = useAuthStore((state) => state.tenantId) ?? 42;
  const [businessType, setBusinessType] = useState('');
  const ledger = useQuery({
    queryKey: protectedQueryKey('trial-prepaid-consumption', tenantId, businessType),
    queryFn: () => listConsumption(tenantId, businessType),
    retry: false,
  });

  return (
    <section className="trial-prepaid-page" data-testid="tenant-trial-prepaid-consumption-ledger-page">
      <nav aria-label="面包屑">机构端 / 消费账本</nav>
      <header className="trial-prepaid-header">
        <div>
          <h1>消费账本</h1>
          <p className="page-description">按机构、期间和业务类型查询试用/预付费消费记录。账本只展示追加记录，不提供修改入口。</p>
        </div>
      </header>

      <section className="card" data-testid="tenant-consumption-ledger">
        <div className="trial-prepaid-form" data-testid="tenant-trial-prepaid-consumption-ledger-filters">
          <label>
            业务类型
            <select
              data-testid="tenant-trial-prepaid-consumption-ledger-business-type"
              value={businessType}
              onChange={(event) => setBusinessType(event.target.value)}
            >
              <option value="">全部</option>
              <option value="SMS">短信</option>
              <option value="MARKETING">营销</option>
              <option value="NOTICE">通知</option>
            </select>
          </label>
          <button type="button" data-testid="tenant-trial-prepaid-consumption-ledger-refresh" onClick={() => void ledger.refetch()}>查询</button>
        </div>
        {ledger.isLoading && <p data-testid="tenant-trial-prepaid-consumption-ledger-loading">正在加载消费账本…</p>}
        {ledger.isError && <p role="alert" data-testid="tenant-trial-prepaid-consumption-ledger-error">消费账本加载失败。</p>}
        <table className="ratio-table" data-testid="tenant-trial-prepaid-consumption-ledger-table">
          <thead>
            <tr><th>消息/业务单</th><th>业务类型</th><th>额度变化</th><th>金额(厘)</th><th>类型</th><th>状态</th><th>操作人</th><th>时间</th></tr>
          </thead>
          <tbody>{(ledger.data ?? []).map((row) => (
            <tr key={`${row.messageRef}-${row.createdAt}`} data-testid="tenant-trial-prepaid-consumption-ledger-row">
              <td>{row.messageRef}</td>
              <td>{row.businessType}</td>
              <td>{row.quotaDelta}</td>
              <td>{row.amountMil}</td>
              <td>{row.entryType}</td>
              <td>{row.state}</td>
              <td>{row.actor}</td>
              <td>{displayTime(row.createdAt)}</td>
            </tr>
          ))}</tbody>
        </table>
      </section>
    </section>
  );
}
