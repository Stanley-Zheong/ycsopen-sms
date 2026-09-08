import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import ModalDialog from '@/components/common/ModalDialog';
import { listChannelHealthMonitor, listChannelPools, saveChannelPool, type ChannelHealthMonitorRow, type ChannelPool, type ChannelPoolMember, type ChannelPoolMode } from '@/api/channelHealthApi';
import { mutationErrorMessage } from '@/api/client';
import { isPlatformRole, useAuthStore } from '@/store/authStore';
import '@/styles/channel-health.css';

interface PoolForm {
  id?: number;
  name: string;
  mode: ChannelPoolMode;
  expectedVersion: number | null;
  members: ChannelPoolMember[];
}

const emptyForm: PoolForm = {
  name: '',
  mode: 'WEIGHTED',
  expectedVersion: null,
  members: [],
};

function fromPool(pool: ChannelPool): PoolForm {
  return {
    id: pool.id,
    name: pool.name,
    mode: pool.mode,
    expectedVersion: pool.version,
    members: pool.members.map((member) => ({ ...member })),
  };
}

function totalWeight(members: ChannelPoolMember[]) {
  return members.filter((member) => member.enabled).reduce((sum, member) => sum + Number(member.weight || 0), 0);
}

function validatePool(form: PoolForm): string | null {
  if (!form.name.trim()) return '通道池名称必填。';
  const enabled = form.members.filter((member) => member.enabled);
  if (enabled.length === 0) return '至少需要一个启用成员。';
  if (form.mode === 'WEIGHTED') {
    if (enabled.some((member) => !Number.isInteger(Number(member.weight)) || Number(member.weight) <= 0)) return '启用成员权重必须为正整数。';
    if (totalWeight(form.members) !== 100) return '加权模式启用成员权重总和必须等于 100。';
  }
  if (form.mode === 'PRIMARY_BACKUP' && enabled.filter((member) => member.primaryMember).length !== 1) {
    return '主备模式必须且只能有一个启用主通道。';
  }
  return null;
}

function channelName(channels: ChannelHealthMonitorRow[], id: number) {
  return channels.find((channel) => channel.channelId === id)?.channelName ?? `#${id}`;
}

