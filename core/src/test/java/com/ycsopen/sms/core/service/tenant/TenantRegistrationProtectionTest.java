package com.ycsopen.sms.core.service.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.web.controller.TenantController;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import com.ycsopen.sms.core.web.dto.TenantRegistrationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRegistrationProtectionTest {

    private static final String SESSION_ID = "12345678-1234-1234-1234-123456789abc";
    private static final String TOKEN = "regup_v1_synthetic-test-credential";
    private static final String BUSINESS_OBJECT = "pobj_v1_business";
    private static final String FRONT_OBJECT = "pobj_v1_front";
    private static final String BACK_OBJECT = "pobj_v1_back";
    private static final String SHORTLINK_OBJECT = "pobj_v1_shortlink";
    private static final String TRADEMARK_OBJECT = "pobj_v1_trademark";
    private static final String LEGAL_ID = "11010119900101123X";
    private static final String CONTACT_ID = "11010119900202234X";
    private static final String CONTACT_PHONE = "13800138000";
    private static final String KEY_REFERENCE = "field-kek.v1";

    @Test
    void protectsEveryIdentityValueWithExactTenantContextAndClaimsAllFiveObjects() throws Exception {
        TenantRepository tenants = mock(TenantRepository.class);
        TenantAccountRepository accounts = mock(TenantAccountRepository.class);
        when(tenants.findByUnifiedSocialCreditCode("91310000123456789X"))
                .thenReturn(Optional.empty());
        when(tenants.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getId() == null) {
                tenant.setId(73L);
            }
            return tenant;
        });

        RecordingKeyPort keys = new RecordingKeyPort();
        AtomicReference<TenantRegistrationProtectionAdapter.ObjectSelection> selection =
                new AtomicReference<>();
        TenantRegistrationProtectionAdapter adapter = adapter(keys,
                (tenantId, sessionId, uploadToken, selected) -> {
                    assertThat(tenantId).isEqualTo(73L);
                    assertThat(sessionId).isEqualTo(SESSION_ID);
                    assertThat(uploadToken).isEqualTo(TOKEN);
                    selection.set(selected);
                    return new TenantRegistrationProtectionAdapter.ClaimedObjects(
                            selected.businessLicenseObjectId(), selected.legalRepIdFrontObjectId(),
                            selected.legalRepIdBackObjectId(), selected.shortlinkDomainProofObjectId(),
                            selected.trademarkProofObjectId());
                });
        TenantService service = new TenantService(tenants, accounts, adapter);

        Tenant saved = service.submitRegistration(completeRequest(), TOKEN);

        assertThat(saved.getId()).isEqualTo(73L);
        assertThat(keys.contexts).containsExactly(
                context("legal_rep_id_no_encrypted"),
                context("contact_id_no_encrypted"),
                context("contact_phone_encrypted"));
        assertThat(selection.get()).isEqualTo(new TenantRegistrationProtectionAdapter.ObjectSelection(
                BUSINESS_OBJECT, FRONT_OBJECT, BACK_OBJECT, SHORTLINK_OBJECT, TRADEMARK_OBJECT));
        assertThat(unprotect(keys, saved, "legalRepIdNoEncrypted",
                "legal_rep_id_no_encrypted")).isEqualTo(LEGAL_ID);
        assertThat(unprotect(keys, saved, "contactIdNoEncrypted",
                "contact_id_no_encrypted")).isEqualTo(CONTACT_ID);
        assertThat(unprotect(keys, saved, "contactPhoneEncrypted",
                "contact_phone_encrypted")).isEqualTo(CONTACT_PHONE);
        assertThat(read(saved, "businessLicenseObjectId")).isEqualTo(BUSINESS_OBJECT);
        assertThat(read(saved, "legalRepIdFrontObjectId")).isEqualTo(FRONT_OBJECT);
        assertThat(read(saved, "legalRepIdBackObjectId")).isEqualTo(BACK_OBJECT);
        assertThat(read(saved, "shortlinkDomainProofObjectId")).isEqualTo(SHORTLINK_OBJECT);
        assertThat(read(saved, "trademarkProofObjectId")).isEqualTo(TRADEMARK_OBJECT);
        verify(tenants, org.mockito.Mockito.times(2)).saveAndFlush(saved);
    }

    @Test
    void rejectsLegacyUrlUnknownAndMissingRequiredObjectsBeforeTenantWrite() {
        TenantRepository tenants = mock(TenantRepository.class);
        TenantRegistrationProtectionAdapter adapter = adapter(new RecordingKeyPort(),
                (tenantId, sessionId, uploadToken, selected) ->
                        new TenantRegistrationProtectionAdapter.ClaimedObjects(
                                selected.businessLicenseObjectId(),
                                selected.legalRepIdFrontObjectId(),
                                selected.legalRepIdBackObjectId(),
                                selected.shortlinkDomainProofObjectId(),
                                selected.trademarkProofObjectId()));
        TenantService service = new TenantService(tenants, mock(TenantAccountRepository.class), adapter);

        TenantRegistrationRequest directUrl = request(
                "https://object.example.invalid/license", FRONT_OBJECT, BACK_OBJECT);
        assertFailure(() -> service.submitRegistration(directUrl, TOKEN),
                TenantRegistrationProtectionAdapter.Failure.Category.LEGACY_OBJECT_URL_NOT_ACCEPTED);

        TenantRegistrationRequest legacyUnknown = completeRequest();
        legacyUnknown.rejectUnknown("proofUrl", "opaque-looking-value");
        assertFailure(() -> service.submitRegistration(legacyUnknown, TOKEN),
                TenantRegistrationProtectionAdapter.Failure.Category.LEGACY_OBJECT_URL_NOT_ACCEPTED);

        TenantRegistrationRequest unknown = completeRequest();
        unknown.rejectUnknown("futureField", "discarded-sensitive-value");
        assertFailure(() -> service.submitRegistration(unknown, TOKEN),
                TenantRegistrationProtectionAdapter.Failure.Category.REGISTRATION_UNKNOWN_FIELD);

        assertFailure(() -> service.submitRegistration(
                        request(null, FRONT_OBJECT, BACK_OBJECT), TOKEN),
                TenantRegistrationProtectionAdapter.Failure.Category.REGISTRATION_OBJECT_REQUIRED);
        verify(tenants, never()).saveAndFlush(any());
    }

    @Test
    void jacksonRoutesUnknownAndLegacyFieldsIntoFailClosedWireFlags() throws Exception {
        String base = """
                {
                  "shortName":"synthetic",
                  "fullName":"Synthetic Tenant",
                  "unifiedSocialCreditCode":"91310000123456789X",
                  "registrationObjectSessionId":"12345678-1234-1234-1234-123456789abc",
                  "businessLicenseObjectId":"pobj_v1_business",
                  "legalRepName":"synthetic representative",
                  "legalRepIdNo":"11010119900101123X",
                  "legalRepIdFrontObjectId":"pobj_v1_front",
                  "legalRepIdBackObjectId":"pobj_v1_back",
                  "contactName":"synthetic contact",
                  "contactIdNo":"11010119900202234X",
                  "contactPhone":"13800138000",
                  "shortlinkDomainProofObjectId":null,
                  "trademarkProofObjectId":null,
                  %s
                }
                """;
        ObjectMapper mapper = new ObjectMapper();
        TenantRegistrationRequest unknown = mapper.readValue(
                base.formatted("\"unexpectedField\":\"discarded\""),
                TenantRegistrationRequest.class);
        TenantRegistrationRequest legacy = mapper.readValue(
                base.formatted("\"businessLicenseUrl\":\"https://example.invalid/object\""),
                TenantRegistrationRequest.class);

        assertThat(unknown.hasUnknownFields()).isTrue();
        assertThat(unknown.hasLegacyObjectUrlInput()).isFalse();
        assertThat(legacy.hasUnknownFields()).isTrue();
        assertThat(legacy.hasLegacyObjectUrlInput()).isTrue();
    }

    @Test
    void preservesDistinctClaimFailuresAndDoesNotPerformFinalSave() {
        for (TenantRegistrationProtectionAdapter.Failure failure : List.of(
                TenantRegistrationProtectionAdapter.Failure.uploadTokenInvalid(),
                TenantRegistrationProtectionAdapter.Failure.sessionNotOpen(),
                TenantRegistrationProtectionAdapter.Failure.sessionExpired(),
                TenantRegistrationProtectionAdapter.Failure.objectBindingMismatch(),
                TenantRegistrationProtectionAdapter.Failure.objectAlreadyClaimed(),
                TenantRegistrationProtectionAdapter.Failure.objectNotStaged(),
                TenantRegistrationProtectionAdapter.Failure.objectExpired(),
                TenantRegistrationProtectionAdapter.Failure.objectMediaMismatch(),
                TenantRegistrationProtectionAdapter.Failure.objectSizeInvalid(),
                TenantRegistrationProtectionAdapter.Failure.partialClaim())) {
            TenantRepository tenants = mock(TenantRepository.class);
            when(tenants.findByUnifiedSocialCreditCode("91310000123456789X"))
                    .thenReturn(Optional.empty());
            when(tenants.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant tenant = invocation.getArgument(0);
                tenant.setId(73L);
                return tenant;
            });
            TenantRegistrationProtectionAdapter adapter = adapter(new RecordingKeyPort(),
                    (tenantId, sessionId, uploadToken, selected) -> { throw failure; });
            TenantService service = new TenantService(tenants,
                    mock(TenantAccountRepository.class), adapter);

            assertFailure(() -> service.submitRegistration(completeRequest(), TOKEN),
                    failure.category());
            verify(tenants, org.mockito.Mockito.times(1)).saveAndFlush(any(Tenant.class));
        }
    }

    @Test
    void registrationResponseAndControllerContractExposeOnlyPublicState() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(73L);
        tenant.setTenantNo("T_PUBLIC");
        tenant.setShortName("示例机构");
        tenant.setFullName("示例机构有限公司");
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.SUBMITTED);

        String json = new ObjectMapper().writeValueAsString(TenantRegistrationResponse.from(tenant));
        assertThat(json).contains("T_PUBLIC", "PENDING", "SUBMITTED")
                .doesNotContain("legal", "contact", "ObjectId", "Token", "storage");

        Method register = TenantController.class.getMethod("register", String.class,
                TenantRegistrationRequest.class);
        assertThat(register.getGenericReturnType().getTypeName())
                .contains("TenantRegistrationResponse").doesNotContain("<Tenant>");
        Method submit = TenantService.class.getMethod("submitRegistration",
                TenantRegistrationRequest.class, String.class);
        assertThat(submit.getAnnotation(Transactional.class)).isNotNull();
    }

    private static TenantRegistrationProtectionAdapter adapter(
            RecordingKeyPort keys, TenantRegistrationProtectionAdapter.ClaimStore claims) {
        return new TenantRegistrationProtectionAdapter(new ProtectedFieldCodec(
                new EnvelopeCodec(), keys, new FixedSecureRandom(), KEY_REFERENCE), claims);
    }

    private static ProtectionContext context(String field) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "tenants", field,
                "tenant:73", "tenant_id=73");
    }

    private static String unprotect(RecordingKeyPort keys, Tenant tenant,
                                    String entityField, String contextField) throws Exception {
        byte[] envelope = (byte[]) read(tenant, entityField);
        ProtectedFieldCodec verifier = new ProtectedFieldCodec(
                new EnvelopeCodec(), keys, new FixedSecureRandom(), KEY_REFERENCE);
        return new String(verifier.unprotect(envelope, context(contextField),
                EnvelopeCodec.Target.DATABASE_FIELD), StandardCharsets.US_ASCII);
    }

    private static Object read(Tenant tenant, String fieldName) throws Exception {
        Field field = Tenant.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(tenant);
        return value instanceof byte[] bytes ? bytes.clone() : value;
    }

    private static TenantRegistrationRequest completeRequest() {
        return request(BUSINESS_OBJECT, FRONT_OBJECT, BACK_OBJECT);
    }

    private static TenantRegistrationRequest request(String businessObject,
                                                     String frontObject,
                                                     String backObject) {
        return new TenantRegistrationRequest("示例机构", "示例机构有限公司",
                "91310000123456789X", SESSION_ID, businessObject, "示例法人", LEGAL_ID,
                frontObject, backObject, "示例联系人", CONTACT_ID, CONTACT_PHONE,
                SHORTLINK_OBJECT, TRADEMARK_OBJECT);
    }

    private static void assertFailure(Runnable action,
                                      TenantRegistrationProtectionAdapter.Failure.Category category) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).isInstanceOf(TenantRegistrationProtectionAdapter.Failure.class);
        assertThat(((TenantRegistrationProtectionAdapter.Failure) thrown).category())
                .isEqualTo(category);
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private int invocation;

        @Override
        public void nextBytes(byte[] bytes) {
            invocation++;
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (invocation * 19 + index);
            }
        }
    }

    private static final class RecordingKeyPort implements KeyProtectionPort {
        private final Map<String, byte[]> keys = new HashMap<>();
        private final List<ProtectionContext> contexts = new ArrayList<>();

        @Override
        public WrappedDataKey wrap(byte[] dataEncryptionKey, byte[] authenticatedHeader,
                                   ProtectionContext semanticContext) {
            keys.put(semanticContext.contentRole(), dataEncryptionKey.clone());
            contexts.add(semanticContext);
            return new WrappedDataKey(KEY_REFERENCE, new byte[12], new byte[48]);
        }

        @Override
        public byte[] unwrap(WrappedDataKey wrappedDataKey, byte[] authenticatedHeader,
                             ProtectionContext semanticContext) {
            return keys.get(semanticContext.contentRole()).clone();
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }
    }
}
