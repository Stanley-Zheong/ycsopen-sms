import { Link } from 'react-router-dom';

const TOOLS = [
  { name: '号码归属与携号转网', description: '查询运营商、省份并维护携号转网缓存。', to: '/admin/number-attribution', action: '进入号码工具' },
  { name: '状态码映射', description: '维护上游状态码到平台状态的映射。', to: '/admin/status-codes', action: '管理状态码' },
  { name: '号段管理', description: '导入、查询和校验全国手机号段数据。', to: '/admin/prefixes', action: '管理号段' },
  { name: '短链审核', description: '审核短信中的短链并查看安全证据。', to: '/admin/shortlinks/review', action: '进入短链审核' },
];

export default function AdminToolsOverviewPage() {
  const testIdSuffix = (path: string) => path.slice(6).replace(/\//g, '-');
  return (
    <section data-testid="admin-tools-overview-page">
      <nav aria-label="面包屑">工具管理 / 工具总览</nav>
      <header><h1>工具总览</h1><p className="page-description">集中访问号码、状态码、号段和短链工具。</p></header>
      <section className="card">
        <table className="ratio-table" data-testid="admin-tools-overview-table">
          <thead><tr><th>工具</th><th>用途</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{TOOLS.map((tool) => <tr key={tool.to} data-testid={`admin-tools-overview-row-${testIdSuffix(tool.to)}`}><td>{tool.name}</td><td>{tool.description}</td><td>可用</td><td><Link data-testid={`admin-tools-overview-link-${testIdSuffix(tool.to)}`} to={tool.to}>{tool.action}</Link></td></tr>)}</tbody>
        </table>
      </section>
    </section>
  );
}
