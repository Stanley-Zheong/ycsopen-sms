import { type ChangeEvent, type ReactNode, useRef, useState } from 'react';
import {
  classifyQualificationFailure,
  createRegistrationObjectSession,
  requestContactChallenge,
  uploadRegistrationObject,
  verifyContactChallenge,
  type QualificationPayload,
  type QualificationSubmissionContext,
  type RegistrationObjectSession,
  type TenantQualificationStatus,
  type UploadPurpose,
} from '@/api/tenantQualificationApi';
import { QUALIFICATION_FORM_TEST_IDS as T } from './tenantQualificationTestIds';

const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const USCC_ALPHABET = '0123456789ABCDEFGHJKLMNPQRTUWXY';
const USCC_WEIGHTS = [1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28];
const ID_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
const ID_CHECKSUM = '10X98765432';

type TextField = 'shortName' | 'fullName' | 'unifiedSocialCreditCode' | 'registeredCapital'
  | 'businessScope' | 'registeredAddress' | 'businessAddress' | 'licenseValidUntil'
  | 'legalRepName' | 'legalRepIdNo' | 'contactName' | 'contactIdNo' | 'contactPhone';

interface FormValues extends Record<TextField, string> {
  trademarkUse: boolean;
}

interface UploadValue {
  objectId: string;
  fileName: string;
  sessionId: string;
}

type UploadKey = 'businessLicenseObjectId' | 'legalRepIdFrontObjectId' | 'legalRepIdBackObjectId'
  | 'shortlinkDomainProofObjectId' | 'trademarkProofObjectId';

const EMPTY_VALUES: FormValues = {
  shortName: '', fullName: '', unifiedSocialCreditCode: '', registeredCapital: '', businessScope: '',
  registeredAddress: '', businessAddress: '', licenseValidUntil: '', legalRepName: '', legalRepIdNo: '',
  contactName: '', contactIdNo: '', contactPhone: '', trademarkUse: false,
};

const EMPTY_UPLOADS: Record<UploadKey, UploadValue | null> = {
  businessLicenseObjectId: null,
  legalRepIdFrontObjectId: null,
  legalRepIdBackObjectId: null,
  shortlinkDomainProofObjectId: null,
  trademarkProofObjectId: null,
};

const FIELD_DOM_ORDER: Array<TextField | UploadKey | 'contactCode'> = [
  'shortName', 'fullName', 'unifiedSocialCreditCode', 'registeredCapital', 'registeredAddress',
  'businessAddress', 'licenseValidUntil', 'businessScope', 'businessLicenseObjectId',
  'legalRepName', 'legalRepIdNo', 'legalRepIdFrontObjectId', 'legalRepIdBackObjectId',
  'contactName', 'contactIdNo', 'contactPhone', 'contactCode', 'trademarkProofObjectId',
];

const FIELD_HINTS: Record<TextField, string> = {
  shortName: '必填，不超过 20 个字符',
  fullName: '必填，不超过 100 个字符',
  unifiedSocialCreditCode: '必填，18 位且校验位正确',
  registeredCapital: '必填，非负数，最多两位小数',
  businessScope: '必填，不超过 1000 个字符',
  registeredAddress: '必填，不超过 255 个字符',
  businessAddress: '必填，不超过 255 个字符',
  licenseValidUntil: '必填，有效日期',
  legalRepName: '必填，不超过 50 个字符',
  legalRepIdNo: '必填，18 位且校验位正确；已提交值不会回显',
  contactName: '必填，不超过 50 个字符',
  contactIdNo: '必填，18 位且校验位正确；已提交值不会回显',
  contactPhone: '必填，11 位中国大陆手机号码；已提交值不会回显',
};

function initialValues(status?: TenantQualificationStatus): FormValues {
  if (!status) return EMPTY_VALUES;
  return {
    ...EMPTY_VALUES,
    shortName: status.shortName ?? '',
    fullName: status.fullName ?? '',
    unifiedSocialCreditCode: status.unifiedSocialCreditCode ?? '',
    registeredCapital: status.registeredCapital ?? '',
    businessScope: status.businessScope ?? '',
    registeredAddress: status.registeredAddress ?? '',
    businessAddress: status.businessAddress ?? '',
    licenseValidUntil: status.licenseValidUntil ?? '',
    legalRepName: status.legalRepresentativeName ?? '',
    contactName: status.contactName ?? '',
    trademarkUse: status.trademarkUse ?? false,
  };
}

