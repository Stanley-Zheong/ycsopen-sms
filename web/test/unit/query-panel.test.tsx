import { useState } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QueryField, QueryPanel } from '../../src/components/common/QueryPanel';

const READY_QUERY_STATUS = {
  testId: 'example-query-status',
  label: '查询结果',
  isFetching: false,
  isError: false,
  isEmpty: false,
  count: 1,
};

describe('QueryPanel', () => {
  it('exposes separate field and action regions with associated controls', () => {
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
        <QueryField name="keyword" label="关键字"><input data-testid="admin-example-keyword" /></QueryField>
        <QueryField name="status" label="状态"><select data-testid="admin-example-status"><option>全部</option></select></QueryField>
      </QueryPanel>,
    );

    const panel = screen.getByTestId('query-panel');
    const fields = within(panel).getByTestId('query-fields');
    const actions = within(panel).getByTestId('query-actions');
    const keyword = within(fields).getByTestId('admin-example-keyword');

    expect(within(fields).getByText('关键字')).toHaveAttribute('for', keyword.id);
    expect(fields).not.toContainElement(within(actions).getByTestId('query-submit'));
    expect(actions).toContainElement(within(actions).getByTestId('query-reset'));
    expect(within(actions).getByRole('button', { name: '查询' })).toBeInTheDocument();
  });

  it('keeps multi-row fields collapsed until the operator expands them', () => {
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
        <QueryField name="tenant-id" label="租户 ID"><input /></QueryField>
        <QueryField name="status" label="状态"><select><option>全部</option></select></QueryField>
        <QueryField name="keyword" label="关键字"><input /></QueryField>
        <QueryField name="created-at" label="创建时间"><input /></QueryField>
      </QueryPanel>,
    );

    const panel = screen.getByTestId('query-panel');
    const fields = within(panel).getByTestId('query-panel-fields');
    const toggle = within(panel).getByTestId('query-panel-toggle');

    expect(fields).not.toBeVisible();
    expect(within(fields).getByTestId('query-fields')).toBeInTheDocument();
    expect(within(fields).getByTestId('query-actions')).toBeInTheDocument();
    expect(within(panel).getByTestId('query-submit')).not.toBeVisible();
    expect(within(panel).getByTestId('query-reset')).not.toBeVisible();
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);
    expect(fields).toBeVisible();
    expect(fields).toContainElement(within(panel).getByTestId('query-submit'));
    expect(fields).toContainElement(within(panel).getByTestId('query-reset'));
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(within(panel).getByTestId('query-label-tenant-id')).toHaveAttribute('for');
    expect(within(panel).getByTestId('query-input-tenant-id').querySelector('input')).toBeInTheDocument();
  });

  it('keeps three desktop fields visible because they fit on one row', () => {
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
        <QueryField name="keyword" label="关键字"><input /></QueryField>
        <QueryField name="verification-status" label="认证状态"><select><option>全部</option></select></QueryField>
        <QueryField name="operating-status" label="运行状态"><select><option>全部</option></select></QueryField>
      </QueryPanel>,
    );

    expect(screen.queryByTestId('query-panel-toggle')).not.toBeInTheDocument();
    expect(screen.getByTestId('query-panel-fields')).toBeVisible();
  });

  it('keeps search and reset visible for a single field and invokes their page-owned behavior', () => {
    const onSubmit = vi.fn();
    const onReset = vi.fn();
    const onRefresh = vi.fn();
    render(
      <QueryPanel
        onSubmit={onSubmit}
        onReset={onReset}
        onRefresh={onRefresh}
        queryStatus={READY_QUERY_STATUS}
        submitLegacyTestId="legacy-search"
        resetLegacyTestId="legacy-reset"
        result={<table><tbody><tr><td>初始数据</td></tr></tbody></table>}
      >
        <QueryField name="status" label="状态"><input /></QueryField>
      </QueryPanel>,
    );

    const panel = screen.getByTestId('query-panel');
    expect(within(panel).queryByTestId('query-panel-toggle')).not.toBeInTheDocument();
    expect(within(panel).getByTestId('query-panel-fields')).toBeVisible();

    fireEvent.click(within(panel).getByTestId('query-submit'));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onReset).not.toHaveBeenCalled();
    expect(within(panel).getByTestId('legacy-search')).toBeInTheDocument();
    fireEvent.click(within(panel).getByTestId('query-reset'));
    expect(onReset).toHaveBeenCalledTimes(1);
    expect(within(panel).getByTestId('legacy-reset')).toBeInTheDocument();
    fireEvent.click(within(panel).getByTestId('query-refresh'));
    expect(onRefresh).toHaveBeenCalledTimes(2);
    expect(within(panel).getByTestId('query-result-table')).toHaveTextContent('初始数据');
  });

  it('keeps search and reset together for multiple fields', () => {
    const onReset = vi.fn();
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={onReset} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
        <QueryField name="status" label="状态"><input /></QueryField>
        <QueryField name="keyword" label="关键字"><input /></QueryField>
      </QueryPanel>,
    );

    const fields = screen.getByTestId('query-panel-fields');
    const submit = screen.getByTestId('query-submit');
    const reset = screen.getByTestId('query-reset');
    expect(fields).toContainElement(submit);
    expect(fields).toContainElement(reset);
    fireEvent.click(reset);
    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it('keeps the required page refresh action in the query action region', () => {
    const onRefresh = vi.fn();
    render(
      <QueryPanel
        onSubmit={vi.fn()}
        onReset={vi.fn()}
        onRefresh={onRefresh}
        queryStatus={READY_QUERY_STATUS}
        refreshLegacyTestId="legacy-refresh"
      >
        <QueryField name="status" label="状态"><select><option value="">全部</option></select></QueryField>
        <QueryField name="keyword" label="关键字"><input /></QueryField>
      </QueryPanel>,
    );

    const actions = screen.getByTestId('query-actions');
    const refresh = within(actions).getByTestId('query-refresh');
    expect(within(actions).getByTestId('legacy-refresh')).toHaveTextContent('刷新');
    fireEvent.click(refresh);
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it('can disable submission while page permissions are unresolved', () => {
    render(
      <QueryPanel
        onSubmit={vi.fn()}
        onReset={vi.fn()}
        onRefresh={vi.fn()}
        queryStatus={READY_QUERY_STATUS}
        submitDisabled
      >
        <QueryField name="keyword" label="关键字"><input /></QueryField>
      </QueryPanel>,
    );

    expect(screen.getByTestId('query-submit')).toBeDisabled();
  });

  it('collapses two fields when the responsive grid wraps to one column', () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      media: '(max-width: 900px)',
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    });

    try {
      render(
        <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
          <QueryField name="status" label="状态"><select><option>全部</option></select></QueryField>
          <QueryField name="severity" label="级别"><select><option>全部</option></select></QueryField>
        </QueryPanel>,
      );

      expect(screen.getByTestId('query-panel-fields')).not.toBeVisible();
      expect(screen.getByTestId('query-panel-toggle')).toHaveAttribute('aria-expanded', 'false');
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });

  it('collapses three fields when the medium grid wraps to two columns', () => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === '(max-width: 1200px)',
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    try {
      render(
        <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
          <QueryField name="keyword" label="关键字"><input /></QueryField>
          <QueryField name="status" label="状态"><select><option>全部</option></select></QueryField>
          <QueryField name="severity" label="级别"><select><option>全部</option></select></QueryField>
        </QueryPanel>,
      );

      expect(screen.getByTestId('query-panel-fields')).not.toBeVisible();
      expect(screen.getByTestId('query-panel-toggle')).toHaveAttribute('aria-expanded', 'false');
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });

  it('gives each field control its stable query name', () => {
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={vi.fn()} queryStatus={READY_QUERY_STATUS}>
        <QueryField name="tenant-id" label="租户 ID"><input name="unstable-name" /></QueryField>
      </QueryPanel>,
    );

    expect(screen.getByLabelText('租户 ID')).toHaveAttribute('name', 'tenant-id');
  });

  it('submits changed criteria once and retries an unchanged submission after it', () => {
    const calls: string[] = [];
    render(
      <QueryPanel
        onSubmit={() => calls.push('submit')}
        onReset={vi.fn()}
        onRefresh={() => calls.push('refresh')}
        queryStatus={READY_QUERY_STATUS}
      >
        <QueryField name="keyword" label="关键字"><input defaultValue="alpha" /></QueryField>
      </QueryPanel>,
    );

    fireEvent.change(screen.getByLabelText('关键字'), { target: { value: 'beta' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    expect(calls).toEqual(['submit']);

    fireEvent.click(screen.getByTestId('query-submit'));
    expect(calls).toEqual(['submit', 'submit', 'refresh']);

    fireEvent.change(screen.getByLabelText('关键字'), { target: { value: 'gamma' } });
    fireEvent.click(screen.getByTestId('query-submit'));
    expect(calls).toEqual(['submit', 'submit', 'refresh', 'submit']);
  });

  it('retries when the initial applied criteria are submitted unchanged', () => {
    const calls: string[] = [];
    render(
      <QueryPanel
        onSubmit={() => calls.push('submit')}
        onReset={vi.fn()}
        onRefresh={() => calls.push('refresh')}
        queryStatus={READY_QUERY_STATUS}
      >
        <QueryField name="keyword" label="关键字"><input defaultValue="alpha" /></QueryField>
      </QueryPanel>,
    );

    fireEvent.click(screen.getByTestId('query-submit'));

    expect(calls).toEqual(['submit', 'refresh']);
  });

  it('compares signatures independently of field render order', () => {
    const onSubmit = vi.fn();
    const onRefresh = vi.fn();
    const queryPanel = (reverse: boolean) => (
      <QueryPanel
        onSubmit={onSubmit}
        onReset={vi.fn()}
        onRefresh={onRefresh}
        queryStatus={READY_QUERY_STATUS}
      >
        {reverse ? [
          <QueryField key="status" name="status" label="状态"><input defaultValue="enabled" /></QueryField>,
          <QueryField key="keyword" name="keyword" label="关键字"><input defaultValue="alpha" /></QueryField>,
        ] : [
          <QueryField key="keyword" name="keyword" label="关键字"><input defaultValue="alpha" /></QueryField>,
          <QueryField key="status" name="status" label="状态"><input defaultValue="enabled" /></QueryField>,
        ]}
      </QueryPanel>
    );
    const { rerender } = render(queryPanel(false));

    fireEvent.click(screen.getByTestId('query-submit'));
    expect(onRefresh).toHaveBeenCalledTimes(1);
    onRefresh.mockClear();
    rerender(queryPanel(true));
    fireEvent.click(screen.getByTestId('query-submit'));

    expect(onSubmit).toHaveBeenCalledTimes(2);
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it('synchronizes a canonical reset before deciding whether the next edit is unchanged', () => {
    const onSubmit = vi.fn();
    const onRefresh = vi.fn();

    function ControlledPanel() {
      const [keyword, setKeyword] = useState('reused-value');
      return (
        <QueryPanel
          onSubmit={onSubmit}
          onReset={() => setKeyword('')}
          onRefresh={onRefresh}
          queryStatus={READY_QUERY_STATUS}
        >
          <QueryField name="keyword" label="关键字">
            <input value={keyword} onChange={(event) => setKeyword(event.target.value)} />
          </QueryField>
        </QueryPanel>
      );
    }

    render(<ControlledPanel />);
    fireEvent.change(screen.getByLabelText('关键字'), { target: { value: '' } });
    fireEvent.click(screen.getByTestId('query-reset'));
    fireEvent.change(screen.getByLabelText('关键字'), { target: { value: 'reused-value' } });
    fireEvent.click(screen.getByTestId('query-submit'));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onRefresh).not.toHaveBeenCalled();
  });

  it.each([
    {
      title: 'loading before every other state',
      status: { isFetching: true, isError: true, isEmpty: true },
      state: 'loading',
      role: 'status',
      live: 'polite',
      busy: 'true',
      text: '正在加载查询结果',
    },
    {
      title: 'error before empty',
      status: { isFetching: false, isError: true, isEmpty: true },
      state: 'error',
      role: 'alert',
      live: 'assertive',
      busy: 'false',
      text: '查询结果加载失败',
    },
    {
      title: 'empty when no higher-priority state applies',
      status: { isFetching: false, isError: false, isEmpty: true },
      state: 'empty',
      role: 'status',
      live: 'polite',
      busy: 'false',
      text: '暂无查询结果',
    },
    {
      title: 'success after all exceptional states are clear',
      status: { isFetching: false, isError: false, isEmpty: false },
      state: 'success',
      role: 'status',
      live: 'polite',
      busy: 'false',
      text: '查询结果：3 条',
    },
  ])('exposes $title with a single accessible result status', ({ status, state, role, live, busy, text }) => {
    render(
      <QueryPanel
        onSubmit={vi.fn()}
        onReset={vi.fn()}
        onRefresh={vi.fn()}
        queryStatus={{
          testId: 'admin-example-query-status',
          label: '查询结果',
          count: 3,
          ...status,
        }}
        result={<div data-testid="page-owned-result">页面结果</div>}
      >
        <QueryField name="keyword" label="关键字"><input /></QueryField>
      </QueryPanel>,
    );

    const result = screen.getByTestId('query-result-table');
    const resultStatus = within(result).getByTestId('admin-example-query-status');
    expect(result.querySelectorAll('[data-query-result-state]')).toHaveLength(1);
    expect(resultStatus).toHaveAttribute('data-query-result-state', state);
    expect(resultStatus).toHaveAttribute('data-state', state);
    expect(resultStatus).toHaveAttribute('role', role);
    expect(resultStatus).toHaveAttribute('aria-live', live);
    expect(resultStatus).toHaveAttribute('aria-busy', busy);
    expect(resultStatus).toHaveTextContent(text);
    expect(result).toContainElement(screen.getByTestId('page-owned-result'));
  });
});
