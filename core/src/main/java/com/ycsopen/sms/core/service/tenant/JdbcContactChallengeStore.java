package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.envelope.*;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.*;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.function.Function;

/** Row locks serialize attempts and single-use transitions; consume joins the tenant transaction. */
@Repository
public class JdbcContactChallengeStore implements ContactVerificationService.ChallengeStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ProtectedFieldCodec codec;
    private final FieldReferencePublicationFence fence;

    @Autowired
    public JdbcContactChallengeStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                     KeyProtectionPort keys, ActiveFieldKeyReference active,
                                     FieldReferencePublicationFence fence) {
        this(jdbc, manager, new ProtectedFieldCodec(new EnvelopeCodec(), keys, new SecureRandom(), active::current), fence);
    }

    public JdbcContactChallengeStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                     ProtectedFieldCodec codec, FieldReferencePublicationFence fence) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager); this.codec = codec; this.fence = fence;
    }

    @Override public void put(ContactVerificationService.StoredChallenge challenge) {
        byte[] plaintext = challenge.phone().getBytes(StandardCharsets.US_ASCII);
        byte[] encrypted = codec.protect(plaintext, plaintext.length, context(challenge.challengeId()), EnvelopeCodec.Target.DATABASE_FIELD);
        Arrays.fill(plaintext, (byte) 0);
        try {
            transactions.executeWithoutResult(status -> {
                fence.lockAndValidate(encrypted, EnvelopeCodec.Target.DATABASE_FIELD);
                // The FIELD publication fence serializes issuance before this bounded IP check.
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tenant_contact_verification_challenges WHERE request_ip=? AND expires_at>?",
                        Integer.class, challenge.requestIp(), Timestamp.from(challenge.expiresAt().minus(ContactVerificationService.CHALLENGE_TTL)));
                if (count != null && count >= 5) throw new ContactVerificationService.ChallengeFailure("CONTACT_VERIFICATION_RATE_LIMITED");
                jdbc.update("DELETE FROM tenant_contact_verification_challenges WHERE expires_at < ?", Timestamp.from(java.time.Instant.now().minusSeconds(86400)));
                jdbc.update("INSERT INTO tenant_contact_verification_challenges (challenge_id,phone_encrypted,code_hash,expires_at,request_ip) VALUES (?,?,?,?,?)",
                        challenge.challengeId(), encrypted, challenge.codeHash(), Timestamp.from(challenge.expiresAt()), challenge.requestIp());
            });
        } finally { Arrays.fill(encrypted, (byte) 0); }
    }

    @Override public String locked(String id, Function<ContactVerificationService.StoredChallenge, String> operation) {
        if (id == null || !id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) return "CONTACT_VERIFICATION_INVALID";
        return transactions.execute(status -> {
            var rows = jdbc.query("SELECT * FROM tenant_contact_verification_challenges WHERE challenge_id=? FOR UPDATE", (rs, index) -> {
                byte[] phone = codec.unprotect(rs.getBytes("phone_encrypted"), context(id), EnvelopeCodec.Target.DATABASE_FIELD);
                try {
                    return new ContactVerificationService.StoredChallenge(id, new String(phone, StandardCharsets.US_ASCII),
                            rs.getString("code_hash"), rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempt_count"),
                            rs.getTimestamp("verified_at") != null, rs.getTimestamp("consumed_at") != null, rs.getString("request_ip"));
                } finally { Arrays.fill(phone, (byte) 0); }
            }, id);
            if (rows.isEmpty()) return operation.apply(null);
            var challenge = rows.getFirst();
            String failure = operation.apply(challenge);
            jdbc.update("UPDATE tenant_contact_verification_challenges SET attempt_count=?, verified_at=CASE WHEN ? THEN COALESCE(verified_at,CURRENT_TIMESTAMP) ELSE verified_at END, consumed_at=CASE WHEN ? THEN COALESCE(consumed_at,CURRENT_TIMESTAMP) ELSE consumed_at END WHERE challenge_id=?",
                    challenge.attempts(), challenge.verified(), challenge.consumed(), id);
            return failure;
        });
    }

    private static ProtectionContext context(String id) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD, "tenant-qualification",
                "tenant_contact_verification_challenges", "phone_encrypted", "global", id);
    }
}
