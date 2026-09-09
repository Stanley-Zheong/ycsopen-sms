import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import InternalErrorNotice, {
  INTERNAL_ERROR_EVENT,
  type InternalErrorDetail,
} from '@/components/common/InternalErrorNotice';

describe('InternalErrorNotice', () => {
  it('shows only the safe message and correlation identifier', () => {
    render(<InternalErrorNotice />);

    act(() => {
      window.dispatchEvent(new CustomEvent<InternalErrorDetail>(INTERNAL_ERROR_EVENT, {
        detail: { traceId: 'trace-123' },
      }));
    });

    expect(screen.getByTestId('shared-console-identity-internal-error-message')).toHaveTextContent(
      '系统繁忙，请稍后再试。',
    );
    expect(screen.getByTestId('shared-console-identity-internal-error-trace-id')).toHaveTextContent('trace-123');
    expect(screen.queryByText(/Exception|stack/i)).not.toBeInTheDocument();
  });

  it('can be dismissed', () => {
    render(<InternalErrorNotice />);
    act(() => {
      window.dispatchEvent(new CustomEvent<InternalErrorDetail>(INTERNAL_ERROR_EVENT, { detail: {} }));
    });

    fireEvent.click(screen.getByTestId('shared-console-identity-internal-error-dismiss'));
    expect(screen.queryByTestId('shared-console-identity-internal-error-message')).not.toBeInTheDocument();
  });
});
