import { FormEvent, useState } from 'react';
import { apiClient } from '@/api/client';

/**
 * F-6.10 在线发送（控制台手工发送）。注意：真正的 HTTP API（F-6.1）走独立的
 * HMAC 签名鉴权（见 core HmacAuthInterceptor），本页面走的是控制台 JWT 会话，
 * 生产实现需要后端为控制台内发送提供一个走会话鉴权的独立 endpoint（当前 core 暂未提供，
 * 见 web/docs/ROADMAP.md 与 core/docs/ROADMAP.md 的对应条目）。
 */
export default function SendPage() {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [templateId, setTemplateId] = useState('');
  const [result, setResult] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      const res = await apiClient.post('/sms/send', { phoneNumber, templateId, templateParams: {} });
      setResult(`提交成功，消息ID：${res.data.data.messageId}`);
    } catch (err: any) {
      setResult(`提交失败：${err.response?.data?.message ?? '未知错误'}`);
    }
  }

  return (
    <div className="card">
      <h2>手工发送</h2>
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 12 }}>
          <input placeholder="手机号" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} />
        </div>
        <div style={{ marginBottom: 12 }}>
          <input placeholder="模板ID" value={templateId} onChange={(e) => setTemplateId(e.target.value)} />
        </div>
        <button type="submit">发送</button>
      </form>
      {result && <p>{result}</p>}
    </div>
  );
}
