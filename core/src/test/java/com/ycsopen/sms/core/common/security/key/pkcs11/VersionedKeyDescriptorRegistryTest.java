package com.ycsopen.sms.core.common.security.key.pkcs11;

import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionedKeyDescriptorRegistryTest {

    @Test
    void loadsExactConfiguredIdentitiesWithDatabaseOwnedStatesAcrossRestart() {
        List<Pkcs11KeyDescriptor> configured = VersionedKeyDescriptorRegistry.configured(
                "FIELD_ENCRYPTION_KEK|1|field-kek.v1|field-alias-v1,"
                        + "FIELD_ENCRYPTION_KEK|2|field-kek.v2|field-alias-v2,"
                        + "SNAPSHOT_RECOVERY|1|snapshot.v1|snapshot-alias,"
                        + "MOBILE_BLIND_INDEX|1|mobile.v1|mobile-alias,"
                        + "OBJECT_CAPABILITY_DIGEST|1|object.v1|object-alias,"
                        + "REGISTRATION_UPLOAD_DIGEST|1|registration.v1|registration-alias",
                List.of());
        MutableReferences references = new MutableReferences(List.of(
                reference(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1,
                        "field-kek.v1", KeyState.DECRYPT_ONLY),
                reference(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 2,
                        "field-kek.v2", KeyState.ACTIVE),
                reference(KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1,
                        "snapshot.v1", KeyState.ACTIVE),
                reference(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 1,
                        "mobile.v1", KeyState.ACTIVE),
                reference(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                        "object.v1", KeyState.ACTIVE),
                reference(KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                        "registration.v1", KeyState.ACTIVE)));

        List<Pkcs11KeyDescriptor> restarted =
                new VersionedKeyDescriptorRegistry(references, configured).load();

        assertThat(restarted).filteredOn(key -> key.purpose()
                        == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK)
                .extracting(Pkcs11KeyDescriptor::keyVersion, Pkcs11KeyDescriptor::state)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L,
                                Pkcs11KeyDescriptor.State.DECRYPT_ONLY),
                        org.assertj.core.groups.Tuple.tuple(2L,
                                Pkcs11KeyDescriptor.State.ACTIVE));
        assertThat(new ActiveFieldKeyReference(references).current()).isEqualTo("field-kek.v2");
    }

    @Test
    void rejectsMissingExtraOrIdentityDriftAndMalformedConfiguration() {
        List<Pkcs11KeyDescriptor> configured = VersionedKeyDescriptorRegistry.configured(
                "FIELD_ENCRYPTION_KEK|1|field-kek.v1|field-alias", List.of());
        MutableReferences missing = new MutableReferences(List.of());
        assertThatThrownBy(() -> new VersionedKeyDescriptorRegistry(missing, configured).load())
                .hasMessage("PKCS11_DESCRIPTOR_REGISTRY_REJECTED");
        MutableReferences drift = new MutableReferences(List.of(reference(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1,
                "field-kek.other", KeyState.ACTIVE)));
        assertThatThrownBy(() -> new VersionedKeyDescriptorRegistry(drift, configured).load())
                .hasMessage("PKCS11_DESCRIPTOR_REGISTRY_REJECTED");
        assertThatThrownBy(() -> VersionedKeyDescriptorRegistry.configured(
                "FIELD_ENCRYPTION_KEK|not-a-version|field-kek.v1|alias", List.of()))
                .hasMessage("PKCS11_DESCRIPTOR_REGISTRY_REJECTED");
    }

    private static KeyReferenceRepository.KeyReference reference(
            KeyReferenceRepository.Purpose purpose,
            long version,
            String reference,
            KeyState state) {
        return new KeyReferenceRepository.KeyReference(
                purpose, version, "pkcs11", reference, state, 0, false, 0);
    }

    private static final class MutableReferences implements KeyReferenceRepository {
        private final List<KeyReference> values;

        private MutableReferences(List<KeyReference> values) {
            this.values = new ArrayList<>(values);
        }

        @Override
        public List<KeyReference> findByPurpose(Purpose purpose) {
            return values.stream().filter(value -> value.purpose() == purpose).toList();
        }

        @Override
        public List<KeyReference> findAll() {
            return List.copyOf(values);
        }

        @Override
        public boolean transitionAtomicallyGuarded(
                Purpose purpose,
                List<Transition> transitions,
                java.util.function.BooleanSupplier guard) {
            return false;
        }
    }
}
