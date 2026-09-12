import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QueryField, QueryPanel } from '../../src/components/common/QueryPanel';

describe('QueryPanel', () => {
  it('keeps multi-row fields collapsed until the operator expands them', () => {
    render(
      <QueryPanel onSubmit={vi.fn()} onReset={vi.fn()}>
        <QueryField name="tenant-id" label="租户 ID"><input /></QueryField>
        <QueryField name="status" label="状态"><select><option>全部</option></select></QueryField>
        <QueryField name="keyword" label="关键字"><input /></QueryField>
      </QueryPanel>,
    );

    const panel = screen.getByTestId('query-panel');
    const fields = within(panel).getByTestId('query-panel-fields');
    const toggle = within(panel).getByTestId('query-panel-toggle');

    expect(fields).not.toBeVisible();
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);
    expect(fields).toBeVisible();
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(within(panel).getByTestId('query-label-tenant-id')).toHaveAttribute('for');
    expect(within(panel).getByTestId('query-input-tenant-id').querySelector('input')).toBeInTheDocument();
  });

  it('keeps search and reset visible and invokes their page-owned behavior', () => {
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
    fireEvent.click(within(panel).getByTestId('query-reset'));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onReset).toHaveBeenCalledTimes(1);
    expect(within(panel).getByTestId('legacy-search')).toBeInTheDocument();
    expect(within(panel).getByTestId('legacy-reset')).toBeInTheDocument();
    expect(within(panel).getByTestId('query-result-table')).toHaveTextContent('初始数据');
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
});
