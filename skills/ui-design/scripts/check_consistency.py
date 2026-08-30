#!/usr/bin/env python3
"""Check phase prototype token and data-testid consistency."""

from __future__ import annotations

import re
import sys
from pathlib import Path


HEX_COLOR_RE = re.compile(r"#[0-9a-fA-F]{3,8}\b")
TAG_RE = re.compile(r"<([a-z][a-z0-9:-]*)\b([^>]*)>", re.I)
TEST_ID_RE = re.compile(r'\bdata-testid\s*=\s*["\'][^"\']+["\']', re.I)
COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
TOKEN_LINK_RE = re.compile(r'<link\b[^>]*\bhref\s*=\s*["\'][^"\']*tokens\.css(?:[?#][^"\']*)?["\'][^>]*>', re.I)
ROLE_RE = re.compile(r'\brole\s*=\s*["\']([^"\']+)["\']', re.I)
INTERACTIVE_ROLES = {
    "button", "checkbox", "combobox", "link", "menuitem", "menuitemcheckbox",
    "menuitemradio", "option", "radio", "searchbox", "slider", "spinbutton",
    "switch", "tab", "textbox", "treeitem",
}
NATIVE_INTERACTIVE = {"a", "button", "input", "select", "textarea", "summary"}


def check_page(path: Path) -> list[str]:
    source = path.read_text(encoding="utf-8", errors="ignore")
    executable_source = COMMENT_RE.sub(lambda match: "\n" * match.group(0).count("\n"), source)
    issues: list[str] = []
    if not TOKEN_LINK_RE.search(executable_source) and "var(--color" not in executable_source:
        issues.append("missing shared tokens.css reference or token variables")
    if len(HEX_COLOR_RE.findall(executable_source)) > 3:
        issues.append("more than three hard-coded colors; justify or replace with tokens")
    for match in TAG_RE.finditer(executable_source):
        tag = match.group(1).lower()
        attributes = match.group(2)
        role_match = ROLE_RE.search(attributes)
        role = role_match.group(1).lower() if role_match else ""
        scripted = bool(re.search(r"\b(onclick|onkeydown|onkeyup|contenteditable)\b", attributes, re.I))
        focusable = bool(re.search(r'\btabindex\s*=\s*["\']?0["\']?', attributes, re.I))
        interactive = tag in NATIVE_INTERACTIVE or role in INTERACTIVE_ROLES or scripted or focusable
        if interactive and not TEST_ID_RE.search(match.group(0)):
            line = executable_source.count("\n", 0, match.start()) + 1
            issues.append(f"interactive <{tag}> lacks data-testid at line {line}")
    return issues


def main() -> int:
    prototype = Path(sys.argv[1]) if len(sys.argv) == 2 else Path("prototype")
    pages = sorted(prototype.rglob("*.html"))
    if not pages:
        print(f"no HTML pages found under {prototype}", file=sys.stderr)
        return 2

    issue_count = 0
    for page in pages:
        issues = check_page(page)
        issue_count += len(issues)
        print(f"{page}: {'PASS' if not issues else 'FAIL'}")
        for issue in issues:
            print(f"  - {issue}")
    print(f"pages={len(pages)} issues={issue_count}")
    return 1 if issue_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
