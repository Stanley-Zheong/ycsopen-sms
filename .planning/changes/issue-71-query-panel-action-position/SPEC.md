# Issue 71 Query-Panel Action Position

GitHub issue #71 corrects the shared query-panel layout after the issue #64
change moved the field grid into an inner container. The shared `QueryPanel`
remains the layout owner for every current consumer. Page-owned query values,
permissions, pagination, API calls, result rendering, and selectors do not
change.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-71-query-action-position | A visible action group never starts at the left edge of a new row. It occupies the trailing edge of a single field row, aligns with the final field row when the configured field columns and action group fit, and moves to a separate right-aligned row when they do not. | At 1440 px, the tenant-list actions overlap the three-field row vertically and end at the panel's right edge. At the 1201 px and 901 px column-transition cliffs, they render below the final field without overlap and end at the right edge. At 800 px, the expanded actions overlap the final field row in the right-side region. At 600 px, they move below the final field and remain aligned to the right edge. |

## Layout contract

- The action group is a sibling of the inner field grid inside the existing
  disclosure region.
- The disclosure is a wrapping flex row. The field grid keeps its existing
  3/2/1 responsive columns and a minimum width that preserves every field's
  label/control track.
- The action group stays on the final field row when both siblings fit. Flex
  wrapping moves the whole action group to the following row when they do not;
  automatic left margin keeps either placement right-aligned.
- Search and reset remain on one line. Existing single-field, reset, collapse,
  focus, and accessible-label behavior is unchanged.

## Verification boundary

The issue browser case renders the real React component and measures layout in
Chromium at 1440, 1201, 901, 800, and 600 px. The 1201 px and 901 px checks
cover the narrow side of the three- and two-column layouts. It intercepts page
APIs because the defect is limited to browser layout; it does not claim
backend-service acceptance. The full Web unit suite and build continue to own
non-layout regression coverage.
