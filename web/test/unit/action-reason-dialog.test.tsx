import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ActionReasonDialog from '@/components/common/ActionReasonDialog';

describe('ActionReasonDialog', () => {
  it('requires an operator-entered reason and keeps action context visible', () => {
    const onReasonChange = vi.fn();
    const onCancel = vi.fn();
    const onConfirm = vi.fn();

    const { rerender } = render(
      <ActionReasonDialog
        idPrefix="test-action"
        title="确认暂停任务"
        target="任务 BULK-301 · 机构 42"
        consequence="暂停后不再派发新的子消息。"
        reasonLabel="暂停原因"
        reasonTestId="test-action-reason"
        reason=""
        placeholder="请填写暂停依据"
        confirmLabel="确认暂停"
        pending={false}
        onReasonChange={onReasonChange}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );

    expect(screen.getByTestId('modal')).toBeVisible();
    expect(screen.getByTestId('test-action-dialog')).toHaveTextContent('确认暂停任务');
    expect(screen.getByTestId('test-action-target')).toHaveTextContent('BULK-301');
    expect(screen.getByTestId('test-action-consequence')).toHaveTextContent('不再派发');
    expect(screen.getByTestId('test-action-reason')).toHaveValue('');
    expect(screen.getByTestId('test-action-reason')).toHaveAttribute('maxlength', '500');
    expect(screen.getByTestId('test-action-reason')).toHaveAttribute(
      'aria-describedby',
      'test-action-target-description test-action-consequence',
    );
    expect(screen.getByTestId('test-action-confirm')).toBeDisabled();

    fireEvent.keyDown(screen.getByTestId('modal'), { key: 'Escape' });
    expect(onCancel).toHaveBeenCalledTimes(1);

    fireEvent.change(screen.getByTestId('test-action-reason'), { target: { value: '人工暂停复核' } });
    expect(onReasonChange).toHaveBeenCalledWith('人工暂停复核');
    expect(onConfirm).not.toHaveBeenCalled();

    rerender(
      <ActionReasonDialog
        idPrefix="test-action"
        title="确认暂停任务"
        target="任务 BULK-301 · 机构 42"
        consequence="暂停后不再派发新的子消息。"
        reasonLabel="暂停原因"
        reasonTestId="test-action-reason"
        reason="人工暂停复核"
        placeholder="请填写暂停依据"
        confirmLabel="确认暂停"
        pending={false}
        onReasonChange={onReasonChange}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );
    fireEvent.click(screen.getByTestId('test-action-confirm'));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    rerender(
      <ActionReasonDialog
        idPrefix="test-action"
        title="确认暂停任务"
        target="任务 BULK-301 · 机构 42"
        consequence="暂停后不再派发新的子消息。"
        reasonLabel="暂停原因"
        reasonTestId="test-action-reason"
        reason="人工暂停复核"
        placeholder="请填写暂停依据"
        confirmLabel="确认暂停"
        pending
        onReasonChange={onReasonChange}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );
    expect(screen.getByTestId('test-action-confirm')).toBeDisabled();
    expect(screen.getByTestId('test-action-cancel')).toBeDisabled();
    fireEvent.submit(screen.getByTestId('test-action-dialog'));
    fireEvent.keyDown(screen.getByTestId('modal'), { key: 'Escape' });
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
