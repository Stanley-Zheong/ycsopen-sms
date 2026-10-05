import { type KeyboardEvent, type ReactNode, useEffect, useRef } from 'react';
import '@/styles/modal-dialog.css';

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])';

/** Accessible desktop modal with focus entry, trapping, Escape handling, and focus restoration. */
export default function ModalDialog({ labelledBy, onRequestClose, dismissible = true, children }: {
  labelledBy: string;
  onRequestClose: () => void;
  dismissible?: boolean;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    ref.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    return () => opener?.focus();
  }, []);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      if (dismissible) onRequestClose();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = Array.from(ref.current?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
    if (!focusable.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="modal-backdrop" data-testid="modal-backdrop">
      <div ref={ref} className="card modal-dialog" role="dialog" aria-modal="true" aria-labelledby={labelledBy} data-testid="modal" onKeyDown={onKeyDown}>
        {children}
      </div>
    </div>
  );
}
