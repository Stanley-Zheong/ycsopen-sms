# Phase 5 — Console identity and platform RBAC

## Goal

Authenticate valid users, reject invalid/expired/disabled/locked/forged access, enforce current server-side permissions, and provide auditable account/role/session operations and production UI.

## Scope

- Platform accounts, password policy, lock/disable/enable/unlock, validity.
- JWT session lifecycle and server-side authorization.
- Menu, button, API, and data permissions; safe role migration.
- Login history, unusual-login notification, safe correlation-backed 500 state.
- Login, user, role, account, history, and error UI with stable `data-testid` values.

## Owned obligations

- OBL-F-1-1-A
- OBL-F-1-1-B
- OBL-F-1-2-A
- OBL-F-1-2-B
- OBL-F-1-2-C
- OBL-F-1-4-A
- OBL-F-1-4-B
- OBL-F-1-4-C
- OBL-F-1-5-A
- OBL-CRYPTO-PASSWORD-001
- OBL-FIELD-ACCOUNT-USERNAME
- OBL-FIELD-ACCOUNT-PASSWORD
- OBL-FIELD-ACCOUNT-PHONE
- OBL-FIELD-ACCOUNT-TYPE
- OBL-FIELD-ACCOUNT-VALIDITY
- OBL-STATE-ACCOUNT-LOCK
- OBL-STATE-ACCOUNT-UNLOCK
- OBL-STATE-ACCOUNT-DISABLE
- OBL-STATE-ACCOUNT-ENABLE
- OBL-EDGE-INTERNAL-ERROR
- OBL-DATA-10-1-IDENTITY
