import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { consumeTrial, getTrialOverview, requestConversion } from '@/api/trialPrepaidApi';
import { getContractOverview } from '@/api/contractPricingApi';
import { mutationErrorMessage } from '@/api/client';
import { protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/trial-prepaid.css';

function displayTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export default function OverviewPage() {
  const tenantId = useAuthStore((state) => state.tenantId) ?? 42;
  const queryClient = useQueryClient();
  const overviewKey = protectedQueryKey('trial-prepaid-overview', tenantId);
  const contractKey = protectedQueryKey('contract-pricing-overview', tenantId);
  const overview = useQuery({ queryKey: overviewKey, queryFn: () => getTrialOverview(tenantId), retry: false });
  const contract = useQuery({ queryKey: contractKey, queryFn: () => getContractOverview(tenantId), retry: false });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const consumeMutation = useMutation({
    mutationFn: () => consumeTrial(tenantId, `MANUAL-${Date.now()}`, 'SMS'),
    onSuccess: async (result) => {
      setMessage(`试用短信已扣减，剩余额度 ${result.quotaRemaining}`);
      setError('');
      await queryClient.invalidateQueries({ queryKey: overviewKey });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '试用额度扣减失败'));
      setMessage('');
    },
  });
  const conversionMutation = useMutation({
    mutationFn: () => requestConversion(tenantId),
    onSuccess: (result) => {
      setMessage(`转正申请已提交：${result.status}`);
      setError('');
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '转正申请提交失败'));
      setMessage('');
    },
  });
  const data = overview.data;
  return (
    <section className="trial-prepaid-page" data-testid="tenant-trial-prepaid-overview-page">
      <nav aria-label="面包屑">机构端 / 账户总览</nav>
      <header className="trial-prepaid-header">
        <div>
          <h1>账户总览</h1>
          <p className="page-description">展示当前试用状态、剩余额度、有效期，并提供试用转正式预付费申请入口。</p>
        </div>
      </header>
      {message && <p role="status" data-testid="tenant-trial-prepaid-overview-message" className="trial-prepaid-alert success">{message}</p>}
      {error && <p role="alert" data-testid="tenant-trial-prepaid-overview-error" className="trial-prepaid-alert error">{error}</p>}
      <section className="card" data-testid="tenant-trial-prepaid-overview-trial-status">
        <h2>试用状态</h2>
        {overview.isLoading && <p data-testid="tenant-trial-prepaid-overview-loading">正在加载试用状态…</p>}
        {overview.isError && <p role="alert">试用状态加载失败。</p>}
        {data && (
          <>
            <span className={`trial-prepaid-status status-${data.trialStatus.toLowerCase()}`}>{data.trialStatus}</span>
            <dl className="trial-prepaid-metrics">
              <div className="trial-prepaid-metric"><dt>总额度</dt><dd data-testid="tenant-trial-prepaid-overview-quota-total">{data.quotaTotal}</dd></div>
              <div className="trial-prepaid-metric"><dt>剩余额度</dt><dd data-testid="tenant-trial-prepaid-overview-quota-remaining">{data.quotaRemaining}</dd></div>
              <div className="trial-prepaid-metric"><dt>有效期开始</dt><dd>{displayTime(data.validFrom)}</dd></div>
              <div className="trial-prepaid-metric"><dt>有效期结束</dt><dd>{displayTime(data.validUntil)}</dd></div>
            </dl>
          </>
        )}
        <div className="trial-prepaid-form">
          <button type="button" data-testid="tenant-trial-prepaid-overview-consume-trial" onClick={() => consumeMutation.mutate()}>记录试用发送扣减</button>
          <button type="button" data-testid="tenant-trial-prepaid-overview-conversion-request" onClick={() => conversionMutation.mutate()}>申请转正式预付费</button>
        </div>
      </section>
      <section className="card" data-testid="tenant-contract-pricing-overview-contract-status">
        <h2>签约状态</h2>
        {contract.isLoading && <p>正在加载签约状态…</p>}
        {contract.isError && <p role="alert">签约状态加载失败。</p>}
        {contract.data && (
          <dl className="trial-prepaid-metrics">
            <div className="trial-prepaid-metric"><dt>状态</dt><dd>{contract.data.tenantState}</dd></div>
            <div className="trial-prepaid-metric"><dt>计费模式</dt><dd>{contract.data.billingMode ?? '-'}</dd></div>
            <div className="trial-prepaid-metric"><dt>价目表版本</dt><dd>{contract.data.priceBookVersion ?? '-'}</dd></div>
            <div className="trial-prepaid-metric"><dt>授信额度</dt><dd>{contract.data.creditLimitMil ?? '-'}</dd></div>
            <div className="trial-prepaid-metric"><dt>账期</dt><dd>{contract.data.billingPeriod ?? '-'}</dd></div>
          </dl>
        )}
      </section>
    </section>
  );
}
