# Spirit 05 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Unit tests | `npm --prefix web test` when web source changes | Pass or not applicable reason | Not recorded |
| Build | `npm --prefix web run build` when web source changes | Pass or not applicable reason | Not recorded |
| Docker release | `scripts/verify-docker-release` or selected lane command when release behavior changes | Pass | Not recorded |
| Login smoke | API or Chrome login against Docker Web URL | Pass | Not recorded |

## Merge Gate

- Gate item: Build commit identity is recorded for release-sensitive work.
- Gate item: Health and login evidence are recorded.
- Gate item: Report paths and verification boundaries are listed in the PR.
