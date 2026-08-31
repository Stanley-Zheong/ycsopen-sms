# Phase 03: Cryptographic storage and migration bootstrap - Pattern Map

**Mapped:** 2026-08-31  
**Completion metric:** scoped TODO empty  
**Scope:** Java 21 backend, persistence, Flyway, real-service verification; no UI or browser artifacts

## File Classification

The research defines package families rather than a closed class list. The planner may split a family into the named value objects and exceptions required by the contract, but must keep the semantic owner shown here.

| New/modified file or family | Role | Data flow | Closest existing analog | Match quality |
| --- | --- | --- | --- | --- |
| `core/src/main/java/com/ycsopen/sms/core/common/security/envelope/EnvelopeCodec.java` and immutable envelope/context types | utility/model | transform | `common/security/FieldEncryptor.java` | primitive-only |
| `core/src/main/java/com/ycsopen/sms/core/common/security/key/KeyProtectionPort.java`, `BlindIndexPort.java`, key-state/rotation service | provider/service | request-response + batch | `service/message/MessageSubmitService.java` | construction/transaction-only |
| `core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/*` | provider/config | request-response | none | no analog |
| test-only deterministic key adapters under mirrored test packages | provider/test | request-response | `FieldEncryptorTest.java` fixture style | test-structure-only |
| `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodec.java` and explicit protected repository adapters | service/store | CRUD + transform | `MessageTaskRepository.java`, `MessageSubmitService.java` | partial |
| `MessageSubmitService.java`, `MessageTask.java`, and affected repository mapping | service/model/store | transactional CRUD | same files | exact modification targets |
| `core/src/main/java/com/ycsopen/sms/core/common/security/object/*` | provider/service/store | file-I/O + request-response | none | no analog |
| `core/src/main/java/com/ycsopen/sms/core/common/security/migration/*` | service/store | bounded batch | `Phase01MySqlIntegrationTest.java` transaction pattern | infrastructure-only |
| `core/src/main/java/com/ycsopen/sms/core/common/security/logging/*` and `core/src/main/resources/logback-spring.xml` | utility/config | transform | `GlobalExceptionHandler.java` | call-site location only |
| `core/src/main/resources/db/migration/V1200__*.sql` | migration | schema CRUD | `V1__init_schema.sql` | naming/dialect-only |
| protected-data inventory manifest and validator | config/utility | batch + transform | `.planning/tools/validate-phase-entry.rb` family | role-match |
| `skills/flyway-migration/scripts/next_flyway_version.py` and its tests | utility/test | file-I/O + transform | same file | exact modification target |
| Phase 3 MySQL, MinIO, and SoftHSM integration fixtures/tests | test/provider | request-response + file-I/O | `Phase01MySqlIntegrationTest.java` and shared service harness | lifecycle-match |
| `application.yml` plus Phase 3 test profile resources | config | dependency injection | `application.yml`, `application-phase01-integration.yml` | exact configuration style |
| `FieldEncryptor.java`, `FieldEncryptorTest.java`, `HashUtil.java`, `Phase01MySqlIntegrationTest.java` | utility/test | transform + integration | same files | compatibility/removal targets |

## Pattern Assignments

### Envelope core and strict parser

**Analog:** `core/src/main/java/com/ycsopen/sms/core/common/security/FieldEncryptor.java`

Reuse only the platform primitive choices and fresh nonce generation. Lines 22-24 establish `AES/GCM/NoPadding`, a 128-bit authentication tag, and a 12-byte nonce; lines 38-45 show a fresh nonce per encryption:

```java
private static final String TRANSFORMATION = "AES/GCM/NoPadding";
private static final int GCM_TAG_LENGTH_BITS = 128;
private static final int IV_LENGTH_BYTES = 12;

byte[] iv = new byte[IV_LENGTH_BYTES];
secureRandom.nextBytes(iv);
Cipher cipher = Cipher.getInstance(TRANSFORMATION);
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
```

