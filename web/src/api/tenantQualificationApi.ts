import axios from 'axios';
import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

export const TENANT_PERMISSIONS = {
  menu: 'tenant:menu',
  read: 'tenant:read',
  review: 'tenant:qualification:review',
  update: 'tenant:update',
  statusUpdate: 'tenant:status:update',
  evidenceRead: 'tenant:evidence:read',
} as const;

export type VerificationStatus = 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED' | 'SUPPLEMENT_REQUIRED';
export type LifecycleStatus = 'SUBMITTED' | 'TRIAL' | 'TRIAL_FROZEN' | 'SIGNED' | 'FROZEN' | 'TERMINATED';
export type OperatingStatus = 'NORMAL' | 'DISABLED' | 'ARREARS_FROZEN';
export type InspectionStatus = 'NOT_STARTED' | 'PENDING' | 'COMPLETED' | 'FAILED';

export interface TenantQualificationSummary {
  tenantId: number;
  tenantNo: string;
  shortName: string;
  fullName: string;
  verificationStatus: VerificationStatus;
  lifecycleStatus: LifecycleStatus;
  submittedAt: string | null;
  verifiedAt: string | null;
  verificationUpdatedAt: string | null;
  revision: number;
  reason: string | null;
}

/** Authenticated own-tenant aggregate. Protected plaintext and object identifiers are never included. */
export interface TenantQualificationStatus extends TenantQualificationSummary {
  unifiedSocialCreditCode?: string;
  legalRepresentativeName?: string;
  contactName?: string;
  registeredCapital?: string;
  businessScope?: string;
  registeredAddress?: string;
  businessAddress?: string;
  licenseValidUntil?: string;
  trademarkUse?: boolean;
  businessLicensePresent?: boolean;
  legalRepresentativeIdentityPresent?: boolean;
  legalRepresentativeIdFrontPresent?: boolean;
  legalRepresentativeIdBackPresent?: boolean;
  contactIdentityPresent?: boolean;
  contactPhonePresent?: boolean;
  shortlinkProofPresent?: boolean;
  trademarkProofPresent?: boolean;
}

export interface QualificationPayload {
  shortName: string;
  fullName: string;
  unifiedSocialCreditCode: string;
  registrationObjectSessionId: string;
  businessLicenseObjectId: string;
  legalRepName: string;
  legalRepIdNo: string;
  legalRepIdFrontObjectId: string;
  legalRepIdBackObjectId: string;
  contactName: string;
  contactIdNo: string;
  contactPhone: string;
  shortlinkDomainProofObjectId: string | null;
  trademarkProofObjectId: string | null;
  trademarkUse: boolean;
  registeredCapital: string;
  businessScope: string;
  registeredAddress: string;
  businessAddress: string;
  licenseValidUntil: string;
}

export interface QualificationSubmissionContext {
  qualification: QualificationPayload;
  contactChallengeId: string;
  uploadToken: string;
}

export interface RegistrationObjectSession {
  registrationObjectSessionId: string;
  registrationUploadToken: string;
  expiresAt: string;
}

export type UploadPurpose = 'business-license' | 'legal-rep-id-front' | 'legal-rep-id-back'
  | 'shortlink-domain-proof' | 'trademark-proof';

export interface UploadedRegistrationObject {
  protectedObjectId: string;
  purpose: UploadPurpose;
  expiresAt: string;
}

export interface ContactChallenge {
  challengeId: string;
  expiresAt: string;
}

export interface AdminTenantReview {
  tenantId: number;
  tenantNo: string;
  shortName: string;
  fullName: string;
  unifiedSocialCreditCode: string;
  legalRepresentativeName: string;
  contactName: string;
  registeredCapital: string;
  businessScope: string;
  registeredAddress: string;
  businessAddress: string;
  customerLevel: number | null;
  bizManager: string | null;
  industry: string | null;
  licenseValidUntil: string;
  trademarkUse: boolean;
  verificationStatus: VerificationStatus;
  lifecycleStatus: LifecycleStatus;
  operatingStatus: OperatingStatus | null;
  accountRevision: number | null;
  qualificationRevision: number;
  submittedAt: string | null;
  reason: string | null;
  inspectionStatus: InspectionStatus;
  inspectedCompanyName: string | null;
  inspectedCreditCode: string | null;
  inspectionConfidence: number | null;
  inspectionRequestId: string | null;
  inspectionCompletedAt: string | null;
}

export interface QualificationEvent {
  id: number;
  action: string;
  beforeVerificationStatus: string | null;
  afterVerificationStatus: string | null;
  beforeLifecycleStatus: string | null;
  afterLifecycleStatus: string | null;
  beforeAccountStatus: string | null;
  afterAccountStatus: string | null;
  changedFields: string | null;
  reason: string;
  actor: string;
  createdAt: string;
}

export interface SafeRequestFailure {
  kind: 'denied' | 'stale' | 'duplicate' | 'challengeExpired' | 'uploadExpired'
    | 'expired' | 'provider' | 'rateLimited' | 'validation' | 'conflict' | 'other';
  code: string | null;
  traceId: string | null;
}

