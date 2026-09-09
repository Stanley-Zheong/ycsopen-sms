import { useEffect, useState } from 'react';

export const INTERNAL_ERROR_EVENT = 'ycsopen-sms:internal-error';

export type InternalErrorDetail = {
  traceId?: string | null;
};

/**
 * Displays a safe, support-friendly message for unexpected API failures.
 * The backend correlation identifier is shown without exposing exception details.
 */
export default function InternalErrorNotice() {
  const [traceId, setTraceId] = useState<string | null | undefined>();

  useEffect(() => {
    const onInternalError = (event: Event) => {
      setTraceId((event as CustomEvent<InternalErrorDetail>).detail?.traceId ?? null);
    };
    window.addEventListener(INTERNAL_ERROR_EVENT, onInternalError);
    return () => window.removeEventListener(INTERNAL_ERROR_EVENT, onInternalError);
  }, []);

  if (traceId === undefined) {
    return null;
  }

  return (
    <aside className="internal-error-notice" role="alert" data-testid="shared-console-identity-internal-error-message">
      <span>系统繁忙，请稍后再试。</span>
      {traceId ? (
        <span data-testid="shared-console-identity-internal-error-trace-id">问题编号：{traceId}</span>
      ) : null}
      <button
        type="button"
        onClick={() => setTraceId(undefined)}
        aria-label="关闭错误提示"
        data-testid="shared-console-identity-internal-error-dismiss"
      >
        关闭
      </button>
    </aside>
  );
}