export default function ChannelPoolsPage() {
  const queryClient = useQueryClient();
  const userType = useAuthStore((state) => state.userType);
  const canRead = userType === 'ADMIN' || userType === 'OPERATOR';
  const [form, setForm] = useState<PoolForm | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pools = useQuery({
    queryKey: ['channel-health-pools'],
    queryFn: listChannelPools,
    retry: false,
    enabled: isPlatformRole(userType) && canRead,
  });
  const channels = useQuery({
    queryKey: ['channel-health-monitor'],
    queryFn: listChannelHealthMonitor,
    retry: false,
    enabled: isPlatformRole(userType) && canRead,
  });
  const poolRows = useMemo(() => pools.data ?? [], [pools.data]);
  const channelRows = useMemo(() => channels.data ?? [], [channels.data]);
  const formError = form ? validatePool(form) : null;

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['channel-health-pools'] }),
      queryClient.invalidateQueries({ queryKey: ['channel-health-monitor'] }),
    ]);
  };

  const save = useMutation({
    mutationFn: async () => {
      if (!form || formError) throw new Error(formError ?? '表单不完整');
      return saveChannelPool({
        name: form.name.trim(),
        mode: form.mode,
        expectedVersion: form.expectedVersion,
        members: form.members.map((member) => ({
          channelId: Number(member.channelId),
          weight: Number(member.weight),
          primaryMember: Boolean(member.primaryMember),
          enabled: Boolean(member.enabled),
        })),
      }, form.id);
    },
    onSuccess: async () => {
      setForm(null);
      setMessage('通道池已保存。');
      setError(null);
      await refresh();
    },
    onError: (failure) => setError(failure instanceof Error ? failure.message : mutationErrorMessage(failure, '通道池保存失败')),
  });

  const addMember = () => {
    if (!form) return;
    const candidate = channelRows.find((channel) => channel.candidateEligible
      && !form.members.some((member) => member.channelId === channel.channelId));
    if (!candidate) return;
    setForm({
      ...form,
      members: [...form.members, { channelId: candidate.channelId, weight: 100, primaryMember: form.members.length === 0, enabled: true }],
    });
  };

  const updateMember = (index: number, patch: Partial<ChannelPoolMember>) => {
    if (!form) return;
    setForm({ ...form, members: form.members.map((member, i) => (i === index ? { ...member, ...patch } : member)) });
  };

  if (!isPlatformRole(userType) || !canRead) {
    return <p role="alert" data-testid="admin-channel-health-channel-pools-access-denied">无权查看通道池。</p>;
  }

  return (
    <section data-testid="admin-channel-health-channel-pools-page">
      <nav aria-label="面包屑">通道管理 / 通道池</nav>
      <header className="channel-health-header">
        <div>
          <h1>通道池</h1>
          <p className="page-description">维护加权和主备通道池，保存前校验成员候选资格。</p>
        </div>
        <div className="channel-health-toolbar">
          <button type="button" className="button-secondary" data-testid="admin-channel-health-channel-pools-refresh" onClick={() => void refresh()} disabled={pools.isFetching || channels.isFetching}>刷新</button>
          <button type="button" data-testid="admin-channel-health-channel-pools-create-open" onClick={() => setForm({ ...emptyForm, members: [] })}>新建通道池</button>
        </div>
      </header>

      {message && <p role="status" className="channel-health-alert success">{message}</p>}
      {error && <div role="alert" className="channel-health-alert error">{error}</div>}

      <section className="card channel-health-table">
        {(pools.isLoading || channels.isLoading) && <p>正在加载通道池…</p>}
        {(pools.isError || channels.isError) && <p role="alert">通道池加载失败。<button type="button" onClick={() => void refresh()}>重试</button></p>}
        {!pools.isLoading && !pools.isError && poolRows.length === 0 && <p>暂无通道池。</p>}
        {poolRows.length > 0 && (
          <table className="ratio-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>模式</th>
                <th>版本</th>
                <th>状态</th>
                <th>启用成员</th>
                <th>总权重</th>
                <th>主通道</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {poolRows.map((pool) => {
                const enabledMembers = pool.members.filter((member) => member.enabled);
                const primary = pool.members.find((member) => member.primaryMember && member.enabled);
                return (
                  <tr key={pool.id} data-testid="admin-channel-health-channel-pools-row" data-business-key={pool.id}>
                    <td>{pool.name}</td>
                    <td>{pool.mode}</td>
                    <td>v{pool.version}</td>
                    <td>{pool.status}</td>
                    <td>{enabledMembers.map((member) => channelName(channelRows, member.channelId)).join('、') || '无'}</td>
                    <td>{totalWeight(pool.members)}</td>
                    <td>{primary ? channelName(channelRows, primary.channelId) : '无'}</td>
                    <td><button type="button" data-testid="admin-channel-health-channel-pools-edit" onClick={() => setForm(fromPool(pool))}>编辑</button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>

      {form && (
        <ModalDialog labelledBy="channel-pool-form-title" onRequestClose={() => setForm(null)}>
          <h2 id="channel-pool-form-title">{form.id ? '编辑通道池' : '新建通道池'}</h2>
          <div className="channel-health-form" data-testid="admin-channel-health-channel-pools-weight-editor">
            <label>通道池名称
              <input data-testid="admin-channel-health-channel-pools-form-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </label>
            <label>模式
              <select data-testid="admin-channel-health-channel-pools-form-mode" value={form.mode} onChange={(event) => setForm({ ...form, mode: event.target.value as ChannelPoolMode })}>
                <option value="WEIGHTED">WEIGHTED</option>
                <option value="PRIMARY_BACKUP">PRIMARY_BACKUP</option>
              </select>
            </label>
            <div className="channel-health-wide channel-health-member-actions">
              <button type="button" className="button-secondary" data-testid="admin-channel-health-channel-pools-member-add" onClick={addMember} disabled={channelRows.length === 0}>添加成员</button>
              <span>启用权重合计：{totalWeight(form.members)}</span>
            </div>
            <div className="channel-health-wide channel-health-member-list">
              {form.members.map((member, index) => (
                <div key={`${member.channelId}-${index}`} className="channel-health-member-row" data-testid="admin-channel-health-channel-pools-member-row">
                  <label>通道
                    <select data-testid="admin-channel-health-channel-pools-member-channel" value={member.channelId} onChange={(event) => updateMember(index, { channelId: Number(event.target.value) })}>
                      {channelRows.map((channel) => (
                        <option key={channel.channelId} value={channel.channelId}>{channel.channelName} - {channel.candidateEligible ? '可候选' : channel.candidateReasonCode}</option>
                      ))}
                    </select>
                  </label>
                  <label>权重
                    <input data-testid="admin-channel-health-channel-pools-member-weight" type="number" value={member.weight} onChange={(event) => updateMember(index, { weight: Number(event.target.value) })} />
                  </label>
                  <label className="channel-health-checkbox"><input data-testid="admin-channel-health-channel-pools-member-primary" type="checkbox" checked={member.primaryMember} onChange={(event) => updateMember(index, { primaryMember: event.target.checked })} />主通道</label>
                  <label className="channel-health-checkbox"><input data-testid="admin-channel-health-channel-pools-member-enabled" type="checkbox" checked={member.enabled} onChange={(event) => updateMember(index, { enabled: event.target.checked })} />启用</label>
                </div>
              ))}
              {form.members.length === 0 && <p>尚未添加成员。</p>}
            </div>
          </div>
          {formError && <p role="alert" className="channel-health-alert error">{formError}</p>}
          <div className="channel-health-dialog-actions">
            <button type="button" className="button-secondary" onClick={() => setForm(null)}>取消</button>
            <button type="button" data-testid="admin-channel-health-channel-pools-form-save" onClick={() => save.mutate()} disabled={Boolean(formError) || save.isPending}>保存</button>
          </div>
        </ModalDialog>
      )}
    </section>
  );
}
