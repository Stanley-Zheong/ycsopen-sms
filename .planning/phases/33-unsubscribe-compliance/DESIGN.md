# Phase 33 Design

## Backend design

- `V4200__unsubscribe_compliance.sql` extends `unsubscribe_keywords` and `unsubscribe_records`.
- `UnsubscribeComplianceService` owns keyword normalization, uplink matching, evidence insertion, tenant export requests, statistics, and alert source events.
- `WebhookDeliveryTransportService.enqueueUnsubscribeEvent` creates UNSUBSCRIBE delivery events using the configured tenant destination only.
- `UplinkNormalizationService` calls Phase33 processing after normalized uplink persistence.

## Frontend design

- Admin route: `/admin/unsubscribes`.
- Tenant route: `/tenant/unsubscribes`.
- Pages use simple cards/tables and explicit query buttons.
- Chrome is the only browser in automated verification.

## Data protection boundary

Blacklist suppression uses the protected blacklist adapter. Unsubscribe evidence never exposes plaintext phone; it records masked phone plus hash/protected digest. Full envelope migration remains governed by the existing Phase3 protected-data migration ownership.
