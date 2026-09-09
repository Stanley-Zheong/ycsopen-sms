import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getTenantWebhookConfig,
  saveTenantWebhookConfig,
  testTenantWebhook,
} from '@/api/webhookDeliveryApi';
import { mutationErrorMessage } from '@/api/client';
import '@/styles/webhook-delivery.css';

export default function TenantWebhooksPage() {
  const queryClient = useQueryClient();
  const config = useQuery({ queryKey: ['tenant-webhook-config'], queryFn: getTenantWebhookConfig, retry: false });
  const [statusUrl, setStatusUrl] = useState('');
  const [uplinkUrl, setUplinkUrl] = useState('');
  const [unsubscribeUrl, setUnsubscribeUrl] = useState('');
  const [retryMaxCount, setRetryMaxCount] = useState(5);
  const [retryBackoffSeconds, setRetryBackoffSeconds] = useState(30);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!config.data) return;
    setStatusUrl(config.data.statusCallbackUrl ?? '');
    setUplinkUrl(config.data.uplinkCallbackUrl ?? '');
    setUnsubscribeUrl(config.data.unsubscribeCallbackUrl ?? '');
    setRetryMaxCount(config.data.retryMaxCount);
    setRetryBackoffSeconds(config.data.retryBackoffSeconds);
  }, [config.data]);

  const save = useMutation({
    mutationFn: () => saveTenantWebhookConfig({
      statusCallbackUrl: statusUrl,
      uplinkCallbackUrl: uplinkUrl,
      unsubscribeCallbackUrl: unsubscribeUrl,
      retryMaxCount,
      retryBackoffSeconds,
    }),
    onSuccess: async () => {
      setMessage('回调配置已保存');
      setError('');
      await queryClient.invalidateQueries({ queryKey: ['tenant-webhook-config'] });
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '回调配置保存失败'));
      setMessage('');
    },
  });

  const test = useMutation({
    mutationFn: () => testTenantWebhook('STATUS', statusUrl),
    onSuccess: (result) => {
      setMessage(`测试回调完成：${result.state}/${result.resultCode}`);
      setError('');
    },
    onError: (failure) => {
      setError(mutationErrorMessage(failure, '测试回调失败'));
      setMessage('');
    },
  });

  return (
    <section className="webhook-delivery-page" data-testid="tenant-webhooks">
      <nav aria-label="面包屑">租户配置 / Webhook 回调</nav>
      <header className="webhook-delivery-header">
        <div>
          <h1>Webhook 回调配置</h1>
          <p className="page-description">分别配置状态、上行、退订回调地址；系统按版本化签名信封投递，并保留失败重试证据。</p>
        </div>
        <span data-testid="tenant-webhook-delivery-config-version">v{config.data?.version ?? 0}</span>
      </header>

      {message && <p role="status" className="webhook-delivery-alert success" data-testid="tenant-webhook-delivery-operation-message">{message}</p>}
      {error && <p role="alert" className="webhook-delivery-alert error" data-testid="tenant-webhook-delivery-operation-error">{error}</p>}
      {config.isError && <p role="alert">回调配置加载失败。</p>}

      <section className="card webhook-delivery-form" data-testid="tenant-webhook-delivery-config-form">
        <label>状态回调 URL<input data-testid="tenant-webhook-delivery-status-url" value={statusUrl} onChange={(event) => setStatusUrl(event.target.value)} placeholder="https://callback.example.com/status" /></label>
        <label>上行回调 URL<input data-testid="tenant-webhook-delivery-uplink-url" value={uplinkUrl} onChange={(event) => setUplinkUrl(event.target.value)} placeholder="https://callback.example.com/uplink" /></label>
        <label>退订回调 URL<input data-testid="tenant-webhook-delivery-unsubscribe-url" value={unsubscribeUrl} onChange={(event) => setUnsubscribeUrl(event.target.value)} placeholder="https://callback.example.com/unsubscribe" /></label>
        <label>最大重试次数<input data-testid="tenant-webhook-delivery-retry-max" type="number" value={retryMaxCount} onChange={(event) => setRetryMaxCount(Number(event.target.value))} /></label>
        <label>基础退避秒数<input data-testid="tenant-webhook-delivery-backoff-seconds" type="number" value={retryBackoffSeconds} onChange={(event) => setRetryBackoffSeconds(Number(event.target.value))} /></label>
        <div className="webhook-delivery-actions">
          <button type="button" data-testid="tenant-webhook-delivery-save" onClick={() => save.mutate()} disabled={save.isPending}>保存配置</button>
          <button type="button" data-testid="tenant-webhook-delivery-test" onClick={() => test.mutate()} disabled={test.isPending}>SSRF 安全测试</button>
        </div>
      </section>
    </section>
  );
}
