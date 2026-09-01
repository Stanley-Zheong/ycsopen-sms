package com.ycsopen.sms.core.common.security.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.Grantee;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort.Failure.Category.OBJECT_INPUT_INVALID;
import static com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort.Failure.Category.OBJECT_INTEGRITY_INVALID;
import static com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort.Failure.Category.OBJECT_POLICY_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3PrivateObjectStoreAdapterTest {
    private static final String BUCKET = "phase03-private-bucket";
    private static final String MEDIA = "image/png";
    private static final String KEY = "obj_v1_" + "a".repeat(64);

    private S3Client client;
    private S3PrivateObjectStoreAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        allowPrivateBucket(client);
        ObjectStoreProperties properties = new ObjectStoreProperties(
                true,
                BUCKET,
                "us-east-1",
                URI.create("https://s3.test.invalid"),
                Set.of(URI.create("https://s3.test.invalid")),
                ObjectStoreProperties.CredentialProvider.DEFAULT_CHAIN,
                true,
                false);
        adapter = new S3PrivateObjectStoreAdapter(client, properties, new EnvelopeCodec(), new SecureRandom());
    }

    @ParameterizedTest
    @EnumSource(PrivateObjectStorePort.ObjectPurpose.class)
    void acceptsEachPurposeAtItsExactCompleteEnvelopeBoundary(
            PrivateObjectStorePort.ObjectPurpose purpose) throws Exception {
        byte[] envelope = maximumEnvelope(purpose);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> PutObjectResponse.builder()
                        .checksumSHA256(invocation.getArgument(0, PutObjectRequest.class).checksumSHA256())
                        .build());

        StoredObjectMetadata result = adapter.put(
                purpose, admittedMedia(purpose), new ByteArrayInputStream(envelope), (long) envelope.length);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(result.size()).isEqualTo(purpose.maximumEnvelopeBytes());
        assertThat(result.storageKey()).matches("obj_v1_[0-9a-f]{64}");
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(result.storageKey());
        assertThat(request.getValue().contentLength()).isEqualTo(envelope.length);
        assertThat(request.getValue().metadata()).containsOnly(
                entry("purpose", purpose.name()),
                entry("envelope-length", Integer.toString(envelope.length)),
                entry("sha256", sha256Hex(envelope)));
        assertThat(request.getValue().checksumSHA256()).isEqualTo(checksumBase64(envelope));
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).containsExactly(envelope);
    }

    @ParameterizedTest
    @EnumSource(PrivateObjectStorePort.ObjectPurpose.class)
    void rejectsOneByteOverEveryPurposeBeforeReadingOrTransfer(
            PrivateObjectStorePort.ObjectPurpose purpose) {
        CountingInputStream input = new CountingInputStream(purpose.maximumEnvelopeBytes() + 1);

        assertFailure(() -> adapter.put(
                purpose, admittedMedia(purpose), input, purpose.maximumEnvelopeBytes() + 1), OBJECT_INPUT_INVALID);

        assertThat(input.readCount()).isZero();
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsUnknownLengthAtFirstExcessByte() {
        var purpose = PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_FRONT;
        CountingInputStream input = new CountingInputStream(purpose.maximumEnvelopeBytes() + 1);

        assertFailure(() -> adapter.put(purpose, MEDIA, input, null), OBJECT_INPUT_INVALID);

        assertThat(input.readCount()).isEqualTo(purpose.maximumEnvelopeBytes() + 1);
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsDeclaredActualMismatchAndUnsignedLengthBeforeTransfer() {
        byte[] envelope = smallEnvelope(PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE);
        CountingInputStream huge = new CountingInputStream(1);

        assertFailure(() -> adapter.put(PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE,
                MEDIA, new ByteArrayInputStream(envelope), (long) envelope.length - 1), OBJECT_INPUT_INVALID);
        assertFailure(() -> adapter.put(PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE,
                MEDIA, new ByteArrayInputStream(envelope), (long) envelope.length + 1), OBJECT_INPUT_INVALID);
        assertFailure(() -> adapter.put(PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE,
                MEDIA, huge, 0x1_0000_0000L), OBJECT_INPUT_INVALID);

        assertThat(huge.readCount()).isZero();
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void validatesHeadBeforeOpeningGetAndRejectsOversizedOrUnexpectedMetadata() {
        var purpose = PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_BACK;
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(purpose.maximumEnvelopeBytes() + 1)
                .contentType(MEDIA)
                .metadata(Map.of())
                .build());

        assertFailure(() -> adapter.get(KEY, purpose), OBJECT_INTEGRITY_INVALID);
        verify(client, never()).getObject(any(GetObjectRequest.class));

        byte[] envelope = smallEnvelope(purpose);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(envelope, purpose, MEDIA,
                Map.of("unexpected", "metadata")));
        assertFailure(() -> adapter.head(KEY, purpose), OBJECT_INTEGRITY_INVALID);
    }

    @Test
    void rejectsShortExtraAndChecksumMismatchedBodies() {
        var purpose = PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE;
        byte[] envelope = smallEnvelope(purpose);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(envelope, purpose, MEDIA, Map.of()));

        when(client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response(Arrays.copyOf(envelope, envelope.length - 1), envelope, purpose, MEDIA));
        assertFailure(() -> adapter.get(KEY, purpose), OBJECT_INTEGRITY_INVALID);

        byte[] extra = Arrays.copyOf(envelope, envelope.length + 1);
        when(client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response(extra, envelope, purpose, MEDIA));
        assertFailure(() -> adapter.get(KEY, purpose), OBJECT_INTEGRITY_INVALID);

        byte[] changed = envelope.clone();
        changed[changed.length - 1] ^= 1;
        when(client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response(changed, envelope, purpose, MEDIA));
        assertFailure(() -> adapter.get(KEY, purpose), OBJECT_INTEGRITY_INVALID);
    }

    @Test
    void returnsOnlyVerifiedCiphertextAndSanitizedMetadataFacts() {
        var purpose = PrivateObjectStorePort.ObjectPurpose.TRADEMARK_PROOF;
        byte[] envelope = smallEnvelope(purpose);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(envelope, purpose, MEDIA, Map.of()));
        when(client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response(envelope, envelope, purpose, MEDIA));

        PrivateObjectStorePort.StoredCiphertext result = adapter.get(KEY, purpose);

        assertThat(result.ciphertext()).containsExactly(envelope);
        result.ciphertext()[0] = 0;
        assertThat(result.ciphertext()).containsExactly(envelope);
        assertThat(result.metadata().size()).isEqualTo(envelope.length);
        assertThat(result.metadata().sha256()).isEqualTo(sha256Hex(envelope));
        assertThat(result.metadata().mediaType()).isEqualTo(MEDIA);
        assertThat(result.toString()).doesNotContain(KEY, sha256Hex(envelope));
    }

    @Test
    void rejectsTraversalOriginalNamesAndCallerControlledLocations() {
        var purpose = PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE;

        assertFailure(() -> adapter.head("../original-license.pdf", purpose), OBJECT_INPUT_INVALID);
        assertFailure(() -> adapter.delete("https://example.invalid/object", purpose), OBJECT_INPUT_INVALID);
        assertFailure(() -> adapter.put(purpose, "application/octet-stream",
                new ByteArrayInputStream(smallEnvelope(purpose)), null), OBJECT_INPUT_INVALID);

        assertThat(PrivateObjectStorePort.class.getMethods())
                .allSatisfy(method -> assertThat(Arrays.stream(method.getParameterTypes()))
                        .doesNotContain(URI.class));
    }

    @Test
    void rejectsGroupAclAndEveryBucketPolicy() {
        var publicGrant = Grant.builder()
                .grantee(Grantee.builder().type(Type.GROUP).uri("http://acs.amazonaws.com/groups/global/AllUsers").build())
                .permission(Permission.READ)
                .build();
        when(client.getBucketAcl(any(GetBucketAclRequest.class)))
                .thenReturn(GetBucketAclResponse.builder().grants(publicGrant).build());

        assertFailure(() -> adapter.head(KEY, PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE),
                OBJECT_POLICY_INVALID);

        allowPrivateBucket(client);
        when(client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenReturn(GetBucketPolicyResponse.builder()
                        .policy("{\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\"}]}")
                        .build());
        assertFailure(() -> adapter.head(KEY, PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE),
                OBJECT_POLICY_INVALID);
    }

    @Test
    void configurationRejectsUnapprovedAndCredentialBearingEndpointsWithoutEchoingThem() {
        URI endpoint = URI.create("https://user:credential@storage.secret.invalid/private?token=value");

        assertThatThrownBy(() -> new ObjectStoreProperties(
                true, "private-bucket", "us-east-1", endpoint, Set.of(endpoint),
                ObjectStoreProperties.CredentialProvider.CONTAINER, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("private object configuration is invalid")
                .hasMessageNotContaining("credential")
                .hasMessageNotContaining("storage.secret.invalid")
                .hasMessageNotContaining("token=value");

        URI unapproved = URI.create("https://unapproved.invalid");
        assertThatThrownBy(() -> new ObjectStoreProperties(
                true, "private-bucket", "us-east-1", unapproved, Set.of(),
                ObjectStoreProperties.CredentialProvider.INSTANCE_PROFILE, true, false))
                .hasMessage("private object configuration is invalid")
                .hasMessageNotContaining("unapproved.invalid")
                .hasMessageNotContaining("INSTANCE_PROFILE");
    }

    @Test
    void providerFailureIsStableAndRedactsAllBoundaryValues() {
        String providerText = "endpoint=https://secret.invalid bucket=" + BUCKET
                + " key=" + KEY + " credential=top-secret body=plaintext-canary";
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(new IllegalStateException(providerText));

        assertThatThrownBy(() -> adapter.head(KEY, PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE))
                .isInstanceOf(PrivateObjectStorePort.Failure.class)
                .hasMessage("private object store is unavailable")
                .hasMessageNotContaining("secret.invalid")
                .hasMessageNotContaining(BUCKET)
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("top-secret")
                .hasMessageNotContaining("plaintext-canary")
                .satisfies(failure -> assertThat(failure.getCause()).isNull());
    }

    @Test
    void bestEffortDeletesAWriteWhoseProviderOutcomeIsUncertain() {
        var purpose = PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE;
        byte[] envelope = smallEnvelope(purpose);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("provider detail must not escape"));

        assertThatThrownBy(() -> adapter.put(
                purpose, MEDIA, new ByteArrayInputStream(envelope), (long) envelope.length))
                .isInstanceOf(PrivateObjectStorePort.Failure.class)
                .hasMessage("private object store is unavailable")
                .hasMessageNotContaining("provider detail");

        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
    void executesPrivateCiphertextLifecycleAgainstDigestLockedMinio() throws Exception {
        String runId = "minio-" + randomHex(6);
        String accessKey = "phase03" + randomHex(6);
        String secretKey = randomHex(24);
        S3Client realClient = null;
        S3Client anonymousClient = null;
        String bucket = "phase03-adapter-" + randomHex(6);
        String storedKey = null;
        try {
            JsonNode service = serviceCommand("start", runId, Map.of(
                    "PHASE03_MINIO_ACCESS_KEY", accessKey,
                    "PHASE03_MINIO_SECRET_KEY", secretKey));
            assertThat(service.path("status").asText()).isEqualTo("READY");
            assertThat(service.path("image_reference").asText()).isEqualTo(
                    "minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e");
            URI endpoint = URI.create("http://" + service.path("host").asText()
                    + ":" + service.path("port").asInt());
            S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
            realClient = S3Client.builder()
                    .endpointOverride(endpoint)
                    .region(Region.US_EAST_1)
                    .serviceConfiguration(pathStyle)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .httpClient(UrlConnectionHttpClient.create())
                    .build();
            anonymousClient = S3Client.builder()
                    .endpointOverride(endpoint)
                    .region(Region.US_EAST_1)
                    .serviceConfiguration(pathStyle)
                    .credentialsProvider(AnonymousCredentialsProvider.create())
                    .httpClient(UrlConnectionHttpClient.create())
                    .build();
            realClient.createBucket(request -> request.bucket(bucket));

            ObjectStoreProperties properties = new ObjectStoreProperties(
                    true, bucket, "us-east-1", endpoint, Set.of(endpoint),
                    ObjectStoreProperties.CredentialProvider.DEFAULT_CHAIN, true, true);
            S3PrivateObjectStoreAdapter realAdapter = new S3PrivateObjectStoreAdapter(
                    realClient, properties, new EnvelopeCodec(), new SecureRandom());
            var purpose = PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE;
            byte[] envelope = smallEnvelope(purpose);

            StoredObjectMetadata stored;
            try {
                stored = realAdapter.put(
                        purpose, "application/pdf", new ByteArrayInputStream(envelope), (long) envelope.length);
            } catch (PrivateObjectStorePort.Failure failure) {
                throw new AssertionError("MINIO_ADAPTER_PUT_" + failure.category());
            }
            storedKey = stored.storageKey();
            byte[] rawObject = realClient.getObjectAsBytes(
                    request -> request.bucket(bucket).key(stored.storageKey())).asByteArray();

            assertThat(rawObject).containsExactly(envelope);
            assertThat(new String(rawObject, StandardCharsets.ISO_8859_1))
                    .doesNotContain("phase03-plaintext-canary");
            HeadObjectResponse rawHead = realClient.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(stored.storageKey()).checksumMode(ChecksumMode.ENABLED).build());
            assertRealHeadShape(rawHead, envelope, purpose);
            try {
                assertThat(realAdapter.head(stored.storageKey(), purpose).sha256()).isEqualTo(sha256Hex(envelope));
            } catch (PrivateObjectStorePort.Failure failure) {
                throw new AssertionError("MINIO_ADAPTER_HEAD_" + failure.category());
            }
            try {
                assertThat(realAdapter.get(stored.storageKey(), purpose).ciphertext()).containsExactly(envelope);
            } catch (PrivateObjectStorePort.Failure failure) {
                throw new AssertionError("MINIO_ADAPTER_GET_" + failure.category());
            }
            S3Client deniedClient = anonymousClient;
            assertThatThrownBy(() -> deniedClient.getObjectAsBytes(
                    request -> request.bucket(bucket).key(stored.storageKey())))
                    .isInstanceOf(S3Exception.class)
                    .satisfies(failure -> assertThat(((S3Exception) failure).statusCode()).isEqualTo(403));

            realAdapter.delete(stored.storageKey(), purpose);
            storedKey = null;
        } finally {
            if (realClient != null) {
                if (storedKey != null) {
                    try {
                        String keyToDelete = storedKey;
                        realClient.deleteObject(request -> request.bucket(bucket).key(keyToDelete));
                    } catch (RuntimeException ignored) {
                        // The run-owned service cleanup is the final containment boundary.
                    }
                }
                try {
                    realClient.deleteBucket(request -> request.bucket(bucket));
                } catch (RuntimeException ignored) {
                    // The run-owned service cleanup removes the ephemeral data volume.
                }
                realClient.close();
            }
            if (anonymousClient != null) {
                anonymousClient.close();
            }
            try {
                serviceCommand("stop", runId, Map.of());
            } catch (Exception cleanupFailure) {
                throw new AssertionError("MINIO_SERVICE_CLEANUP_FAILURE", cleanupFailure);
            }
        }
    }

    private static void allowPrivateBucket(S3Client client) {
        Grant owner = Grant.builder()
                .grantee(Grantee.builder().type(Type.CANONICAL_USER).id("owner-id").build())
                .permission(Permission.FULL_CONTROL)
                .build();
        when(client.getBucketAcl(any(GetBucketAclRequest.class)))
                .thenReturn(GetBucketAclResponse.builder().grants(owner).build());
        when(client.getBucketPolicy(any(GetBucketPolicyRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucketPolicy").build())
                .build());
    }

    private static HeadObjectResponse head(byte[] envelope,
                                           PrivateObjectStorePort.ObjectPurpose purpose,
                                           String mediaType,
                                           Map<String, String> extraMetadata) {
        Map<String, String> metadata = new java.util.HashMap<>(metadata(envelope, purpose));
        metadata.putAll(extraMetadata);
        return HeadObjectResponse.builder()
                .contentLength((long) envelope.length)
                .contentType(mediaType)
                .checksumSHA256(checksumBase64(envelope))
                .metadata(metadata)
                .build();
    }

    private static void assertRealHeadShape(HeadObjectResponse head,
                                            byte[] envelope,
                                            PrivateObjectStorePort.ObjectPurpose purpose) {
        if (!Long.valueOf(envelope.length).equals(head.contentLength())) {
            throw new AssertionError("MINIO_HEAD_LENGTH");
        }
        if (!"application/pdf".equals(head.contentType())) {
            throw new AssertionError("MINIO_HEAD_MEDIA");
        }
        Map<String, String> normalized = new java.util.HashMap<>();
        head.metadata().forEach((key, value) -> normalized.put(key.toLowerCase(java.util.Locale.ROOT), value));
        if (!normalized.equals(metadata(envelope, purpose))) {
            throw new AssertionError("MINIO_HEAD_METADATA");
        }
        if (head.checksumSHA256() != null && !head.checksumSHA256().equals(checksumBase64(envelope))) {
            throw new AssertionError("MINIO_HEAD_CHECKSUM");
        }
        if (head.contentDisposition() != null) {
            throw new AssertionError("MINIO_HEAD_DISPOSITION");
        }
        if (head.contentEncoding() != null) {
            throw new AssertionError("MINIO_HEAD_ENCODING");
        }
        if (head.websiteRedirectLocation() != null) {
            throw new AssertionError("MINIO_HEAD_REDIRECT");
        }
    }

    private static ResponseInputStream<GetObjectResponse> response(byte[] body,
                                                                   byte[] declaredEnvelope,
                                                                   PrivateObjectStorePort.ObjectPurpose purpose,
                                                                   String mediaType) {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength((long) declaredEnvelope.length)
                .contentType(mediaType)
                .checksumSHA256(checksumBase64(declaredEnvelope))
                .metadata(metadata(declaredEnvelope, purpose))
                .build();
        return new ResponseInputStream<>(response,
                AbortableInputStream.create(new ByteArrayInputStream(body)));
    }

    private static Map<String, String> metadata(byte[] envelope,
                                                PrivateObjectStorePort.ObjectPurpose purpose) {
        return Map.of(
                "purpose", purpose.name(),
                "envelope-length", Integer.toString(envelope.length),
                "sha256", sha256Hex(envelope));
    }

    private static byte[] maximumEnvelope(PrivateObjectStorePort.ObjectPurpose purpose) {
        return envelope(purpose, Math.toIntExact(purpose.maximumEnvelopeBytes() - EnvelopeCodec.MAXIMUM_OVERHEAD_BYTES));
    }

    private static byte[] smallEnvelope(PrivateObjectStorePort.ObjectPurpose purpose) {
        return envelope(purpose, 64);
    }

    private static byte[] envelope(PrivateObjectStorePort.ObjectPurpose purpose, int plaintextLength) {
        byte[] ciphertext = new byte[Math.addExact(plaintextLength, EnvelopeCodec.DATA_TAG_BYTES)];
        Arrays.fill(ciphertext, (byte) 0x5a);
        CipherEnvelope envelope = new CipherEnvelope(
                "pkcs11",
                "k".repeat(32),
                new byte[EnvelopeCodec.NONCE_BYTES],
                new byte[EnvelopeCodec.WRAPPED_DEK_BYTES],
                new byte[EnvelopeCodec.NONCE_BYTES],
                ciphertext);
        return new EnvelopeCodec().encode(envelope, purpose.envelopeTarget());
    }

    private static String admittedMedia(PrivateObjectStorePort.ObjectPurpose purpose) {
        return switch (purpose) {
            case REPRESENTATIVE_ID_FRONT, REPRESENTATIVE_ID_BACK -> "image/jpeg";
            case BUSINESS_LICENSE, SHORT_LINK_DOMAIN_PROOF, TRADEMARK_PROOF -> "application/pdf";
        };
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String checksumBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256Hex(bytes)));
    }

    private static JsonNode serviceCommand(String action,
                                           String runId,
                                           Map<String, String> environment) throws Exception {
        Path script = locateServiceScript();
        ProcessBuilder builder = new ProcessBuilder(
                "/usr/bin/env", "ruby", script.toString(), action,
                "--service", "minio", "--run-id", runId);
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output;
        try (var drain = Executors.newSingleThreadExecutor()) {
            Future<byte[]> outputFuture = drain.submit(() -> process.getInputStream().readNBytes(1_048_577));
            boolean exited = process.waitFor(12, TimeUnit.MINUTES);
            if (!exited) {
                process.destroyForcibly();
                throw new AssertionError("MINIO_SERVICE_TIMEOUT");
            }
            output = outputFuture.get(5, TimeUnit.SECONDS);
            if (output.length > 1_048_576 || process.exitValue() != 0) {
                throw new AssertionError("MINIO_SERVICE_FAILURE");
            }
        }
        JsonNode result = new ObjectMapper().readTree(output);
        if (result == null || !result.isObject()) {
            throw new AssertionError("MINIO_SERVICE_OUTPUT_INVALID");
        }
        return result;
    }

    private static Path locateServiceScript() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve("scripts/lib/phase-03/service_checks.rb");
            if (Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new AssertionError("MINIO_SERVICE_SCRIPT_NOT_FOUND");
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }

    private static void assertFailure(Runnable operation,
                                      PrivateObjectStorePort.Failure.Category expectedCategory) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(PrivateObjectStorePort.Failure.class)
                .satisfies(failure -> assertThat(((PrivateObjectStorePort.Failure) failure).category())
                        .isEqualTo(expectedCategory));
    }

    private static final class CountingInputStream extends InputStream {
        private long remaining;
        private long readCount;

        private CountingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            readCount++;
            return 0x5a;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = (int) Math.min(remaining, length);
            Arrays.fill(bytes, offset, offset + count, (byte) 0x5a);
            remaining -= count;
            readCount += count;
            return count;
        }

        private long readCount() {
            return readCount;
        }
    }
}
