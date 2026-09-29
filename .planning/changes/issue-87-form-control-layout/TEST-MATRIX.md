# Issue 87 Test Matrix

| Obligation | Case / Playwright ID | Surface | Oracle |
|---|---|---|---|
| OBL-ISSUE-87-ADMIN-FORM-WIDTH | C-ISSUE-87-ADMIN-FORM-WIDTH / `pw-issue-87-admin-form-contract` width/containment step | Literal 56-route catalog; exact 45-route default-visible form set | Every ordinary visible control <=420px and <=30% of its actual query/main container; form set matches exactly |
| OBL-ISSUE-87-FOUR-COLUMN-LAYOUT | C-ISSUE-87-FOUR-COLUMN-LAYOUT / `pw-issue-87-admin-form-contract` four-column step | QueryPanel unit, seven representative field grids, one nested field group, three page-owned panel/card grids, and two 720px modal grids | Field grids use four aligned columns at 1440px and keep children inside each cell; the nested group spans its parent; non-field and modal grids preserve their intended 2/3-column geometry |
| OBL-ISSUE-87-SELECT-OPTION-FIT | C-ISSUE-87-SELECT-OPTION-FIT / `pw-issue-87-admin-form-contract` option-fit step | Every visible select in the 56-route traversal, five named static selectors, plus `admin-channel-health-channel-pools-member-channel` | Every option set that fits the cap receives its intrinsic width; dynamic data proves the 30% cap is active; all <=420px |
| OBL-ISSUE-87-HORIZONTAL-CONTAINMENT | C-ISSUE-87-HORIZONTAL-CONTAINMENT / `pw-issue-87-admin-form-contract` width/containment step | Every terminal Admin route | `documentElement.scrollWidth === clientWidth` independently of whether a form is visible |

Chrome command and checksum-bound raw/summary reports are recorded under `EVIDENCE/` after the
final source tree is frozen.
