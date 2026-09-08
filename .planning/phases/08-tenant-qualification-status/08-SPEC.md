# Phase 08 — Tenant qualification and status

## Intent

Deliver one coherent qualification module spanning public registration, tenant self-service certification, operator review, profile maintenance, and operating status without implementing later signature, template, credential, billing, contract, or termination modules.

## Scope

In scope: the complete PRD qualification field contract; protected uploads; contact-phone verification through the platform notification provider; credit-code and identity check digits; duplicate prevention; truthful status/timestamps/reasons; OCR-assisted license inspection plus mandatory human confirmation; approve/reject/supplement decisions; controlled initial tenant access and trial entitlement; maintenance and recertification; enable/disable/arrears-freeze; current HTTP-submit eligibility; authorized historical reads; exact RBAC; three desktop-Chrome production routes.

Out of scope: tenant subaccounts/API keys/CMPP credentials (Phase 09), signature/template lifecycle (Phases 12–13), trial accounting and formal billing (later finance phases), contract conversion (Phase 48), termination (Phase 49), CMPP/batch/scheduled ingress that does not yet exist, an OCR engine, generic workflow infrastructure, mobile UI, and non-Chrome browsers.

## Behaviors

- `tenant-qualification-status-01`: public registration or tenant resubmission validates every field, proof, contact-verification receipt, and protected-object claim before entering `PENDING`; status reads never expose identity plaintext, tokens, object IDs, or storage locations.
- `tenant-qualification-status-02`: an authorized reviewer sees safe fields and application-mediated protected evidence, requires completed OCR assistance plus human confirmation, and atomically approves, rejects, or requests supplements with an attributable reason. First approval enables the pending tenant administrator and creates one trial account/entitlement; other decisions create neither sending access nor entitlement.
- `tenant-qualification-status-03`: authorized platform users update non-certification business metadata; protected or certification-bound changes use full recertification and immediately remove sending/resource eligibility until approved again.
- `tenant-qualification-status-04`: authorized operations changes the existing tenant-account operating status among normal, disabled, and arrears-frozen with reason and optimistic concurrency. Disabled/frozen or uncertified tenants fail the shared new-work policy; qualification/audit history reads remain allowed.
- `tenant-qualification-status-05`: all nine catalog field contracts are enforced in client and server validation, including exact check digits, protected values, conditional proofs, and the corrected 10 MiB legal-ID-image limit.
- `tenant-qualification-status-06`: the public browser flow has no offline handoff from protected upload and verified contact through submission and approval-created trial access.
- `tenant-qualification-status-07`: tenant master, contact verification, certification facts, operating state, and immutable safe events persist without identity plaintext or credential leakage.

## Owned obligations

- OBL-F-2-1-A
- OBL-F-2-1-B
- OBL-F-2-1-C
- OBL-F-2-2-A
- OBL-F-2-2-B
- OBL-F-2-2-C
- OBL-F-2-3-A
- OBL-F-2-3-B
- OBL-F-2-4-A
- OBL-F-2-4-B
- OBL-FIELD-TENANT-SHORT-NAME
- OBL-FIELD-TENANT-FULL-NAME
- OBL-FIELD-TENANT-CREDIT-CODE
- OBL-FIELD-TENANT-LICENSE
- OBL-FIELD-TENANT-LEGAL-ID
- OBL-FIELD-TENANT-LEGAL-ID-FILES
- OBL-FIELD-TENANT-CONTACT
- OBL-FIELD-TENANT-SHORTLINK-PROOF
- OBL-FIELD-TENANT-TRADEMARK-PROOF
- OBL-FLOW-12-1-REGISTER
- OBL-DATA-10-2-TENANT

## Completion rule

Complete only when all 21 obligation evidence files PASS; backend, real MySQL, provider sandbox, protected-evidence composition, frontend, and installed-Chrome acceptance pass; the production UI contract passes; independent and Claude reviews have no BLOCKER/HIGH/MEDIUM; and `TODO.md` contains no unchecked item.
