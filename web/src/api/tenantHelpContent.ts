export const TENANT_HELP_VERSION = '2026.09';

export interface GuideArticle {
  id: string;
  title: string;
  category: string;
  summary: string;
  steps: string[];
  permissions: string[];
}

export interface ApiDocEndpoint {
  id: string;
  method: string;
  path: string;
  version: string;
  authentication: string[];
  requestFields: Array<{ name: string; required: boolean; rule: string }>;
  responseFields: Array<{ name: string; rule: string }>;
  errors: Array<{ code: string; meaning: string }>;
  example: string;
}

export interface CustomerServiceChannel {
  name: string;
  availability: string;
  destination: string;
  fallback: string;
}

export const GUIDE_ARTICLES: GuideArticle[] = [
  {
    id: 'send-message',
    title: '发送短信',
    category: '发送',
    summary: '使用模板、签名和变量完成单条发送，发送前需要账号状态和机构状态可用。',
    steps: ['进入发送管理', '选择已审核模板和签名', '填写手机号与模板变量', '提交后查看提交流水和发送状态'],
    permissions: ['TENANT_ADMIN', 'TENANT_USER', 'TENANT_DEV'],
  },
  {
    id: 'qualification',
    title: '资质认证',
    category: '账号',
    summary: '机构管理员维护营业执照、法人、联系人和条件性证明材料。',
    steps: ['进入资质认证', '补齐必填资料', '提交审核', '按审核意见补充或等待通过'],
    permissions: ['TENANT_ADMIN'],
  },
  {
    id: 'reconciliation',
    title: '对账与发票',
    category: '财务',
    summary: '查看账单、确认对账、申请发票并保留财务证据。',
    steps: ['进入对账单', '核对计费周期和金额', '确认或提交异议', '在发票页面申请开票'],
    permissions: ['TENANT_ADMIN', 'TENANT_USER'],
  },
  {
    id: 'shortlink',
    title: '短链管理',
    category: '工具',
    summary: '创建短链后需通过自动安全检查和人工审核；未批准、过期或下线时不会跳转目标地址。',
    steps: ['进入短链管理', '填写目标 URL、域名和有效期', '提交审核', '查看状态和点击统计'],
    permissions: ['TENANT_ADMIN', 'TENANT_USER', 'TENANT_DEV'],
  },
];

export const API_DOCS: ApiDocEndpoint[] = [
  {
    id: 'sms-send-v1',
    method: 'POST',
    path: '/api/v1/sms/send',
    version: 'v1',
    authentication: [
      'X-App-Key: 租户 API Key',
      'X-Timestamp: Unix 秒时间戳，允许 5 分钟偏差',
      'X-Nonce: 一次性随机串，防重放',
      'X-Signature: Base64(HMAC-SHA256(stringToSign, appSecret))；stringToSign = HTTPMethod\\nURI\\nQueryString\\nx-app-key\\nx-nonce\\nx-timestamp\\nBody',
    ],
    requestFields: [
      { name: 'submitId', required: true, rule: '≤64，字母数字下划线点冒号横线' },
      { name: 'phoneNumber', required: true, rule: '中国大陆 1[3-9] 号段手机号' },
      { name: 'templateId', required: true, rule: '已审核模板 ID' },
      { name: 'signId', required: false, rule: '已审核签名 ID' },
      { name: 'templateParams', required: false, rule: '模板变量键值对' },
      { name: 'callbackUrl', required: false, rule: 'HTTPS 回调地址' },
    ],
    responseFields: [
      { name: 'code', rule: '200 表示请求被平台接收；业务拒绝返回错误码' },
      { name: 'data.messageId', rule: '平台消息 ID' },
      { name: 'data.status', rule: 'ACCEPTED、REJECTED 或 QUEUED' },
      { name: 'traceId', rule: '排查问题时提供给客服' },
    ],
    errors: [
      { code: 'TENANT_LIFECYCLE_INELIGIBLE', meaning: '机构状态不可新增提交，包括已终止' },
      { code: 'TENANT_ACCOUNT_INELIGIBLE', meaning: '账户停用或冻结' },
      { code: 'FREQUENCY_MOBILE_IDENTITY_NOT_READY', meaning: '手机号检索能力不可用，频控不能安全判断' },
      { code: 'ROUTING_REJECTED', meaning: '路由、黑名单、内容审核或频控规则拒绝提交' },
      { code: 'FEE_WARNING_CREDIT_BLOCKED', meaning: '费用预警或授信策略阻断提交' },
      { code: 'VALIDATION_FAILED', meaning: '请求字段不合法或请求体不可解析' },
    ],
    example: JSON.stringify({
      submitId: 'ORDER-20260910-0001',
      phoneNumber: '13800138000',
      templateId: '1001',
      signId: '2001',
      templateParams: { code: '2468' },
      callbackUrl: 'https://tenant.example.com/sms/callback',
    }, null, 2),
  },
];

export const CUSTOMER_SERVICE: CustomerServiceChannel = {
  name: 'YCSAN-SMS 客服',
  availability: '工作日 09:00-18:00；非工作时间使用 fallback 留痕',
  destination: 'support@ycsopen.example',
  fallback: '无法即时联系时，请复制 traceId、机构 ID、提交流水号和截图，通过企业内部服务台转交平台运营。',
};
