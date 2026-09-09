---
phase: "06"
slug: privileged-data-access-audit
status: approved-for-entry
created: 2026-09-07
---

# Phase 06 UI contract

## Design source and viewport

Reuse the Phase 2 manual design system and Admin shell: page `#f4f8fc`, white cards, brand blue `#0c85e8`, red only for errors/destructive outcomes, 6px controls, 10px cards/dialogs, and the 4/8/16/24/32/48/64 spacing scale. Acceptance is desktop-only in the locally installed Google Chrome at 1440x900. No mobile or alternate-browser variants are required.

The approved `.pen` baseline is retained unchanged because Pencil Desktop/MCP is unavailable locally. The Phase 06-specific source of interaction truth is `design-output/privileged-audit-prototype.html`; this limitation is recorded in `DECISIONS.md` and must not be presented as a newly rendered Pencil screen.

## Information architecture

- `/admin/system/logs`: title, actor/operation/result/time filters, searchable immutable audit table, page controls, and a right-side detail drawer. The drawer exposes only the already-redacted record.
- `/admin/system/security-events`: title, type/actor/result/time filters, attributable event table, empty/error states, and page controls.
- `/admin/system/users`: the existing phone column remains masked. Users with the live button, API, and full-account-scope permissions receive a “查看完整手机号” action opening a purpose dialog; platform administrators retain their existing override.

## Audit page

The table columns are 时间、操作人、租户、操作、资源、结果、IP、耗时、问题编号、详情. Result is text plus semantic color; `STARTED` means the request was durably captured but its terminal result could not be written and remains visible for investigation. Filters submit together and reset explicitly. The detail drawer contains method/route, parameter names, result, latency, trace, and timestamp; it never renders request values, response bodies, passwords, tokens, ciphertext, or full phone numbers. Escape and “关闭审计详情” close the drawer and restore focus.

## Security events page

The filter supports exact event type, actor, result, start, and end time. Event types are 异常登录、连续登录失败、大批量导出; results are 已检测、已阻断、成功、失败. The table columns are 时间、事件、操作人、租户、结果、IP、问题编号、摘要. Deduplication is a backend truth and is not represented as a misleading UI count.

All Phase 06 structural regions, controls, actions, result rows, conditional states, pagination controls, detail content, and reveal-dialog elements are listed individually in `UI-ELEMENTS.md` with literal `data-testid` values. Repeating table rows/actions pair that selector with `data-row-key` for record identity.

## Temporary reveal

The account table always renders `shared-privileged-data-sensitive-value` first. Clicking `shared-privileged-data-sensitive-value-reveal` opens a modal asking for one controlled purpose: customer support, security investigation, or compliance review. Confirmation calls `POST /api/v1/console/platform-accounts/{id}/phone/reveal`. While pending the button is disabled. Success shows plaintext only inside the modal with “仅本次查看，关闭后清除”; close, Escape, navigation, logout, session expiry, or user change clears it. The page never copies it automatically and never adds it to query strings, storage, logs, screenshots, or cache. A 403 leaves the masked value intact and shows “无权查看完整手机号”.

## Access and states

- `audit:operations:read` opens audit logs; `audit:operations:all` permits broader actor/tenant results.
- `audit:security-events:read` opens security events; `audit:security-events:all` permits broader scope.
- `privileged:data:reveal` exposes the UI action. The backend independently authorizes `privileged:data:reveal:api` plus `identity:accounts:all`; platform administrators retain their explicit override.
- Loading replaces table rows with concise text; empty states say “暂无操作日志” or “暂无安全事件”; fetch failures retain filters and offer retry.
- No sensitive value remains visible while loading, after an error, or after the reveal modal closes.

## Accessibility

Each page has one `h1`; every input has a visible label; tables have captions and headers; dialogs use `role=dialog`, labelled titles, focus trap, Escape close, and focus restoration. Status is never color-only. Keyboard users can submit/reset filters, open rows, and close the drawer/modal.
