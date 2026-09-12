---
phase: "05"
slug: console-identity-platform-rbac
status: approved
shadcn_initialized: false
preset: none
created: 2026-09-07
---

# Phase 5 — Console Identity and Platform RBAC UI Design Contract

> Production visual and interaction contract for console authentication, platform accounts, roles and permissions, login history, account overview, and the shared safe internal-error state. Phase 2 tokens, shell patterns, and the pinned ycsan visual baseline are locked inputs.

## Sources and scope

| Source | Contract used |
| --- | --- |
| `.planning/ROADMAP.md`, Phase 5 | Production surfaces, success criteria, desktop production UI gate, and Phase 6/9 exclusions |
| `.planning/PRD-OBLIGATIONS.md` | All 19 Phase 5 page/element references and their observable behavior |
| Phase 2 `02-UI-SPEC.md` | Canonical Admin login, users, and roles destinations plus shared shell behavior |
| Phase 2 `design-output/tokens.css` | Color, radius, focus, shadow, and 8px base-spacing tokens |
| Current React identity surfaces | Existing compatibility selectors and implementation drift requiring reconciliation |

This phase includes platform identity only. Tenant subaccount administration, privileged plaintext reveal, balance/usage overview, password recovery, mobile layouts, and later observability assurance are out of scope. The account overview may show only the signed-in platform user's identity, roles, permission scope, and login information.

## Design system

| Property | Value |
| --- | --- |
| Tool | Manual CSS using the locked Phase 2 token file; no shadcn initialization |
| Preset | Not applicable |
| Component library | Existing React components and native semantic controls |
| Icon library | None required; text labels remain authoritative |
| Font | `-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` |
| Radius | Controls `6px`; cards/dialogs `10px` |
| Elevation | Phase 2 `--color-card-shadow` for cards and `--color-shadow` for overlays/floating alerts |

No third-party design registry or block is admitted. The current handwritten styles must be aligned to Phase 2 variables rather than introducing a second token system.

## Supported viewport and browser

- Acceptance is desktop-only in the current locally installed Google Chrome at exactly `1440x900`.
- The Admin shell uses a fixed navigation rail and a fluid content region; the content region must not produce horizontal page scrolling at `1440x900`.
- Account tables may use a contained horizontal scroller if their columns cannot fit, with the action column remaining discoverable by keyboard.
- There is no mobile, tablet, touch-layout, alternate-browser, or browser-version-matrix requirement in Phase 5.

## Spacing scale

All layout spacing is derived from the Phase 2 `8px` base and its `4px` half-step.

| Token | Value | Usage |
| --- | --- | --- |
| `space-0.5` | `4px` | Icon/text gap, compact status separation |
| `space-1` | `8px` | Inline controls and compact rows |
| `space-2` | `16px` | Card padding, form-field separation |
| `space-3` | `24px` | Page content padding and major card gap |
| `space-4` | `32px` | Login-card internal grouping |
| `space-6` | `48px` | Large empty-state separation |
| `space-8` | `64px` | Page-level breathing room only |

Exceptions: none. Existing `6px`, `10px`, `12px`, `14px`, and `20px` layout gaps are implementation drift and must be replaced with declared tokens.

## Typography

Exactly four sizes and two weights are allowed in this phase.

| Role | Size | Weight | Line height |
| --- | --- | --- | --- |
| Metadata and status | `12px` | `400` | `1.5` |
| Body, table, label, control | `14px` | `400` | `1.5` |
| Card/section heading | `20px` | `600` | `1.2` |
| Page/login heading | `28px` | `600` | `1.2` |

Permission codes may use the browser monospace font at `12px`; this does not introduce another UI font scale. Status must never be communicated by weight or color alone.

## Color

| Role | Locked value | Usage |
| --- | --- | --- |
| Dominant (60%) | `--color-page: #f4f8fc`, `--color-surface: #ffffff` | Page background and primary surfaces |
| Secondary (30%) | `--color-surface-alt: #eef5ff`, `--color-brand-dark: #123250`, `--color-border: #dbe5ef` | Navigation, cards, grouped permission panels, borders |
| Interactive accent (10%) | `--color-brand: #0c85e8` | The sole interactive accent: primary submit/save actions, active navigation, selection, and focus affordance |
| Reserved decorative token | `--color-accent: #31c9b6` | Not used by Phase 5 interactive controls, selection, focus, or status states |
| Destructive | `--color-danger: #c93647`, `--color-error-bg: #fff0f2` | Disable account, delete role, validation/error states only |
| Warning | `--color-warning: #a56a00`, `--color-warning-bg: #fff8e6` | Locked/expiring status and migration-required state |
| Success | `--color-success: #0d8779`, `--color-success-bg: #e7f8f4` | Enabled state and completed mutation feedback |

