---
name: java-unit-testing
description: Use when adding or reviewing Java/JUnit tests in ycsopen-sms core. Select the smallest test layer that proves the changed behavior, extending the nearest owning test before introducing broader Spring or database fixtures.
---

# Java unit testing

Start with the behavior ID, invariant, and smallest input that distinguishes old
from new behavior.

1. Extend the nearest owning test under `core/src/test/java` when it can express
   the regression.
2. Prefer plain JUnit for services, validators, routing decisions, billing
   calculations, signature checks, and other deterministic logic. Mock only
   direct collaborators.
3. Use MVC/Spring tests when binding, validation, security filters,
   interceptors, exception mapping, serialization, or bean wiring is the claim.
4. Use H2 or MySQL-backed integration only for JPA/SQL, transaction, locking,
   Flyway, or dialect behavior. State where H2 is insufficient for MySQL 8.
5. For async behavior, assert the eventual owned state and side effects rather
   than only scheduling/enqueue success.

Cover meaningful equivalence classes: absent/null/empty, boundary values,
allowed/denied roles, same/cross tenant, success/failure/retry, duplicate or
idempotent calls, and state transitions relevant to the change. A regression
test must fail against the defective behavior and pass after the fix.

Do not persist real phone numbers, message bodies, credentials, provider URLs,
customer records, or machine paths in fixtures. Prefer behavior assertions over
private calls, logs, or exact collaborator counts unless those are contracts.

Run focused tests, then `cd core && mvn test`. Record commands and results in
the phase TEST-MATRIX/evidence; compilation alone does not prove changed
behavior.
