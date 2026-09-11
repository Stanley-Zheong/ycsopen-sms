import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as financialApi from '@/api/financialSourceAnalyticsApi';
import AdminFinancialAnalyticsPage from '@/pages/admin/billing/AdminFinancialAnalyticsPage';

vi.mock('@/api/financialSourceAnalyticsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/financialSourceAnalyticsApi')>();
  return {
    ...actual,
    listFinancialSummaries: vi.fn(),
    listFinancialDrilldown: vi.fn(),
  };
});

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 39 financial source analytics UI', () => {
  beforeEach(() => {
    vi.mocked(financialApi.listFinancialSummaries).mockResolvedValue([{
      tenantId: 42,
      channelId: 7,
      periodStart: '2026-09-01',
      periodEnd: '2026-09-30',
      sourceCount: 3,
      billableCount: 2,
      providerCostMil: 120,
      revenueMil: 100,
      profitMil: -20,
      priceBookVersion: 'SMS_STANDARD_V1',
      unitPriceMil: 50,
      formulaVersion: 'FINANCIAL_SOURCE_V1',
      formula: 'providerCostMil=sum(message_tasks.cost*1000), revenueMil=billableFinalCount*tenant_price_books.unit_price_mil, profitMil=revenueMil-providerCostMil',
      freshnessAt: '2026-09-03T12:00:00',
    }]);
    vi.mocked(financialApi.listFinancialDrilldown).mockResolvedValue([{
      taskId: 1001,
      messageId: 'MSG-39-A',
      tenantId: 42,
      channelId: 7,
      sendStatus: 'SENT',
      finalStatus: 'DELIVERED',
      providerCostMil: 40,
      revenueMil: 50,
      profitMil: 10,
      priceBookVersion: 'SMS_STANDARD_V1',
      unitPriceMil: 50,
      formulaVersion: 'FINANCIAL_SOURCE_V1',
      formula: 'providerCostMil=sum(message_tasks.cost*1000), revenueMil=billableFinalCount*tenant_price_books.unit_price_mil, profitMil=revenueMil-providerCostMil',
      freshnessAt: '2026-09-03T12:00:00',
    }]);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows finance and channel figures and drills down to source formula records', async () => {
    renderWithProviders(<AdminFinancialAnalyticsPage />);

    expect(await screen.findByTestId('admin-financial-source-financial-analytics-page')).toBeVisible();
    expect(screen.getByTestId('admin-financial-source-channel-statistics-page')).toBeVisible();
    await waitFor(() => expect(screen.getByTestId('admin-financial-source-financial-analytics-row')).toHaveTextContent('SMS_STANDARD_V1'));
    expect(screen.getByTestId('admin-financial-source-financial-analytics-row')).toHaveTextContent('-0.020');
    expect(screen.getByTestId('admin-financial-source-channel-statistics-row')).toHaveTextContent('2');

    fireEvent.change(screen.getByTestId('admin-financial-source-financial-analytics-start-date'), {
      target: { value: '2026-09-01' },
    });
    fireEvent.change(screen.getByTestId('admin-financial-source-financial-analytics-end-date'), {
      target: { value: '2026-09-10' },
    });
    fireEvent.click(screen.getByTestId('admin-financial-source-financial-analytics-apply'));
    fireEvent.click(screen.getByTestId('admin-financial-source-financial-analytics-drilldown'));

    await waitFor(() => expect(financialApi.listFinancialDrilldown).toHaveBeenCalledWith({
      startDate: '2026-09-01',
      endDate: '2026-09-10',
      tenantId: 42,
      channelId: 7,
    }));
    expect(await screen.findByTestId('admin-financial-source-financial-analytics-drilldown-row')).toHaveTextContent('MSG-39-A');
    expect(screen.getByTestId('admin-financial-source-financial-analytics-drilldown-row')).toHaveTextContent('FINANCIAL_SOURCE_V1');
  });
});
