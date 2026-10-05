package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** Context-bound protected writer for uplink mobile values. */
@Component
public class UplinkRecordProtectionAdapter {

    private static final String LOGICAL_OWNER = "crypto-storage-bootstrap";
    private static final String LOGICAL_TABLE = "uplink_records";
    private static final String CONTENT_ROLE = "mobile_encrypted";
    private static final int MAXIMUM_MOBILE_BYTES = 32;
    private static final int MAXIMUM_MOBILE_ENVELOPE_BYTES = 255;

    private final ProtectedFieldCodec codec;
    private final SecureRandom secureRandom;

    @Autowired
    public UplinkRecordProtectionAdapter(KeyProtectionPort keyProtectionPort,
                                         ActiveFieldKeyReference activeFieldKeyReference) {
        this(new ProtectedFieldCodec(new EnvelopeCodec(), keyProtectionPort,
                new SecureRandom(), activeFieldKeyReference::current), new SecureRandom());
    }

    UplinkRecordProtectionAdapter(ProtectedFieldCodec codec, SecureRandom secureRandom) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public ProtectedUplinkMobile protect(long tenantId, String phoneNumber) {
        if (tenantId < 1 || phoneNumber == null || phoneNumber.isBlank()) {
            throw rejected();
        }
        byte[] plaintext = phoneNumber.trim().getBytes(StandardCharsets.UTF_8);
        byte[] envelope = null;
        try {
            if (plaintext.length < 1 || plaintext.length > MAXIMUM_MOBILE_BYTES) {
                throw rejected();
            }
            long recordId = positiveRandomLong();
            ProtectionContext context = new ProtectionContext(
                    ProtectionContext.Purpose.DATABASE_FIELD,
                    LOGICAL_OWNER, LOGICAL_TABLE, CONTENT_ROLE,
                    "tenant:" + tenantId, "id=" + recordId);
            envelope = codec.protect(plaintext, context, EnvelopeCodec.Target.DATABASE_FIELD);
            if (envelope.length > MAXIMUM_MOBILE_ENVELOPE_BYTES) {
                throw rejected();
            }
            return new ProtectedUplinkMobile(recordId, envelope);
        } catch (RuntimeException failure) {
            throw rejected();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            clear(envelope);
        }
    }

    private long positiveRandomLong() {
        long value;
        do {
            value = secureRandom.nextLong() & Long.MAX_VALUE;
        } while (value == 0);
        return value;
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("UPLINK_RECORD_PROTECTION_FAILED");
    }

    private static void clear(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public record ProtectedUplinkMobile(long recordId, byte[] mobileEnvelope) {
        public ProtectedUplinkMobile {
            if (recordId < 1 || mobileEnvelope == null || mobileEnvelope.length == 0) {
                throw rejected();
            }
            mobileEnvelope = mobileEnvelope.clone();
        }

        @Override
        public byte[] mobileEnvelope() {
            return mobileEnvelope.clone();
        }
    }
}
