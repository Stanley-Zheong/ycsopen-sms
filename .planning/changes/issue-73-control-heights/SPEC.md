# Issue 73 Primary Control Heights

GitHub issue #73 restores the shared console primary-control height contract.
The shared stylesheet is the semantic owner: ordinary inputs, single-value
selects, and textareas start at 40 px high. The representative rendered route
is admin-routing-policy `/admin/routing-policy`, where an input, textarea, and
select must share that height. Page-owned values, validation, permissions, API
effects, layout columns, and resize behavior remain unchanged.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-73-primary-control-height | The shared field contract gives ordinary inputs, single-value selects, and textareas `height: 40px` and `min-height: 40px`; this overrides larger page-local textarea minimum heights while retaining vertical textarea resizing and the shared `--color-focus` visible-focus outline. | On `/admin/routing-policy`, `admin-routing-circuit-routing-policy-version`, `admin-routing-circuit-routing-policy-import-input`, and `admin-routing-circuit-routing-retry-category` each have a 40 px rendered layout height; keyboard focus on the textarea has the shared 3 px outline. |

## Exclusions

- Checkbox and radio inputs retain their existing 16 px choice-control sizing.
- `select[multiple]` retains its existing 96 px minimum height.
- Explicitly scoped page controls outside the ordinary primary-control contract,
  including login controls, are unchanged.

## Verification boundary

The browser case renders the real React DOM and intercepts routing-policy APIs
because this issue is limited to rendered CSS geometry. It proves the shared
initial control height, not backend-service behavior or manual resize results.
Run from `web/`:

```bash
YCSOPEN_USE_BUNDLED_CHROMIUM=true npm run test:e2e -- control-sizing.spec.ts --project=bundled-chromium --grep pw-issue-73-primary-control-height
```
