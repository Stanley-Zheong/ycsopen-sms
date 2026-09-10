# Phase 32 Summary

Package: `uplink-normalization-operations`

## Delivered

- HTTP/CMPP connector uplinks normalize into the legacy `uplink_records` table with tenant/source labels, masked/hash phone lookup, receive-time aliases, push state, and push event linkage.
- UPLINK push reuses configured tenant webhook transport; raw callback URL replacement is not accepted for UPLINK events.
- Admin console supports uplink search/detail/replay and push monitor actions; tenant console supports tenant-scoped uplink search and audited auto-reply configuration.
- V4100 extends the V1 `uplink_records` table and preserves `mobile_encrypted` for protected-data migration compatibility.

## Verification

- `mvn -f core/pom.xml -Dtest=UplinkNormalizationServiceTest,WebhookDeliveryTransportServiceTest,UplinkNormalizationOperationsMigrationTest test`
- `mvn -f core/pom.xml test`
- `npm --prefix web ci`
- `npm --prefix web test`
- `npm --prefix web run build`
- `npm --prefix web exec -- playwright test uplink-normalization.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=json`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner uplink-normalization-operations --assert-unique --assert-traced`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 32 --package uplink-normalization-operations --stage production`

## Review and delivery

- Review record: `.planning/phases/32-uplink-normalization-operations/CLAUDE-REVIEW.md`
- Branch: `phase/32-uplink-normalization-operations`
- PR: `https://github.com/Stanley-Zheong/ycsopen-sms/pull/24`