Do not reuse the class boundary or payload contract. Lines 26-31 retain a directly configured key, lines 34-46 emit an unversioned Base64 concatenation, and encryption has no AAD. The new owner is a strict binary `YCSE/v1` codec plus `ProtectedFieldCodec`; it validates lengths before allocation, rejects unknown/trailing data, authenticates canonical context, and delegates DEK wrapping to `KeyProtectionPort`.

### Envelope and fault tests

**Analog:** `core/src/test/java/com/ycsopen/sms/core/common/security/FieldEncryptorTest.java`

Mirror production packages, use JUnit 5 and AssertJ, and keep each security invariant visible as a behavior-named test. Lines 33-42 prove nonce freshness and round trip; lines 45-70 use mutation and `assertThatThrownBy` for tamper, wrong-key, malformed, and undersized inputs:

```java
String first = encryptor.encrypt(plaintext);
String second = encryptor.encrypt(plaintext);
assertThat(first).isNotEqualTo(second);
assertThat(encryptor.decrypt(first)).isEqualTo(plaintext);

assertThatThrownBy(() -> encryptor.decrypt(mutatedPayload))
        .isInstanceOf(IllegalStateException.class);
```

Extend this structure with strict header/length/version bounds, AAD row/tenant/field swaps, wrapped-key mutation, unknown provider/key, oversized input, nonce-collision detection, interrupted rotation, and one externally stable authentication-failure category. Synthetic test keys belong only in explicit test fixtures; their passing result must be labeled deterministic-adapter evidence.

### Key ports, rotation service, and production PKCS#11 adapter

**Closest construction analog:** `MessageSubmitService.java` lines 36-52.

Use final collaborators and constructor injection:

```java
private final RoutingEngine routingEngine;
private final MessageTaskRepository messageTaskRepository;

public MessageSubmitService(RoutingEngine routingEngine,
                            MessageTaskRepository messageTaskRepository) {
    this.routingEngine = routingEngine;
    this.messageTaskRepository = messageTaskRepository;
}
```

This is only an injection convention. There is no repository analog for SunPKCS11, opaque KEK/HMAC keys, provider health, alias state, or rewrap rotation. Implement those from `03-RESEARCH.md`, with production startup failing closed when provider, token, mechanism, or alias validation fails. Ports return wrapped data keys, blind indexes, or sanitized health status—never master-key bytes. The deterministic adapter must be test-only and must not be component-scanned as a production fallback.

### Protected persistence boundary and first adoption

**Analogs:** `MessageSubmitService.java`, `MessageTask.java`, `MessageTaskRepository.java`.

Preserve the thin orchestration and transaction boundary at `MessageSubmitService.java` lines 25-29 and 54-55. Preserve generated business identity before persistence at lines 93-105. Replace the unsafe assignments at lines 77 and 100-101:

```java
@Transactional
public SmsSendResponse submit(Long tenantId, SmsSendRequest request, String clientIp) {
    String messageId = /* application-generated immutable identity */;
    // Delegate phone protection and blind-index creation to one persistence adapter.
}
```

The service supplies tenant, immutable `messageId`, logical table, and field purpose; it must not call JCE, `FieldEncryptor`, `HashUtil`, or any key port. The explicit adapter constructs `ProtectionContext`, produces envelope bytes and a versioned HMAC blind index, and performs the write in the owning transaction.

`MessageTask.java` lines 15-18 show the JPA entity convention, lines 24-40 show explicit column naming, and lines 66-67 show optimistic versioning. Preserve those conventions, but do not preserve `String mobileEncrypted` for a `VARBINARY` column. Use `byte[]` or an immutable binary value mapped explicitly and verify Connector/J behavior on MySQL.

`MessageTaskRepository.java` lines 9-13 shows Spring Data repository naming. Derived methods are acceptable only for non-protected fields or an explicitly versioned blind index. Never query protected plaintext or add a derived query that silently keeps raw SHA-256 semantics.

### Migration runner and checkpoint transaction

**Infrastructure analog:** `Phase01MySqlIntegrationTest.java` lines 80-87 and 117-136.

