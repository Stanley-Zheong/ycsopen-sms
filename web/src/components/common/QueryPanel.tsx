import {
  Children,
  cloneElement,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactElement,
  type ReactNode,
} from 'react';

interface QueryPanelProps {
  children: ReactNode;
  onSubmit: () => void | boolean;
  onReset: () => void;
  onRefresh: () => void;
  queryStatus: QueryStatus;
  result?: ReactNode;
  additionalActions?: ReactNode;
  initiallyExpanded?: boolean;
  submitLabel?: ReactNode;
  className?: string;
  legacyPanelTestId?: string;
  submitLegacyTestId?: string;
  submitDisabled?: boolean;
  resetLegacyTestId?: string;
  refreshLegacyTestId?: string;
}

interface QueryFieldProps {
  name: string;
  label: ReactNode;
  children: ReactElement<{ id?: string; name?: string }>;
}

interface QueryResultProps {
  children: ReactNode;
  queryStatus: QueryStatus;
  className?: string;
}

interface QueryStatus {
  testId: string;
  label: string;
  isFetching: boolean;
  isError: boolean;
  isEmpty: boolean;
  count?: number;
  errorDetailsId?: string;
}

type QueryResultState = 'loading' | 'error' | 'empty' | 'success';

const COMPACT_QUERY = '(max-width: 900px)';
const MEDIUM_QUERY = '(max-width: 1200px)';

function queryColumnCount(): number {
  if (typeof window === 'undefined' || !window.matchMedia) return 3;
  if (window.matchMedia(COMPACT_QUERY).matches) return 1;
  if (window.matchMedia(MEDIUM_QUERY).matches) return 2;
  return 3;
}

function compareText(left: string, right: string): number {
  if (left < right) return -1;
  if (left > right) return 1;
  return 0;
}

function queryValueSignature(value: FormDataEntryValue): string {
  if (typeof value === 'string') return value;
  return `${value.name}\u0000${value.size}\u0000${value.type}\u0000${value.lastModified}`;
}

function queryFormSignature(form: HTMLFormElement): string {
  const entries = Array.from(new FormData(form).entries(), ([name, value]) => [
    name,
    queryValueSignature(value),
  ] as const);
  entries.sort(([leftName, leftValue], [rightName, rightValue]) => (
    compareText(leftName, rightName) || compareText(leftValue, rightValue)
  ));
  return JSON.stringify(entries);
}

function queryResultState(status: QueryStatus): QueryResultState {
  if (status.isFetching) return 'loading';
  if (status.isError) return 'error';
  if (status.isEmpty) return 'empty';
  return 'success';
}

function queryResultText(status: QueryStatus, state: QueryResultState): string {
  if (state === 'loading') return `正在加载${status.label}`;
  if (state === 'error') return `${status.label}加载失败`;
  if (state === 'empty') return `暂无${status.label}`;
  if (status.count !== undefined) return `${status.label}：${status.count} 条`;
  return `${status.label}已加载`;
}

export function QueryField({ name, label, children }: QueryFieldProps) {
  const generatedId = useId();
  const controlId = children.props.id ?? generatedId;

  return (
    <div className="query-panel-field">
      <label data-testid={`query-label-${name}`} htmlFor={controlId}>{label}</label>
      <div className="query-panel-control" data-testid={`query-input-${name}`}>
        {cloneElement(children, { id: controlId, name })}
      </div>
    </div>
  );
}