function validUscc(value: string): boolean {
  const normalized = value.trim().toUpperCase();
  if (normalized.length !== 18) return false;
  let sum = 0;
  for (let index = 0; index < 17; index += 1) {
    const alphabetIndex = USCC_ALPHABET.indexOf(normalized[index]);
    if (alphabetIndex < 0) return false;
    sum += alphabetIndex * USCC_WEIGHTS[index];
  }
  return USCC_ALPHABET[(31 - (sum % 31)) % 31] === normalized[17];
}

function validIdentity(value: string): boolean {
  const normalized = value.trim().toUpperCase();
  if (!/^\d{17}[\dX]$/.test(normalized)) return false;
  const sum = ID_WEIGHTS.reduce((total, weight, index) => total + Number(normalized[index]) * weight, 0);
  return ID_CHECKSUM[sum % 11] === normalized[17];
}

function validate(values: FormValues, uploads: Record<UploadKey, UploadValue | null>, contactVerified: boolean) {
  const errors: Partial<Record<TextField | UploadKey | 'contactCode', string>> = {};
  if (!values.shortName.trim() || values.shortName.trim().length > 20) errors.shortName = '企业简称必填，且不超过 20 个字符';
  if (!values.fullName.trim() || values.fullName.trim().length > 100) errors.fullName = '企业全称必填，且不超过 100 个字符';
  if (!validUscc(values.unifiedSocialCreditCode)) errors.unifiedSocialCreditCode = '请输入校验位正确的 18 位统一社会信用代码';
  if (!/^\d+(?:\.\d{1,2})?$/.test(values.registeredCapital) || Number(values.registeredCapital) < 0) errors.registeredCapital = '注册资本必须是非负数，最多两位小数';
  if (!values.businessScope.trim() || values.businessScope.length > 1000) errors.businessScope = '经营范围必填，且不超过 1000 个字符';
  if (!values.registeredAddress.trim() || values.registeredAddress.length > 255) errors.registeredAddress = '注册地址必填，且不超过 255 个字符';
  if (!values.businessAddress.trim() || values.businessAddress.length > 255) errors.businessAddress = '经营地址必填，且不超过 255 个字符';
  if (!values.licenseValidUntil || Number.isNaN(Date.parse(values.licenseValidUntil))) errors.licenseValidUntil = '请选择有效的营业执照截止日期';
  if (!values.legalRepName.trim() || values.legalRepName.length > 50) errors.legalRepName = '法定代表人姓名必填，且不超过 50 个字符';
  if (!validIdentity(values.legalRepIdNo)) errors.legalRepIdNo = '请输入校验位正确的 18 位居民身份证号';
  if (!values.contactName.trim() || values.contactName.length > 50) errors.contactName = '联系人姓名必填，且不超过 50 个字符';
  if (!validIdentity(values.contactIdNo)) errors.contactIdNo = '请输入校验位正确的 18 位联系人身份证号';
  if (!/^1[3-9]\d{9}$/.test(values.contactPhone)) errors.contactPhone = '请输入有效的 11 位手机号码';
  else if (!contactVerified) errors.contactCode = '请完成联系人手机验证';
  if (!uploads.businessLicenseObjectId) errors.businessLicenseObjectId = '请上传营业执照';
  if (!uploads.legalRepIdFrontObjectId) errors.legalRepIdFrontObjectId = '请上传身份证正面';
  if (!uploads.legalRepIdBackObjectId) errors.legalRepIdBackObjectId = '请上传身份证反面';
  if (values.trademarkUse && !uploads.trademarkProofObjectId) errors.trademarkProofObjectId = '申请商标签名时必须上传商标证明';
  return errors;
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? <span id={id} className="qualification-field-error">{message}</span> : null;
}

