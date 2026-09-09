# Phase 22 TODO

- [x] OBL-F-2-8-A — trial activation supports positive quota and start/end validity.
- [x] OBL-F-2-8-B — trial sends decrement quota and tenant sees remaining quota/validity.
- [x] OBL-F-2-8-C — quota exhaustion/expiry freezes new sends once and preserves history.
- [x] OBL-F-2-8-D — tenant conversion request is available from eligible trial states.
- [x] OBL-F-8-1-A — prepaid reserve freezes exact priced amount and rejects insufficient balance.
- [x] OBL-F-8-1-B — confirm/reverse are idempotent under duplicate receipts.
- [x] OBL-F-8-4-A — consumption record links tenant, message/task, business type, amount/quota, and time.
- [x] OBL-F-8-4-B — consumption query filters by tenant and business type without mutation controls.
- [x] OBL-F-8-9-A — balance audit is append-only, typed, versioned, attributable, timed, and business-linked.
- [x] OBL-FIELD-TENANT-TRIAL-QUOTA — quota field is positive integer with default 500.
- [x] OBL-FIELD-TENANT-TRIAL-VALIDITY — validity field enforces start not after end and 14-day default.
- [x] OBL-STATE-TENANT-TRIAL — approved qualification can lead into trial state.
- [x] OBL-STATE-TENANT-TRIAL-FREEZE — quota exhaustion/expiry moves trial into frozen state.
- [x] OBL-FLOW-12-1-TRIAL — activation, quota, validity, consumption, freeze, and history are covered end-to-end.
- [x] OBL-DATA-10-8-BILLING — billing facts use exact monetary units and immutable business links.
