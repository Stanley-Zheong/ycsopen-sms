import { type FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createPlatformRole,
  deletePlatformRole,
  getPermissions,
  getPlatformRoles,
  IDENTITY_PERMISSIONS,
  type PermissionResourceType,
  type PlatformRole,
  replaceRolePermissions,
  updatePlatformRole,
} from '@/api/identity';
import { useIdentityAccess } from './useIdentityAccess';
import { protectedQueryKey } from '@/store/authStore';
import ModalDialog from '@/components/common/ModalDialog';
import { mutationErrorMessage } from '@/api/client';

const PERMISSION_GROUPS: Array<{ type: PermissionResourceType; label: string }> = [
  { type: 'MENU', label: '菜单权限' },
  { type: 'BUTTON', label: '按钮权限' },
  { type: 'API', label: '接口权限' },
  { type: 'DATA', label: '数据权限' },
];

export default function RoleManagementPage() {
  const queryClient = useQueryClient();
  const access = useIdentityAccess();
  const canViewRoles = access.can(IDENTITY_PERMISSIONS.rolesRead);
  const roleQueryKey = protectedQueryKey('platform-roles');
  const roles = useQuery({ queryKey: roleQueryKey, queryFn: getPlatformRoles, enabled: canViewRoles });
  const permissions = useQuery({ queryKey: protectedQueryKey('permissions'), queryFn: getPermissions, enabled: canViewRoles });
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([]);
  const [permissionDraftRoleId, setPermissionDraftRoleId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [editingRoleId, setEditingRoleId] = useState<number | null>(null);
  const [newRole, setNewRole] = useState({ code: '', name: '', description: '', status: 'ACTIVE' as 'ACTIVE' | 'DISABLED' });
  const [migratingRoleId, setMigratingRoleId] = useState<number | null>(null);
  const [replacementRoleId, setReplacementRoleId] = useState<number | null>(null);
  const [pendingUnusedRole, setPendingUnusedRole] = useState<PlatformRole | null>(null);

  useEffect(() => {
    if (selectedRoleId === null && roles.data?.length) {
      setSelectedRoleId(roles.data[0].id);
    }
  }, [roles.data, selectedRoleId]);

  useEffect(() => {
    const selected = roles.data?.find((role) => role.id === selectedRoleId);
    if (selectedRoleId !== permissionDraftRoleId) {
      setSelectedPermissionIds(selected?.permissionIds ?? []);
      setPermissionDraftRoleId(selectedRoleId);
    }
  }, [roles.data, selectedRoleId, permissionDraftRoleId]);

  const savePermissions = useMutation({
    mutationFn: async () => {
      const saved = { roleId: selectedRoleId!, permissionIds: [...selectedPermissionIds] };
      await replaceRolePermissions(saved.roleId, saved.permissionIds);
      return saved;
    },
    onSuccess: async (saved) => {
      queryClient.setQueryData<PlatformRole[]>(roleQueryKey, (current) => current?.map((role) => (
        role.id === saved.roleId ? { ...role, permissionIds: saved.permissionIds } : role
      )));
      setSelectedPermissionIds(saved.permissionIds);
      setPermissionDraftRoleId(saved.roleId);
      await queryClient.invalidateQueries({ queryKey: roleQueryKey });
      const confirmed = queryClient.getQueryData<PlatformRole[]>(roleQueryKey)
        ?.find((role) => role.id === saved.roleId)?.permissionIds;
      if (confirmed) setSelectedPermissionIds(confirmed);
    },
  });
  const saveRoleDetails = useMutation({
    mutationFn: async () => {
      if (editingRoleId === null) {
        await createPlatformRole({ code: newRole.code, name: newRole.name, description: newRole.description });
      } else {
        await updatePlatformRole(editingRoleId, newRole);
      }
    },
    onSuccess: async () => {
      setCreating(false);
      setEditingRoleId(null);
      setNewRole({ code: '', name: '', description: '', status: 'ACTIVE' });
      await queryClient.invalidateQueries({ queryKey: roleQueryKey });
    },
  });
  const removeRole = useMutation({
    mutationFn: ({ roleId, replacementId }: { roleId: number; replacementId: number | null }) => (
      deletePlatformRole(roleId, replacementId)
    ),
    onSuccess: async () => {
      setMigratingRoleId(null);
      setReplacementRoleId(null);
      setSelectedRoleId(null);
      await queryClient.invalidateQueries({ queryKey: roleQueryKey });
    },
  });

  function submitRole(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    saveRoleDetails.mutate();
  }

  function openCreateRole() {
    setEditingRoleId(null);
    setNewRole({ code: '', name: '', description: '', status: 'ACTIVE' });
    setCreating(true);
  }

  function openEditRole(role: NonNullable<typeof roles.data>[number]) {
    setEditingRoleId(role.id);
    setNewRole({ code: role.code, name: role.name, description: role.description, status: role.status });
    setCreating(true);
  }

  function requestDelete(role: PlatformRole) {
    const { id: roleId, userCount } = role;
    if (userCount > 0) {
      setMigratingRoleId(roleId);
      setReplacementRoleId(null);
      return;
    }
    setPendingUnusedRole(role);
  }

  function togglePermission(permissionId: number) {
    setSelectedPermissionIds((current) => current.includes(permissionId)
      ? current.filter((id) => id !== permissionId)
      : [...current, permissionId].sort((left, right) => left - right));
  }

  function discardPermissions() {
    if (permissionsDirty && !window.confirm('放弃未保存的权限修改？')) return;
    setSelectedPermissionIds(persistedPermissionIds);
  }

  function closeRoleForm() {
    if ((newRole.code || newRole.name || newRole.description || editingRoleId !== null)
        && !window.confirm('放弃未保存的角色修改？')) return;
    setCreating(false);
    setEditingRoleId(null);
  }

  if (access.isLoading || (canViewRoles && (roles.isLoading || permissions.isLoading))) return <div className="card">加载角色与权限…</div>;
  if (access.isError) return <div className="card" role="alert">权限范围加载失败，请稍后重试</div>;
  if (!canViewRoles) return <div className="card" role="alert">无权查看平台角色</div>;
  if (roles.isError || permissions.isError) return <div className="card" role="alert">角色数据加载失败，请稍后重试</div>;

  const selectedRole = roles.data?.find((role) => role.id === selectedRoleId);
  const migratingRole = roles.data?.find((role) => role.id === migratingRoleId);
  const persistedPermissionIds = selectedRole?.permissionIds ?? [];
  const permissionsDirty = persistedPermissionIds.length !== selectedPermissionIds.length
    || persistedPermissionIds.some((id) => !selectedPermissionIds.includes(id));

  return (
    <section data-testid="admin-console-identity-roles-page">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div><h1 style={{ marginBottom: 4 }}>角色与权限</h1><p style={{ marginTop: 0, color: '#667085' }}>权限保存后由服务端按当前数据库授权实时执行。</p></div>
          {access.can(IDENTITY_PERMISSIONS.createRole) && (
            <button data-testid="admin-console-identity-roles-create" type="button" onClick={openCreateRole}>新建角色</button>
          )}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 20 }}>
          <div>
            <h3>平台角色</h3>
            {(roles.data ?? []).map((role, index) => (
              <div key={role.id} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <button type="button" aria-pressed={role.id === selectedRoleId} onClick={() => {
                  if (!permissionsDirty || window.confirm('放弃未保存的权限修改？')) setSelectedRoleId(role.id);
                }} style={{ flex: 1, textAlign: 'left' }}>
                  {role.name}（{role.userCount} 人）
                </button>
                {access.can(IDENTITY_PERMISSIONS.updateRole) && (
                  <button data-testid={index === 0 ? 'admin-console-identity-roles-edit' : undefined} type="button" aria-label={`编辑${role.name}`} onClick={() => openEditRole(role)}>编辑</button>
                )}
                {access.can(IDENTITY_PERMISSIONS.deleteRole) && (
                  <button type="button" aria-label={`删除${role.name}`} onClick={() => requestDelete(role)}>删除</button>
                )}
              </div>
            ))}
          </div>

          <div data-testid="admin-console-identity-roles-permission-tree">
            <h3>{selectedRole ? `${selectedRole.name}权限` : '请选择角色'}</h3>
            {PERMISSION_GROUPS.map((group) => (
              <fieldset key={group.type} style={{ marginBottom: 12 }}>
                <legend>{group.label}</legend>
                {(permissions.data ?? []).filter((item) => item.status === 'ACTIVE' && item.resourceType === group.type).map((item) => (
                  <div key={item.id} style={{ margin: '6px 0' }}>
                    <label>
                      <input data-testid="admin-console-identity-roles-permission-choice" data-permission-code={item.code} type="checkbox" aria-label={item.name} checked={selectedPermissionIds.includes(item.id)} onChange={() => togglePermission(item.id)} disabled={!access.can(IDENTITY_PERMISSIONS.saveRole)} />
                      {item.name} <code>{item.code}</code>
                    </label>
                  </div>
                ))}
              </fieldset>
            ))}
            {access.can(IDENTITY_PERMISSIONS.saveRole) && (
              <div>
                <button data-testid="admin-console-identity-roles-save" type="button" disabled={!selectedRoleId || !permissionsDirty || savePermissions.isPending} onClick={() => savePermissions.mutate()}>
                  保存权限
                </button>{' '}
                {permissionsDirty && (
                  <button type="button" onClick={discardPermissions}>放弃权限修改</button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {creating && (
        <ModalDialog labelledBy="platform-role-form-title" onRequestClose={closeRoleForm}>
        <form data-testid="admin-console-identity-roles-form" onSubmit={submitRole}>
          <h2 id="platform-role-form-title">{editingRoleId === null ? '新建自定义角色' : '编辑平台角色'}</h2>
          <label>角色编码 <input required pattern="[A-Z][A-Z0-9_]{2,49}" value={newRole.code} onChange={(event) => setNewRole({ ...newRole, code: event.target.value })} /></label>{' '}
          <label>角色名称 <input required value={newRole.name} onChange={(event) => setNewRole({ ...newRole, name: event.target.value })} /></label>{' '}
          <label>描述 <input value={newRole.description} onChange={(event) => setNewRole({ ...newRole, description: event.target.value })} /></label>{' '}
          {editingRoleId !== null && <label>状态 <select value={newRole.status} onChange={(event) => setNewRole({ ...newRole, status: event.target.value as 'ACTIVE' | 'DISABLED' })}><option value="ACTIVE">启用</option><option value="DISABLED">禁用</option></select></label>}{' '}
          <button type="submit" disabled={saveRoleDetails.isPending}>{editingRoleId === null ? '创建角色' : '保存角色'}</button>{' '}
          <button type="button" onClick={closeRoleForm}>放弃角色修改</button>
        </form>
        </ModalDialog>
      )}

      {migratingRole && (
        <ModalDialog labelledBy="role-migration-title" onRequestClose={() => setMigratingRoleId(null)}>
        <div data-testid="admin-console-identity-roles-migrate-dialog">
          <h3 id="role-migration-title">迁移关联用户后删除角色</h3>
          <p>“{migratingRole.name}”仍关联 {migratingRole.userCount} 个用户，必须先选择有效的目标角色。</p>
          <label>迁移到角色
            <select aria-label="迁移到角色" required value={replacementRoleId ?? ''} onChange={(event) => setReplacementRoleId(Number(event.target.value))}>
              <option value="">请选择</option>
              {(roles.data ?? []).filter((role) => role.id !== migratingRole.id && role.status === 'ACTIVE').map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
            </select>
          </label>
          <div style={{ marginTop: 12 }}>
            <button type="button" disabled={!replacementRoleId || removeRole.isPending} onClick={() => removeRole.mutate({ roleId: migratingRole.id, replacementId: replacementRoleId })}>迁移并删除</button>{' '}
            <button type="button" onClick={() => setMigratingRoleId(null)}>保留原角色</button>
          </div>
        </div>
        </ModalDialog>
      )}
      {pendingUnusedRole && (
        <ModalDialog labelledBy="delete-role-title" onRequestClose={() => setPendingUnusedRole(null)}>
          <h2 id="delete-role-title">确认删除角色</h2>
          <p>删除“{pendingUnusedRole.name}”后无法恢复。确认删除？</p>
          <button type="button" onClick={() => {
            removeRole.mutate({ roleId: pendingUnusedRole.id, replacementId: null });
            setPendingUnusedRole(null);
          }}>确认删除</button>{' '}
          <button type="button" onClick={() => setPendingUnusedRole(null)}>保留角色</button>
        </ModalDialog>
      )}
      {(savePermissions.isError || saveRoleDetails.isError || removeRole.isError) && <div className="card" role="alert">
        {mutationErrorMessage(savePermissions.error ?? saveRoleDetails.error ?? removeRole.error,
          '角色操作失败，请检查权限或稍后重试')}
      </div>}
    </section>
  );
}
