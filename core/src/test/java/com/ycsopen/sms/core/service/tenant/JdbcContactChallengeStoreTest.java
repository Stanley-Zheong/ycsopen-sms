package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.envelope.*;
import com.ycsopen.sms.core.common.security.key.*;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.notification.provider.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class JdbcContactChallengeStoreTest {
    JdbcTemplate jdbc;
    TransactionTemplate transactions;
    ContactVerificationService service;
    AtomicReference<String> delivered = new AtomicReference<>();

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tenant_contact_verification_challenges (challenge_id CHAR(36) PRIMARY KEY, phone_encrypted VARBINARY(255) NOT NULL, code_hash VARCHAR(100) NOT NULL, expires_at TIMESTAMP NOT NULL, attempt_count INT DEFAULT 0, verified_at TIMESTAMP, consumed_at TIMESTAMP, request_ip VARCHAR(45), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var keys = new KeyProtectionPort() {
            final Map<ProtectionContext, byte[]> values = new ConcurrentHashMap<>();
            public WrappedDataKey wrap(byte[] key, byte[] header, ProtectionContext context) {
                values.put(context, key.clone());
                return new WrappedDataKey("field-kek.v1", new byte[12], new byte[48]);
            }
            public byte[] unwrap(WrappedDataKey wrapped, byte[] header, ProtectionContext context) { return values.get(context).clone(); }
            public KeyHealth health() { return new KeyHealth(KeyHealth.Status.READY); }
        };
        var codec = new ProtectedFieldCodec(new EnvelopeCodec(), keys, new SecureRandom(), "field-kek.v1");
        var store = new JdbcContactChallengeStore(jdbc, transactions.getTransactionManager(), codec, (bytes, target) -> 1L);
        service = new ContactVerificationService(new PlatformMessageBootstrapService(request -> {
            delivered.set(request.content().replaceAll("[^0-9]", ""));
            return PlatformNotificationSpi.DeliveryOutcome.accepted("accepted");
        }), store, Clock.systemUTC(), new SecureRandom());
    }

    @Test void storesOnlyEncryptedPhoneAndBcryptCodeAndCommitsFailedAttempts() {
        var receipt = service.request("13800138000", "127.0.0.1");
        var row = jdbc.queryForMap("select * from tenant_contact_verification_challenges");
        assertThat(new String((byte[]) row.get("PHONE_ENCRYPTED"), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("13800138000");
        assertThat(row.get("CODE_HASH").toString()).startsWith("$2").isNotEqualTo(delivered.get());
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.verify(receipt.challengeId(), "13800138000", "wrong")).hasMessage("CONTACT_VERIFICATION_INVALID");
        assertThat(jdbc.queryForObject("select attempt_count from tenant_contact_verification_challenges", Integer.class)).isEqualTo(5);
        assertThatThrownBy(() -> service.verify(receipt.challengeId(), "13800138000", delivered.get())).hasMessage("CONTACT_VERIFICATION_INVALID");
    }

    @Test void consumeJoinsTheRegistrationTransactionAndRollsBackWithIt() {
        var receipt = verified();
        assertThatThrownBy(() -> transactions.execute(status -> {
            service.consumeVerified(receipt.challengeId(), "13800138000");
            throw new IllegalStateException("registration failed");
        })).hasMessage("registration failed");
        service.consumeVerified(receipt.challengeId(), "13800138000");
        assertThatThrownBy(() -> service.consumeVerified(receipt.challengeId(), "13800138000")).hasMessage("CONTACT_VERIFICATION_ALREADY_CONSUMED");
    }

    @Test void exactlyOneConcurrentConsumerWins() throws Exception {
        var receipt = verified();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> consume = () -> { start.await(); try { service.consumeVerified(receipt.challengeId(), "13800138000"); return true; } catch (ContactVerificationService.ChallengeFailure e) { return false; } };
            var first = executor.submit(consume); var second = executor.submit(consume); start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void limitsChallengeIssuanceBeforeSendingMoreCodes() {
        for (int i = 0; i < 5; i++) service.request("13800138000", "127.0.0.1");
        delivered.set(null);
        assertThatThrownBy(() -> service.request("13800138000", "127.0.0.1")).hasMessage("CONTACT_VERIFICATION_RATE_LIMITED");
        assertThat(delivered.get()).isNull();
    }

    @Test void challengeEnvelopeKeepsItsKeyVisibleToRetirementInventory() {
        service.request("13800138000", "127.0.0.1");
        jdbc.execute("CREATE TABLE ycs_crypto_key_references (key_version BIGINT, provider_id VARCHAR(30), provider_key_reference VARCHAR(64), purpose VARCHAR(64))");
        jdbc.update("INSERT INTO ycs_crypto_key_references VALUES (1,'pkcs11','field-kek.v1','FIELD_ENCRYPTION_KEK')");
        var source = com.ycsopen.sms.core.common.security.key.lifecycle.EnvelopeReferenceInventory.jdbcMetadataSources(jdbc).stream()
                .filter(value -> value.sourceId().equals("CONTACT_CHALLENGE_ENVELOPES")).findFirst();
        assertThat(source).isPresent();
        assertThat(source.orElseThrow().liveReferences()).hasSize(1);
    }

    private ContactVerificationService.ChallengeReceipt verified() {
        var receipt = service.request("13800138000", "127.0.0.1");
        service.verify(receipt.challengeId(), "13800138000", delivered.get());
        return receipt;
    }
}
