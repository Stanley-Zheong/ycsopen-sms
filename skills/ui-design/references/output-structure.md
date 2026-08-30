# GSD UI output structure

UI work belongs to the active canonical phase directory, not a detached
`design-output/` tree.

```text
.planning/phases/<NN>-<package-id>/
  <NN>-UI-SPEC.md
  DESIGN.md
  DECISIONS.md
  ITERATIONS.md
  TODO.md
  UI-ELEMENTS.md
  TEST-MATRIX.md
  EVIDENCE/
    ui-contract.json
    ui/
      design.pen
      design-summary.md
      information-architecture.md
      qa-report.md
      prototype/
        index.html
        tokens.css
        components/
        pages/
```

Pencil creates/updates `design.pen` through Pencil tools. `index.html` groups
all prototype pages by menu and states role/permission. UI-ELEMENTS remains the
machine authority for routes/selectors; prototype and Pencil manifests must map
to it exactly. The React implementation and production Playwright evidence stay
under `web/` and are referenced by checksum from `ui-contract.json`.
