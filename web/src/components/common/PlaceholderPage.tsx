interface PlaceholderPageProps {
  title: string;
  prdRef?: string;
}

/**
 * PRD 第 8 章 Web IA 里的绝大多数叶子页面在本次 scaffold 里还没有真实实现——
 * 用这个组件占位，而不是假装它们存在。每个占位页都标注对应的 PRD 功能编号，
 * 方便后续按 F-x 编号逐个"认领"实现，见 web/docs/ROADMAP.md。
 */
export default function PlaceholderPage({ title, prdRef }: PlaceholderPageProps) {
  return (
    <div className="card">
      <h2>{title}</h2>
      <p style={{ color: '#888' }}>
        本页面尚未实现，属于脚手架占位。{prdRef ? `对应 PRD 功能编号：${prdRef}。` : ''}
        详见 web/docs/ROADMAP.md。
      </p>
    </div>
  );
}
