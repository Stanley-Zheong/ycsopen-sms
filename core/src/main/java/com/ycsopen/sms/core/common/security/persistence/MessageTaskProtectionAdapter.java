package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Sole context-bound preparation, protected assignment and persistence owner for message mobiles.
 */
@Component
public class MessageTaskProtectionAdapter {

    public static final String SANITIZED_FAILURE = "PROTECTED_MESSAGE_PERSISTENCE_FAILED";

    private static final String LOGICAL_OWNER = "crypto-storage-bootstrap";
    private static final String LOGICAL_TABLE = "message_tasks";
    private static final String CONTENT_ROLE = "mobile_encrypted";
    private static final String TARGET_TYPE = "MESSAGE_TASK";
    private static final String BLACKLIST_TARGET_TYPE = "BLACKLIST_ENTRY";
    private static final String PORTABILITY_TARGET_TYPE = "MOBILE_PORTABILITY";
    private static final String INDEX_FIELD = "mobile";
    private static final Pattern MESSAGE_ID =
            Pattern.compile("MSG_[0-9]{1,19}_[A-Z0-9]{8}");
    private static final Pattern NORMALIZED_MOBILE = Pattern.compile("1[3-9][0-9]{9}");
    private static final int MOBILE_PLAINTEXT_BYTES = 11;
    private static final int MAXIMUM_MOBILE_ENVELOPE_BYTES = 156;
    private static final AssignmentPermit ASSIGNMENT_PERMIT = new AssignmentPermit();

    private final ProtectedFieldCodec protectedFieldCodec;
    private final BlindIndexPort blindIndexPort;
    private final MessageTaskRepository messageTaskRepository;
    private final BlindIndexMetadataRepository blindIndexMetadataRepository;
    private final SecureRandom secureRandom;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public MessageTaskProtectionAdapter(KeyProtectionPort keyProtectionPort,
                                        BlindIndexPort blindIndexPort,
                                        MessageTaskRepository messageTaskRepository,
                                        BlindIndexMetadataRepository blindIndexMetadataRepository,
                                        PlatformTransactionManager transactionManager,
                                        ActiveFieldKeyReference activeFieldKeyReference) {
        this(new ProtectedFieldCodec(new EnvelopeCodec(), keyProtectionPort,
                        new SecureRandom(), activeFieldKeyReference::current),
                blindIndexPort, messageTaskRepository, blindIndexMetadataRepository,
                new SecureRandom(), transactionManager);
    }

