import { useSearchParams } from 'react-router-dom';
import '@/styles/shortlink-safety.css';

export default function PublicShortLinkSafePage() {
  const [params] = useSearchParams();
  const state = params.get('state') ?? 'pending';
  const content = state === 'expired'
    ? <h1 data-testid="public-shortlink-safety-expired-page">短链已过期，平台不会跳转</h1>
    : state === 'offline'
      ? <h1 data-testid="public-shortlink-safety-offline-page">短链目标巡检异常，已下线</h1>
      : state === 'rejected'
        ? <h1 data-testid="public-shortlink-safety-rejected-page">短链未通过审核，已停止跳转</h1>
        : <h1 data-testid="public-shortlink-safety-pending-page">短链待审核，暂不跳转</h1>;
  return (
    <main className="shortlink-safe-page">
      {content}
      <p data-testid="public-shortlink-safe-message">为了防止未审核、已拒绝、已过期或已下线目标造成风险，本页面不包含目标站点跳转动作。</p>
    </main>
  );
}
