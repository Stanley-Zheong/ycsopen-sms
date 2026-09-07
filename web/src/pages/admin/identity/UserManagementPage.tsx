import { type FormEvent, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  changePlatformAccountState,
  createPlatformAccount,
  getPlatformAccounts,
  getPlatformRoles,
  IDENTITY_PERMISSIONS,
  type PlatformAccount,
  type PlatformUserType,
  type SavePlatformAccountRequest,
  updatePlatformAccount,
} from '@/api/identity';
import { useIdentityAccess } from './useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';
import ModalDialog from '@/components/common/ModalDialog';
import { mutationErrorMessage } from '@/api/client';
import {
  AUDIT_PERMISSIONS,
  revealPlatformAccountPhone,
  type RevealPurpose,
} from '@/api/audit';

interface AccountFormState {
  username: string;
  password: string;
  phone: string;
  email: string;
  realName: string;
  userType: PlatformUserType;
  validUntil: string;
  roleIds: number[];
}

const EMPTY_FORM: AccountFormState = {
  username: '', password: '', phone: '', email: '', realName: '', userType: 'OPERATOR', validUntil: '', roleIds: [],
};

const STATUS_LABELS: Record<PlatformAccount['status'], string> = {
  ACTIVE: '启用', DISABLED: '禁用', LOCKED: '锁定',
};

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function AccountStateAction({
  account,
  documented,
  onDisable,
  onTransition,
}: {
  account: PlatformAccount;
  documented: boolean;
  onDisable: (account: PlatformAccount) => void;
  onTransition: (userId: number, action: 'enable' | 'unlock') => void;
}) {
  if (account.status === 'ACTIVE') {
    if (documented) {
      return (
        <span data-testid="admin-console-identity-users-row-disable">
          <button data-testid="admin-console-identity-users-disable" type="button" aria-label={`禁用${account.username}`} onClick={() => onDisable(account)}>禁用</button>
        </span>
      );
    }
    return <button type="button" aria-label={`禁用${account.username}`} onClick={() => onDisable(account)}>禁用</button>;
  }
  if (account.status === 'DISABLED') {
    return documented
      ? <button data-testid="admin-console-identity-users-enable" type="button" aria-label={`启用${account.username}`} onClick={() => onTransition(account.id, 'enable')}>启用</button>
      : <button type="button" aria-label={`启用${account.username}`} onClick={() => onTransition(account.id, 'enable')}>启用</button>;
  }
  return documented
    ? <button data-testid="admin-console-identity-users-unlock" type="button" aria-label={`解锁${account.username}`} onClick={() => onTransition(account.id, 'unlock')}>解锁</button>
    : <button type="button" aria-label={`解锁${account.username}`} onClick={() => onTransition(account.id, 'unlock')}>解锁</button>;
}