export function QueryPanel({
  children,
  onSubmit,
  onReset,
  onRefresh,
  queryStatus,
  result,
  additionalActions,
  initiallyExpanded = false,
  submitLabel = '查询',
  className = '',
  legacyPanelTestId,
  submitLegacyTestId,
  submitDisabled = false,
  resetLegacyTestId,
  refreshLegacyTestId,
}: QueryPanelProps) {
  const [columns, setColumns] = useState(queryColumnCount);
  const fieldCount = Children.count(children);
  const collapsible = fieldCount > columns;
  const [expanded, setExpanded] = useState(initiallyExpanded || !collapsible);
  const [resetVersion, setResetVersion] = useState(0);
  const fieldsId = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const lastSubmittedSignature = useRef<string | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined;
    const media = [window.matchMedia(COMPACT_QUERY), window.matchMedia(MEDIUM_QUERY)];
    const update = () => setColumns(queryColumnCount());
    update();
    media.forEach((query) => query.addEventListener('change', update));
    return () => media.forEach((query) => query.removeEventListener('change', update));
  }, []);

  useEffect(() => {
    setExpanded(initiallyExpanded || !collapsible);
  }, [collapsible, initiallyExpanded]);

  useLayoutEffect(() => {
    if (!formRef.current) return;
    lastSubmittedSignature.current = queryFormSignature(formRef.current);
  }, [resetVersion]);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const signature = queryFormSignature(event.currentTarget);
    const unchanged = signature === lastSubmittedSignature.current;
    lastSubmittedSignature.current = signature;
    if (unchanged) {
      onRefresh();
      return;
    }
    const applied = onSubmit();
    if (applied === false) onRefresh();
  };

  const reset = () => {
    onReset();
    setResetVersion((version) => version + 1);
  };

  return (
    <section className={`query-panel card ${className}`.trim()} data-testid="query-panel">
      <form ref={formRef} className="query-panel-form" data-testid={legacyPanelTestId} onSubmit={submit}>
        <div className="query-panel-heading">
          <strong>查询条件</strong>
          {collapsible && (
            <button
              type="button"
              className="button-secondary query-panel-toggle"
              data-testid="query-panel-toggle"
              aria-controls={fieldsId}
              aria-expanded={expanded}
              onClick={() => setExpanded((current) => !current)}
            >
              {expanded ? '收起' : '展开'}
            </button>
          )}
        </div>
        <div
          id={fieldsId}
          className="query-panel-fields"
          data-testid="query-panel-fields"
          data-state={expanded ? 'expanded' : 'collapsed'}
          aria-hidden={!expanded}
          hidden={!expanded}
        >
          <div className="query-panel-field-grid" data-testid="query-fields">
            {children}
          </div>
          <div className="query-panel-actions" data-testid="query-actions">
            <button type="submit" data-testid="query-submit" disabled={submitDisabled}>
              {submitLegacyTestId ? <span data-testid={submitLegacyTestId}>{submitLabel}</span> : submitLabel}
            </button>
            <button type="button" className="button-secondary" data-testid="query-reset" onClick={reset}>
              {resetLegacyTestId ? <span data-testid={resetLegacyTestId}>重置</span> : '重置'}
            </button>
            {additionalActions}
            <button
              type="button"
              className="button-secondary"
              data-testid="query-refresh"
              disabled={submitDisabled}
              onClick={onRefresh}
            >
              {refreshLegacyTestId ? <span data-testid={refreshLegacyTestId}>刷新</span> : '刷新'}
            </button>
          </div>
        </div>
      </form>
      <QueryResult queryStatus={queryStatus}>{result}</QueryResult>
    </section>
  );
}

export function QueryResult({ children, queryStatus, className = '' }: QueryResultProps) {
  const state = queryResultState(queryStatus);
  const isError = state === 'error';
  return (
    <div
      className={`query-panel-result ${className}`.trim()}
      data-testid="query-result-table"
      aria-label="查询结果"
      aria-busy={queryStatus.isFetching}
    >
      <div
        data-testid={queryStatus.testId}
        data-query-result-state={state}
        data-state={state}
        role={isError ? 'alert' : 'status'}
        aria-live={isError ? 'assertive' : 'polite'}
        aria-busy={queryStatus.isFetching}
        aria-describedby={isError ? queryStatus.errorDetailsId : undefined}
      >
        {queryResultText(queryStatus, state)}
      </div>
      {children}
    </div>
  );
}
