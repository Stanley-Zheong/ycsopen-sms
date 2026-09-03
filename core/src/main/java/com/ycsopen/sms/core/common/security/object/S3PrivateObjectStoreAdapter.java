package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.Grantee;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Type;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** AWS SDK v2 adapter for a deny-by-default bucket containing YCSE/v1 ciphertext only. */
public final class S3PrivateObjectStoreAdapter implements PrivateObjectStorePort, AutoCloseable {
    private static final Pattern STORAGE_KEY = Pattern.compile("obj_v1_[0-9a-f]{64}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String PURPOSE = "purpose";
    private static final String ENVELOPE_LENGTH = "envelope-length";
    private static final String SHA256_METADATA = "sha256";
    private static final Set<String> EXACT_METADATA = Set.of(PURPOSE, ENVELOPE_LENGTH, SHA256_METADATA);
    private static final Set<String> IMAGE_MEDIA = Set.of("image/jpeg", "image/png");
    private static final Set<String> DOCUMENT_MEDIA = Set.of("application/pdf", "image/jpeg", "image/png");

    private final S3Client s3Client;
    private final ObjectStoreProperties properties;
    private final EnvelopeCodec envelopeCodec;
    private final SecureRandom secureRandom;
    private final boolean ownsClient;
    private final AtomicBoolean closed = new AtomicBoolean();

    public S3PrivateObjectStoreAdapter(S3Client s3Client,
                                       ObjectStoreProperties properties,
                                       EnvelopeCodec envelopeCodec,
                                       SecureRandom secureRandom) {
        this(s3Client, properties, envelopeCodec, secureRandom, false);
    }

    S3PrivateObjectStoreAdapter(S3Client s3Client,
                                ObjectStoreProperties properties,
                                EnvelopeCodec envelopeCodec,
                                SecureRandom secureRandom,
                                boolean ownsClient) {
        if (s3Client == null || properties == null || envelopeCodec == null || secureRandom == null
                || !properties.enabled()) {
            throw Failure.invalidInput();
        }
        this.s3Client = s3Client;
        this.properties = properties;
        this.envelopeCodec = envelopeCodec;
        this.secureRandom = secureRandom;
        this.ownsClient = ownsClient;
    }

    /** Builds the production client from an allowlisted endpoint and an indirect credential provider. */
    public static S3PrivateObjectStoreAdapter create(ObjectStoreProperties properties) {
        if (properties == null || !properties.enabled()) {
            throw Failure.invalidInput();
        }
        try {
            AwsCredentialsProvider credentials = switch (properties.credentialProvider()) {
                case DEFAULT_CHAIN -> DefaultCredentialsProvider.create();
                case CONTAINER -> ContainerCredentialsProvider.builder().build();
                case INSTANCE_PROFILE -> InstanceProfileCredentialsProvider.create();
            };
            var builder = S3Client.builder()
                    .region(Region.of(properties.region()))
                    .credentialsProvider(credentials)
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(properties.pathStyleAccess())
                            .build());
            URI endpoint = properties.endpoint();
            if (endpoint != null) {
                builder.endpointOverride(endpoint);
            }
            return new S3PrivateObjectStoreAdapter(
                    builder.build(), properties, new EnvelopeCodec(), new SecureRandom(), true);
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
    }

    @Override
    public StoredObjectMetadata put(ObjectPurpose purpose,
                                    String mediaType,
                                    InputStream ciphertext,
                                    Long declaredContentLength) {
        requirePurpose(purpose);
        requireMediaType(purpose, mediaType);
        requireDeclaredLength(purpose, declaredContentLength);
        byte[] encoded = canonicalEnvelope(ciphertext, declaredContentLength, purpose);
        String storageKey = newStorageKey();
        String checksum = sha256Hex(encoded);
        String checksumBase64 = checksumBase64(checksum);
        Map<String, String> metadata = Map.of(
                PURPOSE, purpose.name(),
                ENVELOPE_LENGTH, Long.toString(encoded.length),
                SHA256_METADATA, checksum);

        ensurePrivateBucket();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .contentLength((long) encoded.length)
                    .contentType(mediaType)
                    .checksumSHA256(checksumBase64)
                    .metadata(metadata)
                    .build();
            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(encoded));
            if (response.checksumSHA256() != null
                    && !MessageDigest.isEqual(
                    checksumBase64.getBytes(StandardCharsets.US_ASCII),
                    response.checksumSHA256().getBytes(StandardCharsets.US_ASCII))) {
                deleteAfterRejectedPut(storageKey);
                throw Failure.integrity();
            }
            return new StoredObjectMetadata(storageKey, purpose, encoded.length, checksum, mediaType);
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            deleteAfterRejectedPut(storageKey);
            throw Failure.unavailable();
        }
    }

    @Override
    public StoredCiphertext get(String storageKey, ObjectPurpose purpose) {
        StoredObjectMetadata expected = head(storageKey, purpose);
        ResponseInputStream<GetObjectResponse> response = null;
        try {
            response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .checksumMode(ChecksumMode.ENABLED)
                    .build());
            validateGetResponse(response.response(), expected);
            byte[] ciphertext = readExact(response, expected.size());
            verifyEnvelope(ciphertext, purpose);
            requireChecksum(ciphertext, expected.sha256());
            response.close();
            return new StoredCiphertext(ciphertext, expected);
        } catch (Failure failure) {
            abort(response);
            throw failure;
        } catch (IOException | RuntimeException failure) {
            abort(response);
            throw Failure.unavailable();
        }
    }

    @Override
    public StoredObjectMetadata head(String storageKey, ObjectPurpose purpose) {
        requireStorageKey(storageKey);
        requirePurpose(purpose);
        ensurePrivateBucket();
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .checksumMode(ChecksumMode.ENABLED)
                    .build());
            return validateMetadata(storageKey, purpose, response.contentLength(), response.contentType(),
                    response.metadata(), response.checksumSHA256(), response.contentDisposition(),
                    response.contentEncoding(), response.websiteRedirectLocation());
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
    }

    @Override
    public void delete(String storageKey, ObjectPurpose purpose) {
        requireStorageKey(storageKey);
        requirePurpose(purpose);
        ensurePrivateBucket();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .build());
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
    }

    private byte[] canonicalEnvelope(InputStream ciphertext, Long declaredLength, ObjectPurpose purpose) {
        try {
            CipherEnvelope envelope = envelopeCodec.decode(ciphertext, declaredLength, purpose.envelopeTarget());
            return envelopeCodec.encode(envelope, purpose.envelopeTarget());
        } catch (RuntimeException failure) {
            throw Failure.invalidInput();
        }
    }

    private void verifyEnvelope(byte[] ciphertext, ObjectPurpose purpose) {
        try {
            envelopeCodec.decode(ciphertext, purpose.envelopeTarget());
        } catch (RuntimeException failure) {
            throw Failure.integrity();
        }
    }

    private void ensurePrivateBucket() {
        try {
            var acl = s3Client.getBucketAcl(GetBucketAclRequest.builder()
                    .bucket(properties.bucket()).build());
            String ownerId = acl.owner() == null ? null : acl.owner().id();
            int ownerFullControlGrants = 0;
            boolean invalidGrant = acl.owner() == null;
            for (Grant grant : acl.grants()) {
                if (!isExactOwnerFullControl(grant, ownerId)) {
                    invalidGrant = true;
                } else {
                    ownerFullControlGrants++;
                }
            }
            if (invalidGrant || ownerFullControlGrants != 1) {
                throw Failure.invalidPolicy();
            }
            try {
                String policy = s3Client.getBucketPolicy(GetBucketPolicyRequest.builder()
                        .bucket(properties.bucket()).build()).policy();
                if (policy != null && !policy.isBlank()) {
                    throw Failure.invalidPolicy();
                }
            } catch (S3Exception noPolicy) {
                if (!isMissingPolicy(noPolicy)) {
                    throw noPolicy;
                }
                // Absence is the admitted bucket-policy state; access comes from the application identity.
            }
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw Failure.unavailable();
        }
    }

    private static boolean isExactOwnerFullControl(Grant grant, String ownerId) {
        Grantee grantee = grant == null ? null : grant.grantee();
        if (grantee == null || grantee.type() != Type.CANONICAL_USER
                || grant.permission() != Permission.FULL_CONTROL) {
            return false;
        }
        String granteeId = grantee.id();
        if (ownerId == null || ownerId.isBlank()) {
            // MinIO's S3-compatible default ACL omits both canonical IDs. It remains admissible
            // only as the sole canonical FULL_CONTROL grant; any identifiable foreign grant fails.
            return granteeId == null || granteeId.isBlank();
        }
        return ownerId.equals(granteeId);
    }

    private static boolean isMissingPolicy(S3Exception failure) {
        return failure.statusCode() == 404
                && failure.awsErrorDetails() != null
                && "NoSuchBucketPolicy".equals(failure.awsErrorDetails().errorCode());
    }

    private StoredObjectMetadata validateMetadata(String storageKey,
                                                  ObjectPurpose purpose,
                                                  Long contentLength,
                                                  String mediaType,
                                                  Map<String, String> metadata,
                                                  String serviceChecksum,
                                                  String contentDisposition,
                                                  String contentEncoding,
                                                  String redirectLocation) {
        Map<String, String> normalizedMetadata = normalizeMetadata(metadata);
        if (contentLength == null || contentLength < EnvelopeCodec.FIXED_HEADER_BYTES
                || contentLength > purpose.maximumEnvelopeBytes()
                || !normalizedMetadata.keySet().equals(EXACT_METADATA)
                || !purpose.name().equals(normalizedMetadata.get(PURPOSE))
                || !Long.toString(contentLength).equals(normalizedMetadata.get(ENVELOPE_LENGTH))
                || !SHA256.matcher(value(normalizedMetadata.get(SHA256_METADATA))).matches()
                || contentDisposition != null || contentEncoding != null || redirectLocation != null) {
            throw Failure.integrity();
        }
        requireMediaType(purpose, mediaType);
        String checksum = normalizedMetadata.get(SHA256_METADATA);
        if (serviceChecksum != null && !MessageDigest.isEqual(
                checksumBase64(checksum).getBytes(StandardCharsets.US_ASCII),
                serviceChecksum.getBytes(StandardCharsets.US_ASCII))) {
            throw Failure.integrity();
        }
        return new StoredObjectMetadata(storageKey, purpose, contentLength, checksum, mediaType);
    }

    private static Map<String, String> normalizeMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            throw Failure.integrity();
        }
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue()) != null) {
                throw Failure.integrity();
            }
        }
        return Map.copyOf(normalized);
    }

    private void validateGetResponse(GetObjectResponse response, StoredObjectMetadata expected) {
        StoredObjectMetadata actual = validateMetadata(expected.storageKey(), expected.purpose(),
                response.contentLength(), response.contentType(), response.metadata(), response.checksumSHA256(),
                response.contentDisposition(), response.contentEncoding(), response.websiteRedirectLocation());
        if (actual.size() != expected.size()
                || !MessageDigest.isEqual(
                actual.sha256().getBytes(StandardCharsets.US_ASCII),
                expected.sha256().getBytes(StandardCharsets.US_ASCII))
                || !actual.mediaType().equals(expected.mediaType())) {
            throw Failure.integrity();
        }
    }

    private static byte[] readExact(InputStream input, long expectedLength) throws IOException {
        int expected = Math.toIntExact(expectedLength);
        byte[] bytes = new byte[expected];
        int offset = 0;
        while (offset < expected) {
            int count = input.read(bytes, offset, expected - offset);
            if (count == -1) {
                throw Failure.integrity();
            }
            if (count == 0) {
                int one = input.read();
                if (one == -1) {
                    throw Failure.integrity();
                }
                bytes[offset++] = (byte) one;
            } else {
                offset += count;
            }
        }
        if (input.read() != -1) {
            throw Failure.integrity();
        }
        return bytes;
    }

    private static void requireChecksum(byte[] ciphertext, String expected) {
        if (!MessageDigest.isEqual(
                sha256Hex(ciphertext).getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII))) {
            throw Failure.integrity();
        }
    }

    private static void requireDeclaredLength(ObjectPurpose purpose, Long declaredLength) {
        if (declaredLength != null && (declaredLength < EnvelopeCodec.FIXED_HEADER_BYTES
                || declaredLength > purpose.maximumEnvelopeBytes()
                || declaredLength > 0xffff_ffffL)) {
            throw Failure.invalidInput();
        }
    }

    private static void requireStorageKey(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw Failure.invalidInput();
        }
    }

    private static void requirePurpose(ObjectPurpose purpose) {
        if (purpose == null) {
            throw Failure.invalidInput();
        }
    }

    private static void requireMediaType(ObjectPurpose purpose, String mediaType) {
        Set<String> admitted = switch (purpose) {
            case REPRESENTATIVE_ID_FRONT, REPRESENTATIVE_ID_BACK -> IMAGE_MEDIA;
            case BUSINESS_LICENSE, SHORT_LINK_DOMAIN_PROOF, TRADEMARK_PROOF -> DOCUMENT_MEDIA;
        };
        if (mediaType == null || !admitted.contains(mediaType)) {
            throw Failure.invalidInput();
        }
    }

    private String newStorageKey() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return "obj_v1_" + HexFormat.of().formatHex(random);
    }

    private void deleteAfterRejectedPut(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket()).key(storageKey).build());
        } catch (RuntimeException ignored) {
            // The higher object service reconciles a split write by operation identity.
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("required digest unavailable", impossible);
        }
    }

    private static String checksumBase64(String hexChecksum) {
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hexChecksum));
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }

    private static void abort(ResponseInputStream<?> response) {
        if (response != null) {
            response.abort();
        }
    }

    /** Closes only the client built by {@link #create(ObjectStoreProperties)}. */
    @Override
    public void close() {
        if (ownsClient && closed.compareAndSet(false, true)) {
            s3Client.close();
        }
    }
}
