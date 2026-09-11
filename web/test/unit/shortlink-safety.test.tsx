import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as api from '@/api/shortLinkApi';
import TenantShortLinkPage from '@/pages/tenant/shortlinks/TenantShortLinkPage';
import AdminShortLinkReviewPage from '@/pages/admin/shortlinks/AdminShortLinkReviewPage';
import PublicShortLinkSafePage from '@/pages/public/PublicShortLinkSafePage';
import { MemoryRouter } from 'react-router-dom';

vi.mock('@/api/shortLinkApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/shortLinkApi')>();
  return {
    ...actual,
    listTenantShortLinks: vi.fn(),
    createShortLink: vi.fn(),
    shortLinkAnalytics: vi.fn(),
    listShortLinkReview: vi.fn(),
    approveShortLink: vi.fn(),
    rejectShortLink: vi.fn(),
    inspectShortLink: vi.fn(),
  };
});

const row = {
  id: 48,
  tenantId: 7,
  targetUrl: 'https://example.com/campaign',
  customDomain: 's.ycsopen.test',
  shortCode: 'abc12345',
  shortUrl: 'https://s.ycsopen.test/s/abc12345',
  validUntil: '2026-10-10',
  status: 'PENDING' as const,
  clickCount: 12,
  targetVersion: 1,
  immutableTargetSha256: '1234567890abcdef',
  automatedResultJson: '{"verdict":"PASS","checks":["URL_VALID","DOMAIN_APPROVED"]}',
  screenshotEvidenceRef: 'domain-evidence:example.com',
  domainEvidenceJson: '{"targetDomain":"example.com"}',
  riskLevel: 'LOW' as const,
  reviewOpinion: null,
  reviewedBy: null,
  reviewedAt: null,
  offlineReason: null,
  offlineAt: null,
  lastRecheckAt: null,
};

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('Phase 48 short-link UI', () => {
  beforeEach(() => {
    vi.mocked(api.listTenantShortLinks).mockResolvedValue([row]);
    vi.mocked(api.createShortLink).mockResolvedValue(row);
    vi.mocked(api.shortLinkAnalytics).mockResolvedValue({ uniqueClicks: 2, regions: [{ label: '华东', count: 2 }], devices: [{ label: 'MOBILE', count: 2 }] });
    vi.mocked(api.listShortLinkReview).mockResolvedValue([row]);
    vi.mocked(api.approveShortLink).mockResolvedValue({ ...row, status: 'APPROVED' });
    vi.mocked(api.rejectShortLink).mockResolvedValue({ ...row, status: 'REJECTED' });
    vi.mocked(api.inspectShortLink).mockResolvedValue({ ...row, status: 'OFFLINE', offlineReason: '目标漂移或风险升高' });
  });

  afterEach(() => vi.clearAllMocks());

  it('creates tenant short link and displays list plus privacy-safe analytics', async () => {
    renderWithClient(<TenantShortLinkPage />);

    expect(await screen.findByTestId('tenant-shortlink-safety-shortlinks-form-url')).toHaveValue('https://example.com/campaign');
    expect(await screen.findByTestId('tenant-shortlink-safety-shortlinks-list')).toHaveTextContent('https://s.ycsopen.test/s/abc12345');
    expect(screen.getByTestId('tenant-shortlink-safety-shortlinks-click-count')).toHaveTextContent('12');
    expect(await screen.findByTestId('tenant-shortlink-safety-shortlinks-analytics-region')).toHaveTextContent('华东 2');
    fireEvent.click(screen.getByTestId('tenant-shortlink-safety-shortlinks-create'));
    await waitFor(() => expect(api.createShortLink).toHaveBeenCalledWith(expect.objectContaining({ originalUrl: 'https://example.com/campaign' })));
  });

  it('reviews immutable target evidence and performs approve reject inspect actions', async () => {
    renderWithClient(<AdminShortLinkReviewPage />);

    expect(await screen.findByTestId('admin-shortlink-safety-shortlinks-target-version')).toHaveTextContent('sha256:12345678');
    expect(screen.getByTestId('admin-shortlink-safety-shortlinks-domain-evidence')).toHaveTextContent('example.com');
    expect(screen.getByTestId('admin-shortlink-safety-shortlinks-auto-result')).toHaveTextContent('DOMAIN_APPROVED');
    fireEvent.click(screen.getByTestId('admin-shortlink-safety-shortlinks-approve'));
    await waitFor(() => expect(api.approveShortLink).toHaveBeenCalledWith(48, '自动审核通过，域名证据完整'));
    fireEvent.click(screen.getByTestId('admin-shortlink-safety-shortlinks-reject'));
    await waitFor(() => expect(api.rejectShortLink).toHaveBeenCalledWith(48, '自动审核通过，域名证据完整'));
    fireEvent.click(screen.getByTestId('admin-shortlink-safety-shortlinks-inspect'));
    await waitFor(() => expect(api.inspectShortLink).toHaveBeenCalledWith(48, 'https://example.com/campaign'));
  });

  it('renders public safe page without target redirect controls', () => {
    render(
      <MemoryRouter initialEntries={['/s/abc12345?state=offline']}>
        <PublicShortLinkSafePage />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('public-shortlink-safety-offline-page')).toHaveTextContent('已下线');
    expect(screen.getByTestId('public-shortlink-safe-message')).not.toHaveTextContent('example.com');
  });
});
