# Issue 120 Design

Schema migrations: none

## Scope Table

| Concern | Owner | Contract |
|---|---|---|
| Persisted intent | Existing `uplink_records.tenant_id` and `webhook_delivery_events.tenant_id` | Unchanged stable relation; tenant display data is not duplicated. |
| Derived state | `UplinkNormalizationService` read projections | Left join `tenants` and expose nullable institution number, short name, and full name. |
| Public contract | Uplink console JSON | Existing fields remain; `tenantNo`, `tenantShortName`, and `tenantFullName` are additive. |
| Filter contract | Uplink tenant options plus existing `tenantId` query parameter | Labels are searchable; result requests contain only stable `tenantId`. |
| Compatibility | Missing/deleted tenant row or nullable display value | Return null display fields and preserve `tenantId`; UI renders an ID fallback. |
| Exclusions | Replay, delivery state, persistence, tenant portal | No behavior change. |

## Ownership Matrix

| Rule | Owner |
|---|---|
| Uplink record and detail projection | `UplinkNormalizationService.search/detail/bySource` |
| Push monitor projection | `UplinkNormalizationService.pushMonitor` |
| Read-only institution options | `UplinkNormalizationService.tenantOptions` and `UplinkNormalizationController` |
| API types and request serialization | `web/src/api/uplinkNormalizationApi.ts` |
| Display, fallback, and selection | `AdminUplinksPage.tsx` |
| API contract text | `core/docs/API.md` |

## State and Failure Model

1. The page loads uplink rows, push-monitor rows, and the identity-only tenant option list.
2. Each row already contains its tenant display projection; rendering never triggers a row lookup.
3. A candidate displays short name plus institution number, while its selected input value is the institution number that the real lookup endpoint can round-trip. A unique name/number resolves to one `tenantId`. Numeric internal IDs remain valid direct input for deleted or unavailable tenants.
4. Unknown or ambiguous nonnumeric text blocks query submission with actionable feedback.
5. If option loading fails, current rows still render their projected identity and numeric-ID filtering remains available.
6. Missing short name, number, or full name never hides the stable internal ID.

## Validation Ladder

- Service: joined projections, missing-metadata fallback, push monitor, and option list.
- Controller/MockMvc: list, detail, push-monitor, and tenant-option JSON serialization through the real response envelope.
- React: list/detail/monitor labels, no-name fallback, filter validation, and serialized stable ID.
- Chrome: visible surfaces plus captured list and monitor query strings.
- Full backend/frontend/build/planning/diff gates for regression.

The scoped Docker release check is not applicable because this issue changes no schema, seed, Compose service, runtime configuration, or packaged release identity. MockMvc API serialization plus installed local Google Chrome supplies the affected API/browser evidence; the repository's unchanged Docker lane remains the release-level regression boundary.
