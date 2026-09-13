import {
  Children,
  cloneElement,
  useEffect,
  useId,
  useState,
  type FormEvent,
  type ReactElement,
  type ReactNode,
} from 'react';

interface QueryPanelProps {
  children: ReactNode;
  onSubmit: () => void;
  onReset: () => void;
  onRefresh?: () => void;
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
  children: ReactElement<{ id?: string }>;
}

interface QueryResultProps {
  children: ReactNode;
  className?: string;
}

const COMPACT_QUERY = '(max-width: 900px)';
const MEDIUM_QUERY = '(max-width: 1200px)';

function queryColumnCount(): number {
  if (typeof window === 'undefined' || !window.matchMedia) return 3;
  if (window.matchMedia(COMPACT_QUERY).matches) return 1;
  if (window.matchMedia(MEDIUM_QUERY).matches) return 2;
  return 3;
}

export function QueryField({ name, label, children }: QueryFieldProps) {
  const generatedId = useId();
  const controlId = children.props.id ?? generatedId;

  return (
    <div className="query-panel-field">
      <label data-testid={`query-label-${name}`} htmlFor={controlId}>{label}</label>
      <div className="query-panel-control" data-testid={`query-input-${name}`}>
        {cloneElement(children, { id: controlId })}
      </div>
    </div>
  );
}

export function QueryPanel({
  children,
  onSubmit,
  onReset,
  onRefresh,
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
  const fieldsId = useId();

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

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <section className={`query-panel card ${className}`.trim()} data-testid="query-panel">
      <form className="query-panel-form" data-testid={legacyPanelTestId} onSubmit={submit}>
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
            {(fieldCount > 1 || Boolean(resetLegacyTestId)) && (
              <button type="button" className="button-secondary" data-testid="query-reset" onClick={onReset}>
                {resetLegacyTestId ? <span data-testid={resetLegacyTestId}>重置</span> : '重置'}
              </button>
            )}
            {additionalActions}
            {onRefresh && (
              <button type="button" className="button-secondary" data-testid="query-refresh" onClick={onRefresh}>
                {refreshLegacyTestId ? <span data-testid={refreshLegacyTestId}>刷新</span> : '刷新'}
              </button>
            )}
          </div>
        </div>
      </form>
      {result !== undefined && <QueryResult>{result}</QueryResult>}
    </section>
  );
}

export function QueryResult({ children, className = '' }: QueryResultProps) {
  return (
    <div className={`query-panel-result ${className}`.trim()} data-testid="query-result-table" aria-label="查询结果">
      {children}
    </div>
  );
}
