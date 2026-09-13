/** 与 core 后端 ApiResponse<T> 对应，见 core/.../web/dto/ApiResponse.java（PRD 9.1 节统一响应结构）。 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
  traceId: string | null;
}

export interface LoginResponse {
  accessToken: string;
  userType:
    | 'ADMIN'
    | 'OPERATOR'
    | 'FINANCE'
    | 'TENANT_ADMIN'
    | 'TENANT_USER'
    | 'TENANT_DEV';
  tenantId: number | null;
}

/** F-11.9 通道/机构月度投诉占比看板单行数据。 */
export interface ComplaintRatioItem {
  statMonth?: string;
  dimensionType?: 'CHANNEL' | 'TENANT';
  dimensionId: number;
  dimensionName: string | null;
  sendCount: number;
  complaintCount: number;
  ratio: number;
  thresholdValue?: number;
  thresholdConfigVersion?: string;
  overThreshold: boolean;
  dataQuality?: 'COMPLETE' | 'ZERO_DENOMINATOR' | 'UNKNOWN' | string;
  thresholdResult?: 'BREACHED' | 'NORMAL' | 'UNKNOWN' | string;
  sourceRegistry?: string;
  freshnessPolicy?: string;
  calculatedAt?: string | null;
  rank?: number;
  interventionAvailable?: boolean;
  alertSourceKey?: string;
}

export interface ComplaintRatioCase {
  id: number;
  source: string;
  tenantId: number | null;
  channelId: number | null;
  messageId: string | null;
  summary: string | null;
  status: string;
  attributionQuality: string;
  createdAt: string;
}

export interface ComplaintRatioInterventionResult {
  dimensionType: 'CHANNEL' | 'TENANT';
  dimensionId: number;
  status: string;
  evidenceId: number;
  alertRecordId: number | null;
  sourceKey: string;
  action: string;
}

export interface Tenant {
  id: number;
  tenantNo: string;
  shortName: string;
  fullName: string;
  verificationStatus: 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED';
  lifecycleStatus: 'SUBMITTED' | 'TRIAL' | 'TRIAL_FROZEN' | 'SIGNED' | 'FROZEN' | 'TERMINATED';
}

export interface Channel {
  id: number;
  channelName: string;
  protocol: 'CMPP' | 'SGIP' | 'SMGP' | 'HTTP';
  operator: 'MOBILE' | 'UNICOM' | 'TELECOM' | 'VIRTUAL' | 'INTERNATIONAL';
  status: 'NORMAL' | 'MAINTENANCE' | 'ABNORMAL' | 'PAUSED' | 'OFFLINE';
  priority: number;
}
