import { useEffect, useRef } from 'react';
import ModalDialog from '@/components/common/ModalDialog';

export default function ActionReasonDialog({
  idPrefix,
  title,
  target,
  consequence,
  reasonLabel,
  reasonTestId,
  reason,
  placeholder,
  maxLength = 500,
  confirmLabel,
  pending,
  reasonReadOnly = false,
  onReasonChange,
  onCancel,
  onConfirm,
}: {
  idPrefix: string;
  title: string;
  target: string;
  consequence: string;
  reasonLabel: string;
  reasonTestId: string;
  reason: string;
  placeholder: string;
  maxLength?: number;
  confirmLabel: string;
  pending: boolean;
  reasonReadOnly?: boolean;
  onReasonChange: (reason: string) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const titleId = `${idPrefix}-title`;
  const targetId = `${idPrefix}-target-description`;
  const consequenceId = `${idPrefix}-consequence`;
  const reasonId = `${idPrefix}-reason-field`;
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const submitLatched = useRef(false);
  const observedPending = useRef(pending);

  useEffect(() => {
    if (pending) {
      observedPending.current = true;
      reasonRef.current?.focus();
    } else if (observedPending.current) {
      submitLatched.current = false;
      observedPending.current = false;
    }
  }, [pending]);

  return (
    <div className="action-reason-dialog-backdrop">
      <ModalDialog labelledBy={titleId} onRequestClose={() => { if (!pending) onCancel(); }}>
        <form
          className="action-reason-dialog"
          data-testid={`${idPrefix}-dialog`}
          aria-busy={pending}
          onSubmit={(event) => {
            event.preventDefault();
            if (!reason.trim() || pending || submitLatched.current) return;
            submitLatched.current = true;
            try {
              onConfirm();
            } catch (failure) {
              submitLatched.current = false;
              throw failure;
            }
          }}
        >
          <h2 id={titleId}>{title}</h2>
          <p id={targetId} className="action-reason-target" data-testid={`${idPrefix}-target`}>{target}</p>
          <p id={consequenceId} data-testid={`${idPrefix}-consequence`}>{consequence}</p>
          <label htmlFor={reasonId}>{reasonLabel} <span aria-hidden="true">*</span>
            <textarea
              ref={reasonRef}
              id={reasonId}
              data-testid={reasonTestId}
              rows={4}
              maxLength={maxLength}
              required
              readOnly={pending || reasonReadOnly}
              aria-describedby={`${targetId} ${consequenceId}`}
              value={reason}
              placeholder={placeholder}
              onChange={(event) => onReasonChange(event.target.value)}
            />
          </label>
          <div className="action-reason-dialog-actions">
            <button type="button" className="button-secondary" data-testid={`${idPrefix}-cancel`} disabled={pending} onClick={onCancel}>取消</button>
            <button type="submit" data-testid={`${idPrefix}-confirm`} disabled={!reason.trim() || pending}>{pending ? '提交中…' : confirmLabel}</button>
          </div>
        </form>
      </ModalDialog>
    </div>
  );
}
