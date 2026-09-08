package com.ycsopen.sms.core.service.channel;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class ChannelCredentialProtectionService implements ChannelSecretProtector {
    private final ProtectedFieldCodec codec;

    public ChannelCredentialProtectionService(KeyProtectionPort keys, ActiveFieldKeyReference active) {
        this.codec = new ProtectedFieldCodec(new EnvelopeCodec(), keys, new SecureRandom(), active::current);
    }

    @Override
    public byte[] protect(long channelId, String field, char[] value) {
        byte[] plaintext = new String(value).getBytes(StandardCharsets.UTF_8);
        try {
            return codec.protect(plaintext, new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                    "channel-configuration-lifecycle", "channels", field, "global",
                    "id=" + channelId), EnvelopeCodec.Target.DATABASE_FIELD);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(value, '\0');
        }
    }
}
