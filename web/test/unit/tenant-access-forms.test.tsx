import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as tenantAccessApi from '@/api/tenantAccessApi';
import TenantApiKeysPage from '@/pages/tenant/TenantApiKeysPage';
import TenantCmppAccessPage from '@/pages/tenant/TenantCmppAccessPage';

vi.mock('@/api/tenantAccessApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/tenantAccessApi')>();
  return {
    ...actual,
    listTenantApiKeys: vi.fn(),
    createTenantApiKey: vi.fn(),
    revokeTenantApiKey: vi.fn(),
    listTenantCmpp: vi.fn(),
    createTenantCmpp: vi.fn(),
    revokeTenantCmpp: vi.fn(),
  };
});

describe('tenant access creation forms', () => {
  beforeEach(() => {
    vi.mocked(tenantAccessApi.listTenantApiKeys).mockResolvedValue([]);
    vi.mocked(tenantAccessApi.listTenantCmpp).mockResolvedValue([]);
    vi.mocked(tenantAccessApi.createTenantApiKey).mockResolvedValue({
      id: 1,
      appKey: 'app-key',
      name: 'automation',
      description: 'acceptance key',
      status: 'ACTIVE',
      ipWhitelist: '127.0.0.1/32',
      perSecond: 11,
      perMinute: 111,
      perHour: 1111,
      perDay: 11111,
      expireTime: '2099-01-01T00:00',
      lastUsedTime: null,
      secret: 'secret-once',
    });
    vi.mocked(tenantAccessApi.createTenantCmpp).mockResolvedValue({
      id: 2,
      protocol: 'CMPP',
      account: 'custom-account',
      spid: 'custom-spid',
      endpointHost: '127.0.0.1',
      endpointPort: 7891,
      maxConnections: 5,
      tpsLimit: 101,
      windowSize: 9,
      ipWhitelist: null,
      status: 'ACTIVE',
      password: 'secret-password',
    });
  });

  afterEach(() => vi.clearAllMocks());

  it('submits every editable API key field', async () => {
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-page');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));

    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'automation' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-description'), { target: { value: 'acceptance key' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-expiry'), { target: { value: '2099-01-01T00:00' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-ip-allow-list'), { target: { value: '127.0.0.1/32' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-rate-second'), { target: { value: '11' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-rate-minute'), { target: { value: '111' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-rate-hour'), { target: { value: '1111' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-rate-day'), { target: { value: '11111' } });
    fireEvent.click(screen.getByRole('button', { name: /^创建$/ }));

    await waitFor(() => expect(tenantAccessApi.createTenantApiKey).toHaveBeenCalledWith({
      name: 'automation',
      description: 'acceptance key',
      expireTime: '2099-01-01T00:00',
      ipWhitelist: '127.0.0.1/32',
      perSecond: 11,
      perMinute: 111,
      perHour: 1111,
      perDay: 11111,
    }));
  });

  it('submits every editable CMPP field', async () => {
    render(<TenantCmppAccessPage />);
    await screen.findByTestId('tenant-tenant-access-cmpp-access-page');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-cmpp-request-form'));

    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-host'), { target: { value: '127.0.0.1' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-port'), { target: { value: '7891' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-spid'), { target: { value: 'custom-spid' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-account'), { target: { value: 'custom-account' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-password'), { target: { value: 'secret-password' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-max-connections'), { target: { value: '5' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-tps-limit'), { target: { value: '101' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-window-size'), { target: { value: '9' } });
    fireEvent.click(screen.getByRole('button', { name: /^提交申请$/ }));

    await waitFor(() => expect(tenantAccessApi.createTenantCmpp).toHaveBeenCalledWith({
      spid: 'custom-spid',
      endpointHost: '127.0.0.1',
      endpointPort: 7891,
      account: 'custom-account',
      password: 'secret-password',
      ipWhitelist: null,
      maxConnections: 5,
      tpsLimit: 101,
      windowSize: 9,
    }));
  });

  it('rejects invalid rate and CMPP connection policies before calling the API', async () => {
    const apiKeyView = render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-page');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'invalid-policy' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-rate-second'), { target: { value: '200' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-rate-minute'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: /^创建$/ }));
    expect(tenantAccessApi.createTenantApiKey).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('速率必须');
    apiKeyView.unmount();

    render(<TenantCmppAccessPage />);
    await screen.findByTestId('tenant-tenant-access-cmpp-access-page');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-cmpp-request-form'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-host'), { target: { value: '127.0.0.1' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-password'), { target: { value: 'secret-password' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-max-connections'), { target: { value: '1001' } });
    fireEvent.click(screen.getByRole('button', { name: /^提交申请$/ }));
    expect(tenantAccessApi.createTenantCmpp).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('连接策略');
  });

  it('prevents duplicate API key submission and reports request failures for both forms', async () => {
    let rejectApiKey: (reason?: unknown) => void = () => undefined;
    vi.mocked(tenantAccessApi.createTenantApiKey).mockImplementationOnce(() => new Promise((_, reject) => {
      rejectApiKey = reject;
    }));
    const apiKeyView = render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-page');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'single-submit' } });
    const createButton = screen.getByRole('button', { name: /^创建$/ });
    fireEvent.click(createButton);
    expect(createButton).toBeDisabled();
    fireEvent.click(createButton);
    expect(tenantAccessApi.createTenantApiKey).toHaveBeenCalledTimes(1);
    await act(async () => rejectApiKey(new Error('request failed')));
    expect(await screen.findByRole('alert')).toHaveTextContent('创建 API Key 失败');
    apiKeyView.unmount();

    vi.mocked(tenantAccessApi.createTenantCmpp).mockRejectedValueOnce(new Error('request failed'));
    render(<TenantCmppAccessPage />);
    await screen.findByTestId('tenant-tenant-access-cmpp-access-page');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-cmpp-request-form'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-host'), { target: { value: '127.0.0.1' } });
    fireEvent.change(screen.getByTestId('tenant-tenant-access-cmpp-password'), { target: { value: 'secret-password' } });
    fireEvent.click(screen.getByRole('button', { name: /^提交申请$/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('CMPP 申请失败');
  });
});