export default function TenantQualificationForm({
  readOnly = false,
  initialStatus,
  submitTestId,
  submitLabel,
  errorSummaryTestId,
  beforeFields,
  validateBefore,
  onSubmit,
}: {
  readOnly?: boolean;
  initialStatus?: TenantQualificationStatus;
  submitTestId: string;
  submitLabel: string;
  errorSummaryTestId: string;
  beforeFields?: ReactNode;
  validateBefore?: () => HTMLElement | null;
  onSubmit: (context: QualificationSubmissionContext) => Promise<void>;
}) {
  const [values, setValues] = useState<FormValues>(() => initialValues(initialStatus));
  const [uploads, setUploads] = useState<Record<UploadKey, UploadValue | null>>(EMPTY_UPLOADS);
  const [uploading, setUploading] = useState<UploadKey | null>(null);
  const [uploadSession, setUploadSession] = useState<RegistrationObjectSession | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [challenge, setChallenge] = useState<{ id: string; phone: string; expiresAt: string } | null>(null);
  const [contactCode, setContactCode] = useState('');
  const [verifiedReceipt, setVerifiedReceipt] = useState<{ challengeId: string; phone: string } | null>(null);
  const [contactStatus, setContactStatus] = useState('尚未验证');
  const [sendingContact, setSendingContact] = useState(false);
  const [verifyingContact, setVerifyingContact] = useState(false);
  const [errors, setErrors] = useState<ReturnType<typeof validate>>({});
  const [externalInvalid, setExternalInvalid] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitStatus, setSubmitStatus] = useState(() => initialStatus?.verificationStatus === 'PENDING'
    ? '资质资料已提交，等待人工审核。' : '');
  const fieldRefs = useRef<Partial<Record<TextField | UploadKey | 'contactCode', HTMLElement | null>>>({});
  const contactPhoneRef = useRef(values.contactPhone);
  const contactGenerationRef = useRef(0);
  const contactPendingRef = useRef(false);
  const uploadPendingRef = useRef(false);
  const contactVerified = Boolean(challenge && verifiedReceipt
    && verifiedReceipt.challengeId === challenge.id
    && verifiedReceipt.phone === values.contactPhone
    && challenge.phone === values.contactPhone);

  const clearError = (field: keyof ReturnType<typeof validate>) => {
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  function change(field: TextField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    clearError(field);
    if (field === 'contactPhone') {
      contactPhoneRef.current = value;
      contactGenerationRef.current += 1;
      contactPendingRef.current = false;
      setChallenge(null);
      setVerifiedReceipt(null);
      setContactCode('');
      setSendingContact(false);
      setVerifyingContact(false);
      setContactStatus('手机号码已变更，请重新验证');
      clearError('contactCode');
    }
  }

  function invalidateUploadSession(message: string) {
    (Object.keys(EMPTY_UPLOADS) as UploadKey[]).forEach((key) => {
      const control = fieldRefs.current[key];
      if (control instanceof HTMLInputElement) control.value = '';
    });
    setSessionExpired(true);
    setUploadSession(null);
    setUploads(EMPTY_UPLOADS);
    setUploadError(message);
  }

  async function ensureUploadSession(): Promise<RegistrationObjectSession | null> {
    if (sessionExpired) return null;
    if (uploadSession) {
      if (new Date(uploadSession.expiresAt).getTime() <= Date.now()) {
        invalidateUploadSession('上传会话已过期，原会话中的全部材料必须重新上传。');
        return null;
      }
      return uploadSession;
    }
    const created = await createRegistrationObjectSession();
    setUploadSession(created);
    return created;
  }

  async function upload(key: UploadKey, purpose: UploadPurpose, file: File | undefined, imageOnly = false) {
    if (!file) return;
    const accepted = imageOnly ? ['image/jpeg', 'image/png'] : ['image/jpeg', 'image/png', 'application/pdf'];
    if (!accepted.includes(file.type) || file.size > MAX_UPLOAD_BYTES) {
      setErrors((current) => ({ ...current, [key]: `文件格式不支持或超过 10 MiB` }));
      return;
    }
    if (uploadPendingRef.current || sessionExpired) return;
    uploadPendingRef.current = true;
    setUploading(key);
    setUploadError(null);
    try {
      const session = await ensureUploadSession();
      if (!session) return;
      const uploaded = await uploadRegistrationObject(session, purpose, file);
      if (new Date(session.expiresAt).getTime() <= Date.now()) {
        invalidateUploadSession('上传会话已过期，原会话中的全部材料必须重新上传。');
        return;
      }
      setUploads((current) => ({ ...current, [key]: {
        objectId: uploaded.protectedObjectId, fileName: file.name, sessionId: session.registrationObjectSessionId,
      } }));
      clearError(key);
    } catch (failure) {
      const kind = classifyQualificationFailure(failure).kind;
      if (kind === 'uploadExpired' || kind === 'expired') {
        invalidateUploadSession('上传会话已过期，原会话中的全部材料必须重新上传。');
      } else {
        setUploadError('材料上传失败，已保留其他表单内容，请重试。');
      }
    } finally {
      uploadPendingRef.current = false;
      setUploading(null);
    }
  }

  async function sendCode() {
    if (contactPendingRef.current) return;
    if (!/^1[3-9]\d{9}$/.test(values.contactPhone)) {
      setErrors((current) => ({ ...current, contactPhone: '请输入有效的 11 位手机号码' }));
      fieldRefs.current.contactPhone?.focus();
      return;
    }
    const phone = values.contactPhone;
    const generation = contactGenerationRef.current;
    contactPendingRef.current = true;
    setSendingContact(true);
    setContactStatus('正在发送验证码…');
    try {
      const receipt = await requestContactChallenge(phone);
      if (generation !== contactGenerationRef.current || contactPhoneRef.current !== phone) return;
      setChallenge({ id: receipt.challengeId, phone, expiresAt: receipt.expiresAt });
      setVerifiedReceipt(null);
      setContactCode('');
      setContactStatus('验证码已发送，请在有效期内完成验证');
    } catch (failure) {
      if (generation !== contactGenerationRef.current || contactPhoneRef.current !== phone) return;
      const kind = classifyQualificationFailure(failure).kind;
      setContactStatus(kind === 'provider' ? '验证码服务暂不可用，请稍后重试'
        : kind === 'rateLimited' ? '验证码请求过于频繁，请稍后再试' : '验证码发送失败，请稍后重试');
    } finally {
      if (generation === contactGenerationRef.current) {
        contactPendingRef.current = false;
        setSendingContact(false);
      }
    }
  }

  async function verifyCode() {
    if (contactPendingRef.current) return;
    if (!challenge || challenge.phone !== values.contactPhone || !/^\d{6}$/.test(contactCode)) {
      setErrors((current) => ({ ...current, contactCode: '请输入收到的 6 位验证码' }));
      const codeControl = fieldRefs.current.contactCode;
      if (codeControl instanceof HTMLInputElement && !codeControl.disabled) codeControl.focus();
      else fieldRefs.current.contactPhone?.focus();
      return;
    }
    const receipt = challenge;
    const generation = contactGenerationRef.current;
    contactPendingRef.current = true;
    setVerifyingContact(true);
    setContactStatus('正在验证…');
    try {
      await verifyContactChallenge(receipt.id, receipt.phone, contactCode);
      if (generation !== contactGenerationRef.current || contactPhoneRef.current !== receipt.phone
        || challenge?.id !== receipt.id) return;
      setVerifiedReceipt({ challengeId: receipt.id, phone: receipt.phone });
      clearError('contactCode');
      setContactStatus('联系人手机已验证');
    } catch (failure) {
      if (generation !== contactGenerationRef.current || contactPhoneRef.current !== receipt.phone) return;
      const kind = classifyQualificationFailure(failure).kind;
      setVerifiedReceipt(null);
      if (kind === 'challengeExpired' || kind === 'expired') {
        setChallenge(null);
        setContactCode('');
        setContactStatus('验证码已过期，请重新发送');
      } else {
        setContactStatus('验证码无效或尝试次数已用尽');
      }
    } finally {
      if (generation === contactGenerationRef.current) {
        contactPendingRef.current = false;
        setVerifyingContact(false);
      }
    }
  }

  async function submit() {
    const externalTarget = validateBefore?.() ?? null;
    setExternalInvalid(Boolean(externalTarget));
    if (externalTarget) {
      setSubmitStatus('请修正管理员账号信息后再提交。');
      externalTarget.focus();
      return;
    }
    const nextErrors = validate(values, uploads, contactVerified);
    setErrors(nextErrors);
    const first = FIELD_DOM_ORDER.find((field) => Boolean(nextErrors[field]));
    if (first) {
      setSubmitStatus('请修正表单中的错误后再提交。');
      const target = fieldRefs.current[first];
      if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
        if (!target.disabled) target.focus();
        else if (first === 'contactCode') fieldRefs.current.contactPhone?.focus();
      }
      else if (first === 'contactCode') fieldRefs.current.contactPhone?.focus();
      return;
    }
    const sessionExpiredLocally = !uploadSession || new Date(uploadSession.expiresAt).getTime() <= Date.now();
    const crossSessionClaim = Object.values(uploads).filter(Boolean)
      .some((item) => item!.sessionId !== uploadSession?.registrationObjectSessionId);
    if (uploading || uploadPendingRef.current || sessionExpired || sessionExpiredLocally || crossSessionClaim) {
      invalidateUploadSession('上传会话已过期或材料会话不一致，请重新上传全部材料。');
      setSubmitStatus('请重新上传全部材料后再提交。');
      return;
    }
    if (!challenge || !verifiedReceipt) return;
    const qualification: QualificationPayload = {
      shortName: values.shortName.trim(),
      fullName: values.fullName.trim(),
      unifiedSocialCreditCode: values.unifiedSocialCreditCode.trim().toUpperCase(),
      registrationObjectSessionId: uploadSession.registrationObjectSessionId,
      businessLicenseObjectId: uploads.businessLicenseObjectId!.objectId,
      legalRepName: values.legalRepName.trim(),
      legalRepIdNo: values.legalRepIdNo.trim().toUpperCase(),
      legalRepIdFrontObjectId: uploads.legalRepIdFrontObjectId!.objectId,
      legalRepIdBackObjectId: uploads.legalRepIdBackObjectId!.objectId,
      contactName: values.contactName.trim(),
      contactIdNo: values.contactIdNo.trim().toUpperCase(),
      contactPhone: values.contactPhone,
      shortlinkDomainProofObjectId: uploads.shortlinkDomainProofObjectId?.objectId ?? null,
      trademarkProofObjectId: uploads.trademarkProofObjectId?.objectId ?? null,
      trademarkUse: values.trademarkUse,
      registeredCapital: values.registeredCapital,
      businessScope: values.businessScope.trim(),
      registeredAddress: values.registeredAddress.trim(),
      businessAddress: values.businessAddress.trim(),
      licenseValidUntil: values.licenseValidUntil,
    };
    setSubmitting(true);
    setSubmitStatus('正在提交资质资料…');
    try {
      await onSubmit({ qualification, contactChallengeId: challenge.id, uploadToken: uploadSession.registrationUploadToken });
      setSubmitStatus('资质资料已提交，等待人工审核。');
    } catch (failure) {
      const kind = classifyQualificationFailure(failure).kind;
      if (kind === 'uploadExpired' || kind === 'expired') {
        invalidateUploadSession('上传会话已过期，原会话中的全部材料必须重新上传。');
        setSubmitStatus('上传会话已过期，请重新上传材料后提交。');
      } else if (kind === 'challengeExpired') {
        setChallenge(null);
        setVerifiedReceipt(null);
        setContactCode('');
        setContactStatus('验证码已过期，请重新发送并验证。');
        setSubmitStatus('联系人验证已过期，请重新验证后提交。');
      } else if (kind === 'duplicate') {
        setErrors((current) => ({ ...current, unifiedSocialCreditCode: '该统一社会信用代码已注册，请核对或联系平台管理员' }));
        fieldRefs.current.unifiedSocialCreditCode?.focus();
        setSubmitStatus('企业登记信息重复，表单内容已保留。');
      } else if (kind === 'stale') {
        setSubmitStatus('资料状态已更新，请刷新后重新提交。');
      } else {
        setSubmitStatus(kind === 'provider' ? '外部服务暂不可用，表单内容已保留。' : '提交失败，表单内容已保留，请重试。');
      }
      throw failure;
    } finally {
      setSubmitting(false);
    }
  }

  const disabled = readOnly || submitting;
  const submitDisabled = disabled || uploading !== null || uploadPendingRef.current || sessionExpired
    || sendingContact || verifyingContact;
  const input = (field: TextField, testId: string, label: string, options?: {
    type?: string; maxLength?: number; multiline?: boolean;
  }) => {
    const errorId = `${testId}-error`;
    const hintId = `${testId}-hint`;
    const common = {
      id: testId,
      'data-testid': testId,
      value: values[field],
      disabled,
      maxLength: options?.maxLength,
      'aria-invalid': Boolean(errors[field]),
      'aria-describedby': errors[field] ? `${hintId} ${errorId}` : hintId,
      ref: (node: HTMLInputElement | HTMLTextAreaElement | null) => { fieldRefs.current[field] = node; },
      onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => change(field, event.target.value),
    };
    return (
      <label htmlFor={testId}>{label}
        {options?.multiline ? <textarea {...common} rows={3} /> : <input {...common} type={options?.type ?? 'text'} />}
        <span id={hintId} className="qualification-field-hint">{FIELD_HINTS[field]}</span>
        <FieldError id={errorId} message={errors[field]} />
      </label>
    );
  };

  const uploadInput = (key: UploadKey, purpose: UploadPurpose, testId: string, label: string, imageOnly = false) => {
    const errorId = `${testId}-error`;
    const present = key === 'businessLicenseObjectId' ? initialStatus?.businessLicensePresent
      : key === 'legalRepIdFrontObjectId' ? initialStatus?.legalRepresentativeIdFrontPresent
        : key === 'legalRepIdBackObjectId' ? initialStatus?.legalRepresentativeIdBackPresent
          : key === 'shortlinkDomainProofObjectId' ? initialStatus?.shortlinkProofPresent
            : initialStatus?.trademarkProofPresent;
    const state = sessionExpired ? '上传会话已过期，必须重新上传'
      : uploading === key ? '上传中…'
      : uploads[key]?.fileName ? `已上传：${uploads[key]!.fileName}`
        : present ? '材料已提交，出于安全不回显' : '尚未上传';
    return (
      <label htmlFor={testId}>{label}
        <input
          id={testId}
          data-testid={key === 'businessLicenseObjectId' ? undefined : testId}
          type="file"
          accept={imageOnly ? 'image/jpeg,image/png' : 'image/jpeg,image/png,application/pdf'}
          disabled={disabled || uploading !== null || sessionExpired}
          aria-invalid={Boolean(errors[key])}
          aria-describedby={errors[key] ? errorId : undefined}
          ref={(node) => { fieldRefs.current[key] = node; }}
          onChange={(event) => void upload(key, purpose, event.currentTarget.files?.[0], imageOnly)}
        />
        <span
          data-testid={key === 'businessLicenseObjectId' ? testId : undefined}
          className="qualification-upload-state"
          role="status"
          aria-live="polite"
        >
          {state}
        </span>
        <FieldError id={errorId} message={errors[key]} />
      </label>
    );
  };

  return (
    <form data-testid={T.form} className="qualification-form" onSubmit={(event) => { event.preventDefault(); void submit().catch(() => undefined); }} noValidate>
      {beforeFields}
      {(externalInvalid || Object.values(errors).some(Boolean)) && (
        <div data-testid={errorSummaryTestId} className="qualification-alert error" role="alert" tabIndex={-1}>
          表单信息不完整或格式有误，请按字段提示修正。
        </div>
      )}

      <fieldset data-testid={T.companySection} disabled={disabled}>
        <legend>企业资料</legend>
        <div className="qualification-grid">
          {input('shortName', T.shortName, '企业简称', { maxLength: 20 })}
          {input('fullName', T.fullName, '企业全称', { maxLength: 100 })}
          {input('unifiedSocialCreditCode', T.creditCode, '统一社会信用代码', { maxLength: 18 })}
          {input('registeredCapital', T.registrationCapital, '注册资本（万元）')}
          {input('registeredAddress', T.registeredAddress, '注册地址', { maxLength: 255 })}
          {input('businessAddress', T.operatingAddress, '经营地址', { maxLength: 255 })}
          {input('licenseValidUntil', T.licenseValidUntil, '营业执照有效期至', { type: 'date' })}
        </div>
        {input('businessScope', T.businessScope, '经营范围', { maxLength: 1000, multiline: true })}
        <div data-testid={T.licenseUpload} className="qualification-upload-group">
          <p>营业执照：JPG、PNG 或 PDF，单个不超过 10 MiB。</p>
          {uploadInput('businessLicenseObjectId', 'business-license', T.licenseUploadStatus, '选择营业执照')}
        </div>
      </fieldset>

      <fieldset data-testid={T.legalSection} disabled={disabled}>
        <legend>法定代表人</legend>
        <div className="qualification-grid">
          {input('legalRepName', T.legalName, '法定代表人姓名', { maxLength: 50 })}
          {input('legalRepIdNo', T.legalId, '法定代表人身份证号', { maxLength: 18 })}
        </div>
        <div data-testid={T.legalIdFiles} className="qualification-grid">
          {uploadInput('legalRepIdFrontObjectId', 'legal-rep-id-front', T.legalIdFrontUpload, '身份证正面（JPG/PNG，不超过 10 MiB）', true)}
          {uploadInput('legalRepIdBackObjectId', 'legal-rep-id-back', T.legalIdBackUpload, '身份证反面（JPG/PNG，不超过 10 MiB）', true)}
        </div>
        {readOnly && initialStatus?.legalRepresentativeIdentityPresent && (
          <p className="qualification-field-hint">身份信息已提交，出于安全不回显</p>
        )}
      </fieldset>

      <fieldset data-testid={T.contact} disabled={disabled}>
        <legend>业务联系人</legend>
        <div className="qualification-grid">
          {input('contactName', T.contactName, '联系人姓名', { maxLength: 50 })}
          {input('contactIdNo', T.contactId, '联系人身份证号', { maxLength: 18 })}
          {input('contactPhone', T.contactPhone, '联系人手机号码', { maxLength: 11 })}
        </div>
        <div className="qualification-contact-actions">
          <button data-testid={T.contactCodeSend} type="button" disabled={disabled || sendingContact || verifyingContact} onClick={() => void sendCode()}>
            {sendingContact ? '发送中…' : '发送验证码'}
          </button>
          <label htmlFor={T.contactCodeInput}>手机验证码
            <input
              id={T.contactCodeInput}
              data-testid={T.contactCodeInput}
              inputMode="numeric"
              value={contactCode}
              maxLength={6}
              disabled={disabled || !challenge || sendingContact || verifyingContact}
              aria-invalid={Boolean(errors.contactCode)}
              aria-describedby={errors.contactCode ? `${T.contactCodeInput}-error` : undefined}
              ref={(node) => { fieldRefs.current.contactCode = node; }}
              onChange={(event) => { setContactCode(event.target.value.replace(/\D/g, '')); clearError('contactCode'); }}
            />
            <FieldError id={`${T.contactCodeInput}-error`} message={errors.contactCode} />
          </label>
          <button data-testid={T.contactCodeVerify} type="button" disabled={disabled || !challenge || sendingContact || verifyingContact} onClick={() => void verifyCode()}>
            {verifyingContact ? '验证中…' : '验证'}
          </button>
        </div>
        {readOnly && (initialStatus?.contactIdentityPresent || initialStatus?.contactPhonePresent) && (
          <p className="qualification-field-hint">手机号与身份信息已提交，出于安全不回显</p>
        )}
        <p data-testid={T.contactVerifiedStatus} className={contactVerified ? 'qualification-success' : undefined} role="status" aria-live="polite">
          {contactStatus}{challenge && !contactVerified ? `（有效至 ${new Date(challenge.expiresAt).toLocaleTimeString('zh-CN', { hour12: false })}）` : ''}
        </p>
      </fieldset>

      <fieldset disabled={disabled}>
        <legend>可选与条件证明</legend>
        <div data-testid={T.shortlinkProof}>
          {uploadInput('shortlinkDomainProofObjectId', 'shortlink-domain-proof', T.shortlinkProofUpload, '短链域名权属证明（试用期可选）')}
        </div>
        <div data-testid={T.trademarkProof}>
          <label htmlFor={T.trademarkIntent} className="qualification-check">
            <input
              id={T.trademarkIntent}
              data-testid={T.trademarkIntent}
              type="checkbox"
              checked={values.trademarkUse}
              disabled={disabled}
              onChange={(event) => setValues((current) => ({ ...current, trademarkUse: event.target.checked }))}
            />
            申请使用商标签名（选择后必须上传权属证明）
          </label>
          {uploadInput('trademarkProofObjectId', 'trademark-proof', T.trademarkProofUpload, '商标权属证明')}
        </div>
      </fieldset>

      {uploadError && <p className="qualification-alert error" role="alert">{uploadError}</p>}
      {sessionExpired && (
        <div data-testid="public-tenant-qualification-register-session-expired" className="qualification-alert warning" role="alert">
          上传会话已过期，旧会话不可继续使用。
          <button
            data-testid="public-tenant-qualification-register-session-restart"
            type="button"
            onClick={() => { setSessionExpired(false); setUploadError(null); setUploadSession(null); setUploads(EMPTY_UPLOADS); }}
          >重新开始上传</button>
        </div>
      )}
      <div className="qualification-form-footer">
        <button data-testid={submitTestId} type="submit" disabled={submitDisabled}>{submitting ? '提交中…' : submitLabel}</button>
        <span data-testid={T.submitStatus} role="status" aria-live="polite">{submitStatus}</span>
      </div>
    </form>
  );
}
