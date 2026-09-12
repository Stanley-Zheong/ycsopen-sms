# Issue 65 Choice-Control UI Elements

This production-change addendum defines the shared native choice-control and
number-attribution lookup layout required by GitHub issue #65. Existing page
permissions, state transitions, and API behavior remain owned by their original
production phases.

The shared CSS rule applies by semantic input type, so every current and future
native checkbox or radio receives the same 16px control size. Labels that wrap a
choice control remain inline-aligned. Forms keep their owning layout; only the
number-attribution lookup row receives the issue's explicit compact-row layout,
which wraps when the available width cannot contain all three control groups.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| shared-choice-controls `/login`, `/tenant/register`, `/tenant/qualification`, `/admin/tenants`, `/admin/system/roles`, `/admin/channel/pools`, `/tenant/uplink`, `/admin/number-attribution` | Existing route and action permission for each owning page | native choice controls | checkbox and radio | 16px square control; brand accent; wrapping labels use inline vertical alignment | Preserves every owning page's checked value, change handler, disabled rule, and API effect | clear, checked, disabled, keyboard-focus | shared-auth-login-remember,tenant-tenant-qualification-qualification-trademark-signature-intent,admin-tenant-qualification-tenants-review-human-confirmed,admin-console-identity-roles-permission-choice,admin-channel-health-channel-pools-member-primary,admin-channel-health-channel-pools-member-enabled,tenant-uplink-normalization-auto-reply-enabled,admin-number-attribution-force-provider-failure | OBL-ISSUE-65-CHOICE-CONTROLS,PROJECT-UI-CONTRACT | issue-65-shared-choice-controls | T-ISSUE-65-CHOICE-CONTROLS:playwright | pw-issue-65-choice-control-layout |
| admin-number-attribution `/admin/number-attribution` | ADMIN or existing `number-attribution:read` permission | attribution lookup | responsive compact form row | mobile label and value, provider-failure label and checkbox, lookup action; semantic label/control associations | Lookup keeps the existing GET request and result/error behavior; layout wraps only when the row no longer fits | default, checked, disabled, keyboard-focus, responsive-wrap, success, error | admin-number-attribution-lookup-form,admin-number-attribution-mobile-label,admin-number-attribution-mobile,admin-number-attribution-force-provider-failure-label,admin-number-attribution-force-provider-failure,admin-number-attribution-lookup | OBL-ISSUE-65-CHOICE-CONTROLS,PROJECT-UI-CONTRACT | issue-65-shared-choice-controls | T-ISSUE-65-CHOICE-CONTROLS:playwright | pw-issue-65-choice-control-layout |
