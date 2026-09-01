package com.ycsopen.sms.core.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Real-service launcher and PKCS11 fixture provisioning for the Plan-14 snapshot proof. */
final class Phase03EncryptedSnapshotHarness {

    private Phase03EncryptedSnapshotHarness() {
    }

    static void runRealProof() throws Exception {
        Path storeRoot = null;
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.SoftHsmHandoff handoff = fixtures.softHsm().softHsm();
            Path destination = handoff.config().getParent().getParent();
            provisionKeys(destination, handoff);
            storeRoot = Files.createTempDirectory(
                    repositoryRoot().resolve("core/target/phase03"), "snapshot-store-");

            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            char[] rootPassword = fixtures.mysql().rootPassword();
            String rootCredential = new String(rootPassword);
            java.util.Arrays.fill(rootPassword, '\0');
            try {
                Phase03ServiceHarness.CommandResult result = Phase03ServiceHarness.runChecked(
                        List.of(javaExecutable.toString(), "-cp", System.getProperty("java.class.path"),
                                Phase03MigrationIntegrationTest.class.getName(), "real-snapshot-proof"),
                        Map.ofEntries(
                                Map.entry("SOFTHSM2_CONF", handoff.config().toString()),
                                Map.entry("PHASE03_HSM_LIBRARY", handoff.library().toString()),
                                Map.entry("PHASE03_HSM_PIN_SOURCE", handoff.pinSource().toString()),
                                Map.entry("PHASE03_HSM_SLOT", Long.toUnsignedString(handoff.slot())),
                                Map.entry("PHASE03_MYSQL_HOST", fixtures.mysql().host()),
                                Map.entry("PHASE03_MYSQL_PORT", Integer.toString(fixtures.mysql().port())),
                                Map.entry("PHASE03_MYSQL_CONTAINER", fixtures.mysql().containerName()),
                                Map.entry("PHASE03_MYSQL_USER", fixtures.mysql().username()),
                                Map.entry("PHASE03_MYSQL_PASSWORD", fixtures.mysql().password()),
                                Map.entry("PHASE03_MYSQL_ROOT_PASSWORD", rootCredential),
                                Map.entry("PHASE03_SNAPSHOT_STORE", storeRoot.toString())));
                if (result.stdout().contains(rootCredential) || result.stderr().contains(rootCredential)) {
                    throw new IllegalStateException("root credential reached child output");
                }
                if (!"PHASE03_SNAPSHOT_REAL_PROOF_PASS\n".equals(result.stdout())) {
                    throw new IllegalStateException("snapshot child proof did not return PASS");
                }
            } finally {
                // The immutable environment String is scoped to this block and never persisted.
                rootCredential = null;
            }
        } finally {
            deleteTree(storeRoot);
        }
    }

    private static void provisionKeys(
            Path destination, Phase03ServiceHarness.SoftHsmHandoff handoff) throws Exception {
        Path header;
        try (var files = Files.walk(destination.resolve("source"))) {
            List<Path> headers = files
                    .filter(path -> path.getFileName().toString().equals("cryptoki.h"))
                    .filter(Files::isRegularFile)
                    .toList();
            if (headers.size() != 1) {
                throw new IllegalStateException("SoftHSM header identity is ambiguous");
            }
            header = headers.getFirst();
        }
        Path source = destination.resolve("runtime/plan14-key-provisioner.c");
        Path helper = destination.resolve("runtime/plan14-key-provisioner");
        Files.writeString(source, NATIVE_KEY_PROVISIONER, StandardCharsets.US_ASCII);
        Phase03ServiceHarness.runChecked(List.of(
                "/usr/bin/cc", "-std=c11", "-O2", "-I", header.getParent().toString(),
                source.toString(), handoff.library().toString(),
                "-Wl,-rpath," + handoff.library().getParent(), "-o", helper.toString()), Map.of());
        Phase03ServiceHarness.runChecked(List.of(
                        helper.toString(), handoff.pinSource().toString(),
                        Long.toUnsignedString(handoff.slot())),
                Map.of("SOFTHSM2_CONF", handoff.config().toString()));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("scripts/lib/phase-03/service_checks.rb"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root unavailable");
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("snapshot fixture cleanup failed", exception);
        }
    }

    private static final String NATIVE_KEY_PROVISIONER = """
            #include "cryptoki.h"
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>

            static void trim(char *value) {
              size_t size = strlen(value);
              while (size > 0 && (value[size - 1] == '\\n' || value[size - 1] == '\\r')) value[--size] = 0;
            }
            static int generate(CK_SESSION_HANDLE session, const char *label, int aes) {
              CK_BBOOL yes = CK_TRUE, no = CK_FALSE;
              CK_OBJECT_CLASS klass = CKO_SECRET_KEY;
              CK_KEY_TYPE type = aes ? CKK_AES : CKK_GENERIC_SECRET;
              CK_ULONG length = 32;
              CK_ATTRIBUTE attrs[] = {
                {CKA_CLASS, &klass, sizeof(klass)}, {CKA_KEY_TYPE, &type, sizeof(type)},
                {CKA_TOKEN, &yes, sizeof(yes)}, {CKA_PRIVATE, &yes, sizeof(yes)},
                {CKA_SENSITIVE, &yes, sizeof(yes)}, {CKA_EXTRACTABLE, &no, sizeof(no)},
                {CKA_ENCRYPT, aes ? &yes : &no, sizeof(yes)},
                {CKA_DECRYPT, aes ? &yes : &no, sizeof(yes)},
                {CKA_WRAP, aes ? &yes : &no, sizeof(yes)},
                {CKA_UNWRAP, aes ? &yes : &no, sizeof(yes)},
                {CKA_SIGN, aes ? &no : &yes, sizeof(yes)},
                {CKA_VERIFY, aes ? &no : &yes, sizeof(yes)},
                {CKA_VALUE_LEN, &length, sizeof(length)},
                {(CK_ATTRIBUTE_TYPE)CKA_LABEL, (void *)label, strlen(label)}
              };
              CK_MECHANISM mechanism = {
                aes ? CKM_AES_KEY_GEN : CKM_GENERIC_SECRET_KEY_GEN, NULL_PTR, 0
              };
              CK_OBJECT_HANDLE key = 0;
              return C_GenerateKey(session, &mechanism, attrs,
                sizeof(attrs) / sizeof(attrs[0]), &key) == CKR_OK ? 0 : 1;
            }
            int main(int argc, char **argv) {
              if (argc != 3) return 64;
              FILE *pins = fopen(argv[1], "r");
              char so_pin[128] = {0}, user_pin[128] = {0};
              if (!pins || !fgets(so_pin, sizeof(so_pin), pins) ||
                  !fgets(user_pin, sizeof(user_pin), pins)) return 65;
              fclose(pins); trim(user_pin);
              if (C_Initialize(NULL_PTR) != CKR_OK) return 66;
              char *end = NULL;
              unsigned long long parsed = strtoull(argv[2], &end, 10);
              if (!end || *end != 0) return 67;
              CK_SESSION_HANDLE session = 0;
              if (C_OpenSession((CK_SLOT_ID)parsed, CKF_SERIAL_SESSION | CKF_RW_SESSION,
                                NULL_PTR, NULL_PTR, &session) != CKR_OK) return 68;
              if (C_Login(session, CKU_USER, (CK_UTF8CHAR_PTR)user_pin,
                          strlen(user_pin)) != CKR_OK) return 69;
              const char *aes[] = {
                "ycs.field-encryption-kek.v1", "ycs.snapshot-recovery.v1"
              };
              const char *hmac[] = {
                "ycs.mobile-blind-index.v1", "ycs.object-capability-digest.v1",
                "ycs.registration-upload-digest.v1"
              };
              int failed = 0;
              for (size_t i = 0; i < sizeof(aes) / sizeof(aes[0]); i++)
                failed |= generate(session, aes[i], 1);
              for (size_t i = 0; i < sizeof(hmac) / sizeof(hmac[0]); i++)
                failed |= generate(session, hmac[i], 0);
              C_Logout(session); C_CloseSession(session); C_Finalize(NULL_PTR);
              memset(so_pin, 0, sizeof(so_pin)); memset(user_pin, 0, sizeof(user_pin));
              return failed ? 70 : 0;
            }
            """;
}