Phase 5 has exactly one interactive accent: `--color-brand: #0c85e8`, used for primary CTAs, the active destination, selection, and focus. `--color-accent: #31c9b6` is reserved and unused in Phase 5 interactive or status states; success remains exclusively `--color-success: #0d8779`. Destructive red is never used for ordinary navigation or non-destructive abandonment. Text/background pairs must meet WCAG 2.1 AA contrast; controls and focus indicators must remain distinguishable without color.

## Production route contract

| Surface | Canonical production route | Audience | Phase 2 mapping | Required behavior |
| --- | --- | --- | --- | --- |
| Platform login | `/admin/auth/login` | Unauthenticated | `/admin/auth/login` | Password login with safe, non-enumerating rejection; `/login` remains a compatibility redirect |
| Platform accounts | `/admin/system/users` | Platform administrator or explicit current menu/API permission | `/admin/users` | List, create, edit, validity, role association, and state transitions |
| Roles and permissions | `/admin/system/roles` | Platform administrator or explicit current menu/API permission | `/admin/roles` | Custom roles, four permission groups, live save, safe association migration |
| Login history | `/admin/system/login-history` | Signed-in user for own history; administrator permission for broader history | Shared profile/history behavior | Filtered, paged actor/time/address/client/outcome history |
| Account overview | `/admin/account-overview` | Signed-in platform user | Phase 5-owned destination | Current roles, permission scope, last-login data, own login history |
| Shared internal error | Mounted at application root on every console route | Any console user | Shared component state | Safe busy notice and correlation identity for real HTTP 500 |

Phase 2 `/admin/users` and `/admin/roles` paths remain compatibility redirects to the canonical Phase 5 `/admin/system/users` and `/admin/system/roles` routes, not competing page identities. Direct navigation to a forbidden destination renders a permission-denied state inside the authenticated shell; unauthenticated, expired, revoked, or malformed sessions return to `/admin/auth/login` without rendering protected data.

### Page focal hierarchy

- `/admin/system/users`: the account table and current account state are primary; page title and summary are secondary; filters and permitted row actions are tertiary.
- `/admin/system/roles`: the selected role's permission tree and save action are primary; the role list is secondary; create, delete, and migration actions are tertiary.
- `/admin/system/login-history`: the login-outcome history table is primary; user and outcome filters are secondary; pagination and supporting metadata are tertiary.
- `/admin/account-overview`: current identity, roles, and last-login facts are primary; grouped permission scope is secondary; own login history is tertiary.

## Surface and interaction contracts

### Platform login

- Use the Phase 2 authentication shell: brand panel plus a single `320–400px` credential card centered in the content area.
- The brand panel identifies YCSOpen SMS as an enterprise SMS operations workspace and uses the repository-owned abstract messaging-network background. Its copy describes the product domain without claiming that unfinished roadmap modules are delivered.
- Fields are labeled “用户名” and “密码”, with `autocomplete="username"` and `autocomplete="current-password"`.
- “记住用户名” is a `16px × 16px` checkbox aligned with its adjacent label inside the credential form. It stores only the username; passwords and bearer tokens are never placed in local storage.
- The primary CTA is “登录”. While pending, label it “登录中…” and disable duplicate submission.
- Invalid credentials retain the Phase 1 compatibility copy “用户名或密码错误，或账号已被锁定”; locked, disabled, and expired-account outcomes may use their controlled server messages without exposing hashes, internal identifiers, stack text, or policy internals.
- On rejection, focus the error summary, keep the username, clear the password, and remain on the login route.
- Preserve Phase 1 compatibility selectors. The phase-owned submit button uses `admin-console-identity-auth-login-submit`; a nested text span retains `shared-auth-login-submit` so existing foundation browser checks still activate the same button.

### Platform account management

