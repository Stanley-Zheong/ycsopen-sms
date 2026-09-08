import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { AxiosError } from 'axios';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '@/api/client';
import TenantListPage from '@/pages/admin/tenants/TenantListPage';
import TenantQualificationPage from '@/pages/tenant/TenantQualificationPage';
import TenantRegistrationPage from '@/pages/tenant/TenantRegistrationPage';
import { useAuthStore } from '@/store/authStore';
import {
  classifyQualificationFailure,
  type AdminTenantReview,
  type TenantQualificationStatus,
} from '@/api/tenantQualificationApi';

type SeenRequest = { method: string; url: string; body: unknown };

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function responseError(config: InternalAxiosRequestConfig, status: number, code: string) {
  return new AxiosError(code, 'ERR_BAD_RESPONSE', config, undefined, {
    config,
    data: { code, message: code, data: null, timestamp: '2026-09-08T00:00:00Z', traceId: 'trace-error' },
    headers: {}, status, statusText: 'Error',
  });
}

function apiResponse<T>(data: T) {
  return { code: 200, message: 'success', data, timestamp: '2026-09-08T00:00:00Z', traceId: 'trace-p8' };
}

function axiosResponse(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function renderPage(page: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{page}</QueryClientProvider>
    </MemoryRouter>,
  );
}

const reviewTenant = {
  tenantId: 42,
  tenantNo: 'TENANT-0042',
  shortName: '示例机构',
  fullName: '示例机构有限公司',
  unifiedSocialCreditCode: '91350211M000100Y43',
  legalRepresentativeName: '张三',
  contactName: '李四',
  registeredCapital: '1000',
  businessScope: '软件服务',
  registeredAddress: '厦门市思明区示例路 1 号',
  businessAddress: '厦门市思明区示例路 2 号',
  customerLevel: 3,
  bizManager: 'operator',
  industry: '软件',
  licenseValidUntil: '2030-01-01',
  trademarkUse: false,
  verificationStatus: 'PENDING',
  lifecycleStatus: 'SUBMITTED',
  operatingStatus: 'NORMAL',
  accountRevision: 2,
  qualificationRevision: 7,
  submittedAt: '2026-09-08T08:00:00Z',
  reason: null,
  inspectionStatus: 'COMPLETED',
  inspectedCompanyName: '示例机构有限公司',
  inspectedCreditCode: '91350211M000100Y43',
  inspectionConfidence: 0.98,
  inspectionRequestId: 'inspection-safe-id',
  inspectionCompletedAt: '2026-09-08T08:01:00Z',
} as const;

