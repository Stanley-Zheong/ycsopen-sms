package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.migration.LegacyValueClassifier.Classification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyValueClassifierTest {

    private static final Path MANIFEST = Path.of("src/main/resources/security/protected-data-inventory.json");
    private static ProtectedDataManifest manifest;
    private final EnvelopeCodec envelopeCodec = new EnvelopeCodec();
    private final LegacyValueClassifier classifier = new LegacyValueClassifier(envelopeCodec);

    @BeforeAll
    static void loadManifest() throws IOException {
        byte[] bytes = Files.readAllBytes(MANIFEST);
        manifest = ProtectedDataManifest.load(
                Files.newInputStream(MANIFEST), ProtectedDataManifest.canonicalDigest(bytes));
    }

    @Test
    void classifiesCanonicalEnvelopeAndNeverFallsBackFromMagicCorruption() {
        ProtectedDataTarget target = manifest.requireTarget("message_tasks.mobile_encrypted");
        byte[] valid = envelopeCodec.encode(new CipherEnvelope(
                "pkcs11",
                "field-kek.v1",
                repeated(12, 0x11),
                repeated(48, 0x22),
                repeated(12, 0x33),
                repeated(27, 0x44)), EnvelopeCodec.Target.DATABASE_FIELD);

        assertThat(classifier.classify(target, valid)).isEqualTo(Classification.VALID_ENVELOPE);

        byte[] unsupportedVersion = valid.clone();
        unsupportedVersion[4] = 2;
        assertThat(classifier.classify(target, unsupportedVersion)).isEqualTo(Classification.CORRUPT);
        assertThat(classifier.classify(target, "YCSE1234567".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Classification.CORRUPT);
    }

    @Test
    void approvesOnlyTheReviewedNonmagicLegacyRule() {
        ProtectedDataTarget mobile = manifest.requireTarget("message_tasks.mobile_encrypted");
        assertThat(classifier.classify(mobile, "13800138000".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Classification.APPROVED_LEGACY);
        assertThat(classifier.classify(mobile, "138001380001".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Classification.AMBIGUOUS);
        assertThat(classifier.classify(mobile, new byte[] {(byte) 0xc3, 0x28}))
                .isEqualTo(Classification.AMBIGUOUS);

        ProtectedDataTarget digest = manifest.requireTarget("third_party_risk_check_logs.mobile_hash");
        assertThat(classifier.classify(digest, "a".repeat(64).getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(Classification.APPROVED_LEGACY);
        assertThat(classifier.classify(digest, "A".repeat(64).getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(Classification.AMBIGUOUS);
    }

    @Test
    void appliesExplicitNullAndObjectReferenceRules() {
        assertThat(classifier.classify(manifest.requireTarget("users.phone_encrypted"), null))
                .isEqualTo(Classification.NULL_ALLOWED);
        assertThat(classifier.classify(manifest.requireTarget("message_tasks.mobile_encrypted"), null))
                .isEqualTo(Classification.CORRUPT);

        ProtectedDataTarget object = manifest.requireTarget("tenants.business_license_url");
        assertThat(classifier.classify(object, "pobj_v1_abCD_12".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(Classification.APPROVED_LEGACY);
        assertThat(classifier.classify(object, "https://objects.example/proof".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(Classification.APPROVED_LEGACY);
        assertThat(classifier.classify(object, "https://user:secret@objects.example/proof".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(Classification.AMBIGUOUS);
    }

    private static byte[] repeated(int length, int value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
