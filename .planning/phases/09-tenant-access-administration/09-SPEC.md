# tenant-access-administration: Tenant subaccounts and access credentials

## Intent

Tenant administrators delegate tenant-scoped access, tenant developers manage
HTTP API keys, and permitted tenant users request downstream CMPP access without
creating a cross-tenant or secret-disclosure path.

## Scope

### In

- Tenant administrator creates and updates tenant subaccounts with only the
  tenant business-user and developer roles.
- Every subaccount query and mutation derives tenant identity from the
  authenticated account and rejects cross-tenant identifiers.
- Tenant administrators manage the existing tenant role membership contract.
- Tenant developers create named HTTP API keys with expiry, IP allow-list, and
  second/minute/hour/day rate policy; the generated App Secret is handed off
  once, encrypted at rest, masked in later reads, and immediately revocable.
- Permitted tenant developers request and revoke downstream CMPP credentials;
  connection metadata is visible according to role while password material is
  encrypted and handed off only through the controlled create response.
- Credential revocation emits one in-process domain event and changes the
  persisted status used by every current credential lookup immediately.
- Three production tenant pages reuse the Phase 02 shell/tokens and stable
  routes: `/tenant/administrators`, `/tenant/api/keys`, and
  `/tenant/cmpp/access`.

### Out

- HTTP HMAC body/signature verification, nonce storage, and request-rate
  enforcement; those are owned by the HTTP acceptance/rate-control phases.
- CMPP CONNECT/SUBMIT/DELIVER/ACTIVE_TEST protocol sessions or a CMPP server;
  this phase owns credential administration only.
- Mobile UI, responsive mobile optimization, and non-Chrome browser support.
- Generic event bus, workflow engine, credential recovery, export, bulk
  operations, or a second authentication/session system.

## External behavior

### tenant-access-administration-01

Where an authenticated tenant administrator operates on tenant account
settings, when creating or editing a subaccount, the service shall accept only
tenant-scoped `TENANT_USER` or `TENANT_DEV` membership, persist its role, return
redacted account data, and deny users or roles belonging to another tenant.

### tenant-access-administration-02

Where an authenticated tenant administrator or developer has the credential
permission, when creating an HTTP API key, the service shall generate a unique
App Key and secret, validate name/expiry/IP/rate policy, return the secret only
in that create response, and never include secret plaintext in list/detail or
audit output. A revoke operation shall make the key unusable immediately and
emit a revocation event.

### tenant-access-administration-03

Where a permitted tenant developer requests CMPP access, the service shall
create or update one tenant-owned CMPP credential with protocol/account/SPID,
endpoint and connection metadata, window/TPS/allow-list policy, and protected
password material. Later reads shall not recover or expose the password, and a
revoke operation shall immediately disable the credential and emit the same
revocation event contract.

### tenant-access-administration-04

Where API and CMPP credentials are persisted, the database shall retain tenant,
protected secret, allow-list, rate, connection, window, state, expiry, and use
metadata in the existing credential tables without recoverable plaintext secret
storage.

### tenant-access-administration-05

Where a tenant user opens an owned access page in desktop Chrome, the React
route shall render the role-authorized page, stable documented test IDs,
loading/empty/error/denied states, controlled dialogs, and immediate success or
failure feedback for the corresponding credential or subaccount action.

## Errors and boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| Missing tenant administrator/developer permission | 403 or route-level denied state; no data leak | tenant-access-administration-01/02/03 |
| Cross-tenant user, role, API key, or CMPP credential ID | 404/403 without revealing ownership | tenant-access-administration-01/02/03 |
| Unsupported subaccount role or platform role | Validation failure; no write | tenant-access-administration-01 |
| Duplicate username/App Key/name policy violation | Conflict/validation response; no partial write | tenant-access-administration-01/02 |
| Invalid expiry, address, port, allow-list, or rate/window value | Validation failure; no write | tenant-access-administration-02/03 |
| Secret list/detail request after create | Masked metadata only; no decrypt/reveal endpoint | tenant-access-administration-02/03 |
| Revoke twice or use revoked credential | Idempotent terminal response or explicit already-revoked conflict; lookup remains disabled | tenant-access-administration-02/03 |
| Concurrent revoke/update | Optimistic or row-locked update preserves one terminal state and one event | tenant-access-administration-02/03 |

## Verification

| Verification ID | Evidence |
| --- | --- |
| T-P09-ACCOUNT-UNIT | Service/controller tests cover role allow-list, tenant binding, permission denial, and cross-tenant adversarial identifiers. |
| T-P09-CREDENTIAL-INTEGRATION | MySQL tests prove encrypted columns, policy fields, one-time handoff, revocation, and no plaintext leak. |
| T-P09-UI-CHROME | Real installed desktop Chrome at 1440x900 exercises six direct UI obligations on the three routes. |
| T-P09-TRACE | Seven obligation evidence files, exact TEST-MATRIX rows, production UI contract, and empty scoped TODO. |

## Requirement trace

| PRD requirement | Behavior IDs | Verification IDs |
| --- | --- | --- |
| REQ-F-1-3 | tenant-access-administration-01 | T-P09-ACCOUNT-UNIT, T-P09-UI-CHROME |
| REQ-F-2-6 | tenant-access-administration-02, tenant-access-administration-03 | T-P09-CREDENTIAL-INTEGRATION, T-P09-UI-CHROME |
| REQ-F-2-7 | tenant-access-administration-01 | T-P09-ACCOUNT-UNIT, T-P09-UI-CHROME |

## Owned obligations

- OBL-F-1-3-A
- OBL-F-1-3-B
- OBL-F-2-6-A
- OBL-F-2-6-B
- OBL-F-2-6-C
- OBL-F-2-7-A
- OBL-DATA-10-6-ACCESS