Use Spring `JdbcTemplate` for exact SQL semantics and `TransactionTemplate` where the row update, sanitized outcome, and checkpoint must commit or roll back together:

```java
transactionTemplate.executeWithoutResult(status -> {
    jdbcTemplate.update(/* optimistic update over PK and original-cell digest */);
    // update checkpoint and sanitized counters in this same transaction
    // mark rollback on any classification, integrity, or write failure
});
```

The existing example creates and drops a synthetic table, which is suitable only for isolated verification. Production migration owns reviewed manifest targets, stable cursor/lease state, strict envelope classification, integrity comparison, and resumable checkpoints. It must not use ad-hoc SQL, dump plaintext, mutate V1 DDL, or advance the checkpoint separately from the row transition.

### Real MySQL and external-service verification

**Analog:** `Phase01MySqlIntegrationTest.java` lines 49-78, 89-115, and `Phase01ServiceHarness` lines 149-208.

Reuse these verified lifecycle properties:

- system-property-gated real integration lane;
- `@DynamicPropertySource` wiring from an isolated service session;
- `@AfterAll` exact cleanup;
- server version, image digest, platform, schema, and Flyway identity assertions;
- random test-owned run IDs and synthetic credentials;
- cleanup on failed handoff.

Representative structure:

```java
@DynamicPropertySource
static void mysqlProperties(DynamicPropertyRegistry registry) {
    mysql = Phase01ServiceHarness.startMySql();
    registry.add("spring.datasource.url", mysql::jdbcUrl);
    registry.add("spring.datasource.username", mysql::username);
    registry.add("spring.datasource.password", mysql::password);
}

@AfterAll
static void stopMySql() {
    if (mysql != null) mysql.close();
}
```

Refactor the shared harness rather than creating unrelated container lifecycle code. Add exact adapters for the already-present MinIO image and an operator-provisioned SoftHSM installation. A missing real prerequisite remains an open TODO. Mockito, an in-memory map, or the deterministic key adapter cannot close MySQL, S3, or PKCS#11 evidence.

### Flyway migration and owner-range helper

**Analogs:** `V1__init_schema.sql`; `skills/flyway-migration/scripts/next_flyway_version.py`.

Keep integer `V<integer>__description.sql` naming and MySQL/InnoDB conventions. Extend the helper's current filename validation and duplicate detection at lines 13-49. Replace the global `current_max + 1` selection at lines 51-60 with schema-registry owner/range selection. For `crypto-storage-bootstrap`, it must select the first unoccupied ID in `V1200-V1299`, reject an unknown owner/range, and reject global collisions.

`V1200__*.sql` may create only registered `ycs.sms.crypto-storage-bootstrap.*` metadata: key references/state, protected object/capability digests, migration run/checkpoint/lease, and sanitized events. It must not alter V1 legacy tables, store plaintext DEKs/master keys/PINs/protected values/raw URLs/capability tokens, edit `V1__init_schema.sql`, or mix expand/backfill/destructive contract in automatic startup.

Update `Phase01MySqlIntegrationTest.java` lines 99-105: retain immutable V1 checksum and Flyway validation, but replace the claim that the latest migration is always `1` with an assertion over the declared immutable migration set.

### Protected object storage and expiring application capability

**Analog:** none.

No existing repository code has private S3 semantics, application-side object encryption, capability digesting, expiry/revocation, or orphan reconciliation. Follow the ports and flow in `03-RESEARCH.md`:

- `PrivateObjectStorePort` handles opaque object keys and ciphertext bytes only;
- `ProtectedObjectService` validates content, encrypts before put, persists safe metadata, and decrypts only after complete authentication;
- application capability responses contain neither bucket/key nor raw S3 URLs;
- capability storage contains a keyed digest, binding, purpose, expiry and revocation state—not the token;
- MinIO tests use the real AWS SDK adapter and prove anonymous denial plus raw-object canary absence.

Do not copy controller URL patterns from the existing web layer: Phase 3 owns the storage/capability boundary, while final RBAC/reveal policy belongs to Phase 6.

