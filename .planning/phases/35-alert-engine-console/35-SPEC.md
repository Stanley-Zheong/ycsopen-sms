# Phase 35 Spec

Required behavior:

- Operators can configure channel, failure-rate, balance, queue-backlog, and complaint-ratio alert rules.
- Rules preserve metric, source, comparison, threshold, duration, severity, state, channels, and targets.
- A sustained threshold breach creates exactly one active alert episode per rule/source key.
- Repeated matching evaluations update the same open episode without duplicate records.
- Recovery evaluations resolve open episodes with source-recovery evidence.
- Notification attempts record channel, target snapshot, provider result, retry count, status, and failure reason.
- Adapter failure does not delete or hide the underlying alert episode.
- Active alerts can be acknowledged with actor/time.
- Acknowledged alerts can be resolved with actor/time/reason.
- Global mute suppresses new notification attempts for a bounded period without changing lifecycle state.
- Admin `/admin/alerts` exposes dashboard cards, tabs, rule form/list, notification targets, alert history/actions, and delivery evidence.

Non-goals:

- No external SMS/email/DingTalk/WeCom provider integration.
- No background worker.
- No tenant-side alert console.
- No mobile/browser-matrix support; Chrome-only verification.