export function classifyQualificationFailure(error: unknown): SafeRequestFailure {
  if (!axios.isAxiosError(error)) return { kind: 'other', code: null, traceId: null };
  const body = error.response?.data as { message?: unknown; code?: unknown; traceId?: unknown } | undefined;
  const stableCode = typeof body?.code === 'string'
    ? body.code
    : typeof body?.message === 'string' ? body.message : null;
  const status = error.response?.status;
  const kind = stableCode?.includes('STALE') ? 'stale'
    : stableCode === 'DUPLICATE_REGISTRATION' ? 'duplicate'
      : stableCode?.startsWith('CONTACT_VERIFICATION_') && stableCode.includes('EXPIRED') ? 'challengeExpired'
        : stableCode?.startsWith('REGISTRATION_') && stableCode.includes('EXPIRED') ? 'uploadExpired'
          : stableCode?.includes('UNAVAILABLE') || status === 503 ? 'provider'
            : stableCode?.includes('RATE_LIMITED') || status === 429 ? 'rateLimited'
              : status === 403 ? 'denied'
                : status === 410 ? 'expired'
                  : status === 409 ? 'conflict'
                    : status === 400 || status === 422 ? 'validation' : 'other';
  return {
    kind,
    code: stableCode,
    traceId: typeof body?.traceId === 'string' ? body.traceId : null,
  };
}

export async function createRegistrationObjectSession(): Promise<RegistrationObjectSession> {
  const response = await apiClient.post<ApiResponse<RegistrationObjectSession>>('/console/tenants/registration-object-sessions');
  return response.data.data;
}

export async function uploadRegistrationObject(
  session: RegistrationObjectSession,
  purpose: UploadPurpose,
  file: File,
): Promise<UploadedRegistrationObject> {
  const body = new FormData();
  body.append('file', file);
  const response = await apiClient.post<ApiResponse<UploadedRegistrationObject>>(
    `/console/tenants/registration-object-sessions/${session.registrationObjectSessionId}/objects/${purpose}`,
    body,
    { headers: { 'X-Registration-Upload-Token': session.registrationUploadToken } },
  );
  return response.data.data;
}

export async function requestContactChallenge(phone: string): Promise<ContactChallenge> {
  const response = await apiClient.post<ApiResponse<ContactChallenge>>(
    '/public/tenant-registrations/contact-challenges', { phone },
  );
  return response.data.data;
}

export async function verifyContactChallenge(challengeId: string, phone: string, code: string): Promise<void> {
  await apiClient.post(`/public/tenant-registrations/contact-challenges/${encodeURIComponent(challengeId)}/verify`, { phone, code });
}

export async function registerTenant(
  context: QualificationSubmissionContext,
  credentials: { adminUsername: string; adminPassword: string; adminEmail: string },
): Promise<TenantQualificationSummary> {
  const response = await apiClient.post<ApiResponse<TenantQualificationSummary>>(
    '/public/tenant-registrations',
    { qualification: context.qualification, contactChallengeId: context.contactChallengeId, ...credentials },
    { headers: { 'X-Registration-Upload-Token': context.uploadToken } },
  );
  return response.data.data;
}

export async function getOwnQualification(): Promise<TenantQualificationStatus> {
  const response = await apiClient.get<ApiResponse<TenantQualificationStatus>>('/console/tenant/qualification');
  return response.data.data;
}

export async function submitOwnQualification(context: QualificationSubmissionContext): Promise<TenantQualificationSummary> {
  const response = await apiClient.post<ApiResponse<TenantQualificationSummary>>(
    '/console/tenant/qualification',
    { qualification: context.qualification, contactChallengeId: context.contactChallengeId },
    { headers: { 'X-Registration-Upload-Token': context.uploadToken } },
  );
  return response.data.data;
}

export async function listAdminTenants(): Promise<AdminTenantReview[]> {
  const response = await apiClient.get<ApiResponse<AdminTenantReview[]>>('/console/admin/tenants');
  return response.data.data;
}

export async function getAdminTenant(tenantId: number): Promise<AdminTenantReview> {
  const response = await apiClient.get<ApiResponse<AdminTenantReview>>(`/console/admin/tenants/${tenantId}`);
  return response.data.data;
}

export async function inspectTenant(tenantId: number, expectedRevision: number): Promise<AdminTenantReview> {
  const response = await apiClient.post<ApiResponse<AdminTenantReview>>(
    `/console/admin/tenants/${tenantId}/inspection`, { expectedRevision },
  );
  return response.data.data;
}

export async function decideTenant(tenantId: number, request: {
  expectedRevision: number;
  decision: 'APPROVE' | 'REJECT' | 'SUPPLEMENT_REQUIRED';
  reason: string;
  humanConfirmed: boolean;
}): Promise<AdminTenantReview> {
  const response = await apiClient.post<ApiResponse<AdminTenantReview>>(
    `/console/admin/tenants/${tenantId}/decision`, request,
  );
  return response.data.data;
}

export async function updateTenantProfile(tenantId: number, request: {
  expectedRevision: number;
  shortName: string;
  contactName: string;
  businessAddress: string;
  customerLevel: number;
  bizManager: string;
  industry: string;
  reason: string;
}): Promise<void> {
  await apiClient.patch(`/console/admin/tenants/${tenantId}`, request);
}

export async function updateTenantOperatingStatus(tenantId: number, request: {
  expectedRevision: number;
  target: OperatingStatus;
  reason: string;
}): Promise<void> {
  await apiClient.post(`/console/admin/tenants/${tenantId}/status`, request);
}

export async function getTenantEvents(tenantId: number): Promise<QualificationEvent[]> {
  const response = await apiClient.get<ApiResponse<QualificationEvent[]>>(`/console/admin/tenants/${tenantId}/events`);
  return response.data.data;
}

export async function getTenantEvidence(tenantId: number, kind: 'BUSINESS_LICENSE' | 'REPRESENTATIVE_ID_FRONT' | 'REPRESENTATIVE_ID_BACK'): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/console/admin/tenants/${tenantId}/evidence/${kind}`, {
    responseType: 'blob',
  });
  return response.data;
}
