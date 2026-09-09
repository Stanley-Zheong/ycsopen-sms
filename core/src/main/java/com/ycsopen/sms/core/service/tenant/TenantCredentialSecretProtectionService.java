package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Shared Phase 03 envelope boundary for tenant-managed credential values. */
@Service
public class TenantCredentialSecretProtectionService {
    private final ProtectedFieldCodec codec;
    public TenantCredentialSecretProtectionService(KeyProtectionPort keys, ActiveFieldKeyReference active) {
        codec = new ProtectedFieldCodec(new EnvelopeCodec(), keys, new SecureRandom(), active::current);
    }
    public byte[] protect(long tenantId, long credentialId, String field, char[] value) {
        byte[] plaintext = new String(value).getBytes(StandardCharsets.UTF_8);
        try {
            return codec.protect(plaintext, new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                    "tenant-access-administration", "tenant_credentials", field,
                    "tenant:" + tenantId, "id=" + credentialId), EnvelopeCodec.Target.DATABASE_FIELD);
        } finally { java.util.Arrays.fill(plaintext, (byte) 0); }
    }

    public char[] reveal(long tenantId, long credentialId, String field, byte[] envelope) {
        byte[] plaintext = null;
        try {
            plaintext = codec.unprotect(envelope, new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                    "tenant-access-administration", "tenant_credentials", field,
                    "tenant:" + tenantId, "id=" + credentialId), EnvelopeCodec.Target.DATABASE_FIELD);
            return new String(plaintext, StandardCharsets.UTF_8).toCharArray();
        } finally {
            if (plaintext != null) java.util.Arrays.fill(plaintext, (byte) 0);
        }
    }
}
