import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as identityApi from '@/api/identity';
import * as frequencyRuleApi from '@/api/frequencyRuleApi';
import FrequencyRulesPage from '@/pages/admin/risk/FrequencyRulesPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/identity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/identity')>();
  return { ...actual, getAccountOverview: vi.fn() };
});

vi.mock('@/api/frequencyRuleApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/frequencyRuleApi')>();
  return {
    ...actual,
    listFrequencyRules: vi.fn(),
    saveFrequencyRule: vi.fn(),
    importFrequencyRules: vi.fn(),
    requestFrequencyExport: vi.fn(),
    disableFrequencyRule: vi.fn(),
    enableFrequencyRule: vi.fn(),
    frequencyAnalytics: vi.fn(),
  };
});

const rule = {
  id: 1,
  ruleName: '同号秒级限制',
  limitType: 'MOBILE',
  limitCount: 3,
  limitWindowSeconds: 1,
  action: 'BLOCK',
  scope: 'GLOBAL',
  scopeRefId: null,
  status: 'ACTIVE',
  hitCount: 7,
  createdAt: '2026-09-09T00:00:00',
  updatedAt: '2026-09-09T00:00:00',
};

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Phase 18 frequency and API rate controls UI', () => {
  beforeEach(() => {
    vi.mocked(identityApi.getAccountOverview).mockResolvedValue({
      id: 7,
      username: 'operator-7',
      userType: 'OPERATOR',
      roleNames: ['频控管理员'],
      permissions: [
        { code: 'frequency:menu', resourceType: 'MENU' },
        { code: 'frequency:read', resourceType: 'API' },
        { code: 'frequency:write', resourceType: 'API' },
        { code: 'frequency:import', resourceType: 'BUTTON' },
        { code: 'frequency:export', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    });
    vi.mocked(frequencyRuleApi.listFrequencyRules).mockResolvedValue([rule]);
    vi.mocked(frequencyRuleApi.saveFrequencyRule).mockResolvedValue(rule);
    vi.mocked(frequencyRuleApi.importFrequencyRules).mockResolvedValue({ success: 2, failed: 0, errors: [] });
    vi.mocked(frequencyRuleApi.requestFrequencyExport).mockResolvedValue({ requestId: 'FREQUENCY_EXPORT_1', matchedRows: 1, status: 'REQUESTED' });
    vi.mocked(frequencyRuleApi.disableFrequencyRule).mockResolvedValue({ ...rule, status: 'DISABLED' });
    vi.mocked(frequencyRuleApi.frequencyAnalytics).mockResolvedValue({
      total: 3, active: 2, hits: 10, blocked: 4, delayed: 5, alerts: 1, blockRate: 0.4, coverageRate: 0.67,
    });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '18:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('manages rules, import/export, hot update, and high concurrency feedback', async () => {
    renderWithProviders(<FrequencyRulesPage />);

    await screen.findByTestId('admin-frequency-api-frequency-rules-page');
    expect(await screen.findByTestId('admin-frequency-api-frequency-rules-row')).toHaveTextContent('同号秒级限制');
    expect(await screen.findByText('今日拦截 4')).toBeInTheDocument();
    expect(screen.getByTestId('admin-frequency-api-frequency-rules-filter-status')).toHaveValue('ACTIVE');
    expect(screen.getByTestId('admin-frequency-api-frequency-rules-name')).toHaveValue('同号秒级限制');
    expect(screen.getByTestId('admin-frequency-api-frequency-rules-type')).toHaveValue('MOBILE');
    expect(screen.getByTestId('admin-frequency-api-frequency-rules-window')).toHaveValue('1');
    expect(screen.getByTestId('shared-frequency-api-queued-feedback')).toHaveTextContent('429');

    fireEvent.click(screen.getByTestId('admin-frequency-api-frequency-rules-save'));
    await waitFor(() => expect(frequencyRuleApi.saveFrequencyRule).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-frequency-api-frequency-rules-import'));
    await waitFor(() => expect(frequencyRuleApi.importFrequencyRules).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-frequency-api-frequency-rules-export'));
    await waitFor(() => expect(frequencyRuleApi.requestFrequencyExport).toHaveBeenCalled());
  });
});
