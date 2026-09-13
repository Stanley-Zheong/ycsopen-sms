import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QueryField, QueryPanel } from '../../src/components/common/QueryPanel';

describe('QueryPanel', () => {
  it('exposes separate field and action regions with associated controls', () => {
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()}>
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
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()}>
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
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()}>
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
    render(
      <QueryPanel
        onSubmit={onSubmit}
        onReset={onReset}
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
    expect(within(panel).getByTestId('query-result-table')).toHaveTextContent('初始数据');
  });

  it('keeps search and reset together for multiple fields', () => {
    const onReset = vi.fn();
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={onReset}>
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

  it('keeps an optional page refresh action in the query action region', () => {
    const onRefresh = vi.fn();
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} onRefresh={onRefresh} refreshLegacyTestId="legacy-refresh">
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
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()} submitDisabled>
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
        <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()}>
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
        <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()}>
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
});
