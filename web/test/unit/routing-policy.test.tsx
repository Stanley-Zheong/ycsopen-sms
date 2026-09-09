import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as identityApi from '@/api/identity';
import * as routingApi from '@/api/routingPolicyApi';
import RoutingPolicyPage from '@/pages/admin/channels/RoutingPolicyPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/identity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/identity')>();
  return { ...actual, getAccountOverview: vi.fn() };
});

vi.mock('@/api/routingPolicyApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/routingPolicyApi')>();
  return {
    ...actual,
    listRoutingVersions: vi.fn(),
    listRoutingRules: vi.fn(),
    importRoutingPolicy: vi.fn(),
    simulateRouting: vi.fn(),
    listCircuitStates: vi.fn(),
    recordCircuit: vi.fn(),
    saveRetryPolicy: vi.fn(),
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

describe('Phase 21 routing circuit policy UI', () => {
  beforeEach(() => {
    vi.mocked(identityApi.getAccountOverview).mockResolvedValue({
      id: 21,
      username: 'operator-21',
      userType: 'OPERATOR',
      roleNames: ['路由管理员'],
      permissions: [
        { code: 'routing-policy:menu', resourceType: 'MENU' },
        { code: 'routing-policy:read', resourceType: 'API' },
        { code: 'routing-policy:write', resourceType: 'API' },
        { code: 'routing-policy:import', resourceType: 'BUTTON' },
      ],
      lastLoginAt: null,
      lastLoginIp: null,
    });
    vi.mocked(routingApi.listRoutingVersions).mockResolvedValue([
      { id: 1, versionNo: 'RP20260909', status: 'ACTIVE', sourceName: '运营策略', effectiveAt: '2026-09-09T00:00:00', actor: 'operator', createdAt: '2026-09-09T00:00:00' },
    ]);
    vi.mocked(routingApi.listRoutingRules).mockResolvedValue([
      { id: 1, versionNo: 'RP20260909', priority: 10, conditionType: 'CARRIER', conditionValue: 'MOBILE', targetType: 'CHANNEL', targetRef: 'CH_MAIN', weight: 100, status: 'ACTIVE' },
    ]);
    vi.mocked(routingApi.listCircuitStates).mockResolvedValue([
      { id: 1, channelCode: 'CH_MAIN', status: 'CLOSED', failureCount: 1, successCount: 5, latencyMs: 120, history: 'init -> success:CLOSED' },
    ]);
    vi.mocked(routingApi.importRoutingPolicy).mockResolvedValue({ versionNo: 'RP20260909', imported: 1, status: 'ACTIVE' });
    vi.mocked(routingApi.simulateRouting).mockResolvedValue({
      versionNo: 'RP20260909',
      matchedRuleId: 1,
      targetType: 'CHANNEL',
      targetRef: 'CH_MAIN',
      explanation: 'rule 1 matched version RP20260909 target CHANNEL/CH_MAIN',
      circuitStatus: 'CLOSED',
      retryPolicy: { normalizedCategory: 'FAILURE', retryable: true, delaySeconds: 30, maxAttempts: 3, status: 'DEFAULT' },
    });
    vi.mocked(routingApi.recordCircuit).mockResolvedValue({ id: 1, channelCode: 'CH_MAIN', status: 'OPEN', failureCount: 3, successCount: 5, latencyMs: 900, history: 'failure:OPEN' });
    vi.mocked(routingApi.saveRetryPolicy).mockResolvedValue({ normalizedCategory: 'FAILURE', retryable: true, delaySeconds: 30, maxAttempts: 3, status: 'ACTIVE' });
    useAuthStore.setState({
      accessToken: 'test-token',
      userType: 'OPERATOR',
      tenantId: null,
      expiresAt: Date.now() + 60_000,
      principalKey: '21:test-token',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
    act(() => useAuthStore.getState().logout());
  });

  it('imports, simulates, records circuit state, and saves retry policy', async () => {
    renderWithProviders(<RoutingPolicyPage />);

    await screen.findByTestId('admin-routing-circuit-routing-policy-page');
    expect(await screen.findByTestId('admin-routing-circuit-routing-policy-row')).toHaveTextContent('CH_MAIN');
    expect(await screen.findByTestId('admin-routing-circuit-routing-circuit-row')).toHaveTextContent('CLOSED');

    fireEvent.click(screen.getByTestId('admin-routing-circuit-routing-policy-import'));
    await waitFor(() => expect(routingApi.importRoutingPolicy).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-routing-circuit-routing-policy-simulate'));
    await waitFor(() => expect(screen.getByTestId('admin-routing-circuit-routing-policy-simulation-result')).toHaveTextContent('CH_MAIN'));
    fireEvent.click(screen.getByTestId('admin-routing-circuit-routing-circuit-record'));
    await waitFor(() => expect(routingApi.recordCircuit).toHaveBeenCalled());
    fireEvent.click(screen.getByTestId('admin-routing-circuit-routing-retry-save'));
    await waitFor(() => expect(routingApi.saveRetryPolicy).toHaveBeenCalled());
  });
});