- Page header: “平台账号”; supporting copy: “管理管理员、运营和财务账号；状态变更由服务端鉴权并留痕。”
- The table columns are 用户名、姓名、手机号、类型、角色、状态、有效期、最近登录、操作. Phone is masked; Phase 5 never offers plaintext reveal.
- Empty state: “暂无平台账号” / “创建管理员、运营或财务账号后，可在这里管理角色与有效期。” The “新建账号” action is present only when currently permitted.
- Create/edit is a modal or drawer above the page, not an extra full-width card below the table. It traps focus, returns focus to its opener, and, when dirty, uses the explicit secondary action “放弃账号修改” plus confirmation “放弃未保存的账号修改？”. Escape invokes that same confirmation instead of silently closing; a clean form may close directly.
- Create requires username, password, phone, real name, type, role, and optional validity. Edit displays masked existing phone; blank phone/password means unchanged and never forces re-entry of secrets.
- Username validation copy: “用户名须为 4-20 位字母、数字或下划线”。 Password copy: “密码至少 8 位，且须包含大写字母、小写字母和数字”。 Phone copy: “请输入有效的 11 位国内手机号”。 Validity copy: “有效期不能早于当前日期”。
- The validity field explains “留空为长期有效”. Account type exposes exactly 系统管理员、运营、财务.
- Disable requires confirmation: “禁用后，该账号的现有会话将立即失效。确认禁用？” Enable and manual unlock require explicit actions and display attributable success feedback.
- State badges use both text and semantic color: 启用、禁用、锁定. Only actions valid for the current state are rendered.

### Roles and permissions

- Page header: “角色与权限”; supporting copy: “权限保存后由服务端按当前数据库授权实时执行。”
- Use a two-column desktop layout: `280px` role list and a fluid permission workspace.
- Group the tree under exactly 菜单权限、按钮权限、接口权限、数据权限. Each permission exposes a readable name and secondary code; parent/child hierarchy is conveyed semantically, not only by indentation.
- Selecting a role updates all four groups. Dirty permission changes expose “保存权限” and the explicit secondary action “放弃权限修改”; abandoning or navigating to another role confirms “放弃未保存的权限修改？” and never silently discards changes.
- The primary CTA is “保存权限”; pending state disables repeat saves, success announces “权限已更新并实时生效”.
- Empty roles copy: “暂无自定义角色” / “新建角色并配置菜单、按钮、接口和数据权限。”
- Deleting an unused role requires “删除角色后无法恢复。确认删除？”
- Deleting an in-use role never proceeds directly. Open the required migration dialog with user count, an active replacement-role select, “迁移并删除” as the destructive CTA, and the outcome-specific secondary action “保留原角色”. The CTA remains disabled until a valid distinct target role is selected.

### Account overview and login history

- Account overview uses three cards: identity/last-login facts, current roles and permission scope, and own login history.
- It must not display tenant records, balance, usage, financial data, protected phone plaintext, or administrative controls the user does not possess.
- Permission scope is grouped by MENU/BUTTON/API/DATA rather than an unstructured code dump; codes remain available as secondary diagnostic text.
- Login history columns are 用户、时间、登录地址、客户端、结果. Own-history view omits redundant actor where appropriate; administrator history supports an explicit user filter and pagination.
- Empty history copy: “暂无登录记录” / “成功或失败的登录尝试会在这里显示。”
- Unusual-login rows include a text badge “异常登录”; the UI does not claim notification delivery status unless returned by the server.

### Session and shell behavior

- Logout is available from the profile area on every authenticated platform page. It revokes server session state before clearing the browser state; local state is cleared even if the response cannot be read.
- Session expiry/revocation clears protected content and routes to `/admin/auth/login`; copy is “登录状态已失效，请重新登录”.
- Menu and button visibility reflect current server permissions, but hidden controls are never treated as authorization. A 403 renders “无权执行此操作” and leaves the authenticated session intact.
- Loading uses concise text or Phase 2 skeleton tokens; no protected stale data remains visible after the principal changes.

### Shared safe internal-error state

- A real HTTP 500 produces a fixed, dismissible alert using `--color-error-bg`, `--color-danger`, and Phase 2 overlay elevation.
- Exact primary copy: “系统繁忙，请稍后再试”. When supplied, show only “问题编号：{traceId}”. Do not show exception messages, request bodies, stack traces, SQL, tokens, usernames, phone numbers, or provider details.
- Announce the notice with `role="alert"`; dismissal returns focus to the action that initiated the failed request when it still exists.
- Missing correlation identity does not invent one. The safe primary message still renders.

## State matrix

