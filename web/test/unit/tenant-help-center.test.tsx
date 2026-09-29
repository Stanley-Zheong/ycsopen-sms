import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import TenantHelpCenterPage from '@/pages/tenant/help/TenantHelpCenterPage';

describe('tenant help center query controls', () => {
  it('applies the guide search from the shared query action', () => {
    render(
      <MemoryRouter>
        <TenantHelpCenterPage section="guide" />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('query-panel')).toBeVisible();
    expect(screen.getByTestId('query-fields')).toContainElement(screen.getByTestId('tenant-tenant-help-guide-search-input'));
    fireEvent.change(screen.getByTestId('tenant-tenant-help-guide-search-input'), { target: { value: '短链' } });
    expect(screen.getByTestId('tenant-tenant-help-guide-results')).toHaveTextContent('资质');
    fireEvent.click(screen.getByTestId('query-submit'));
    expect(screen.getByTestId('tenant-tenant-help-guide-results')).toHaveTextContent('短链管理');
    expect(screen.getByTestId('tenant-tenant-help-guide-results')).not.toHaveTextContent('资质');
  });

  it('resets the local guide filter and makes a local refresh observable', () => {
    render(
      <MemoryRouter>
        <TenantHelpCenterPage section="guide" />
      </MemoryRouter>,
    );

    const input = screen.getByTestId('tenant-tenant-help-guide-search-input');
    fireEvent.change(input, { target: { value: '短链' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    expect(screen.getByTestId('tenant-tenant-help-guide-query-status')).toHaveAttribute('data-state', 'success');

    fireEvent.click(screen.getByTestId('query-reset'));
    expect(input).toHaveValue('');
    expect(screen.getByTestId('tenant-tenant-help-guide-results')).toHaveTextContent('资质');

    fireEvent.click(screen.getByTestId('query-refresh'));
    expect(screen.getByTestId('tenant-tenant-help-guide-refresh-result')).toHaveTextContent('已刷新 1 次');
  });
});
