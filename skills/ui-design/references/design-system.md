# ycsopen-sms design system baseline

The visual reference is the approved ycsan-web family. Inspect an explicitly
provided/authorized reference checkout when available; the values below capture
the required project baseline without depending on a sibling filesystem path.

## Brand language

- Primary `#0C85E8`, hover/accent `#4EAFFF`, active `#0B61AA`.
- Teal accent `#31C9B6`; reserve it for positive/connected signals, not every
  primary action.
- Warning `#FF9C00`; danger `#E5484D`; success text `#0D8779`.
- Page `#F6FAFF`, surface `#FFFFFF`, alternate surface `#EEF5FF`.
- Primary text `#123250`, secondary `#5F7389`, border `#DBE5EF`.
- Fonts: Segoe UI, PingFang SC, Hiragino Sans GB, Microsoft YaHei, sans-serif.

Use subtle blue/teal gradients for the login shell, selected summary panels, or
major callouts. Dense management tables and forms remain calm white surfaces;
do not copy the marketing site's large hero spacing or pill buttons into every
console control.

## Console scale

- Type: 12 / 13 / 14 / 16 / 20 / 24 / 28 px.
- Spacing: 4 px base; common 8 / 12 / 16 / 24 / 32 px.
- Radius: 4 px controls, 8-12 px cards/dialogs, pill only for tags/status.
- Shadows: blue-tinted soft shadow only for floating layers and selected cards.
- Shell: 232 px expanded sidebar, 64 px collapsed, 60 px header.
- Desktop is the primary console viewport. When required, tablet collapses the
  sidebar and mobile uses a drawer plus card/table adaptation.

All body text meets WCAG AA contrast. Focus must remain visible. Color never
acts as the only status signal. Loading, empty, error/retry, stale/partial,
disabled, permission-denied, destructive confirmation, and success states are
part of the component contract.
