package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataManifest;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldContexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = "spring.flyway.enabled=false")
class PlatformAccountPhoneStoreTest {

    private static final String KEY_REFERENCE = "test-kek-v1";

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TABLE IF EXISTS users");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, phone_encrypted VARBINARY(255))");
        jdbc.update("INSERT INTO users(id, phone_encrypted) VALUES (42, NULL)");
    }

    @Test
    void realCodecAndJdbcRoundTripUseTheMigrationAuthenticatedData() throws Exception {
        var keyPort = new CapturingKeyPort();
        var codec = new ProtectedFieldCodec(
                new EnvelopeCodec(), keyPort, new SecureRandom(), KEY_REFERENCE);
        var publicationChecked = new AtomicBoolean();
        var store = new PlatformAccountPhoneStore(codec, (envelope, target) -> {
            assertThat(envelope).isNotEmpty();
            assertThat(target).isEqualTo(EnvelopeCodec.Target.DATABASE_FIELD);
            publicationChecked.set(true);
            return 1L;
        }, jdbc);

        store.store(42L, "13800138000");

        byte[] persisted = jdbc.queryForObject(
                "SELECT phone_encrypted FROM users WHERE id = 42", byte[].class);
        assertThat(publicationChecked).isTrue();
        assertThat(persisted).isNotNull();
        assertThat(indexOf(persisted, "13800138000".getBytes(StandardCharsets.US_ASCII)))
                .isNegative();
        assertThat(store.masked(42L)).isEqualTo("138****8000");

        var target = manifest().requireTarget("users.phone_encrypted");
        byte[] plaintext = codec.unprotect(
                persisted,
                ProtectedFieldContexts.migration(target, "global", "42"),
                EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(new String(plaintext, StandardCharsets.US_ASCII)).isEqualTo("13800138000");
    }

    private static ProtectedDataManifest manifest() throws Exception {
        byte[] bytes;
        try (var input = PlatformAccountPhoneStoreTest.class.getResourceAsStream(
                "/security/protected-data-inventory.json")) {
            bytes = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        return ProtectedDataManifest.load(
                new ByteArrayInputStream(bytes), ProtectedDataManifest.canonicalDigest(bytes));
    }

    private static int indexOf(byte[] value, byte[] candidate) {
        outer:
        for (int start = 0; start <= value.length - candidate.length; start++) {
            for (int offset = 0; offset < candidate.length; offset++) {
                if (value[start + offset] != candidate[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }

    /**
     * Keeps key-provider behavior opaque while exercising the production envelope codec and JDBC
     * store. The codec remains responsible for data encryption and authenticated context checks.
     */
    private static final class CapturingKeyPort implements KeyProtectionPort {
        private final AtomicReference<byte[]> dataEncryptionKey = new AtomicReference<>();

        @Override
        public WrappedDataKey wrap(byte[] key, byte[] header,
                                   com.ycsopen.sms.core.common.security.envelope.ProtectionContext context) {
            dataEncryptionKey.set(key.clone());
            return new WrappedDataKey(KEY_REFERENCE, new byte[12], new byte[48]);
        }

        @Override
        public byte[] unwrap(WrappedDataKey wrapped, byte[] header,
                             com.ycsopen.sms.core.common.security.envelope.ProtectionContext context) {
            return dataEncryptionKey.get().clone();
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }
    }
}
