import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import TenantQualificationForm from '@/components/tenant/TenantQualificationForm';
import {
  classifyQualificationFailure,
  getOwnQualification,
  submitOwnQualification,
  type TenantQualificationSummary,
  type TenantQualificationStatus,
  type VerificationStatus,
} from '@/api/tenantQualificationApi';
import { protectedQueryKey, useAuthStore } from '@/store/authStore';
import '@/styles/tenant-qualification.css';

const STATUS_LABELS: Record<VerificationStatus, string> = {
  UNVERIFIED: '尚未认证',
  PENDING: '待审核',
  VERIFIED: '认证通过',
  REJECTED: '审核驳回',
  SUPPLEMENT_REQUIRED: '需补充材料',
};

function displayTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }) : '尚未认证';
}

export default function TenantQualificationPage() {
  const userType = useAuthStore((state) => state.userType);
  const allowed = userType === 'TENANT_ADMIN';
  const [recertifyOpen, setRecertifyOpen] = useState(false);
  const [recertifying, setRecertifying] = useState(false);
  const [success, setSuccess] = useState('');
  const [committedSummary, setCommittedSummary] = useState<TenantQualificationSummary | null>(null);
  const [aggregateRefreshFailed, setAggregateRefreshFailed] = useState(false);
  const [aggregateRefreshing, setAggregateRefreshing] = useState(false);
  const status = useQuery({
    queryKey: protectedQueryKey('tenant-qualification'),
    queryFn: getOwnQualification,
    enabled: allowed,
    retry: false,
  });
  const failureKind = status.error ? classifyQualificationFailure(status.error).kind : null;
  const displayedStatus = committedSummary ?? status.data;
  const displayedAggregate = committedSummary ? null : status.data ?? null;

  async function refreshCommittedAggregate(summary: TenantQualificationSummary) {
    setAggregateRefreshing(true);
    try {
      const refreshed = await status.refetch();
      if (refreshed.isSuccess && refreshed.data && refreshed.data.revision >= summary.revision) {
        setCommittedSummary(null);
        setAggregateRefreshFailed(false);
      } else {
        setAggregateRefreshFailed(true);
      }
    } catch {
      setAggregateRefreshFailed(true);
    } finally {
      setAggregateRefreshing(false);
    }
  }

  if (!allowed || failureKind === 'denied') {
    return <p data-testid="tenant-tenant-qualification-qualification-access-denied" className="qualification-alert error" role="alert">仅机构管理员可查看和提交本机构资质。</p>;
  }

  return (
    <section data-testid="tenant-tenant-qualification-qualification-page">
      <nav data-testid="tenant-tenant-qualification-qualification-breadcrumb" aria-label="面包屑">租户中心 / 资质认证</nav>
      <header className="qualification-page-header">
        <div>
          <h1 data-testid="tenant-tenant-qualification-qualification-heading">资质认证</h1>
          <p className="page-description">维护本机构资质。证件资料仅通过受保护上传提交。</p>
        </div>
      </header>

      {status.isLoading && <p data-testid="tenant-tenant-qualification-qualification-loading">正在加载资质状态…</p>}
      {status.isError && !committedSummary && (
        <div data-testid="tenant-tenant-qualification-qualification-error" className="qualification-alert error" role="alert">
          资质状态加载失败，请重试。
          <button data-testid="tenant-tenant-qualification-qualification-retry" type="button" onClick={() => void status.refetch()}>重新加载</button>
        </div>
      )}
      {committedSummary && aggregateRefreshFailed && (
        <div data-testid="tenant-tenant-qualification-qualification-error" className="qualification-alert warning" role="alert">
          资质资料已成功提交并进入待审核，但完整资质信息暂未刷新。当前页面保持只读，不需要重复提交。
          <button
            data-testid="tenant-tenant-qualification-qualification-retry"
            type="button"
            disabled={aggregateRefreshing}
            onClick={() => void refreshCommittedAggregate(committedSummary)}
          >{aggregateRefreshing ? '正在刷新…' : '重新加载'}</button>
        </div>
      )}

      {displayedStatus && (
        <QualificationWorkspace
          status={displayedStatus}
          aggregate={displayedAggregate}
          recertifying={recertifying}
          onRecertify={() => setRecertifyOpen(true)}
          onSubmit={async (context) => {
            const summary = await submitOwnQualification(context);
            setCommittedSummary(summary);
            setAggregateRefreshFailed(false);
            setRecertifying(false);
            setSuccess('资质资料已成功提交，当前状态为待审核。');
            await refreshCommittedAggregate(summary);
          }}
        />
      )}

      <p role="status" aria-live="polite" className="qualification-success">{success}</p>
      {recertifyOpen && displayedStatus && (
        <ModalDialog labelledBy="tenant-recertify-title" onRequestClose={() => setRecertifyOpen(false)}>
          <div data-testid="tenant-tenant-qualification-qualification-recertify-dialog">
            <h2 id="tenant-recertify-title">重新认证资质</h2>
            <p>确认后可编辑认证资料；只有提交重新认证后，当前发送和新资源创建资格才会进入待审核状态。</p>
            <div className="qualification-dialog-actions">
              <button data-testid="tenant-tenant-qualification-qualification-recertify-cancel" type="button" className="button-secondary" onClick={() => setRecertifyOpen(false)}>取消</button>
              <button data-testid="tenant-tenant-qualification-qualification-recertify-confirm" type="button" onClick={() => { setRecertifyOpen(false); setRecertifying(true); }}>确认并填写资料</button>
            </div>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}

function QualificationWorkspace({ status, aggregate, recertifying, onRecertify, onSubmit }: {
  status: TenantQualificationSummary;
  aggregate: TenantQualificationStatus | null;
  recertifying: boolean;
  onRecertify: () => void;
  onSubmit: Parameters<typeof TenantQualificationForm>[0]['onSubmit'];
}) {
  const pending = status.verificationStatus === 'PENDING';
  const verified = status.verificationStatus === 'VERIFIED';
  const readOnly = pending || (verified && !recertifying);
  const feedbackVisible = status.verificationStatus === 'REJECTED'
    || status.verificationStatus === 'SUPPLEMENT_REQUIRED';
  return (
    <>
      <section data-testid="tenant-tenant-qualification-qualification-status" className="card qualification-status-card">
        <div>
          <span className={`qualification-status status-${status.verificationStatus.toLowerCase()}`}>{STATUS_LABELS[status.verificationStatus]}</span>
          <strong>{status.shortName}</strong>
          <span>{status.tenantNo}</span>
        </div>
        <dl>
          <div><dt>认证时间</dt><dd data-testid="tenant-tenant-qualification-qualification-certified-at">{displayTime(status.verifiedAt)}</dd></div>
          <div><dt>状态更新时间</dt><dd data-testid="tenant-tenant-qualification-qualification-updated-at">{displayTime(status.verificationUpdatedAt)}</dd></div>
        </dl>
        {feedbackVisible && (
          <p data-testid="tenant-tenant-qualification-qualification-review-feedback" className="qualification-alert warning">
            审核反馈：{status.reason ?? '平台未提供额外说明'}
          </p>
        )}
        {verified && !recertifying && (
          <button data-testid="tenant-tenant-qualification-qualification-recertify" type="button" onClick={onRecertify}>重新认证</button>
        )}
      </section>
      <section className="card">
        {pending && <p className="qualification-alert warning">资料正在审核，当前表单只读。</p>}
        {recertifying && <p className="qualification-alert warning">正在准备重新认证；提交后当前认证资格将进入待审核。</p>}
        {aggregate ? (
          <TenantQualificationForm
            key={`${aggregate.tenantId}:${aggregate.revision}:${recertifying ? 'edit' : 'view'}`}
            readOnly={readOnly}
            initialStatus={aggregate}
            submitTestId="tenant-tenant-qualification-qualification-submit"
            submitLabel={status.verificationStatus === 'UNVERIFIED' ? '提交资质' : '重新提交资质'}
            errorSummaryTestId="tenant-tenant-qualification-qualification-error-summary"
            onSubmit={onSubmit}
          />
        ) : (
          <p className="qualification-alert warning" role="status">
            已提交资料保持只读；完整安全资质信息刷新成功后再显示。
          </p>
        )}
      </section>
    </>
  );
}
