# AGENTS.md

## Engineering contract

- Target Java 21. Run backend checks with `mvn -f core/pom.xml test`.
- Target Node.js 20 or newer. Run frontend checks with `npm --prefix web ci`,
  `npm --prefix web test`, and `npm --prefix web run build`.
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