    public MessageTaskProtectionAdapter(ProtectedFieldCodec protectedFieldCodec,
                                        BlindIndexPort blindIndexPort,
                                        MessageTaskRepository messageTaskRepository,
                                        BlindIndexMetadataRepository blindIndexMetadataRepository,
                                        SecureRandom secureRandom,
                                        PlatformTransactionManager transactionManager) {
        this.protectedFieldCodec = Objects.requireNonNull(protectedFieldCodec, "protectedFieldCodec");
        this.blindIndexPort = Objects.requireNonNull(blindIndexPort, "blindIndexPort");
        this.messageTaskRepository = Objects.requireNonNull(messageTaskRepository, "messageTaskRepository");
        this.blindIndexMetadataRepository = Objects.requireNonNull(
                blindIndexMetadataRepository, "blindIndexMetadataRepository");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    /** Performs all key-dependent work before routing or database mutation. */
    public PreparedMessageMobile prepare(Long tenantId,
                                         String messageId,
                                         String normalizedMobile) {
        long checkedTenantId = requireTenantId(tenantId);
        String checkedMessageId = requireMessageId(messageId);
        String checkedMobile = requireMobile(normalizedMobile);
        byte[] plaintext = mobileAscii(checkedMobile);
        byte[] envelope = null;
        byte[] historicalDigest = null;
        try {
            String tenantScope = "tenant:" + checkedTenantId;
            ProtectionContext fieldContext = new ProtectionContext(
                    ProtectionContext.Purpose.DATABASE_FIELD,
                    LOGICAL_OWNER, LOGICAL_TABLE, CONTENT_ROLE,
                    tenantScope, "message_id=" + checkedMessageId);
            BlindIndexPort.Context indexContext = new BlindIndexPort.Context(
                    TARGET_TYPE, INDEX_FIELD, BlindIndexPort.Purpose.MOBILE_ROUTING, tenantScope);

            BlindIndexPort.OrderedIndexes writeIndexes =
                    blindIndexPort.writeIndexes(checkedMobile, indexContext);
            BlindIndexPort.OrderedIndexes queryIndexes =
                    blindIndexPort.queryIndexes(checkedMobile, indexContext);
            BlindIndexPort.OrderedIndexes blacklistIndexes = blindIndexPort.queryIndexes(
                    checkedMobile, new BlindIndexPort.Context(
                            BLACKLIST_TARGET_TYPE, INDEX_FIELD,
                            BlindIndexPort.Purpose.MOBILE_ROUTING, tenantScope));
            BlindIndexPort.OrderedIndexes portabilityIndexes = blindIndexPort.queryIndexes(
                    checkedMobile, new BlindIndexPort.Context(
                            PORTABILITY_TARGET_TYPE, INDEX_FIELD,
                            BlindIndexPort.Purpose.MOBILE_ROUTING, "global"));
            historicalDigest = sha256(plaintext);
            LegacyMobileLookupToken legacyLookupToken = new LegacyMobileLookupToken(
                    historicalDigest, blacklistIndexes, portabilityIndexes);
            envelope = protectedFieldCodec.protect(
                    plaintext, fieldContext, EnvelopeCodec.Target.DATABASE_FIELD);
            if (envelope.length > MAXIMUM_MOBILE_ENVELOPE_BYTES) {
                throw new IllegalStateException(SANITIZED_FAILURE);
            }

            String locator = MessageTaskRowBinding.issueCurrentLocator(secureRandom);
            return new PreparedMessageMobile(
                    checkedTenantId, checkedMessageId, envelope, locator,
                    writeIndexes, queryIndexes, legacyLookupToken);
        } catch (RuntimeException failure) {
            throw sanitized();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            clear(envelope);
            clear(historicalDigest);
        }
    }

    /** Saves the binary entity and all write-compatible index versions in one transaction. */
    @Transactional
    public MessageTask save(MessageTask task, PreparedMessageMobile prepared) {
        requireMatchingIdentity(task, prepared);
        Long originalId = task.getId();
        try {
            MessageTask saved = transactionTemplate.execute(status -> {
                task.assignPreparedMobile(prepared, ASSIGNMENT_PERMIT);
                MessageTask persisted = messageTaskRepository.saveAndFlush(task);
                if (persisted.getId() == null
                        || !prepared.messageId().equals(persisted.getMessageId())
                        || !Long.valueOf(prepared.tenantId()).equals(persisted.getTenantId())) {
                    throw new IllegalStateException(SANITIZED_FAILURE);
                }
                blindIndexMetadataRepository.insertMessageTaskIndexes(persisted, prepared);
                return persisted;
            });
            if (saved == null) {
                throw new IllegalStateException(SANITIZED_FAILURE);
            }
            return saved;
        } catch (RuntimeException failure) {
            task.clearPreparedMobile(prepared, ASSIGNMENT_PERMIT);
            if (originalId == null) {
                task.setId(null);
            }
            throw sanitized();
        }
    }

    private static long requireTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("tenant identity is required");
        }
        return tenantId;
    }

    private static String requireMessageId(String messageId) {
        if (messageId == null || !MESSAGE_ID.matcher(messageId).matches()) {
            throw new IllegalArgumentException("generated message identity is required");
        }
        return messageId;
    }

    private static String requireMobile(String normalizedMobile) {
        if (normalizedMobile == null || !NORMALIZED_MOBILE.matcher(normalizedMobile).matches()) {
            throw new IllegalArgumentException("normalized mobile is required");
        }
        return normalizedMobile;
    }

    private static byte[] mobileAscii(String mobile) {
        byte[] bytes = new byte[MOBILE_PLAINTEXT_BYTES];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) mobile.charAt(index);
        }
        return bytes;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(SANITIZED_FAILURE);
        }
    }

    private static void requireMatchingIdentity(MessageTask task, PreparedMessageMobile prepared) {
        if (task == null || prepared == null || task.getId() != null
                || !Long.valueOf(prepared.tenantId()).equals(task.getTenantId())
                || !prepared.messageId().equals(task.getMessageId())
                || task.hasPreparedMobile()) {
            throw sanitized();
        }
    }

    private static IllegalStateException sanitized() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    /** Unforgeable cross-package permit that keeps protected entity mutation adapter-owned. */
    public static final class AssignmentPermit {
        private AssignmentPermit() {
        }
    }
}
