# Phase 12 Spec — Signature Lifecycle and Channel Filing

## Scope

Deliver the `signature-lifecycle-filing` module only.

In scope:

- Qualified tenant signature application with text, type, usage type, proof reference, applicant context, and risk level.
- Tenant-visible pending/supplement/rejected/approved review result and history.
- Operator review list, filters, and decisions: approve, reject, supplement required.
- Per-channel filing state: none, registering, registered, failed; request/result/retry evidence.
- Usable-channel calculation: a signature can be used on a channel only when the signature is approved, that channel is filed successfully, and the channel is route-eligible under the Phase 11 candidate fence.
- Tenant and admin React pages with stable `data-testid` selectors documented in `UI-ELEMENTS.md`.

Out of scope:

- Template lifecycle and send compliance contract: Phase 13.
- Exemption policy: Phase 14.
- Unified cross-resource review history: Phase 15.
- Runtime sensitive content safety: Phase 17.
- Non-Chrome browsers and mobile UI.

## Owned obligations

Authoritative query:

```bash
ruby .planning/tools/validate-prd-obligations.rb --owner signature-lifecycle-filing --assert-unique --assert-traced
```

Expected selected obligations:

- `OBL-F-3-1-A`
- `OBL-F-3-1-B`
- `OBL-F-3-2-A`
- `OBL-F-3-2-B`
- `OBL-F-3-2-C`
- `OBL-F-3-3-A`
- `OBL-F-3-3-B`
- `OBL-F-3-3-C`

## Completion rule

The phase is complete only when `TODO.md` is fully checked with executable evidence, independent review and Claude review contain no unresolved BLOCKER/HIGH, the implementation commit is pushed to the configured GitHub remote, and no scoped TODO remains.
