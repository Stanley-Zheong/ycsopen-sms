import { FormEvent, useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import { useMutation, useQuery } from '@tanstack/react-query';
import { previewTemplate, listTenantTemplates, type TemplateRecord } from '@/api/templateLifecycleApi';
import { listTenantSignatures } from '@/api/signatureLifecycleApi';
import { sendTenantConsoleMessage, type TenantConsoleSendResponse } from '@/api/tenantConsoleSendApi';
import '@/styles/tenant-send.css';

const NETWORK_MESSAGE = '网络异常，请检查网络后重试';

function newCorrelationId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `CONSOLE-${crypto.randomUUID()}`;
  }
  return `CONSOLE-${Date.now()}`;
}

function splitRecipients(value: string) {
  return value.split(/[\n,，;\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 20);
}

function isNetworkFailure(error: unknown) {
  return isAxiosError(error) && (!error.response || error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK');
}

function errorMessage(error: unknown) {
  if (isNetworkFailure(error)) return NETWORK_MESSAGE;
  if (isAxiosError<{ message?: string }>(error)) return error.response?.data?.message ?? '提交失败，请检查字段后重试';
  return error instanceof Error ? error.message : '提交失败，请检查字段后重试';
}

export default function SendPage() {
  const [templateId, setTemplateId] = useState('');
  const [recipientsText, setRecipientsText] = useState('13800138000');
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [rendered, setRendered] = useState('');
  const [correlationId, setCorrelationId] = useState(newCorrelationId);
  const [results, setResults] = useState<TenantConsoleSendResponse[]>([]);
  const [error, setError] = useState('');
  const [networkRetry, setNetworkRetry] = useState(false);

  const templates = useQuery({ queryKey: ['tenant-send-templates'], queryFn: listTenantTemplates, retry: false });
  const signatures = useQuery({ queryKey: ['tenant-send-signatures'], queryFn: listTenantSignatures, retry: false });

  const approvedSignatures = useMemo(() => (signatures.data ?? []).filter((signature) => signature.auditStatus === 'APPROVED'), [signatures.data]);
  const approvedSignatureIds = useMemo(() => new Set(approvedSignatures.map((signature) => signature.id)), [approvedSignatures]);
  const approvedTemplates = useMemo(() => (templates.data ?? []).filter((template) => (
    template.auditStatus === 'APPROVED' && approvedSignatureIds.has(template.signatureId)
  )), [templates.data, approvedSignatureIds]);
  const selectedTemplate: TemplateRecord | null = approvedTemplates.find((template) => String(template.id) === templateId)
    ?? approvedTemplates[0]
    ?? null;
  const selectedSignature = selectedTemplate
    ? approvedSignatures.find((signature) => signature.id === selectedTemplate.signatureId)
    : null;
  const recipients = splitRecipients(recipientsText);

  const preview = useMutation({
    mutationFn: () => {
      if (!selectedTemplate) throw new Error('请选择已审核通过的模板');
      const scopedVariables = Object.fromEntries(
        selectedTemplate.variableNames.map((name) => [name, variables[name] ?? '']),
      );
      return previewTemplate(selectedTemplate.id, scopedVariables);
    },
    onSuccess: (content) => {
      setRendered(content);
      setError('');
    },
    onError: (failure) => setError(errorMessage(failure)),
  });

  const submit = useMutation({
    mutationFn: async () => {
      if (!selectedTemplate || !selectedSignature) throw new Error('请选择已审核通过的模板和签名');
      if (recipients.length === 0) throw new Error('请至少输入一个手机号');
      const scopedVariables = Object.fromEntries(
        selectedTemplate.variableNames.map((name) => [name, variables[name] ?? '']),
      );
      const responses: TenantConsoleSendResponse[] = [];
      for (const [index, phoneNumber] of recipients.entries()) {
        responses.push(await sendTenantConsoleMessage({
          submitId: `${correlationId}-${index}`,
          phoneNumber,
          templateId: String(selectedTemplate.id),
          signId: String(selectedSignature.id),
          templateParams: scopedVariables,
        }));
      }
      return responses;
    },
    onSuccess: (responses) => {
      setResults(responses);
      setError('');
      setNetworkRetry(false);
      setCorrelationId(newCorrelationId());
    },
    onError: (failure) => {
      setError(errorMessage(failure));
      setNetworkRetry(isNetworkFailure(failure));
    },
  });

  function updateVariable(name: string, value: string) {
    setVariables((current) => ({ ...current, [name]: value }));
    setRendered('');
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    submit.mutate();
  }

  return (
    <section className="tenant-send-page" data-testid="tenant-tenant-console-send-page">
      <nav aria-label="面包屑">发送管理 / 在线发送</nav>
      <header className="tenant-send-header">
        <div>
          <h1>在线发送</h1>
          <p className="page-description">使用已审核模板和签名发送单条或小批量短信，提交走控制台 JWT 会话。</p>
        </div>
      </header>

      {error && (
        <div role="alert" className="tenant-send-alert error">
          <p>{error}</p>
          {networkRetry && (
            <button
              type="button"
              data-testid="shared-tenant-console-send-network-error-retry"
              onClick={() => submit.mutate()}
              disabled={submit.isPending}
            >
              重试
            </button>
          )}
        </div>
      )}
      {results.length > 0 && (
        <p role="status" className="tenant-send-alert success">
          已提交 {results.length} 条，首条消息ID：{results[0]?.messageId}
        </p>
      )}

      <form className="card tenant-send-form" onSubmit={handleSubmit}>
        <label>模板
          <select
            data-testid="tenant-tenant-console-send-template"
            value={selectedTemplate ? String(selectedTemplate.id) : ''}
            onChange={(event) => { setTemplateId(event.target.value); setRendered(''); }}
          >
            {approvedTemplates.map((template) => (
              <option key={template.id} value={template.id}>{template.templateName}</option>
            ))}
          </select>
        </label>
        <label>签名
          <input
            data-testid="tenant-tenant-console-send-signature"
            value={selectedSignature ? selectedSignature.signContent : '无可用签名'}
            readOnly
          />
        </label>
        <label className="tenant-send-wide">收件手机号
          <textarea
            data-testid="tenant-tenant-console-send-recipients"
            value={recipientsText}
            onChange={(event) => setRecipientsText(event.target.value)}
            placeholder="一行一个手机号，小批量最多 20 个"
          />
        </label>

        {selectedTemplate?.variableNames.map((name) => (
          <label key={name}>{name}
            <input
              data-testid={`tenant-tenant-console-send-variable-${name}`}
              value={variables[name] ?? ''}
              onChange={(event) => updateVariable(name, event.target.value)}
            />
          </label>
        ))}

        <section className="tenant-send-wide" data-testid="tenant-tenant-console-send-preview">
          <h2>发送预览</h2>
          <p>{rendered || selectedTemplate?.content || '请选择模板后预览'}</p>
          <button type="button" className="button-secondary" onClick={() => preview.mutate()} disabled={!selectedTemplate || preview.isPending}>生成预览</button>
        </section>

        <p className="tenant-send-wide" data-testid="tenant-tenant-console-send-correlation">
          本次提交流水：{correlationId}
        </p>

        <button
          type="submit"
          data-testid="tenant-tenant-console-send-submit"
          disabled={!selectedTemplate || !selectedSignature || submit.isPending}
        >
          {submit.isPending ? '提交中…' : `发送 ${recipients.length || 0} 条`}
        </button>
      </form>
    </section>
  );
}
