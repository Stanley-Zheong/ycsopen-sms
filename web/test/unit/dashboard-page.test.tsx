import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchComplaintRatio } from '@/api/dashboard';
import DashboardPage from '@/pages/admin/dashboard/DashboardPage';

vi.mock('@/api/dashboard', () => ({ fetchComplaintRatio: vi.fn() }));

describe('admin dashboard release boundary', () => {
  beforeEach(() => {
    vi.mocked(fetchComplaintRatio).mockResolvedValue([]);
  });

  it('exposes the stable dashboard page selector used by release acceptance', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <DashboardPage />
      </QueryClientProvider>,
    );

    expect(await screen.findByTestId('admin-dashboard-page')).toBeVisible();
  });
});
