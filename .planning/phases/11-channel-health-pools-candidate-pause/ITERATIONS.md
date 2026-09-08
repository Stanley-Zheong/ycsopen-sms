# Phase 11 Iterations

| Iteration | Trigger | Change | Evidence |
| --- | --- | --- | --- |
| I-11-001 | Phase entry | Started with four focused plans: health, pools, pause/candidate fence, and UI/Chrome evidence. | Entry review and phase-entry validator |
| I-11-002 | Implementation simplification | Replaced planned JPA entity/repository classes for health/pool tables with JdbcTemplate services because the tables are append-only or simple configuration rows. | D-11-006; `mvn -f core/pom.xml test` |
| I-11-003 | Chrome selector correction | Fixed Playwright row matching and metadata so production UI contract validates exact route/test-id/case/obligation links. | `Phase11RealServicePlaywrightTest`; production UI validator |
| I-11-004 | Review hardening | Fixed review findings for pool CAS, health-failure episode keys, post-maintenance validation, actor trust boundary, duplicate pool members, and MySQL timestamp precision. | `11-REVIEW.md`; `CLAUDE-REVIEW.md`; Phase11 focused/MySQL/Chrome tests |
