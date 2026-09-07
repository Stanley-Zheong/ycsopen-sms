package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.lifecycle.FieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldContexts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** Sole plaintext boundary for the protected {@code users.phone_encrypted} column. */
@Service
public class PlatformAccountPhoneStore {

    private final ProtectedFieldCodec codec;
    private final FieldReferencePublicationFence fieldFence;
    private final JdbcTemplate jdbc;

    @Autowired
    public PlatformAccountPhoneStore(KeyProtectionPort keys,
                                     ActiveFieldKeyReference activeFieldKey,
                                     FieldReferencePublicationFence fieldFence,
                                     JdbcTemplate jdbc) {
        this(new ProtectedFieldCodec(new EnvelopeCodec(), keys, new SecureRandom(), activeFieldKey::current),
                fieldFence, jdbc);
    }

    PlatformAccountPhoneStore(ProtectedFieldCodec codec,
                              FieldReferencePublicationFence fieldFence,
                              JdbcTemplate jdbc) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.fieldFence = Objects.requireNonNull(fieldFence, "fieldFence");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public void store(long userId, String phone) {
        byte[] plaintext = phone.getBytes(StandardCharsets.US_ASCII);
        byte[] envelope = null;
        try {
            envelope = codec.protect(plaintext, context(userId), EnvelopeCodec.Target.DATABASE_FIELD);
            fieldFence.lockAndValidate(envelope, EnvelopeCodec.Target.DATABASE_FIELD);
            jdbc.update("UPDATE users SET phone_encrypted = ? WHERE id = ?", envelope, userId);
        } catch (RuntimeException failure) {
            throw unavailable();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            if (envelope != null) {
                Arrays.fill(envelope, (byte) 0);
            }
        }
    }

    public String masked(long userId) {
        byte[] envelope = null;
        byte[] plaintext = null;
        try {
            envelope = jdbc.queryForObject(
                    "SELECT phone_encrypted FROM users WHERE id = ?", byte[].class, userId);
            if (envelope == null) {
                return null;
            }
            plaintext = codec.unprotect(envelope, context(userId), EnvelopeCodec.Target.DATABASE_FIELD);
            String phone = new String(plaintext, StandardCharsets.US_ASCII);
            if (!phone.matches("1[3-9]\\d{9}")) {
                throw unavailable();
            }
            return phone.substring(0, 3) + "****" + phone.substring(7);
        } catch (RuntimeException failure) {
            throw unavailable();
        } finally {
            if (envelope != null) {
                Arrays.fill(envelope, (byte) 0);
            }
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    private static ProtectionContext context(long userId) {
        try {
            return ProtectedFieldContexts.usersPhone(userId, null);
        } catch (IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException("ACCOUNT_PHONE_PROTECTION_UNAVAILABLE",
                "账号手机号保护服务不可用");
    }
}