| Surface | Loading | Empty | Validation/rejection | Success | Permission/session |
| --- | --- | --- | --- | --- | --- |
| Login | Disabled “登录中…” CTA | Not applicable | Invalid, locked, disabled, expired; password cleared | Redirect to allowed platform destination | Expired/revoked returns to login |
| Accounts | Table skeleton/text | Guidance plus permitted create CTA | Inline field errors; mutation alert | Row refresh plus status announcement | Page denied; individual actions hidden and API 403 handled |
| Roles | Role/tree skeleton | Create-role guidance | Required role fields; migration target; save/delete errors | Current tree refresh and live-effective announcement | Page/action denied without losing session |
| Overview/history | Card/table skeleton | No roles/permissions/history guidance | Read failure with retry | Current server facts rendered | Own scope by default; broader history permission-gated |
| Shared 500 | Not applicable | Not applicable | Safe alert with optional correlation identity | Dismissed state | Never clears a valid session merely because of 500 |

## Accessibility contract

- One `h1` per page; sections follow ordered `h2`/`h3` hierarchy. Current visual headings implemented as `h2` at page root must be corrected.
- Every input/select has a programmatic label. Required status is conveyed in text and markup. Validation uses `aria-invalid` and `aria-describedby`; the first invalid field receives focus.
- All actions are native buttons. Icon-only actions require an accessible name. Links perform navigation; buttons perform mutations.
- Modal/drawer/dialog surfaces use `role="dialog"`, `aria-modal="true"`, an accessible title, initial focus, focus trap, Escape close when safe, and focus restoration.
- Tables have a caption or equivalent accessible name, scoped column headers, stable row identity, and empty-state content outside an empty `<tbody>`.
- Loading and success feedback use polite live regions; credential rejection and unexpected errors use alert semantics. Repeated announcements must not accumulate duplicate alerts.
- Keyboard users can reach all filters, role choices, tree checkboxes, pagination, confirmations, and dismiss controls in logical order with a visible `--color-focus` outline.
- Minimum control height is `40px`; destructive and adjacent cancel actions retain at least `8px` separation.
- Color contrast meets WCAG 2.1 AA, and status/selection/permission state is always communicated with text or control state in addition to color.

## Stable test-ID registry

Every ID below is literal, unique within the active DOM, and statically discoverable. Dynamic table rows add `data-row-key` using a non-sensitive stable account or role identifier; they do not interpolate identifiers into `data-testid`.

### Catalog-owned required IDs

| Stable ID | Route/surface | Contract | Obligation |
| --- | --- | --- | --- |
| `admin-console-identity-users-create` | `/admin/system/users` | Open create-account dialog | OBL-F-1-1-A |
| `admin-console-identity-users-row-disable` | `/admin/system/users` | Active-row disable action region | OBL-F-1-1-B |
| `admin-console-identity-roles-permission-tree` | `/admin/system/roles` | MENU/BUTTON/API/DATA permission tree | OBL-F-1-2-A |
| `admin-console-identity-roles-save` | `/admin/system/roles` | Save current role permissions | OBL-F-1-2-B |
| `admin-console-identity-roles-migrate-dialog` | `/admin/system/roles` | In-use-role migration dialog | OBL-F-1-2-C |
| `admin-console-identity-auth-login-submit` | `/admin/auth/login` | Submit credential login | OBL-F-1-4-A |
| `shared-console-identity-profile-logout` | Shared authenticated shell | Revoke and clear current session | OBL-F-1-4-B |
| `shared-console-identity-profile-login-history` | Account overview/login history | Login history region/page | OBL-F-1-4-C |
| `admin-console-identity-account-overview` | `/admin/account-overview` | Account scope and login-information page; page identity `admin-account-overview` | OBL-F-1-5-A |
| `admin-console-identity-users-form-username` | Account dialog | Username field | OBL-FIELD-ACCOUNT-USERNAME |
| `admin-console-identity-users-form-password` | Account dialog | Password field | OBL-FIELD-ACCOUNT-PASSWORD |
| `admin-console-identity-users-form-phone` | Account dialog | Phone field | OBL-FIELD-ACCOUNT-PHONE |
| `admin-console-identity-users-form-type` | Account dialog | Exact platform type select | OBL-FIELD-ACCOUNT-TYPE |
| `admin-console-identity-users-form-validity` | Account dialog | Optional account validity | OBL-FIELD-ACCOUNT-VALIDITY |
| `admin-console-identity-users-state` | `/admin/system/users` | Account state table/region | OBL-STATE-ACCOUNT-LOCK |
| `admin-console-identity-users-unlock` | `/admin/system/users` | Authorized manual unlock | OBL-STATE-ACCOUNT-UNLOCK |
| `admin-console-identity-users-disable` | `/admin/system/users` | Confirmed disable action | OBL-STATE-ACCOUNT-DISABLE |
| `admin-console-identity-users-enable` | `/admin/system/users` | Authorized enable action | OBL-STATE-ACCOUNT-ENABLE |
| `shared-console-identity-internal-error-message` | Shared application root | Safe HTTP-500 message | OBL-EDGE-INTERNAL-ERROR |