export default function UserManagementPage() {
  const queryClient = useQueryClient();
  const access = useIdentityAccess();
  const canViewAccounts = access.can(IDENTITY_PERMISSIONS.usersRead);
  const canManageAccounts = access.can(IDENTITY_PERMISSIONS.createUser) || access.can(IDENTITY_PERMISSIONS.updateUser);
  const canViewRoles = access.can(IDENTITY_PERMISSIONS.rolesRead);
  const shouldLoadRoles = canManageAccounts || canViewRoles;
  const accountQueryKey = protectedQueryKey('platform-accounts');
  const roleQueryKey = protectedQueryKey('platform-roles');
  const accounts = useQuery({ queryKey: accountQueryKey, queryFn: getPlatformAccounts, enabled: canViewAccounts });
  const roles = useQuery({ queryKey: roleQueryKey, queryFn: getPlatformRoles, enabled: shouldLoadRoles });
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<AccountFormState>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingDisable, setPendingDisable] = useState<PlatformAccount | null>(null);
  const [revealAccount, setRevealAccount] = useState<PlatformAccount | null>(null);
  const [revealPurpose, setRevealPurpose] = useState<RevealPurpose | ''>('');
  const [revealedPhone, setRevealedPhone] = useState<string | null>(null);
  const [revealExpiresAt, setRevealExpiresAt] = useState<number | null>(null);
  const [revealPending, setRevealPending] = useState(false);
  const [revealError, setRevealError] = useState<string | null>(null);
  const revealRequestSequence = useRef(0);
  const canRevealPhone = access.can(AUDIT_PERMISSIONS.reveal)
    && access.can(AUDIT_PERMISSIONS.revealApi)
    && access.can(IDENTITY_PERMISSIONS.accountsAll);

  useEffect(() => {
    if (!revealExpiresAt) return undefined;
    const remaining = Math.max(0, revealExpiresAt - Date.now());
    const timer = window.setTimeout(() => {
      setRevealedPhone(null);
      setRevealExpiresAt(null);
      setRevealError('本次查看已过期，请关闭后重新查看');
    }, remaining);
    return () => window.clearTimeout(timer);
  }, [revealExpiresAt]);

  const saveAccount = useMutation({
    mutationFn: ({ userId, request }: { userId: number | null; request: SavePlatformAccountRequest }) => (
      userId === null ? createPlatformAccount(request) : updatePlatformAccount(userId, request)
    ),
    onSuccess: async () => {
      setFormOpen(false);
      setEditingId(null);
      setForm(EMPTY_FORM);
      await queryClient.invalidateQueries({ queryKey: accountQueryKey });
    },
  });

  const transitionAccount = useMutation({
    mutationFn: ({ userId, action }: { userId: number; action: 'disable' | 'enable' | 'unlock' }) => (
      changePlatformAccountState(userId, action)
    ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountQueryKey }),
  });

  function openCreateForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setFormOpen(true);
  }

  function openEditForm(account: PlatformAccount) {
    setEditingId(account.id);
    setForm({
      username: account.username,
      password: '',
      phone: '',
      email: account.email ?? '',
      realName: account.realName ?? '',
      userType: account.userType,
      validUntil: account.validUntil ?? '',
      roleIds: account.roleIds,
    });
    setFormError(null);
    setFormOpen(true);
  }

  function submitAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!/^[A-Za-z0-9_]{4,20}$/.test(form.username)) {
      setFormError('用户名须为 4-20 位字母、数字或下划线');
      return;
    }
    if ((!editingId || form.password) && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/.test(form.password)) {
      setFormError('密码至少 8 位，且须包含大写字母、小写字母和数字');
      return;
    }
    if ((!editingId || form.phone) && !/^1[3-9]\d{9}$/.test(form.phone)) {
      setFormError('请输入有效的 11 位国内手机号');
      return;
    }
    if (form.roleIds.length === 0) {
      setFormError('请至少选择一个有效角色');
      return;
    }
    if (form.validUntil && form.validUntil < today()) {
      setFormError('有效期不能早于当前日期');
      return;
    }
    const request: SavePlatformAccountRequest = {
      username: form.username,
      phone: form.phone,
      email: form.email,
      realName: form.realName,
      userType: form.userType,
      validUntil: form.validUntil || null,
      roleIds: form.roleIds,
      ...(form.password ? { password: form.password } : {}),
    };
    saveAccount.mutate({ userId: editingId, request });
  }

  function closeAccountForm() {
    const baseline = editingId === null ? EMPTY_FORM : (() => {
      const account = accounts.data?.find((item) => item.id === editingId);
      return account ? {
        username: account.username, password: '', phone: '', email: account.email ?? '',
        realName: account.realName ?? '', userType: account.userType, validUntil: account.validUntil ?? '',
        roleIds: account.roleIds,
      } : EMPTY_FORM;
    })();
    if (JSON.stringify(form) !== JSON.stringify(baseline)
        && !window.confirm('放弃未保存的账号修改？')) return;
    setFormOpen(false);
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  function openRevealDialog(account: PlatformAccount) {
    revealRequestSequence.current += 1;
    setRevealAccount(account);
    setRevealPurpose('');
    setRevealedPhone(null);
    setRevealExpiresAt(null);
    setRevealPending(false);
    setRevealError(null);
  }

  function closeRevealDialog() {
    revealRequestSequence.current += 1;
    setRevealAccount(null);
    setRevealPurpose('');
    setRevealedPhone(null);
    setRevealExpiresAt(null);
    setRevealPending(false);
    setRevealError(null);
  }

  async function confirmReveal() {
    if (!revealAccount || !revealPurpose || revealPending) return;
    const requestSequence = ++revealRequestSequence.current;
    setRevealedPhone(null);
    setRevealExpiresAt(null);
    setRevealError(null);
    setRevealPending(true);
    try {
      const result = await revealPlatformAccountPhone(revealAccount.id, revealPurpose);
      if (requestSequence !== revealRequestSequence.current) return;
      const expiresAt = Date.parse(result.expiresAt);
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
        setRevealError('完整手机号查看已过期，请重新申请');
        return;
      }
      setRevealedPhone(result.value);
      setRevealExpiresAt(expiresAt);
    } catch (error) {
      if (requestSequence !== revealRequestSequence.current) return;
      setRevealError(axios.isAxiosError(error) && error.response?.status === 403
        ? '无权查看完整手机号'
        : '完整手机号加载失败，请稍后重试');
    } finally {
      if (requestSequence === revealRequestSequence.current) setRevealPending(false);
    }
  }

  if (access.isLoading || (canViewAccounts && accounts.isLoading) || (shouldLoadRoles && roles.isLoading)) return <div className="card">加载账号…</div>;
  if (access.isError) return <div className="card" role="alert">权限范围加载失败，请稍后重试</div>;
  if (!canViewAccounts) return <div className="card" role="alert">无权查看平台账号</div>;
  if (accounts.isError || (shouldLoadRoles && roles.isError)) return <div className="card" role="alert">账号数据加载失败，请稍后重试</div>;

  const accountRows = accounts.data ?? [];
  const roleNames = new Map((roles.data ?? []).map((role) => [role.id, role.name]));
  const firstActiveIndex = accountRows.findIndex((account) => account.status === 'ACTIVE');
  const firstDisabledIndex = accountRows.findIndex((account) => account.status === 'DISABLED');
  const firstLockedIndex = accountRows.findIndex((account) => account.status === 'LOCKED');

  return (
    <section data-testid="admin-console-identity-users-page">
      <div className="card">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <h1 style={{ marginBottom: 4 }}>平台账号</h1>
            <p style={{ marginTop: 0, color: '#667085' }}>管理管理员、运营和财务账号；状态变更由服务端鉴权并留痕。</p>
          </div>
          {access.can(IDENTITY_PERMISSIONS.createUser) && (
            <button data-testid="admin-console-identity-users-create" type="button" onClick={openCreateForm}>新建账号</button>
          )}
        </div>

        <table className="ratio-table" data-testid="admin-console-identity-users-state">
          <caption className="visually-hidden">平台账号状态与可执行操作</caption>
          <thead>
            <tr><th>用户名</th><th>姓名</th><th>手机号</th><th>类型</th><th>角色</th><th>状态</th><th>有效期</th><th>最近登录</th><th>操作</th></tr>
          </thead>
          <tbody>
            {accountRows.map((account, index) => (
              <tr key={account.id} data-row-key={account.id}>
                <td>{account.username}</td><td>{account.realName ?? '—'}</td>
                <td><span className="sensitive-value-cell">
                  {index === 0
                    ? <span data-testid="shared-privileged-data-sensitive-value">{account.maskedPhone ?? '—'}</span>
                    : <span data-testid={`shared-privileged-data-sensitive-value-${account.id}`}>{account.maskedPhone ?? '—'}</span>}
                  {canRevealPhone && account.maskedPhone && (index === 0 ? (
                    <button
                      data-testid="shared-privileged-data-sensitive-value-reveal"
                      type="button"
                      aria-label={`查看${account.username}完整手机号`}
                      onClick={() => openRevealDialog(account)}
                    >查看完整手机号</button>
                  ) : (
                    <button
                      data-testid={`shared-privileged-data-sensitive-value-reveal-${account.id}`}
                      type="button"
                      aria-label={`查看${account.username}完整手机号`}
                      onClick={() => openRevealDialog(account)}
                    >查看完整手机号</button>
                  ))}
                </span></td>
                <td>{account.userType}</td>
                <td>{account.roleIds.map((roleId) => roleNames.get(roleId) ?? `角色 #${roleId}`).join('、') || '未分配'}</td>
                <td>{STATUS_LABELS[account.status]}</td><td>{account.validUntil ?? '长期有效'}</td>
                <td>{account.lastLoginAt ? new Date(account.lastLoginAt).toLocaleString('zh-CN', { hour12: false }) : '暂无'}</td>
                <td style={{ display: 'flex', gap: 6 }}>
                  {access.can(IDENTITY_PERMISSIONS.updateUser) && (
                    <button data-testid={index === 0 ? 'admin-console-identity-users-edit' : undefined} type="button" aria-label={`编辑${account.username}`} onClick={() => openEditForm(account)}>编辑</button>
                  )}
                  {access.can(IDENTITY_PERMISSIONS.changeUserState) && (
                    <AccountStateAction
                      account={account}
                      documented={index === (account.status === 'ACTIVE' ? firstActiveIndex : account.status === 'DISABLED' ? firstDisabledIndex : firstLockedIndex)}
                      onDisable={setPendingDisable}
                      onTransition={(userId, action) => transitionAccount.mutate({ userId, action })}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {formOpen && (
        <ModalDialog labelledBy="platform-account-form-title" onRequestClose={closeAccountForm}>
        <form data-testid="admin-console-identity-users-form" onSubmit={submitAccount}>
          <h2 id="platform-account-form-title">{editingId ? '编辑平台账号' : '新建平台账号'}</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(220px, 1fr))', gap: 12 }}>
            <label>用户名
              <input data-testid="admin-console-identity-users-form-username" required minLength={4} maxLength={20} value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
            </label>
            <label>密码
              <input data-testid="admin-console-identity-users-form-password" type="password" required={!editingId} maxLength={72} autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
            </label>
            <label>手机号
              <input data-testid="admin-console-identity-users-form-phone" required={!editingId} inputMode="numeric" placeholder={editingId ? '留空则保持原手机号' : undefined} value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} />
            </label>
            <label>邮箱
              <input aria-label="邮箱" type="email" maxLength={100} value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
            </label>
            <label>真实姓名
              <input aria-label="真实姓名" required maxLength={50} value={form.realName} onChange={(event) => setForm({ ...form, realName: event.target.value })} />
            </label>
            <label>用户类型
              <select data-testid="admin-console-identity-users-form-type" required value={form.userType} onChange={(event) => setForm({ ...form, userType: event.target.value as PlatformUserType })}>
                <option value="ADMIN">系统管理员</option><option value="OPERATOR">运营</option><option value="FINANCE">财务</option>
              </select>
            </label>
            <label>有效期（留空为长期有效）
              <input data-testid="admin-console-identity-users-form-validity" type="date" min={today()} value={form.validUntil} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} />
            </label>
            <label>角色
              <select
                data-testid="admin-console-identity-users-form-roles"
                aria-label="角色"
                multiple
                required
                value={form.roleIds.map(String)}
                onChange={(event) => setForm({ ...form, roleIds: Array.from(event.target.selectedOptions, (option) => Number(option.value)) })}
              >
                {(roles.data ?? []).filter((role) => role.status === 'ACTIVE').map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
              </select>
            </label>
          </div>
          {(formError || saveAccount.isError) && <p role="alert">{formError
            ?? mutationErrorMessage(saveAccount.error, '账号保存失败，请检查字段或稍后重试')}</p>}
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button type="submit" disabled={saveAccount.isPending}>保存账号</button>
            <button type="button" onClick={closeAccountForm}>放弃账号修改</button>
          </div>
        </form>
        </ModalDialog>
      )}
      {pendingDisable && (
        <ModalDialog labelledBy="disable-account-title" onRequestClose={() => setPendingDisable(null)}>
          <h2 id="disable-account-title">确认禁用账号</h2>
          <p>禁用后，该账号的现有会话将立即失效。确认禁用？</p>
          <button type="button" onClick={() => {
            transitionAccount.mutate({ userId: pendingDisable.id, action: 'disable' });
            setPendingDisable(null);
          }}>确认禁用</button>{' '}
          <button type="button" onClick={() => setPendingDisable(null)}>保留启用状态</button>
        </ModalDialog>
      )}
      {revealAccount && (
        <ModalDialog labelledBy="privileged-data-reveal-title" onRequestClose={closeRevealDialog}>
          <div data-testid="shared-privileged-data-sensitive-value-dialog">
            <h2 data-testid="shared-privileged-data-sensitive-value-title" id="privileged-data-reveal-title">查看完整手机号</h2>
            <p data-testid="shared-privileged-data-sensitive-value-account">账号：{revealAccount.username}</p>
            <p data-testid="shared-privileged-data-sensitive-value-warning" className="sensitive-value-warning">完整手机号属于敏感信息。本次查看会记录操作人、用途和问题编号。</p>
            <label>查看用途
              <select
                data-testid="shared-privileged-data-sensitive-value-purpose"
                value={revealPurpose}
                disabled={revealPending || Boolean(revealedPhone)}
                onChange={(event) => setRevealPurpose(event.target.value as RevealPurpose | '')}
              >
                <option value="">请选择用途</option>
                <option value="CUSTOMER_SUPPORT">客户支持</option>
                <option value="SECURITY_INVESTIGATION">安全调查</option>
                <option value="COMPLIANCE_REVIEW">合规审查</option>
              </select>
            </label>
            {revealedPhone && (
              <div data-testid="shared-privileged-data-sensitive-value-result" className="sensitive-value-result" aria-live="polite">
                {revealedPhone}
                <div style={{ marginTop: 4, fontSize: 13, fontWeight: 400, letterSpacing: 0 }}>仅本次查看，关闭后清除</div>
              </div>
            )}
            {revealError && <p data-testid="shared-privileged-data-sensitive-value-error" role="alert">{revealError}</p>}
            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button
                data-testid="shared-privileged-data-sensitive-value-confirm"
                type="button"
                disabled={!revealPurpose || revealPending || Boolean(revealedPhone)}
                onClick={confirmReveal}
              >{revealPending ? '查看中…' : '确认查看'}</button>
              <button data-testid="shared-privileged-data-sensitive-value-close" type="button" className="button-secondary" onClick={closeRevealDialog}>关闭并清除</button>
            </div>
          </div>
        </ModalDialog>
      )}
      {transitionAccount.isError && <div className="card" role="alert">
        {mutationErrorMessage(transitionAccount.error, '账号状态更新失败，请稍后重试')}
      </div>}
    </section>
  );
}
