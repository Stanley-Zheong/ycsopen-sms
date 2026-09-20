# AGENTS.md

## Engineering contract

- Target Java 21. Run backend checks with `mvn -f core/pom.xml test`.
- Target Node.js 20 or newer. Run frontend checks with `npm --prefix web ci`,
  `npm --prefix web test`, and `npm --prefix web run build`.
- Frontend work must follow `docs/frontend页面实现规范.md` and
  `.planning/frontend-spirits/README.md`. Each frontend change is delivered
  inside one frontend spirit package with `SPEC.md`, `DECISIONS.md`,
  `SYSTEM-DESIGN.md`, `ITERATIONS.md`, and `QUALITY-GATEWAY.md` updated before
  implementation evidence is claimed.
- Frontend page work must define the page goal, primary object, action
  contract, data source, empty/loading/error states, stable `data-testid`
  contract, and Chrome Playwright verification before merging. Inputs without
  query, submit, or explicit async-link behavior are not allowed.
- Every frontend spirit finishes through a quality gate: targeted unit tests,
  relevant Chrome Playwright coverage for changed user behavior, `npm --prefix
  web test`, `npm --prefix web run build`, `git diff --check`, and any scoped
  Docker release check named by the spirit. Record commands and boundaries in
  the spirit `QUALITY-GATEWAY.md`.
- Deliver repository changes through a branch and pull request. Do not push
  implementation commits directly to `main`.
- Keep changes scoped to the GitHub issue. Add or update tests for behavior
  changes and record any verification boundary that could not be executed.
- This is an independent open implementation. Do not copy source code,
  credentials, configuration, test data, or private documentation from the
  internal YCSAN system or any other non-public repository.
- Never commit secrets, production data, local runtime state, generated build
  output, or agent credentials.
- Keep README, roadmap, and status claims evidence-based. A placeholder,
  prototype, or unverified integration must not be described as complete.

## GitHub issue workflow

Jarvis work starts only from an explicit `@jarvis` or `/jarvis` comment by an
allowed user. The issue is the delivery scope and the pull request is the code
review and merge boundary. Include the issue reference, verification commands,
and known limitations in the pull request body.