### Safe logging and exception boundary

**Location analog:** `GlobalExceptionHandler.java` lines 16-32.

Keep one centralized exception boundary and the public generic unexpected-error response:

```java
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("INTERNAL_ERROR", "系统繁忙，请稍后再试"));
```

Do not copy the current logging arguments. Lines 23 and 30 log a business message and a raw throwable; both can carry protected input or provider details. Introduce safe event/category values, correlation, allowlisted identifiers, CR/LF sanitization, redacted value types, and a tested Logback output defense. Tests must seed canaries through business and unexpected exception paths and prove absence without removing stable diagnostic category/correlation.

### Configuration profiles

**Analogs:** `application.yml` lines 1-15 and 24-32; `application-phase01-integration.yml` lines 1-24.

Preserve `ycsopen.*` namespacing, Flyway ownership, `ddl-auto: validate` outside isolated integration tests, and profile-specific test configuration. Remove the production-wirable direct key default at `application.yml` lines 29-32. New production configuration names provider configuration, token identity, key aliases, credential indirection, private object store, and capability policy without embedding any key or PIN.

An explicit test profile may wire synthetic deterministic adapters. Production profile validation must reject that adapter and any implicit fallback. Service test resources may carry synthetic endpoints and aliases but no local machine paths, production URLs, or secret values.

### Inventory manifest, leak scanner, and evidence validator

**Closest analog family:** `.planning/tools/validate-phase-entry.rb` and other `.planning/tools/validate-*.rb` files.

Follow the repository's fail-closed validator shape: deterministic input paths, stable machine-readable status, nonzero exit on missing/unknown/duplicate/unclassified data, and fixtures for pass/fail branches. The inventory validator must reconcile Flyway SQL, JPA mappings, writers/readers, runtime `INFORMATION_SCHEMA`, object references, logs, and exceptions. Exemptions are explicit reviewed rows; ignore regexes cannot make candidates disappear.

Leak evidence stores canary IDs/hashes, counts, adapter identity, and artifact digests only. It must never copy plaintext, key bytes, capability tokens, ciphertext, provider exception text, or public object URLs into phase evidence.

## Shared Patterns

### Construction and ownership

- Use constructor injection with final collaborators, following `MessageSubmitService.java` lines 36-52.
- Keep orchestration thin and put each semantic rule in one owner: envelope parsing in `EnvelopeCodec`, field crypto in `ProtectedFieldCodec`, key operations behind ports, DB context in explicit persistence adapters, objects in `ProtectedObjectService`, and migration state in `ProtectedDataMigrationRunner`.
- Use package-private implementation details where possible; expose immutable values rather than raw mutable arrays.

### Transactions and concurrency

- Use `@Transactional` on the application orchestration boundary when all JPA work belongs to one unit, following `MessageSubmitService.java` line 54.
- Use `TransactionTemplate` for migration fault hooks and exact checkpoint coupling, following `Phase01MySqlIntegrationTest.java` lines 117-136.
- Reuse JPA `@Version` where entity optimistic locking expresses the claim; migration leases and original-cell digests still require explicit MySQL assertions.

### Errors and logging

- Map internal crypto/provider failures to stable sanitized categories.
- Preserve correlation IDs and logical purpose; omit plaintext, ciphertext, AAD, key material, capability, raw URL, domain object, and arbitrary exception text.
- Authentication/tamper failures expose one category and no wrong-key/wrong-context oracle detail.

### Tests

- Mirror production packages and use JUnit 5 plus AssertJ.
- Use plain tests for codecs, normalization, state machines, manifest classification, and deterministic fault adapters.
- Use Spring/MySQL only for wiring, binary binding, Flyway, transaction, locking, checkpoint, and concurrency claims.
- Keep deterministic, real MySQL, real MinIO, real SoftHSM, and deployment evidence distinctly labeled.
- Use synthetic canaries and test-owned resources; never fixture real phone, credential, tenant, object, or key data.

## Patterns That Must Not Be Reused

