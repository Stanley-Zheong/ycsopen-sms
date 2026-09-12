import { FormEvent, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  listTenantTemplates,
  previewTemplate,
  resubmitTemplate,
  submitTemplateApplication,
  type TemplatePayload,
  type TemplateRecord,
} from '@/api/templateLifecycleApi';
import { mutationErrorMessage } from '@/api/client';
import ModalDialog from '@/components/common/ModalDialog';
import '@/styles/template-lifecycle.css';

const emptyForm: TemplatePayload = {
  templateName: '',
  content: '您的验证码是 ${code}',
  templateType: 'verification',
  signatureId: 0,
  paramCheckRule: 'code:digits(4-8)',
  description: '',
};

export default function TemplateLifecyclePage() {
  const [form, setForm] = useState<TemplatePayload>(emptyForm);
  const [previewTarget, setPreviewTarget] = useState<TemplateRecord | null>(null);
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [createError, setCreateError] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const templates = useQuery({ queryKey: ['tenant-templates'], queryFn: listTenantTemplates, retry: false });
  const previewTemplateRecord = previewTarget ?? templates.data?.[0] ?? null;
  const previewVariables = previewTemplateRecord?.variableNames ?? [];

  const submit = useMutation({
    mutationFn: () => submitTemplateApplication(form),
    onSuccess: async () => {
      setMessage('模板申请已提交。');
      setError('');
      await templates.refetch();
      setCreateOpen(false);
      setCreateError('');
      setForm(emptyForm);
    },
    onError: (failure) => {
      setMessage('');
      setCreateError(mutationErrorMessage(failure, '模板申请失败'));
    },
  });

  const previewMutation = useMutation({
    mutationFn: () => {
      const target = previewTemplateRecord;
      if (!target) throw new Error('未选择模板');
      const scopedVariables = Object.fromEntries(
        target.variableNames.map((name) => [name, variables[name] ?? '']),
      );
      return previewTemplate(target.id, scopedVariables);
    },
    onSuccess: (rendered) => {
      setPreview(rendered);
      setError('');
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '模板预览失败')),
  });

  const resubmit = useMutation({
    mutationFn: (template: TemplateRecord) => resubmitTemplate(template.id, {
      templateName: template.templateName,
      content: template.content,
      templateType: template.templateType.toLowerCase(),
      signatureId: template.signatureId,
      paramCheckRule: template.paramCheckRule ?? '',
      description: template.description ?? '重新提交',
    }),
    onSuccess: async () => {
      setMessage('模板重新提交成功。');
      setError('');
      await templates.refetch();
    },
    onError: (failure) => setError(mutationErrorMessage(failure, '模板重新提交失败')),
  });

  function update<K extends keyof TemplatePayload>(key: K, value: TemplatePayload[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function submitForm(event: FormEvent) {
    event.preventDefault();
    submit.mutate();
  }

  return (
    <section data-testid="tenant-template-lifecycle-templates-page">
      <nav aria-label="面包屑">模板管理 / 模板申请</nav>
      <header className="template-lifecycle-header">
        <div>
          <h1>模板管理</h1>
          <p className="page-description">提交国内短信模板，绑定已审核签名，并验证变量规则。</p>
        </div>
        <button type="button" data-testid="tenant-template-lifecycle-templates-create-open" onClick={() => { setCreateError(''); setCreateOpen(true); }}>新建模板申请</button>
      </header>
      {message && <p role="status" className="template-lifecycle-alert success">{message}</p>}
      {error && <p role="alert" className="template-lifecycle-alert error">{error}</p>}

      {createOpen && (
        <ModalDialog labelledBy="template-application-create-title" onRequestClose={() => { setCreateOpen(false); setCreateError(''); }}>
          <form className="template-lifecycle-form" data-testid="tenant-template-lifecycle-templates-create-dialog" onSubmit={submitForm}>
            <h2 id="template-application-create-title">新建模板申请</h2>
            <label>模板名称<input required data-testid="tenant-template-lifecycle-templates-form-name" value={form.templateName} onChange={(event) => update('templateName', event.target.value)} /></label>
            <label>模板内容<textarea required data-testid="tenant-template-lifecycle-templates-form-content" value={form.content} onChange={(event) => update('content', event.target.value)} /></label>
            <label>模板类型<select data-testid="tenant-template-lifecycle-templates-form-type" value={form.templateType} onChange={(event) => update('templateType', event.target.value)}><option value="verification">验证码</option><option value="notification">通知</option><option value="marketing">营销</option></select></label>
            <label>绑定签名ID<input required min="1" type="number" data-testid="tenant-template-lifecycle-templates-form-signature" value={form.signatureId || ''} onChange={(event) => update('signatureId', Number(event.target.value))} /></label>
            <label>参数规则<input data-testid="tenant-template-lifecycle-templates-form-param-rule" value={form.paramCheckRule} onChange={(event) => update('paramCheckRule', event.target.value)} /></label>
            <label>申请说明<input value={form.description} onChange={(event) => update('description', event.target.value)} /></label>
            {createError && <p role="alert" data-testid="tenant-template-lifecycle-templates-create-error" className="template-lifecycle-alert error">{createError}</p>}
            <div className="dialog-actions"><button type="button" className="button-secondary" data-testid="tenant-template-lifecycle-templates-create-cancel" onClick={() => { setCreateOpen(false); setCreateError(''); }}>取消</button><button type="submit" data-testid="tenant-template-lifecycle-templates-submit" disabled={submit.isPending}>提交模板申请</button></div>
          </form>
        </ModalDialog>
      )}

      <section className="card">
        <h2>变量预览</h2>
        {previewVariables.length === 0 && <p>选择含变量的模板后可预览。</p>}
        {previewVariables.map((name) => (
          <label key={name}>{name}
            <input data-testid={`tenant-template-lifecycle-templates-variable-${name}`} value={variables[name] ?? ''} onChange={(event) => setVariables((current) => ({ ...current, [name]: event.target.value }))} />
          </label>
        ))}
        <button type="button" data-testid="tenant-template-lifecycle-templates-variable-preview" onClick={() => previewMutation.mutate()}>预览</button>
        {preview && <p>{preview}</p>}
      </section>

      <section className="card">
        <h2>模板列表</h2>
        {templates.isLoading && <p>正在加载…</p>}
        {(templates.data ?? []).map((template) => (
          <article key={template.id} data-testid="tenant-template-lifecycle-templates-row" className="template-lifecycle-row" onClick={() => setPreviewTarget(template)}>
            <strong>{template.templateName}</strong>
            <span>{template.auditStatus} / v{template.versionNo}</span>
            <span>{template.content}</span>
            <span>{template.auditComment ?? '暂无审核意见'}</span>
            <button type="button" data-testid="tenant-template-lifecycle-templates-resubmit" onClick={(event) => { event.stopPropagation(); resubmit.mutate(template); }} disabled={!['REJECTED', 'AMENDMENT_REQUIRED'].includes(template.auditStatus)}>重新提交</button>
          </article>
        ))}
      </section>
    </section>
  );
}
