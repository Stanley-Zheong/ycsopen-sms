package com.ycsopen.sms.core.web;

import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.CreatedSession;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.Failure;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.SessionState;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.StoredSession;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.UploadPurpose;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService.UploadRequest;
import com.ycsopen.sms.core.web.controller.TenantRegistrationObjectController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TenantRegistrationObjectApiTest {

    private static final Instant NOW = Instant.parse("2030-02-03T04:05:06Z");
    private static final byte[] PDF = "%PDF-1.7\nsynthetic".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
    private static final byte[] PNG = new byte[]{(byte) 0x89, 'P', 'N', 'G',
            0x0d, 0x0a, 0x1a, 0x0a, 1};

    @Test
    void oneSessionTokenSequentiallyUploadsFivePurposesAndReplacesWithinOpenBinding() {
        Fixture fixture = fixture();
        CreatedSession session = fixture.service.createSession();

        for (UploadPurpose purpose : UploadPurpose.values()) {
            fixture.service.upload(request(session, purpose, bodyFor(purpose), mediaFor(purpose)));
        }
        fixture.service.upload(request(session, UploadPurpose.BUSINESS_LICENSE, PDF,
                "application/pdf"));
        fixture.service.upload(request(session, UploadPurpose.BUSINESS_LICENSE, PDF,
                "application/pdf"));

        assertThat(fixture.objectRequests).hasSize(7);
        assertThat(fixture.objectRequests.subList(0, 5))
                .extracting(ProtectedObjectService.CreateRequest::purpose)
                .containsExactlyInAnyOrder(Arrays.stream(UploadPurpose.values())
                        .map(UploadPurpose::objectPurpose).toArray(
                                com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort.ObjectPurpose[]::new));
        List<ProtectedObjectService.CreateRequest> businessRequests = fixture.objectRequests.stream()
                .filter(value -> value.purpose() == UploadPurpose.BUSINESS_LICENSE.objectPurpose())
                .toList();
        assertThat(businessRequests).extracting(ProtectedObjectService.CreateRequest::attemptNumber)
                .containsExactly(1, 2, 3);
        assertThat(businessRequests.get(0).replacesObjectId()).isNull();
        assertThat(businessRequests.get(1).replacesObjectId())
                .isEqualTo(fixture.createdObjectIds.get(0));
        assertThat(businessRequests.get(2).replacesObjectId())
                .isEqualTo(fixture.createdObjectIds.get(5));
        assertThat(fixture.digestPort.lastVerifyPurpose)
                .isEqualTo(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD);
    }

    @Test
    void purposeAttemptsTwoThreeAndSessionAttemptsFourteenFifteenSucceedThenSixteenFails() {
        Fixture fixture = fixture();
        CreatedSession session = fixture.service.createSession();

        int accepted = 0;
        for (UploadPurpose purpose : UploadPurpose.values()) {
            for (int attempt = 0; attempt < 3; attempt++) {
                fixture.service.upload(request(session, purpose, bodyFor(purpose), mediaFor(purpose)));
                accepted++;
                if (accepted == 14 || accepted == 15) {
                    assertThat(fixture.store.sessionAttempts(session.registrationObjectSessionId()))
                            .isEqualTo(accepted);
                }
            }
        }

        assertFailure(() -> fixture.service.upload(request(session,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_LIMIT_REACHED);
        assertThat(fixture.objectRequests).hasSize(15);
        assertThat(fixture.store.sessionAttempts(session.registrationObjectSessionId())).isEqualTo(15);
    }

    @Test
    void concurrentReservationsNeverExceedPurposeOrSessionCeilings() throws Exception {
        Fixture purposeFixture = fixture();
        CreatedSession purposeSession = purposeFixture.service.createSession();
        List<Failure.Category> purposeFailures = race(12, () -> purposeFixture.service.upload(
                request(purposeSession, UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")));

        assertThat(purposeFixture.objectRequests).hasSize(3);
        assertThat(purposeFailures).hasSize(9)
                .allMatch(category -> category == Failure.Category.REGISTRATION_UPLOAD_LIMIT_REACHED);
        assertThat(purposeFixture.store.purposeAttempts(
                purposeSession.registrationObjectSessionId(), UploadPurpose.BUSINESS_LICENSE))
                .isEqualTo(3);

        Fixture sessionFixture = fixture();
        CreatedSession session = sessionFixture.service.createSession();
        List<Callable<Object>> calls = new ArrayList<>();
        for (UploadPurpose purpose : UploadPurpose.values()) {
            for (int attempt = 0; attempt < 4; attempt++) {
                calls.add(() -> sessionFixture.service.upload(
                        request(session, purpose, bodyFor(purpose), mediaFor(purpose))));
            }
        }
        List<Failure.Category> sessionFailures = race(calls);
        assertThat(sessionFixture.objectRequests).hasSize(15);
        assertThat(sessionFailures).hasSize(5)
                .allMatch(category -> category == Failure.Category.REGISTRATION_UPLOAD_LIMIT_REACHED);
        assertThat(sessionFixture.store.sessionAttempts(session.registrationObjectSessionId()))
                .isEqualTo(15);
    }

    @Test
    void everyPostReservationFailureBurnsItsSlot() {
        Fixture fixture = fixture();
        CreatedSession session = fixture.service.createSession();
        fixture.failObjectCreates.set(3);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertFailure(() -> fixture.service.upload(request(session,
                            UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                    Failure.Category.REGISTRATION_UPLOAD_UNAVAILABLE);
        }
        assertFailure(() -> fixture.service.upload(request(session,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_LIMIT_REACHED);

        assertThat(fixture.objectCalls).hasValue(3);
        assertThat(fixture.store.purposeAttempts(
                session.registrationObjectSessionId(), UploadPurpose.BUSINESS_LICENSE)).isEqualTo(3);
    }

    @Test
    void mediaSizeAndMagicValidationRejectBeforeReservationForEveryPurpose() {
        Fixture fixture = fixture();
        CreatedSession session = fixture.service.createSession();

        for (UploadPurpose purpose : UploadPurpose.values()) {
            assertFailure(() -> fixture.service.upload(new UploadRequest(
                            session.registrationObjectSessionId(), session.registrationUploadToken(),
                            purpose, mediaFor(purpose), new ByteArrayInputStream(bodyFor(purpose)),
                            purpose.maximumPlaintextBytes() + 1)),
                    Failure.Category.REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED);
            assertFailure(() -> fixture.service.upload(new UploadRequest(
                            session.registrationObjectSessionId(), session.registrationUploadToken(),
                            purpose, "text/plain", new ByteArrayInputStream(new byte[]{1}), 1)),
                    Failure.Category.REGISTRATION_UPLOAD_MEDIA_TYPE_NOT_ACCEPTED);
            byte[] wrongMagic = "not-an-evidence-file".getBytes(StandardCharsets.US_ASCII);
            assertFailure(() -> fixture.service.upload(new UploadRequest(
                            session.registrationObjectSessionId(), session.registrationUploadToken(),
                            purpose, mediaFor(purpose), new ByteArrayInputStream(wrongMagic),
                            wrongMagic.length)),
                    Failure.Category.REGISTRATION_UPLOAD_SIGNATURE_MISMATCH);
        }

        assertThat(fixture.store.sessionAttempts(session.registrationObjectSessionId())).isZero();
        assertThat(fixture.objectCalls).hasValue(0);
        assertThat(UploadPurpose.LEGAL_REP_ID_FRONT.maximumPlaintextBytes()).isEqualTo(5_242_880L);
        assertThat(UploadPurpose.BUSINESS_LICENSE.maximumPlaintextBytes()).isEqualTo(10_485_760L);
        assertThat(UploadPurpose.LEGAL_REP_ID_FRONT.maximumEnvelopeBytes()).isEqualTo(5_243_025L);
        assertThat(UploadPurpose.BUSINESS_LICENSE.maximumEnvelopeBytes()).isEqualTo(10_485_905L);
    }

    @Test
    void claimCloseExpiryCrossSessionAndCrossDraftAllFailClosed() {
        Fixture fixture = fixture();
        CreatedSession first = fixture.service.createSession();
        CreatedSession second = fixture.service.createSession();

        assertFailure(() -> fixture.service.upload(new UploadRequest(
                        second.registrationObjectSessionId(), first.registrationUploadToken(),
                        UploadPurpose.BUSINESS_LICENSE, "application/pdf",
                        new ByteArrayInputStream(PDF), PDF.length)),
                Failure.Category.REGISTRATION_UPLOAD_TOKEN_INVALID);

        fixture.store.changeTenantDraft(first.registrationObjectSessionId(),
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        assertFailure(() -> fixture.service.upload(request(first,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_TOKEN_INVALID);

        Fixture closed = fixture();
        CreatedSession closedSession = closed.service.createSession();
        assertThat(closed.service.close(closedSession.registrationObjectSessionId(),
                closedSession.registrationUploadToken())).isEqualTo(SessionState.CLOSED);
        assertFailure(() -> closed.service.upload(request(closedSession,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_SESSION_NOT_OPEN);

        Fixture claimed = fixture();
        CreatedSession claimedSession = claimed.service.createSession();
        assertThat(claimed.service.claim(claimedSession.registrationObjectSessionId(),
                claimedSession.registrationUploadToken())).isEqualTo(SessionState.CLAIMED);
        assertFailure(() -> claimed.service.upload(request(claimedSession,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_SESSION_NOT_OPEN);

        Fixture expired = fixture();
        CreatedSession expiredSession = expired.service.createSession();
        expired.clock.advance(TenantRegistrationObjectSessionService.SESSION_TTL);
        assertFailure(() -> expired.service.upload(request(expiredSession,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_SESSION_EXPIRED);
        assertThat(expired.store.state(expiredSession.registrationObjectSessionId()))
                .isEqualTo(SessionState.EXPIRED);
    }

    @Test
    void activeIssuesRetiringVerifiesAndRevokedUnknownOrCapabilityDomainFailClosed() {
        Fixture fixture = fixture();
        CreatedSession versionOne = fixture.service.createSession();
        assertThat(fixture.store.digestVersion(versionOne.registrationObjectSessionId())).isEqualTo(1);

        fixture.digestPort.states.put(1L, DigestState.RETIRING);
        fixture.digestPort.states.put(2L, DigestState.ACTIVE);
        fixture.service.upload(request(versionOne, UploadPurpose.BUSINESS_LICENSE,
                PDF, "application/pdf"));
        CreatedSession versionTwo = fixture.service.createSession();
        assertThat(fixture.store.digestVersion(versionTwo.registrationObjectSessionId())).isEqualTo(2);

        fixture.digestPort.states.put(1L, DigestState.REVOKED);
        assertFailure(() -> fixture.service.upload(request(versionOne,
                        UploadPurpose.LEGAL_REP_ID_FRONT, JPEG, "image/jpeg")),
                Failure.Category.REGISTRATION_UPLOAD_TOKEN_INVALID);

        fixture.store.changeDigestVersion(versionTwo.registrationObjectSessionId(), 99);
        assertFailure(() -> fixture.service.upload(request(versionTwo,
                        UploadPurpose.BUSINESS_LICENSE, PDF, "application/pdf")),
                Failure.Category.REGISTRATION_UPLOAD_TOKEN_INVALID);

        String capabilityDomainToken = versionTwo.registrationUploadToken()
                .replace(TenantRegistrationObjectSessionService.TOKEN_PREFIX, "ocap_v1_");
        assertFailure(() -> fixture.service.upload(new UploadRequest(
                        versionTwo.registrationObjectSessionId(), capabilityDomainToken,
                        UploadPurpose.BUSINESS_LICENSE, "application/pdf",
                        new ByteArrayInputStream(PDF), PDF.length)),
                Failure.Category.REGISTRATION_UPLOAD_TOKEN_INVALID);
    }

    @Test
    void mvcSurfaceUsesExactMultipartShapeAndReturnsNoStorageReference() throws Exception {
        TenantRegistrationObjectSessionService service = mock(
                TenantRegistrationObjectSessionService.class);
        CreatedSession session = new CreatedSession(
                "11111111-1111-4111-8111-111111111111",
                "regup_v1_AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                NOW.plus(Duration.ofHours(24)));
        when(service.createSession()).thenReturn(session);
        when(service.upload(any())).thenReturn(
                new TenantRegistrationObjectSessionService.UploadedObject(
                        "pobj_v1_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        UploadPurpose.BUSINESS_LICENSE, session.expiresAt()));
        when(service.close(any(), any())).thenReturn(SessionState.CLOSED);
        MockMvc mvc = mvc(service);

        mvc.perform(post(TenantRegistrationObjectController.BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.registrationObjectSessionId")
                        .value(session.registrationObjectSessionId()))
                .andExpect(jsonPath("$.data.registrationUploadToken")
                        .value(session.registrationUploadToken()))
                .andExpect(jsonPath("$.data.expiresAt").exists());

        MockMultipartFile file = new MockMultipartFile("file", "ignored.pdf",
                "application/pdf", PDF);
        MvcResult uploaded = mvc.perform(multipart(TenantRegistrationObjectController.BASE_PATH
                        + "/{sessionId}/objects/{purpose}",
                        session.registrationObjectSessionId(), "business-license")
                        .file(file)
                        .header(TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER,
                                session.registrationUploadToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protectedObjectId")
                        .value("pobj_v1_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(jsonPath("$.data.purpose").value("business-license"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andReturn();
        assertNoStorageReference(uploaded.getResponse().getContentAsString());

        ArgumentCaptor<UploadRequest> captured = ArgumentCaptor.forClass(UploadRequest.class);
        verify(service).upload(captured.capture());
        assertThat(captured.getValue().purpose()).isEqualTo(UploadPurpose.BUSINESS_LICENSE);
        assertThat(captured.getValue().declaredPlaintextLength()).isEqualTo(PDF.length);

        mvc.perform(delete(TenantRegistrationObjectController.BASE_PATH + "/{sessionId}",
                        session.registrationObjectSessionId())
                        .header(TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER,
                                session.registrationUploadToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CLOSED"));
    }

    @Test
    void mvcLimitAndExtraPartUseStableErrorsWithoutEchoingCredential() throws Exception {
        TenantRegistrationObjectSessionService service = mock(
                TenantRegistrationObjectSessionService.class);
        when(service.upload(any())).thenThrow(Failure.limitReached());
        MockMvc mvc = mvc(service);
        String sessionId = "11111111-1111-4111-8111-111111111111";
        String token = "regup_v1_AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        MockMultipartFile file = new MockMultipartFile("file", "ignored.pdf",
                "application/pdf", PDF);

        MvcResult limited = mvc.perform(multipart(TenantRegistrationObjectController.BASE_PATH
                        + "/{sessionId}/objects/{purpose}", sessionId, "business-license")
                        .file(file)
                        .header(TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER, token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("REGISTRATION_UPLOAD_LIMIT_REACHED"))
                .andReturn();
        assertThat(limited.getResponse().getContentAsString()).doesNotContain(token);

        MockMultipartFile extra = new MockMultipartFile("metadata", "ignored.txt",
                "text/plain", new byte[]{1});
        mvc.perform(multipart(TenantRegistrationObjectController.BASE_PATH
                        + "/{sessionId}/objects/{purpose}", sessionId, "business-license")
                        .file(file).file(extra)
                        .header(TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REGISTRATION_UPLOAD_INPUT_INVALID"));
    }

    @Test
    void docsMatchRuntimeRoutesTtlLimitsPurposesAndStableErrors() throws IOException {
        String api = Files.readString(Path.of("docs/API.md"));
        String manual = Files.readString(Path.of("../docs/使用手册.md"));
        for (String document : List.of(api, manual)) {
            assertThat(document)
                    .contains(TenantRegistrationObjectController.BASE_PATH,
                            TenantRegistrationObjectController.UPLOAD_ROUTE,
                            TenantRegistrationObjectSessionService.UPLOAD_TOKEN_HEADER,
                            TenantRegistrationObjectSessionService.TOKEN_PREFIX,
                            "PT24H", "3", "15", "5,242,880", "10,485,760",
                            "5,243,025", "10,485,905",
                            Failure.Category.REGISTRATION_UPLOAD_LIMIT_REACHED.name(),
                            "LEGACY_OBJECT_URL_NOT_ACCEPTED",
                            "businessLicenseObjectId", "legalRepIdFrontObjectId",
                            "legalRepIdBackObjectId", "shortlinkDomainProofObjectId",
                            "trademarkProofObjectId");
            for (UploadPurpose purpose : UploadPurpose.values()) {
                assertThat(document).contains(purpose.wireName());
            }
        }
        assertThat(TenantRegistrationObjectSessionService.SESSION_TTL).isEqualTo(Duration.ofHours(24));
        assertThat(TenantRegistrationObjectSessionService.MAX_ATTEMPTS_PER_PURPOSE).isEqualTo(3);
        assertThat(TenantRegistrationObjectSessionService.MAX_ATTEMPTS_PER_SESSION).isEqualTo(15);
    }

    private static Fixture fixture() {
        MutableClock clock = new MutableClock(NOW);
        DigestPort digestPort = new DigestPort();
        InMemorySessionStore store = new InMemorySessionStore();
        ProtectedObjectService protectedObjects = mock(ProtectedObjectService.class);
        Fixture fixture = new Fixture(clock, digestPort, store, protectedObjects);
        when(protectedObjects.create(any())).thenAnswer(invocation -> {
            ProtectedObjectService.CreateRequest request = invocation.getArgument(0);
            fixture.objectCalls.incrementAndGet();
            fixture.objectRequests.add(request);
            if (fixture.failObjectCreates.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw ProtectedObjectService.Failure.unavailable();
            }
            String objectId = "pobj_v1_" + String.format("%032d", fixture.objectCalls.get());
            fixture.createdObjectIds.add(objectId);
            store.recordCurrent(request.registrationSessionId(), requestPurpose(request.purpose()),
                    objectId);
            return new ProtectedObjectService.CreatedObject(objectId, request.purpose(),
                    request.mediaType(), request.declaredPlaintextLength(), request.expiresAt());
        });
        return fixture;
    }

    private static UploadPurpose requestPurpose(
            com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort.ObjectPurpose purpose) {
        return Arrays.stream(UploadPurpose.values())
                .filter(value -> value.objectPurpose() == purpose).findFirst().orElseThrow();
    }

    private static UploadRequest request(CreatedSession session,
                                         UploadPurpose purpose,
                                         byte[] body,
                                         String mediaType) {
        return new UploadRequest(session.registrationObjectSessionId(),
                session.registrationUploadToken(), purpose, mediaType,
                new ByteArrayInputStream(body), body.length);
    }

    private static byte[] bodyFor(UploadPurpose purpose) {
        return switch (purpose) {
            case BUSINESS_LICENSE, SHORTLINK_DOMAIN_PROOF, TRADEMARK_PROOF -> PDF;
            case LEGAL_REP_ID_FRONT -> JPEG;
            case LEGAL_REP_ID_BACK -> PNG;
        };
    }

    private static String mediaFor(UploadPurpose purpose) {
        return switch (purpose) {
            case BUSINESS_LICENSE, SHORTLINK_DOMAIN_PROOF, TRADEMARK_PROOF -> "application/pdf";
            case LEGAL_REP_ID_FRONT -> "image/jpeg";
            case LEGAL_REP_ID_BACK -> "image/png";
        };
    }

    private static List<Failure.Category> race(int callers, Callable<?> call) throws Exception {
        List<Callable<Object>> calls = new ArrayList<>();
        for (int index = 0; index < callers; index++) {
            calls.add(() -> call.call());
        }
        return race(calls);
    }

    private static List<Failure.Category> race(List<Callable<Object>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.call();
                }));
            }
            ready.await();
            start.countDown();
            List<Failure.Category> failures = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof Failure registrationFailure) {
                        failures.add(registrationFailure.category());
                    } else {
                        throw new AssertionError(cause);
                    }
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertFailure(Runnable action, Failure.Category category) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(Failure.class,
                failure -> assertThat(failure.category()).isEqualTo(category));
    }

    private static MockMvc mvc(TenantRegistrationObjectSessionService service) {
        return standaloneSetup(new TenantRegistrationObjectController(service)).build();
    }

    private static void assertNoStorageReference(String body) {
        assertThat(body.toLowerCase())
                .doesNotContain("bucket", "objectkey", "storagekey", "presigned",
                        "https://", "http://", "provider", "ciphertext");
    }

    private record Fixture(MutableClock clock,
                           DigestPort digestPort,
                           InMemorySessionStore store,
                           ProtectedObjectService protectedObjects,
                           TenantRegistrationObjectSessionService service,
                           AtomicInteger objectCalls,
                           AtomicInteger failObjectCreates,
                           List<ProtectedObjectService.CreateRequest> objectRequests,
                           List<String> createdObjectIds) {
        private Fixture(MutableClock clock,
                        DigestPort digestPort,
                        InMemorySessionStore store,
                        ProtectedObjectService protectedObjects) {
            this(clock, digestPort, store, protectedObjects,
                    new TenantRegistrationObjectSessionService(digestPort, protectedObjects,
                            store, clock, new SecureRandom()),
                    new AtomicInteger(), new AtomicInteger(),
                    java.util.Collections.synchronizedList(new ArrayList<>()),
                    java.util.Collections.synchronizedList(new ArrayList<>()));
        }
    }

    private enum DigestState {
        ACTIVE,
        RETIRING,
        RETIRED,
        REVOKED
    }

    private static final class DigestPort implements OpaqueTokenDigestPort {
        private final Map<Long, DigestState> states = new HashMap<>(Map.of(1L, DigestState.ACTIVE));
        private volatile Purpose lastVerifyPurpose;

        @Override
        public synchronized VersionedTokenDigest issue(Purpose purpose,
                                                        Binding binding,
                                                        byte[] tokenSecret) {
            long active = states.entrySet().stream()
                    .filter(entry -> entry.getValue() == DigestState.ACTIVE)
                    .mapToLong(Map.Entry::getKey).max().orElseThrow();
            return new VersionedTokenDigest(purpose, active,
                    digest(purpose, binding, tokenSecret, active));
        }

        @Override
        public synchronized boolean verify(Purpose purpose,
                                           Binding binding,
                                           byte[] tokenSecret,
                                           VersionedTokenDigest storedDigest) {
            lastVerifyPurpose = purpose;
            DigestState state = states.get(storedDigest.keyVersion());
            return storedDigest.purpose() == purpose
                    && (state == DigestState.ACTIVE || state == DigestState.RETIRING)
                    && MessageDigest.isEqual(storedDigest.digest(),
                    digest(purpose, binding, tokenSecret, storedDigest.keyVersion()));
        }

        @Override
        public KeyHealth health(Purpose purpose) {
            return new KeyHealth(states.containsValue(DigestState.ACTIVE)
                    ? KeyHealth.Status.READY : KeyHealth.Status.UNAVAILABLE);
        }

        private static byte[] digest(Purpose purpose,
                                     Binding binding,
                                     byte[] secret,
                                     long version) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(purpose.name().getBytes(StandardCharsets.US_ASCII));
                digest.update(Long.toString(version).getBytes(StandardCharsets.US_ASCII));
                digest.update(binding.tenant().getBytes(StandardCharsets.US_ASCII));
                digest.update(binding.subject().getBytes(StandardCharsets.US_ASCII));
                digest.update(binding.resourceOrSession().getBytes(StandardCharsets.US_ASCII));
                return digest.digest(secret);
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    private static final class InMemorySessionStore
            implements TenantRegistrationObjectSessionService.SessionStore {
        private final Map<String, StoredSession> sessions = new HashMap<>();
        private final Map<String, EnumMap<UploadPurpose, Integer>> attempts = new HashMap<>();
        private final Map<String, EnumMap<UploadPurpose, String>> currentObjects = new HashMap<>();

        @Override
        public synchronized boolean create(StoredSession session) {
            if (sessions.putIfAbsent(session.registrationSessionId(), session) != null) {
                return false;
            }
            attempts.put(session.registrationSessionId(), new EnumMap<>(UploadPurpose.class));
            currentObjects.put(session.registrationSessionId(), new EnumMap<>(UploadPurpose.class));
            return true;
        }

        @Override
        public synchronized TenantRegistrationObjectSessionService.Reservation reserve(
                String registrationSessionId,
                UploadPurpose purpose,
                Instant now,
                java.util.function.Predicate<StoredSession> credentialVerifier) {
            StoredSession session = Optional.ofNullable(sessions.get(registrationSessionId))
                    .orElseThrow(Failure::tokenInvalid);
            if (!now.isBefore(session.expiresAt())) {
                replace(session, SessionState.EXPIRED, session.credentialDigest(),
                        session.tenantDraftId(), session.admittedAttemptCount());
                throw Failure.sessionExpired();
            }
            if (session.state() != SessionState.OPEN) {
                throw Failure.sessionNotOpen();
            }
            if (!credentialVerifier.test(session)) {
                throw Failure.tokenInvalid();
            }
            int purposeCount = attempts.get(registrationSessionId).getOrDefault(purpose, 0);
            if (purposeCount >= TenantRegistrationObjectSessionService.MAX_ATTEMPTS_PER_PURPOSE
                    || session.admittedAttemptCount()
                    >= TenantRegistrationObjectSessionService.MAX_ATTEMPTS_PER_SESSION) {
                throw Failure.limitReached();
            }
            int nextPurpose = purposeCount + 1;
            int nextSession = session.admittedAttemptCount() + 1;
            attempts.get(registrationSessionId).put(purpose, nextPurpose);
            replace(session, SessionState.OPEN, session.credentialDigest(),
                    session.tenantDraftId(), nextSession);
            return new TenantRegistrationObjectSessionService.Reservation(
                    registrationSessionId, session.tenantDraftId(), purpose,
                    nextPurpose, nextSession, session.expiresAt(),
                    currentObjects.get(registrationSessionId).get(purpose));
        }

        @Override
        public synchronized SessionState transition(String registrationSessionId,
                                                    SessionState targetState,
                                                    Instant now,
                                                    java.util.function.Predicate<StoredSession> credentialVerifier) {
            StoredSession session = Optional.ofNullable(sessions.get(registrationSessionId))
                    .orElseThrow(Failure::tokenInvalid);
            if (!now.isBefore(session.expiresAt())) {
                replace(session, SessionState.EXPIRED, session.credentialDigest(),
                        session.tenantDraftId(), session.admittedAttemptCount());
                throw Failure.sessionExpired();
            }
            if (session.state() != SessionState.OPEN) {
                throw Failure.sessionNotOpen();
            }
            if (!credentialVerifier.test(session)) {
                throw Failure.tokenInvalid();
            }
            replace(session, targetState, session.credentialDigest(),
                    session.tenantDraftId(), session.admittedAttemptCount());
            return targetState;
        }

        synchronized void recordCurrent(String sessionId, UploadPurpose purpose, String objectId) {
            currentObjects.get(sessionId).put(purpose, objectId);
        }

        synchronized int sessionAttempts(String sessionId) {
            return sessions.get(sessionId).admittedAttemptCount();
        }

        synchronized int purposeAttempts(String sessionId, UploadPurpose purpose) {
            return attempts.get(sessionId).getOrDefault(purpose, 0);
        }

        synchronized SessionState state(String sessionId) {
            return sessions.get(sessionId).state();
        }

        synchronized long digestVersion(String sessionId) {
            return sessions.get(sessionId).credentialDigest().keyVersion();
        }

        synchronized void changeTenantDraft(String sessionId, String tenantDraftId) {
            StoredSession current = sessions.get(sessionId);
            replace(current, current.state(), current.credentialDigest(), tenantDraftId,
                    current.admittedAttemptCount());
        }

        synchronized void changeDigestVersion(String sessionId, long version) {
            StoredSession current = sessions.get(sessionId);
            replace(current, current.state(), new VersionedTokenDigest(
                            OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                            version, current.credentialDigest().digest()),
                    current.tenantDraftId(), current.admittedAttemptCount());
        }

        private void replace(StoredSession current,
                             SessionState state,
                             VersionedTokenDigest digest,
                             String tenantDraftId,
                             int admittedAttemptCount) {
            sessions.put(current.registrationSessionId(), new StoredSession(
                    current.registrationSessionId(), tenantDraftId, state, digest,
                    current.expiresAt(), admittedAttemptCount));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