`OBL-CRYPTO-PASSWORD-001` and `OBL-DATA-10-1-IDENTITY` intentionally have no UI selector; their truths are proven below the browser layer.

### Required structural and compatibility IDs

| Stable ID | Purpose |
| --- | --- |
| `shared-auth-login-page` | Preserve Phase 1 login page contract |
| `shared-auth-login-card` | Preserve Phase 1 login card contract |
| `shared-auth-login-username` | Preserve Phase 1 username-field contract |
| `shared-auth-login-password` | Preserve Phase 1 password-field contract |
| `shared-auth-login-remember` | Preserve Phase 1 remembered-username contract |
| `shared-auth-login-error` | Preserve Phase 1 controlled-error contract |
| `shared-auth-login-submit` | Nested compatibility target inside the phase-owned login button |
| `admin-console-identity-auth-intro-title` | Stable product-context heading on the login brand panel |
| `admin-console-identity-auth-intro-summary` | Stable product-context summary on the login brand panel |
| `admin-console-identity-users-page` | Account page root |
| `admin-console-identity-users-form` | Create/edit account dialog/form |
| `admin-console-identity-users-edit` | Edit-account action |
| `admin-console-identity-roles-page` | Roles page root |
| `admin-console-identity-roles-create` | Create-role action |
| `admin-console-identity-roles-form` | Create-role dialog/form |
| `shared-console-identity-internal-error-trace-id` | Safe displayed correlation identity |
| `shared-console-identity-internal-error-dismiss` | Dismiss shared 500 alert |

## Copywriting contract

| Element | Exact copy |
| --- | --- |
| Login primary CTA | 登录 |
| Account primary CTA | 新建账号 |
| Account save CTA | 保存账号 |
| Dirty-account abandon action | 放弃账号修改 |
| Dirty-account abandon confirmation | 放弃未保存的账号修改？ |
| Role primary CTA | 保存权限 |
| Dirty-permission abandon action | 放弃权限修改 |
| Dirty-permission abandon confirmation | 放弃未保存的权限修改？ |
| Empty accounts heading | 暂无平台账号 |
| Empty roles heading | 暂无自定义角色 |
| Empty history heading | 暂无登录记录 |
| Authentication error | 用户名或密码错误，或账号已被锁定 |
| Session-expired error | 登录状态已失效，请重新登录 |
| Permission error | 无权执行此操作 |
| Internal error | 系统繁忙，请稍后再试 |
| Disable confirmation | 禁用后，该账号的现有会话将立即失效。确认禁用？ |
| Delete-role confirmation | 删除角色后无法恢复。确认删除？ |
| In-use role CTA | 迁移并删除 |
| In-use role preserve action | 保留原角色 |

## Registry safety

| Registry | Blocks used | Safety gate |
| --- | --- | --- |
| shadcn official | None | Not applicable; Phase 2 manual system is locked |
| Third party | None | No third-party source admitted as of 2026-09-07 |

## Production reconciliation requirements

- Preserve `/admin/system/users` and `/admin/system/roles` as the canonical page routes and make Phase 2 `/admin/users` and `/admin/roles` compatibility redirects.
- Add the catalog-required `admin-console-identity-auth-login-submit` while retaining Phase 1 compatibility as specified above.
- Align `web/src/styles/index.css` to Phase 2 token values; current dark sidebar and undeclared spacing/radius/color literals are drift, not new design decisions.
- Mount the account, role, history, overview, and internal-error surfaces through real production routes; an unmounted component or mocked-only browser path does not satisfy this contract.
- Permission-based visibility is UX only. Playwright must also observe server 401/403 behavior for direct requests and direct route navigation.
- Production Playwright must cover loading, empty, validation, success, forbidden, locked/disabled/expired session, role migration, live permission removal, and a real injected HTTP 500 with safe correlation display.

## Checker sign-off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** independent UI checker verified the revised contract with all six dimensions PASS.
