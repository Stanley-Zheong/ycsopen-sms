import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { API_DOCS, CUSTOMER_SERVICE, GUIDE_ARTICLES, TENANT_HELP_VERSION } from '@/api/tenantHelpContent';
import '@/styles/tenant-help.css';

type HelpSection = 'guide' | 'api' | 'service';

export default function TenantHelpCenterPage({ section }: { section: HelpSection }) {
  if (section === 'guide') {
    return <section className="tenant-help-page" data-testid="tenant-tenant-help-guide-page"><HelpShell><Guide /></HelpShell></section>;
  }
  if (section === 'api') {
    return <section className="tenant-help-page" data-testid="tenant-tenant-help-api-docs-page"><HelpShell><ApiDocs /></HelpShell></section>;
  }
  return <section className="tenant-help-page" data-testid="tenant-tenant-help-customer-service-page"><HelpShell><CustomerService /></HelpShell></section>;
}

function HelpShell({ children }: { children: ReactNode }) {
  return (
    <>
      <nav aria-label="面包屑">机构端 / 帮助中心</nav>
      <header className="tenant-help-header">
        <div>
          <h1>帮助与开发者中心</h1>
          <p className="page-description">内容版本 {TENANT_HELP_VERSION}，只发布当前系统已实现的操作与 API。</p>
        </div>
        <span className="tenant-help-version" data-testid="tenant-tenant-help-content-version">version:{TENANT_HELP_VERSION}</span>
      </header>
      <nav className="tenant-help-tabs" aria-label="帮助中心导航">
        <NavLink data-testid="tenant-tenant-help-guide-tab" to="/tenant/help/guide">使用指南</NavLink>
        <NavLink data-testid="tenant-tenant-help-api-docs-tab" to="/tenant/help/api">API 文档</NavLink>
        <NavLink data-testid="tenant-tenant-help-customer-service-tab" to="/tenant/help/customer-service">联系客服</NavLink>
      </nav>
      {children}
    </>
  );
}

function Guide() {
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return GUIDE_ARTICLES;
    return GUIDE_ARTICLES.filter((article) => [article.title, article.category, article.summary, ...article.steps]
      .join(' ')
      .toLowerCase()
      .includes(needle));
  }, [query]);
  return (
    <section className="card tenant-help-card" data-testid="tenant-tenant-help-guide-search-region">
      <h2>版本化使用指南</h2>
      <label>搜索指南
        <input data-testid="tenant-tenant-help-guide-search-input" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="发送、资质、对账、短链" />
      </label>
      <div className="tenant-help-article-grid" data-testid="tenant-tenant-help-guide-results">
        {filtered.map((article) => (
          <article key={article.id} className="tenant-help-article" data-testid="tenant-tenant-help-guide-article">
            <h3>{article.title}</h3>
            <p>{article.category} / 权限：{article.permissions.join('、')}</p>
            <p>{article.summary}</p>
            <ol>{article.steps.map((step) => <li key={step}>{step}</li>)}</ol>
          </article>
        ))}
      </div>
    </section>
  );
}

function ApiDocs() {
  return (
    <section className="card tenant-help-card" data-testid="tenant-tenant-help-api-docs-contract">
      <h2>已实现 HTTP API 合同</h2>
      {API_DOCS.map((endpoint) => (
        <article key={endpoint.id} className="tenant-help-api-block">
          <h3><code>{endpoint.method}</code> <span data-testid="tenant-tenant-help-api-docs-endpoint">{endpoint.path}</span> / {endpoint.version}</h3>
          <h4>认证请求头</h4>
          <ul data-testid="tenant-tenant-help-api-docs-auth-headers">
            {endpoint.authentication.map((line) => <li key={line}>{line}</li>)}
          </ul>
          <h4>请求字段</h4>
          <table className="tenant-help-table" data-testid="tenant-tenant-help-api-docs-request-table">
            <thead><tr><th>字段</th><th>必填</th><th>规则</th></tr></thead>
            <tbody>{endpoint.requestFields.map((field) => (
              <tr key={field.name}><td>{field.name}</td><td>{field.required ? '是' : '否'}</td><td>{field.rule}</td></tr>
            ))}</tbody>
          </table>
          <h4>响应与错误</h4>
          <table className="tenant-help-table" data-testid="tenant-tenant-help-api-docs-response-table">
            <thead><tr><th>字段</th><th>规则</th></tr></thead>
            <tbody>{endpoint.responseFields.map((field) => (
              <tr key={field.name}><td>{field.name}</td><td>{field.rule}</td></tr>
            ))}</tbody>
          </table>
          <table className="tenant-help-table" data-testid="tenant-tenant-help-api-docs-error-table">
            <thead><tr><th>错误码</th><th>含义</th></tr></thead>
            <tbody>{endpoint.errors.map((error) => <tr key={error.code}><td>{error.code}</td><td>{error.meaning}</td></tr>)}</tbody>
          </table>
          <pre data-testid="tenant-tenant-help-api-docs-example">{endpoint.example}</pre>
        </article>
      ))}
    </section>
  );
}

function CustomerService() {
  const [fallbackVisible, setFallbackVisible] = useState(false);
  return (
    <section className="card tenant-help-card" data-testid="tenant-tenant-help-customer-service-entry">
      <h2>客服入口</h2>
      <p data-testid="tenant-tenant-help-customer-service-availability">可用时间：{CUSTOMER_SERVICE.availability}</p>
      <p data-testid="tenant-tenant-help-customer-service-destination">联系目标：{CUSTOMER_SERVICE.destination}</p>
      <button type="button" data-testid="tenant-tenant-help-customer-service-fallback-action" onClick={() => setFallbackVisible(true)}>
        联系不可用，显示 fallback
      </button>
      {fallbackVisible && (
        <p role="status" className="tenant-help-fallback" data-testid="tenant-tenant-help-customer-service-fallback">
          {CUSTOMER_SERVICE.fallback}
        </p>
      )}
    </section>
  );
}