describe('Phase 08 tenant qualification production UI', () => {
  const seen: SeenRequest[] = [];
  let permissions: string[];
  let staleDecision: boolean;
  let staleEdit: boolean;
  let staleStatus: boolean;
  let listDenied: boolean;
  let latestReview: AdminTenantReview;
  let contactSendGate: ReturnType<typeof deferred<AxiosResponse>> | null;
  let contactVerifyGate: ReturnType<typeof deferred<AxiosResponse>> | null;
  let evidenceGate: ReturnType<typeof deferred<AxiosResponse>> | null;
  let sessionExpiresAt: string;
  let ownStatus: TenantQualificationStatus;
  let ownGetFailuresRemaining: number;

  beforeEach(() => {
    seen.length = 0;
    permissions = [
      'tenant:menu', 'tenant:read', 'tenant:qualification:review',
      'tenant:update', 'tenant:status:update', 'tenant:evidence:read',
    ];
    staleDecision = false;
    staleEdit = false;
    staleStatus = false;
    listDenied = false;
    latestReview = reviewTenant;
    contactSendGate = null;
    contactVerifyGate = null;
    evidenceGate = null;
    sessionExpiresAt = '2030-01-01T00:00:00Z';
    ownGetFailuresRemaining = 0;
    ownStatus = {
      tenantId: 42, tenantNo: 'TENANT-0042', shortName: '示例机构',
      fullName: '示例机构有限公司', verificationStatus: 'REJECTED',
      lifecycleStatus: 'SUBMITTED', submittedAt: '2026-09-08T08:00:00Z',
      verifiedAt: null, verificationUpdatedAt: '2026-09-08T09:00:00Z',
      revision: 7, reason: '请补充清晰的营业执照',
      unifiedSocialCreditCode: '91350211M000100Y46', legalRepresentativeName: '张三',
      contactName: '李四', registeredCapital: '1000', businessScope: '软件服务',
      registeredAddress: '厦门市思明区示例路 1 号', businessAddress: '厦门市思明区示例路 2 号',
      licenseValidUntil: '2030-01-01', trademarkUse: false,
      businessLicensePresent: true, legalRepresentativeIdentityPresent: true,
      legalRepresentativeIdFrontPresent: true, legalRepresentativeIdBackPresent: true,
      contactIdentityPresent: true, contactPhonePresent: true,
      shortlinkProofPresent: false, trademarkProofPresent: false,
    };
    useAuthStore.setState({
      accessToken: 'test-token', userType: 'TENANT_ADMIN', tenantId: 42,
      expiresAt: Date.now() + 60_000, principalKey: '81:test-token',
    });
    apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
      const request = config as InternalAxiosRequestConfig;
      const method = (request.method ?? 'get').toUpperCase();
      const url = request.url ?? '';
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data;
      seen.push({ method, url, body });
      if (url === '/console/account-overview') {
        return axiosResponse(request, apiResponse({
          id: 7, username: 'operator', userType: 'OPERATOR', roleNames: [],
          permissions: permissions.map((code) => ({ code, resourceType: 'API' })),
          lastLoginAt: null, lastLoginIp: null,
        }));
      }
      if (url === '/console/tenant/qualification' && method === 'GET') {
        if (ownGetFailuresRemaining > 0) {
          ownGetFailuresRemaining -= 1;
          throw responseError(request, 503, 'QUALIFICATION_STATUS_UNAVAILABLE');
        }
        return axiosResponse(request, apiResponse(ownStatus));
      }
      if (url === '/console/tenant/qualification' && method === 'POST') {
        ownStatus = {
          ...ownStatus,
          verificationStatus: 'PENDING',
          revision: ownStatus.revision + 1,
          reason: null,
          submittedAt: '2026-09-08T11:00:00Z',
          verificationUpdatedAt: '2026-09-08T11:00:00Z',
        };
        const summary = {
          tenantId: ownStatus.tenantId,
          tenantNo: ownStatus.tenantNo,
          shortName: ownStatus.shortName,
          fullName: ownStatus.fullName,
          verificationStatus: ownStatus.verificationStatus,
          lifecycleStatus: ownStatus.lifecycleStatus,
          submittedAt: ownStatus.submittedAt,
          verifiedAt: ownStatus.verifiedAt,
          verificationUpdatedAt: ownStatus.verificationUpdatedAt,
          revision: ownStatus.revision,
          reason: ownStatus.reason,
        };
        return axiosResponse(request, apiResponse(summary));
      }
      if (url === '/console/admin/tenants' && method === 'GET') {
        if (listDenied) throw responseError(request, 403, 'TENANT_ACCESS_DENIED');
        return axiosResponse(request, apiResponse([reviewTenant]));
      }
      if (url === '/console/admin/tenants/42' && method === 'GET') {
        return axiosResponse(request, apiResponse(latestReview));
      }
      if (url === '/console/admin/tenants/42/decision' && method === 'POST') {
        if (staleDecision) {
          staleDecision = false;
          throw responseError(request, 409, 'QUALIFICATION_REVISION_STALE');
        }
        return axiosResponse(request, apiResponse({
          ...reviewTenant, verificationStatus: 'VERIFIED', qualificationRevision: 8,
        }));
      }
      if (url === '/console/tenants/registration-object-sessions' && method === 'POST') {
        return axiosResponse(request, apiResponse({
          registrationObjectSessionId: '11111111-1111-4111-8111-111111111111',
          registrationUploadToken: 'regup_v1_test.secret',
          get expiresAt() { return sessionExpiresAt; },
        }));
      }
      if (url.includes('/console/tenants/registration-object-sessions/') && url.includes('/objects/')) {
        const segments = url.split('/');
        const purpose = segments[segments.length - 1];
        return axiosResponse(request, apiResponse({
          protectedObjectId: `pobj_v1_${purpose.replace(/-/g, '_')}`,
          purpose,
          expiresAt: '2030-01-01T00:00:00Z',
        }));
      }
      if (url === '/public/tenant-registrations/contact-challenges' && method === 'POST') {
        if (contactSendGate) return contactSendGate.promise;
        return axiosResponse(request, apiResponse({ challengeId: 'challenge-safe-id', expiresAt: '2030-01-01T00:00:00Z' }));
      }
      if (url === '/public/tenant-registrations/contact-challenges/challenge-safe-id/verify' && method === 'POST') {
        if (contactVerifyGate) return contactVerifyGate.promise;
        return axiosResponse(request, apiResponse(null));
      }
      if (url === '/console/admin/tenants/42/evidence/BUSINESS_LICENSE') {
        if (evidenceGate) return evidenceGate.promise;
        return axiosResponse(request, new Blob(['safe evidence'], { type: 'application/pdf' }));
      }
      if (url === '/console/admin/tenants/42' && method === 'PATCH') {
        if (staleEdit) { staleEdit = false; throw responseError(request, 409, 'QUALIFICATION_REVISION_STALE'); }
        return axiosResponse(request, apiResponse(null));
      }
      if (url === '/console/admin/tenants/42/status' && method === 'POST') {
        if (staleStatus) { staleStatus = false; throw responseError(request, 409, 'ACCOUNT_REVISION_STALE'); }
        return axiosResponse(request, apiResponse(null));
      }
      if (url === '/public/tenant-registrations' && method === 'POST') {
        return axiosResponse(request, apiResponse({
          tenantId: 88, tenantNo: 'TENANT-0088', shortName: '示例机构', fullName: '示例机构有限公司',
          verificationStatus: 'PENDING', lifecycleStatus: 'SUBMITTED', submittedAt: '2026-09-08T10:00:00Z',
          verifiedAt: null, verificationUpdatedAt: '2026-09-08T10:00:00Z', revision: 1, reason: null,
        }));
      }
      return axiosResponse(request, apiResponse(null));
    };
  });

  afterEach(() => {
    delete apiClient.defaults.adapter;
    act(() => useAuthStore.getState().logout());
  });

  it('renders truthful own-tenant status and the complete shared form selector contract', async () => {
    renderPage(<TenantQualificationPage />);

    expect(await screen.findByTestId('tenant-tenant-qualification-qualification-status'))
      .toHaveTextContent('审核驳回');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-review-feedback'))
      .toHaveTextContent('请补充清晰的营业执照');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-form')).toBeVisible();
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-license-upload'))
      .toHaveTextContent('JPG、PNG 或 PDF，单个不超过 10 MiB');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact')).toBeVisible();
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-trademark-proof')).toBeVisible();
  });

  it('renders the approved safe aggregate and non-disclosing protected-material presence on a pending mount', async () => {
    ownStatus = { ...ownStatus, verificationStatus: 'PENDING', reason: null };
    renderPage(<TenantQualificationPage />);

    await screen.findByText('资料正在审核，当前表单只读。');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-credit-code')).toHaveValue('91350211M000100Y46');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-registration-capital')).toHaveValue('1000');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-business-scope')).toHaveValue('软件服务');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-legal-id')).toHaveValue('');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-legal-section')).toHaveTextContent('身份信息已提交，出于安全不回显');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-license-upload-status')).toHaveTextContent('材料已提交，出于安全不回显');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact')).toHaveTextContent('手机号与身份信息已提交，出于安全不回显');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-submit-status'))
      .toHaveTextContent('资质资料已提交，等待人工审核。');
  });

  it('classifies stable business codes before ambiguous HTTP status fallbacks', () => {
    const request = {} as InternalAxiosRequestConfig;
    expect(classifyQualificationFailure(responseError(request, 409, 'QUALIFICATION_REVISION_STALE')).kind).toBe('stale');
    expect(classifyQualificationFailure(responseError(request, 409, 'DUPLICATE_REGISTRATION')).kind).toBe('duplicate');
    expect(classifyQualificationFailure(responseError(request, 409, 'CONTACT_VERIFICATION_EXPIRED')).kind).toBe('challengeExpired');
    expect(classifyQualificationFailure(responseError(request, 409, 'REGISTRATION_OBJECT_SESSION_EXPIRED')).kind).toBe('uploadExpired');
    expect(classifyQualificationFailure(responseError(request, 409, 'QUALIFICATION_ALREADY_PENDING')).kind).toBe('conflict');
  });

  it('focuses the first invalid registration field and does not call production APIs', () => {
    useAuthStore.getState().logout();
    renderPage(<TenantRegistrationPage />);

    fireEvent.click(screen.getByTestId('public-tenant-qualification-register-submit'));

    expect(screen.getByTestId('public-tenant-qualification-register-error-summary')).toBeVisible();
    expect(screen.getByTestId('public-tenant-qualification-register-admin-username')).toHaveFocus();
    expect(seen).toHaveLength(0);
  });

  it('completes protected uploads and verified contact before sending the exact registration payload', async () => {
    useAuthStore.getState().logout();
    renderPage(<TenantRegistrationPage />);
    const change = (testId: string, value: string) => fireEvent.change(screen.getByTestId(testId), { target: { value } });
    change('public-tenant-qualification-register-admin-username', 'tenant_admin');
    change('public-tenant-qualification-register-admin-password', 'StrongPass1');
    change('public-tenant-qualification-register-admin-email', 'admin@example.com');
    change('tenant-tenant-qualification-qualification-short-name', '示例机构');
    change('tenant-tenant-qualification-qualification-full-name', '示例机构有限公司');
    change('tenant-tenant-qualification-qualification-credit-code', '91350211M000100Y46');
    change('tenant-tenant-qualification-qualification-registration-capital', '1000');
    change('tenant-tenant-qualification-qualification-business-scope', '软件服务');
    change('tenant-tenant-qualification-qualification-registered-address', '厦门市思明区示例路 1 号');
    change('tenant-tenant-qualification-qualification-operating-address', '厦门市思明区示例路 2 号');
    change('tenant-tenant-qualification-qualification-license-valid-until', '2030-01-01');
    change('tenant-tenant-qualification-qualification-legal-name', '张三');
    change('tenant-tenant-qualification-qualification-legal-id', '11010519491231002X');
    change('tenant-tenant-qualification-qualification-contact-name', '李四');
    change('tenant-tenant-qualification-qualification-contact-id', '11010519491231002X');
    change('tenant-tenant-qualification-qualification-contact-phone', '13800138000');

    const upload = async (testId: string, file: File, count: number) => {
      fireEvent.change(screen.getByTestId(testId), { target: { files: [file] } });
      await waitFor(() => expect(seen.filter((request) => request.url.includes('/objects/'))).toHaveLength(count));
    };
    fireEvent.change(screen.getByLabelText(/选择营业执照/), {
      target: { files: [new File(['pdf'], 'license.pdf', { type: 'application/pdf' })] },
    });
    await waitFor(() => expect(seen.filter((request) => request.url.includes('/objects/'))).toHaveLength(1));
    await upload('tenant-tenant-qualification-qualification-legal-id-front-upload', new File(['front'], 'front.png', { type: 'image/png' }), 2);
    await upload('tenant-tenant-qualification-qualification-legal-id-back-upload', new File(['back'], 'back.png', { type: 'image/png' }), 3);

    fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-send'));
    await waitFor(() => expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-input')).toBeEnabled());
    change('tenant-tenant-qualification-qualification-contact-code-input', '123456');
    fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-verify'));
    await screen.findByText('联系人手机已验证');
    fireEvent.click(screen.getByTestId('public-tenant-qualification-register-submit'));

    expect(await screen.findByTestId('public-tenant-qualification-register-success')).toHaveTextContent('TENANT-0088');
    const registration = seen.find((request) => request.url === '/public/tenant-registrations' && request.method === 'POST');
    expect(registration?.body).toMatchObject({
      adminUsername: 'tenant_admin', adminEmail: 'admin@example.com', contactChallengeId: 'challenge-safe-id',
      qualification: {
        unifiedSocialCreditCode: '91350211M000100Y46',
        businessLicenseObjectId: 'pobj_v1_business_license',
        legalRepIdFrontObjectId: 'pobj_v1_legal_rep_id_front',
        legalRepIdBackObjectId: 'pobj_v1_legal_rep_id_back',
      },
    });
    expect(document.body).not.toHaveTextContent('regup_v1_test.secret');
    expect(document.body).not.toHaveTextContent('pobj_v1_');
  });

  it('shows permission-scoped admin actions and submits an attributable human-confirmed decision', async () => {
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null, principalKey: '7:test-token' });
    renderPage(<TenantListPage />);

    await screen.findByTestId('admin-tenant-qualification-tenants-row');
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-review-open'));
    const drawer = await screen.findByTestId('admin-tenant-qualification-tenants-review-drawer');
    expect(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-ocr-status'))
      .toHaveTextContent('已完成');
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-human-confirmed'));
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-approve-open'));

    const dialog = screen.getByTestId('admin-tenant-qualification-tenants-review-decision');
    fireEvent.change(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-reason'), {
      target: { value: '资料核对一致' },
    });
    fireEvent.click(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-confirm'));

    await waitFor(() => expect(seen).toContainEqual({
      method: 'POST', url: '/console/admin/tenants/42/decision',
      body: { expectedRevision: 7, decision: 'APPROVE', reason: '资料核对一致', humanConfirmed: true },
    }));
  });

  it('hides every mutation action from a read-only operator', async () => {
    permissions = ['tenant:menu', 'tenant:read'];
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null, principalKey: '7:test-token' });
    renderPage(<TenantListPage />);

    await screen.findByTestId('admin-tenant-qualification-tenants-row');
    expect(screen.queryByTestId('admin-tenant-qualification-tenants-review-open')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-tenant-qualification-tenants-edit')).not.toBeInTheDocument();
    expect(screen.queryByTestId('admin-tenant-qualification-tenants-status-action')).not.toBeInTheDocument();
  });

  it('renders returned qualification and conflicting OCR facts before human confirmation', async () => {
    latestReview = {
      ...reviewTenant,
      inspectedCompanyName: '不一致企业有限公司',
      inspectedCreditCode: '91350211M000100Y47',
      inspectionCompletedAt: '2026-09-08T08:01:00Z',
    };
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null, principalKey: '7:test-token' });
    renderPage(<TenantListPage />);
    await screen.findByTestId('admin-tenant-qualification-tenants-row');
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-review-open'));
    const drawer = await screen.findByTestId('admin-tenant-qualification-tenants-review-drawer');

    expect(drawer).toHaveTextContent('注册资本');
    expect(drawer).toHaveTextContent('1000');
    expect(drawer).toHaveTextContent('经营范围');
    expect(drawer).toHaveTextContent('软件服务');
    expect(drawer).toHaveTextContent('不一致企业有限公司');
    expect(drawer).toHaveTextContent('91350211M000100Y47');
    expect(drawer).toHaveTextContent('检查完成时间');
    expect(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-human-confirmed')).not.toBeChecked();
  });

  it('maps runtime list 403 to denied and opens evidence dialog during loading', async () => {
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null, principalKey: '7:test-token' });
    listDenied = true;
    const deniedRender = renderPage(<TenantListPage />);
    expect(await screen.findByTestId('admin-tenant-qualification-tenants-access-denied')).toBeVisible();
    deniedRender.unmount();

    listDenied = false;
    evidenceGate = deferred<AxiosResponse>();
    renderPage(<TenantListPage />);
    await screen.findByTestId('admin-tenant-qualification-tenants-row');
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-review-open'));
    const drawer = await screen.findByTestId('admin-tenant-qualification-tenants-review-drawer');
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-evidence-open'));
    const dialog = screen.getByTestId('admin-tenant-qualification-tenants-review-evidence-dialog');
    expect(dialog).toHaveTextContent('正在安全加载证明材料');
    expect(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-evidence-open')).toBeDisabled();
    await act(async () => {
      evidenceGate!.resolve(axiosResponse({} as InternalAxiosRequestConfig, new Blob(['safe'], { type: 'application/pdf' })));
      await evidenceGate!.promise;
    });
    await waitFor(() => expect(within(dialog).getByTitle('营业执照证明材料')).toBeVisible());
  });

  it('ignores delayed contact responses after the phone generation changes', async () => {
    useAuthStore.getState().logout();
    contactSendGate = deferred<AxiosResponse>();
    renderPage(<TenantRegistrationPage />);
    const phone = screen.getByTestId('tenant-tenant-qualification-qualification-contact-phone');
    fireEvent.change(phone, { target: { value: '13800138000' } });
    fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-send'));
    fireEvent.change(phone, { target: { value: '13900139000' } });
    await act(async () => {
      contactSendGate!.resolve(axiosResponse({} as InternalAxiosRequestConfig, apiResponse({ challengeId: 'old-challenge', expiresAt: '2030-01-01T00:00:00Z' })));
      await contactSendGate!.promise;
    });
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-input')).toBeDisabled();
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-verified-status')).not.toHaveTextContent('验证码已发送');

    contactSendGate = null;
    fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-send'));
    await waitFor(() => expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-input')).toBeEnabled());
    fireEvent.change(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-input'), { target: { value: '123456' } });
    contactVerifyGate = deferred<AxiosResponse>();
    fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-verify'));
    fireEvent.change(phone, { target: { value: '13700137000' } });
    await act(async () => {
      contactVerifyGate!.resolve(axiosResponse({} as InternalAxiosRequestConfig, apiResponse(null)));
      await contactVerifyGate!.promise;
    });
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-input')).toHaveValue('');
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-verified-status')).not.toHaveTextContent('联系人手机已验证');
  });

  it('invalidates all prior file claims when the upload session expires locally', async () => {
    useAuthStore.getState().logout();
    renderPage(<TenantRegistrationPage />);
    fireEvent.change(screen.getByLabelText(/选择营业执照/), {
      target: { files: [new File(['pdf'], 'license.pdf', { type: 'application/pdf' })] },
    });
    await waitFor(() => expect(screen.getByTestId('tenant-tenant-qualification-qualification-license-upload-status')).toHaveTextContent('license.pdf'));
    sessionExpiresAt = '2000-01-01T00:00:00Z';
    fireEvent.change(screen.getByTestId('tenant-tenant-qualification-qualification-legal-id-front-upload'), {
      target: { files: [new File(['front'], 'front.png', { type: 'image/png' })] },
    });

    expect(await screen.findByTestId('public-tenant-qualification-register-session-expired')).toBeVisible();
    expect(screen.getByTestId('tenant-tenant-qualification-qualification-license-upload-status'))
      .toHaveTextContent('上传会话已过期，必须重新上传');
    expect(screen.getByTestId('public-tenant-qualification-register-submit')).toBeDisabled();
    expect(seen.filter((request) => request.url.includes('/objects/'))).toHaveLength(1);
  });

  it('refreshes stale decision, edit, and status tokens while retaining input for a valid retry', async () => {
    staleDecision = true;
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null, principalKey: '7:test-token' });
    renderPage(<TenantListPage />);
    await screen.findByTestId('admin-tenant-qualification-tenants-row');
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-review-open'));
    const drawer = await screen.findByTestId('admin-tenant-qualification-tenants-review-drawer');
    latestReview = { ...reviewTenant, qualificationRevision: 8 };
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-human-confirmed'));
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-approve-open'));
    const dialog = screen.getByTestId('admin-tenant-qualification-tenants-review-decision');
    const reason = within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-reason');
    fireEvent.change(reason, { target: { value: '资料核对一致' } });
    fireEvent.click(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-confirm'));

    expect(await within(dialog).findByText(/数据已被其他操作员更新/)).toBeVisible();
    expect(reason).toHaveValue('资料核对一致');
    fireEvent.click(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-confirm'));
    await waitFor(() => expect(seen.filter((request) => request.url.endsWith('/decision')).map((request) => request.body))
      .toEqual([
        { expectedRevision: 7, decision: 'APPROVE', reason: '资料核对一致', humanConfirmed: true },
        { expectedRevision: 8, decision: 'APPROVE', reason: '资料核对一致', humanConfirmed: true },
      ]));
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-drawer-close'));

    staleEdit = true;
    latestReview = { ...reviewTenant, qualificationRevision: 9 };
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-edit'));
    const edit = screen.getByTestId('admin-tenant-qualification-tenants-edit-drawer');
    fireEvent.change(within(edit).getByTestId('admin-tenant-qualification-tenants-edit-business-manager'), { target: { value: 'new-manager' } });
    fireEvent.change(within(edit).getByTestId('admin-tenant-qualification-tenants-edit-reason'), { target: { value: '调整客户经理' } });
    fireEvent.click(within(edit).getByTestId('admin-tenant-qualification-tenants-edit-save'));
    expect(await within(edit).findByText(/数据已被其他操作员更新/)).toBeVisible();
    expect(within(edit).getByTestId('admin-tenant-qualification-tenants-edit-business-manager')).toHaveValue('new-manager');
    fireEvent.click(within(edit).getByTestId('admin-tenant-qualification-tenants-edit-save'));
    await waitFor(() => expect(seen.filter((request) => request.method === 'PATCH').map((request) => (request.body as { expectedRevision: number }).expectedRevision)).toEqual([7, 9]));

    staleStatus = true;
    latestReview = { ...reviewTenant, accountRevision: 4 };
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-status-action'));
    const status = screen.getByTestId('admin-tenant-qualification-tenants-status-dialog');
    fireEvent.change(within(status).getByTestId('admin-tenant-qualification-tenants-status-reason'), { target: { value: '暂停新业务' } });
    fireEvent.click(within(status).getByTestId('admin-tenant-qualification-tenants-status-confirm'));
    expect(await within(status).findByText(/数据已被其他操作员更新/)).toBeVisible();
    expect(within(status).getByTestId('admin-tenant-qualification-tenants-status-reason')).toHaveValue('暂停新业务');
    fireEvent.click(within(status).getByTestId('admin-tenant-qualification-tenants-status-confirm'));
    await waitFor(() => expect(seen.filter((request) => request.url.endsWith('/status')).map((request) => (request.body as { expectedRevision: number }).expectedRevision)).toEqual([2, 4]));
  });

  it('requires fresh human confirmation when stale reconciliation changes review facts', async () => {
    staleDecision = true;
    useAuthStore.setState({ userType: 'OPERATOR', tenantId: null, principalKey: '7:test-token' });
    renderPage(<TenantListPage />);
    await screen.findByTestId('admin-tenant-qualification-tenants-row');
    fireEvent.click(screen.getByTestId('admin-tenant-qualification-tenants-review-open'));
    const drawer = await screen.findByTestId('admin-tenant-qualification-tenants-review-drawer');
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-human-confirmed'));
    fireEvent.click(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-approve-open'));
    const dialog = screen.getByTestId('admin-tenant-qualification-tenants-review-decision');
    fireEvent.change(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-reason'), {
      target: { value: '资料核对一致' },
    });
    latestReview = { ...reviewTenant, qualificationRevision: 8, inspectedCreditCode: '91350211M000100Y47' };
    fireEvent.click(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-confirm'));

    expect(await within(dialog).findByText(/审核事实已变化/)).toBeVisible();
    expect(within(drawer).getByTestId('admin-tenant-qualification-tenants-review-human-confirmed')).not.toBeChecked();
    expect(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-reason')).toHaveValue('资料核对一致');
    expect(within(dialog).getByTestId('admin-tenant-qualification-tenants-review-decision-confirm')).toBeDisabled();
  });

  it.each(['VERIFIED', 'REJECTED'] as const)(
    'keeps a successful %s resubmission pending and read-only when aggregate refresh fails',
    async (previousStatus) => {
      ownStatus = {
        ...ownStatus,
        verificationStatus: previousStatus,
        verifiedAt: previousStatus === 'VERIFIED' ? '2026-09-08T09:00:00Z' : null,
        reason: previousStatus === 'REJECTED' ? '请补充清晰材料' : null,
      };
      renderPage(<TenantQualificationPage />);
      await screen.findByTestId('tenant-tenant-qualification-qualification-status');
      if (previousStatus === 'VERIFIED') {
        fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-recertify'));
        fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-recertify-confirm'));
      }

      const change = (testId: string, value: string) => fireEvent.change(screen.getByTestId(testId), { target: { value } });
      change('tenant-tenant-qualification-qualification-legal-id', '11010519491231002X');
      change('tenant-tenant-qualification-qualification-contact-id', '11010519491231002X');
      change('tenant-tenant-qualification-qualification-contact-phone', '13800138000');
      let uploads = seen.filter((request) => request.url.includes('/objects/')).length;
      const upload = async (control: HTMLElement, file: File) => {
        fireEvent.change(control, { target: { files: [file] } });
        uploads += 1;
        await waitFor(() => expect(seen.filter((request) => request.url.includes('/objects/'))).toHaveLength(uploads));
      };
      await upload(screen.getByLabelText(/选择营业执照/), new File(['pdf'], 'license.pdf', { type: 'application/pdf' }));
      await upload(screen.getByTestId('tenant-tenant-qualification-qualification-legal-id-front-upload'), new File(['front'], 'front.png', { type: 'image/png' }));
      await upload(screen.getByTestId('tenant-tenant-qualification-qualification-legal-id-back-upload'), new File(['back'], 'back.png', { type: 'image/png' }));
      fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-submit'));
      expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-phone')).toHaveFocus();
      expect(seen.filter((request) => request.url === '/console/tenant/qualification' && request.method === 'POST')).toHaveLength(0);
      fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-send'));
      await waitFor(() => expect(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-input')).toBeEnabled());
      change('tenant-tenant-qualification-qualification-contact-code-input', '123456');
      fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-contact-code-verify'));
      await screen.findByText('联系人手机已验证');

      ownGetFailuresRemaining = 1;
      fireEvent.click(screen.getByTestId('tenant-tenant-qualification-qualification-submit'));

      const warning = await screen.findByTestId('tenant-tenant-qualification-qualification-error');
      expect(warning).toHaveTextContent('资质资料已成功提交并进入待审核');
      expect(screen.getByTestId('tenant-tenant-qualification-qualification-status')).toHaveTextContent('待审核');
      expect(screen.queryByTestId('tenant-tenant-qualification-qualification-recertify')).not.toBeInTheDocument();
      expect(screen.queryByTestId('tenant-tenant-qualification-qualification-submit')).not.toBeInTheDocument();
      expect(seen.filter((request) => request.url === '/console/tenant/qualification' && request.method === 'POST')).toHaveLength(1);

      fireEvent.click(within(warning).getByTestId('tenant-tenant-qualification-qualification-retry'));
      await waitFor(() => expect(screen.queryByTestId('tenant-tenant-qualification-qualification-error')).not.toBeInTheDocument());
      expect(screen.getByTestId('tenant-tenant-qualification-qualification-credit-code')).toHaveValue('91350211M000100Y46');
      expect(screen.getByTestId('tenant-tenant-qualification-qualification-credit-code')).toBeDisabled();
      expect(screen.getByTestId('tenant-tenant-qualification-qualification-submit')).toBeDisabled();
      expect(seen.filter((request) => request.url === '/console/tenant/qualification' && request.method === 'POST')).toHaveLength(1);
    },
  );
});
