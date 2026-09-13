# Phase 50 Design

Schema migrations: none

## UI

- `/tenant/help/guide`: search input and article cards.
- `/tenant/help/api`: endpoint, HMAC headers, request fields, errors and JSON example.
- `/tenant/help/customer-service`: availability, destination and fallback action.

## Content

`tenantHelpContent.ts` is the content registry. It carries `TENANT_HELP_VERSION` and only documents implemented product surfaces.
