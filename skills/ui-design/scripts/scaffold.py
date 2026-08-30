#!/usr/bin/env python3
"""Create the phase-local UI evidence/prototype skeleton without overwrites."""

from __future__ import annotations

import shutil
import sys
from pathlib import Path


SKILL_ROOT = Path(__file__).resolve().parent.parent


def copy_if_absent(source: Path, target: Path) -> None:
    if source.is_dir():
        target.mkdir(parents=True, exist_ok=True)
        for child in sorted(source.iterdir()):
            copy_if_absent(child, target / child.name)
        return
    if target.exists():
        print(f"skip existing: {target}")
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    print(f"created: {target}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: scaffold.py .planning/phases/<NN>-<package-id>", file=sys.stderr)
        return 2

    phase_dir = Path(sys.argv[1]).resolve()
    if phase_dir.parent.name != "phases" or not phase_dir.name[:2].isdigit():
        print(f"not a canonical phase directory: {phase_dir}", file=sys.stderr)
        return 2

    ui_dir = phase_dir / "EVIDENCE" / "ui"
    prototype = ui_dir / "prototype"
    (prototype / "pages").mkdir(parents=True, exist_ok=True)
    copy_if_absent(SKILL_ROOT / "assets" / "tokens.css", prototype / "tokens.css")
    copy_if_absent(SKILL_ROOT / "assets" / "components", prototype / "components")

    notes = {
        prototype / "index.html": """<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ycsopen-sms UI 原型入口</title>
  <link rel="stylesheet" href="tokens.css">
</head>
<body>
  <main class="app-content" data-testid="prototype-index-page-main-region">
    <h1 class="page-title" data-testid="prototype-index-page-title">UI 原型入口</h1>
    <nav aria-label="原型页面" data-testid="prototype-index-page-navigation">
      <p data-testid="prototype-index-page-navigation-instruction">生成页面后，按信息架构菜单分组添加页面链接、角色和权限说明。</p>
    </nav>
  </main>
</body>
</html>
""",
        ui_dir / "design-summary.md": "# UI design summary\n\n## Decisions\n\n## Open questions\n",
        ui_dir / "information-architecture.md": "# Information architecture\n\n## Menu and routes\n\n## Roles and permissions\n\n## Flows\n",
        ui_dir / "qa-report.md": "# UI review evidence\n\n## Consistency review\n\n## PRD/spec compliance review\n",
    }
    for path, content in notes.items():
        if path.exists():
            print(f"skip existing: {path}")
        else:
            path.write_text(content, encoding="utf-8")
            print(f"created: {path}")

    print("Pencil must create EVIDENCE/ui/design.pen through Pencil tools.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
