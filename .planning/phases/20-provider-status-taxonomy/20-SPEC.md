# Phase 20 Spec — Provider Status Taxonomy

## Required behavior

1. Operators can map provider, protocol, and provider code to one platform status contract: category, finality, billability, retryability, severity, and advice.
2. Mapping versions have effective dates and history. A new valid import supersedes the prior active version without rewriting existing normalization event evidence.
3. Unknown codes return an explicit safe fallback: `UNKNOWN_REVIEW_REQUIRED`, not final, not billable, not retryable, `WARN`, and source `UNKNOWN_SAFE_FALLBACK`.
4. HTTP and CMPP consumers use the same `ProviderStatusTaxonomyPort`.
5. Authorized operators can inspect versions, inspect current mappings, import mappings, run normalization checks, and create export requests.

## Owned obligations

- `OBL-PROVIDER-TAXONOMY-001`: Operators map provider, protocol, and code to one effective platform category, finality, billability, retryability, and handling advice.
- `OBL-PROVIDER-TAXONOMY-002`: Mapping versions have conflict prevention and effective dates and never rewrite historical normalized evidence.
- `OBL-PROVIDER-TAXONOMY-003`: Unknown codes follow an explicit safe finality, billability, and retry policy and surface as unmapped operations work.
- `OBL-PROVIDER-TAXONOMY-004`: HTTP and CMPP connectors, receipt state, retry, billing, details, and analytics consume the same effective taxonomy contract.
- `OBL-F-13-3-A`: Authorized operators create, update, import, export-request, and inspect version history for provider-to-platform status mappings and advice.

## Acceptance

- `mvn -q -f core/pom.xml test`
- `npm --prefix web ci`
- `npm --prefix web test`
- `npm --prefix web run build`
- `npm --prefix web exec -- playwright test provider-status.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=json`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner provider-status-taxonomy --assert-unique --assert-traced`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 20 --package provider-status-taxonomy --stage design`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 20 --package provider-status-taxonomy --stage production`