| Existing pattern | Source | Why prohibited | Replacement |
| --- | --- | --- | --- |
| Application holds AES key from configuration/environment | `FieldEncryptor.java:26-31`, `application.yml:29-32` | Collapses DEK/KEK separation and permits insecure default wiring | opaque production `KeyProtectionPort` through PKCS#11; test adapter isolated |
| Unversioned `iv || ciphertext || tag` with no AAD | `FieldEncryptor.java:34-46` | Cannot identify key/algorithm/context and permits cross-row substitution | strict `YCSE/v1` envelope with canonical row/object AAD |
| Plain phone saved into `mobile_encrypted` | `MessageSubmitService.java:100` | Column name is not encryption; DB dump exposes protected value | explicit protected persistence adapter before save |
| Raw SHA-256 for phone lookup | `HashUtil.java:16-20` | Small enumerable domain is vulnerable to offline enumeration | context-bound, versioned HMAC blind index with opaque key |
| `String` mapped to `VARBINARY` | `MessageTask.java:36-40`, `V1__init_schema.sql:470-471` | Ambiguous encoding/binding and envelope capacity | binary mapping/value object proven with Connector/J/MySQL |
| Raw business message or throwable logging | `GlobalExceptionHandler.java:23,30` | Can leak protected inputs, aliases, paths, or provider text | safe category/correlation plus tested redaction boundary |
| Latest Flyway migration must equal V1 | `Phase01MySqlIntegrationTest.java:99-105` | Makes every legitimate later migration fail the baseline | immutable V1 checksum plus declared migration-set validation |
| Global next migration is `max + 1` | `next_flyway_version.py:51-60` | Ignores registered owner namespaces and would choose V2 | owner/range-aware first-free selection with collision checks |
| Mock or in-memory adapter described as production proof | no legitimate source pattern | Does not exercise PKCS#11, S3, MySQL, network, provider, or policy semantics | exact real-service lane with identity evidence |
| Public/direct or raw presigned object URL | no existing implementation | Bypasses application authorization/decryption boundary or exposes ciphertext | opaque expiring application capability and private object port |
| Global/context-free JPA converter | no existing implementation | Lacks tenant/field/row identity and can auto-apply too broadly | explicit repository adapter with mandatory `ProtectionContext` |
| Plaintext rollback or edited V1 migration | prohibited by project contract | Reintroduces exposure and violates Flyway immutability | forward repair or verified encrypted snapshot; new V1200+ expand migration |

## No Analog Found

| File/family | Role | Data flow | Planner direction |
| --- | --- | --- | --- |
| `key/pkcs11/*` | provider/config | request-response | Use Java 21 SunPKCS11 contract and real SoftHSM conformance; fail closed |
| `object/*` | provider/service | file-I/O + request-response | Use AWS SDK v2 port/adapter and application capability design from research |
| `migration/*` | service/store | bounded batch | Use research manifest/checkpoint algorithm; borrow only Spring transaction mechanics |
| `logging/*`, `logback-spring.xml` | utility/config | transform | Implement typed redaction and captured-log canary proof from research |
| strict inventory/leak evidence formats | config/utility | batch + transform | Define schemas one-to-one with the four obligations and fail closed |

## Planner Handoff

Plan slices should follow the research dependency order: inventory/namespace, envelope core, production PKCS#11 adapter, persistence adoption, migration bootstrap, protected object storage, redaction/leak proof, then rotation/recovery closure. Each task must cite the analog and the explicit non-reuse constraint above. Real adapter prerequisites remain TODOs until executable evidence exists; no placeholder or mock result can empty them.

## Metadata

**Analog search scope:** `core/src/main`, `core/src/test`, `skills/`, `.planning/tools`, active Phase 3 artifacts  
**Strong analog groups inspected:** crypto primitive/test, message transaction/persistence, MySQL/service harness, centralized exception boundary, Flyway helper/config  
**Source edits:** none  
**Only output:** `.planning/phases/03-crypto-storage-bootstrap/03-PATTERNS.md`
