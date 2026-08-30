# Repository skills

These repo-local skills were migrated from user-provided reference packages and
reconciled with the current ycsopen-sms implementation.

## Catalog

- `SKILL.md`: repository engineering entry point.
- `ui-design/`: Pencil and HTML design workflow aligned with ycsan-web and the
  project UI/Playwright contract.
- `code-review/`, `feature-delivery-guardrails/`, `flyway-migration/`,
  `issue-evidence/`, `java-unit-testing/`, `self-skills-improve/`: focused
  engineering workflows.
- `v2/`: fourteen typed testing lifecycle skills.

## Reconciliation decisions

- Replaced Everest/Lhotse/Hengshi/GitLab assumptions with this repository's
  Java 21/Spring Boot, React/Vite, GitHub, GSD, Pencil, and Playwright contracts.
- Removed dependencies on unavailable `everest-dev`, `hengshi-precheck-diff`,
  `sbin/*`, AKB, and private runtime environments.
- Kept only executable project-local helpers. Old-project issue collectors,
  environment mutation runners, and knowledge-publication clients were not
  copied because they would be unsafe or non-functional here.
- Made `.planning/EXECUTION-STANDARD.md`, `.planning/UI-TEST-CONTRACT.md`, and
  the active phase artifacts authoritative. No skill may introduce estimates;
  verified TODO emptiness is the completion signal.
