# Issue 87 Admin Form Control Layout

## Scope

Issue: <https://github.com/Stanley-Zheong/ycsopen-sms/issues/87>

Unify existing Admin form geometry without changing business behavior. The primary object is an
ordinary single-line input/select and its field grid on the 56 terminal Admin routes. The platform
user's permissions, data sources, validation, commands, API effects, and all loading/empty/error
copy remain page-owned and unchanged.

## Contract

- At 1440px every visible ordinary input/select is at most 420px and at most 30% of its query
  panel or Admin main-content container.
- At 1440px grids fit exactly four fields per row; wrapped rows begin at the same column origin.
- Admin QueryPanel labels stack above controls at the four-column breakpoint so each field remains
  inside its track. Page-owned card/panel grids and 720px modal grids retain their own geometry.
- Breakpoints are four/three/two/one columns at 1440/1439/1200/900px.
- Every visible select whose intrinsic option width fits the desktop cap receives that width;
  longer options exercise the cap without stretching the select to fill an empty row.
- Every Admin terminal route has `scrollWidth === clientWidth` under the layout fixture.
- Textareas, multi-selects, checkbox/radio/file/hidden/range/colour, and button-like inputs are
  excluded from the ordinary-control width rule.

## States and actions

The geometry applies during loading, empty, populated, validation, success, and error states. No
new input or action is introduced, so the existing query/submit/reset/async-link contract remains
the authority for every field.
