# Phase 48 Review

## Claude review attempts

- Attempt 1: returned a valid finding that untracked new files were not included in the diff. Applied `git add -N` to Phase 48 new code/test files so intent-to-add files are visible to diff review.
- Attempt 2: timed out before returning review content. No file changes were made by Claude.

## Local BLOCKER/HIGH review findings

### Fixed: tenant API outside JWT security path

- Finding: tenant endpoints were initially mapped under `/api/v1/tenant/shortlinks`. Existing `JwtAuthenticationFilter` only authenticates `/api/v1/console/**`, so real token-backed tenant requests would not establish `Authentication`.
- Fix: moved tenant endpoints and frontend API calls to `/api/v1/console/tenant/shortlinks/**`; added a tenant-role matcher in `SecurityConfig`.
- Evidence: `mvn -f core/pom.xml test`, `npm --prefix web test`, `npm --prefix web run build`, and local-Chrome Playwright all passed after the fix.

### Fixed: request-body tenant ID could override authenticated tenant

- Finding: tenant create accepted `command.tenantId()` when present. That allowed a tenant user to submit another tenant ID.
- Fix: controller derives tenant ID from `UserRepository.findById(authentication.getName()).tenantId` and ignores request-body tenant ID.
- Evidence: `ShortLinkSafetyControllerTest.tenantCreateUsesAuthenticatedUsersTenantAndIgnoresSpoofedTenantId`.

### Fixed: blacklisted target domain evidence was not explicit

- Finding: target domain lookup required approved domain status before automated review, so a blacklisted domain could fail before persisting `DOMAIN_BLACKLISTED` evidence.
- Fix: target review now loads matching target domains regardless of status, records `DOMAIN_BLACKLISTED`, returns `BLOCKED`, and still forbids approval/redirect. Short custom domains still require approved status.
- Evidence: `ShortLinkSafetyServiceTest.privateNetworkMaliciousAndUnapprovedDomainsCannotBeApprovedOrRedirected`.

## Review result

No remaining BLOCKER/HIGH finding is open for the Phase 48 scope.
