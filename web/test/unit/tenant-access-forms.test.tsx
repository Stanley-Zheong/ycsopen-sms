import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as tenantAccessApi from '@/api/tenantAccessApi';
import TenantApiKeysPage from '@/pages/tenant/TenantApiKeysPage';
import TenantCmppAccessPage from '@/pages/tenant/TenantCmppAccessPage';
import { useAuthStore } from '@/store/authStore';

vi.mock('@/api/tenantAccessApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/tenantAccessApi')>();
  return {
    ...actual,
    listTenantApiKeys: vi.fn(),
    listTenantApiKeyAudits: vi.fn(),
    createTenantApiKey: vi.fn(),
    revokeTenantApiKey: vi.fn(),
    listTenantCmpp: vi.fn(),
    createTenantCmpp: vi.fn(),
    revokeTenantCmpp: vi.fn(),
  };
});

describe('tenant access creation forms', () => {
  beforeEach(() => {
    useAuthStore.setState({ userType: 'TENANT_ADMIN', tenantId: 7 });
    vi.mocked(tenantAccessApi.listTenantApiKeys).mockResolvedValue([]);
    vi.mocked(tenantAccessApi.listTenantApiKeyAudits).mockResolvedValue([]);
    vi.mocked(tenantAccessApi.revokeTenantApiKey).mockResolvedValue(null);
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
      appSecretMask: '******',
      appSecret: 'secret-once',
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

  afterEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ userType: null, tenantId: null });
  });

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
    })).mockRejectedValueOnce(new Error('second request failed'));
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
    expect(await screen.findByTestId('tenant-tenant-access-api-keys-create-unknown')).toHaveTextContent('创建结果未知');
    expect(screen.getByTestId('form-submit')).toBeDisabled();
    expect(screen.getByTestId('form-cancel')).toHaveTextContent('关闭并检查列表');
    fireEvent.click(screen.getByTestId('form-cancel'));
    expect(screen.getByTestId('tenant-tenant-access-api-keys-create-unknown-guard')).toHaveTextContent('single-submit');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'single-submit' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    expect(within(screen.getByTestId('entity-form')).getByRole('alert')).toHaveTextContent('上一次同名创建结果仍未知');
    expect(tenantAccessApi.createTenantApiKey).toHaveBeenCalledTimes(1);
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'second-unknown' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    expect(await screen.findByTestId('tenant-tenant-access-api-keys-create-unknown')).toBeVisible();
    expect(tenantAccessApi.createTenantApiKey).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByTestId('form-cancel'));
    expect(screen.getByTestId('tenant-tenant-access-api-keys-create-unknown-guard')).toHaveTextContent('single-submit、second-unknown');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'single-submit' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    expect(within(screen.getByTestId('entity-form')).getByRole('alert')).toHaveTextContent('上一次同名创建结果仍未知');
    expect(tenantAccessApi.createTenantApiKey).toHaveBeenCalledTimes(2);
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

  it('keeps the one-time secret visible across list failure until explicit acknowledgement', async () => {
    vi.mocked(tenantAccessApi.listTenantApiKeys)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error('refresh failed'));
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-empty');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'secret-safe' } });
    fireEvent.click(screen.getByTestId('form-submit'));

    const secret = await screen.findByTestId('tenant-tenant-access-api-keys-secret-once');
    expect(secret).toHaveTextContent('secret-once');
    fireEvent.keyDown(screen.getByTestId('modal'), { key: 'Escape' });
    expect(secret).toBeVisible();
    expect(await screen.findByTestId('tenant-tenant-access-api-keys-error')).toBeVisible();
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-secret-acknowledge'));
    expect(screen.queryByText('secret-once')).not.toBeInTheDocument();
  });

  it('gates every mutation entry while post-create authorization reads are unresolved', async () => {
    let resolveKeys: (value: Awaited<ReturnType<typeof tenantAccessApi.listTenantApiKeys>>) => void = () => undefined;
    let resolveAudits: (value: Awaited<ReturnType<typeof tenantAccessApi.listTenantApiKeyAudits>>) => void = () => undefined;
    const existing = {
      id: 41, appKey: 'public-app-key', name: 'integration', description: null,
      status: 'ACTIVE', ipWhitelist: null, perSecond: 10, perMinute: 100,
      perHour: 1_000, perDay: 10_000, expireTime: null, lastUsedTime: null,
      appSecretMask: '******',
    };
    vi.mocked(tenantAccessApi.listTenantApiKeys)
      .mockResolvedValueOnce([existing])
      .mockImplementationOnce(() => new Promise((resolve) => { resolveKeys = resolve; }));
    vi.mocked(tenantAccessApi.listTenantApiKeyAudits)
      .mockResolvedValueOnce([])
      .mockImplementationOnce(() => new Promise((resolve) => { resolveAudits = resolve; }));
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-row');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'background-read' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    await screen.findByTestId('tenant-tenant-access-api-keys-secret-once');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-secret-acknowledge'));

    const createButton = screen.getByTestId('tenant-tenant-access-api-keys-create-dialog');
    const revokeButton = screen.getByTestId('tenant-tenant-access-api-keys-revoke');
    expect(createButton).toBeDisabled();
    expect(revokeButton).toBeDisabled();
    fireEvent.click(createButton);
    fireEvent.click(revokeButton);
    expect(screen.queryByTestId('entity-form')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-revoke-dialog')).not.toBeInTheDocument();
    expect(tenantAccessApi.createTenantApiKey).toHaveBeenCalledTimes(1);
    expect(tenantAccessApi.revokeTenantApiKey).not.toHaveBeenCalled();

    await act(async () => {
      resolveKeys([existing]);
      resolveAudits([]);
    });
    await waitFor(() => expect(createButton).toBeEnabled());
    expect(revokeButton).toBeEnabled();
  });

  it('renders masks and uses a controlled revoke confirmation', async () => {
    vi.mocked(tenantAccessApi.listTenantApiKeys).mockResolvedValue([{
      id: 41, appKey: 'public-app-key', name: 'integration', description: null,
      status: 'ACTIVE', ipWhitelist: null, perSecond: 10, perMinute: 100,
      perHour: 1_000, perDay: 10_000, expireTime: null, lastUsedTime: '2026-10-05T10:30:00Z',
      appSecretMask: '******',
    }]);
    render(<TenantApiKeysPage />);

    expect(await screen.findByTestId('tenant-tenant-access-api-keys-secret-mask')).toHaveTextContent('******');
    expect(screen.getByTestId('tenant-tenant-access-api-keys-last-used-time'))
      .toHaveTextContent('2026/10/5 18:30:00');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke'));
    const dialog = screen.getByTestId('tenant-tenant-access-api-keys-revoke-dialog');
    expect(dialog).toHaveTextContent('integration');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke-cancel'));
    expect(tenantAccessApi.revokeTenantApiKey).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke'));
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke-confirm'));
    await waitFor(() => expect(tenantAccessApi.revokeTenantApiKey).toHaveBeenCalledWith(41));
  });

  it('clears tenant data and closes create UI when an in-flight read is denied', async () => {
    let rejectKeys: (reason?: unknown) => void = () => undefined;
    vi.mocked(tenantAccessApi.listTenantApiKeys).mockImplementationOnce(() => new Promise((_, reject) => {
      rejectKeys = reject;
    }));
    render(<TenantApiKeysPage />);
    const createButton = screen.getByTestId('tenant-tenant-access-api-keys-create-dialog');
    expect(createButton).toBeDisabled();
    fireEvent.click(createButton);
    expect(screen.queryByTestId('entity-form')).not.toBeInTheDocument();

    await act(async () => rejectKeys(Object.assign(new Error('forbidden'), { response: { status: 403 } })));

    expect(await screen.findByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
    expect(screen.queryByTestId('entity-form')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-create-dialog')).not.toBeInTheDocument();
    expect(tenantAccessApi.createTenantApiKey).not.toHaveBeenCalled();
  });

  it('does not reveal a late create success after a concurrent authorization read is denied', async () => {
    let resolveCreate: (value: Awaited<ReturnType<typeof tenantAccessApi.createTenantApiKey>>) => void = () => undefined;
    vi.mocked(tenantAccessApi.createTenantApiKey).mockImplementationOnce(() => new Promise((resolve) => {
      resolveCreate = resolve;
    }));
    vi.mocked(tenantAccessApi.listTenantApiKeys)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(Object.assign(new Error('forbidden'), { response: { status: 403 } }));
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-empty');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'late-success' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-refresh'));

    expect(await screen.findByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
    await act(async () => resolveCreate({
      id: 91, appKey: 'late-key', name: 'late-success', description: null, status: 'ACTIVE',
      ipWhitelist: null, perSecond: 10, perMinute: 100, perHour: 1_000, perDay: 10_000,
      expireTime: null, lastUsedTime: null, appSecretMask: '******', appSecret: 'must-not-render',
    }));

    expect(screen.queryByText('must-not-render')).not.toBeInTheDocument();
    expect(screen.getByTestId('tenant-tenant-access-api-keys-status')).toBeEmptyDOMElement();
    expect(screen.queryByTestId('entity-form')).not.toBeInTheDocument();
  });

  it('does not publish a late revoke success after a concurrent authorization read is denied', async () => {
    let resolveRevoke: (value: null) => void = () => undefined;
    vi.mocked(tenantAccessApi.listTenantApiKeys)
      .mockResolvedValueOnce([{
        id: 41, appKey: 'public-app-key', name: 'integration', description: null,
        status: 'ACTIVE', ipWhitelist: null, perSecond: 10, perMinute: 100,
        perHour: 1_000, perDay: 10_000, expireTime: null, lastUsedTime: null,
        appSecretMask: '******',
      }])
      .mockRejectedValueOnce(Object.assign(new Error('forbidden'), { response: { status: 403 } }));
    vi.mocked(tenantAccessApi.revokeTenantApiKey).mockImplementationOnce(() => new Promise((resolve) => {
      resolveRevoke = resolve;
    }));
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-row');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke'));
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke-confirm'));
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-refresh'));

    expect(await screen.findByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
    await act(async () => resolveRevoke(null));

    expect(screen.getByTestId('tenant-tenant-access-api-keys-status')).toBeEmptyDOMElement();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-row')).not.toBeInTheDocument();
  });

  it('enters the denied state when create or revoke mutations return 403', async () => {
    vi.mocked(tenantAccessApi.createTenantApiKey)
      .mockRejectedValueOnce(Object.assign(new Error('forbidden'), { response: { status: 403 } }));
    const createView = render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-empty');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'denied-create' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    expect(await screen.findByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
    expect(screen.queryByTestId('entity-form')).not.toBeInTheDocument();
    createView.unmount();

    vi.mocked(tenantAccessApi.listTenantApiKeys).mockResolvedValue([{
      id: 41, appKey: 'public-app-key', name: 'integration', description: null,
      status: 'ACTIVE', ipWhitelist: null, perSecond: 10, perMinute: 100,
      perHour: 1_000, perDay: 10_000, expireTime: null, lastUsedTime: null,
      appSecretMask: '******',
    }]);
    vi.mocked(tenantAccessApi.revokeTenantApiKey)
      .mockRejectedValueOnce(Object.assign(new Error('forbidden'), { response: { status: 403 } }));
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-row');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke'));
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-revoke-confirm'));

    expect(await screen.findByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-row')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-revoke-dialog')).not.toBeInTheDocument();
  });

  it('denies tenant users locally without sending credential requests', async () => {
    useAuthStore.setState({ userType: 'TENANT_USER', tenantId: 7 });

    render(<TenantApiKeysPage />);

    expect(screen.getByTestId('tenant-tenant-access-api-keys-access-denied')).toBeVisible();
    expect(tenantAccessApi.listTenantApiKeys).not.toHaveBeenCalled();
    expect(tenantAccessApi.listTenantApiKeyAudits).not.toHaveBeenCalled();
    expect(screen.queryByTestId('tenant-tenant-access-api-keys-create-dialog')).not.toBeInTheDocument();
  });

  it('releases the create latch after a definite validation rejection', async () => {
    vi.mocked(tenantAccessApi.createTenantApiKey)
      .mockRejectedValueOnce(Object.assign(new Error('bad request'), { response: { status: 400 } }))
      .mockResolvedValueOnce({
        id: 2, appKey: 'retry-key', name: 'retryable', description: '', status: 'ACTIVE',
        ipWhitelist: null, perSecond: 10, perMinute: 100, perHour: 1_000, perDay: 10_000,
        expireTime: null, lastUsedTime: null, appSecretMask: '******', appSecret: 'retry-secret',
      });
    render(<TenantApiKeysPage />);
    await screen.findByTestId('tenant-tenant-access-api-keys-empty');
    fireEvent.click(screen.getByTestId('tenant-tenant-access-api-keys-create-dialog'));
    fireEvent.change(screen.getByTestId('tenant-tenant-access-api-keys-name'), { target: { value: 'retryable' } });
    fireEvent.click(screen.getByTestId('form-submit'));
    expect(await screen.findByRole('alert')).toHaveTextContent('请求被拒绝');
    expect(screen.getByTestId('form-submit')).toBeEnabled();
    fireEvent.click(screen.getByTestId('form-submit'));
    expect(await screen.findByTestId('tenant-tenant-access-api-keys-secret-once')).toHaveTextContent('retry-secret');
  });
});
