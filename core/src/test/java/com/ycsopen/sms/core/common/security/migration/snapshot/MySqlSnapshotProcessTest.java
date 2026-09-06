package com.ycsopen.sms.core.common.security.migration.snapshot;

import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.Database;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.FileMetadataReader;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.FixedOsCommandRunner;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.OsAclVerifier;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.OsCommandResult;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.PathMetadata;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.RuntimeIdentity;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.SupportedOperatingSystem;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.TrustedExecutablePolicy;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.FixedArgumentClient.UnixUserIdentityReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlSnapshotProcessTest {

    private static final Path MYSQLDUMP = Path.of("/usr/bin/mysqldump");
    private static final Path MYSQL = Path.of("/usr/bin/mysql");
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final Database DATABASE = new Database(
            "127.0.0.1", 3306, "snapshot_user", "secret".toCharArray(), "snapshot_db");

    @TempDir
    Path directory;

    @Test
    void rejectsTemporaryAndOtherPathsOutsideTheFixedTrustRoots() throws Exception {
        Path local = Files.writeString(directory.resolve("mysqldump"), "not trusted");
        FakeMetadataReader metadata = new FakeMetadataReader();

        assertThatThrownBy(() -> policy(metadata, 501).verify(local.toRealPath(), DIGEST))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(metadata.reads).isZero();
    }

    @Test
    void rejectsUnboundedOrNoncanonicalPathComponentsBeforeMetadataAccess() {
        FakeMetadataReader metadata = new FakeMetadataReader();

        assertThatThrownBy(() -> policy(metadata, 501).verify(
                Path.of("/usr/bin/mysql client"), DIGEST))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThatThrownBy(() -> policy(metadata, 501).verify(
                Path.of("/usr/bin/" + "a".repeat(129)), DIGEST))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThatThrownBy(() -> policy(metadata, 501).verify(
                Path.of("/usr/bin/../bin/mysql"), DIGEST))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(metadata.reads).isZero();
    }

    @Test
    void rejectsSymlinkWritableAncestorWrongOwnershipAndAclWriteGrant() {
        assertPolicyRejected(reader -> reader.replace(
                MYSQLDUMP, value -> metadata(value, true, value.directory(),
                        value.regularFile(), value.executable(), 0, 0, 0755, false)), 501);
        assertPolicyRejected(reader -> reader.replace(
                Path.of("/usr"), value -> metadata(value, false, true,
                        false, false, 0, 0, 0775, false)), 501);
        assertPolicyRejected(reader -> reader.replace(
                MYSQLDUMP, value -> metadata(value, false, false,
                        true, true, 501, 0, 0755, false)), 501);
        assertPolicyRejected(reader -> reader.replace(
                MYSQLDUMP, value -> metadata(value, false, false,
                        true, true, 0, 80, 0755, false)), 501);
        assertPolicyRejected(reader -> reader.replace(
                MYSQLDUMP, value -> metadata(value, false, false,
                        true, true, 0, 0, 0755, true)), 501);
    }

    @Test
    void rejectsRootOrMismatchedIdentityAndAcceptsEqualNonRootIdentity() {
        assertIdentityRejected(0, 501);
        assertIdentityRejected(501, 0);
        assertIdentityRejected(501, 502);

        FakeMetadataReader metadata = new FakeMetadataReader();
        assertThat(policy(metadata, 501, 501).verify(MYSQLDUMP, DIGEST).path())
                .isEqualTo(MYSQLDUMP);
    }

    @Test
    void unixIdentityReadsExactEffectiveAndRealIdCommands() {
        AtomicReference<java.util.List<java.util.List<String>>> argv =
                new AtomicReference<>(new java.util.ArrayList<>());
        UnixUserIdentityReader reader = new UnixUserIdentityReader(command -> {
            argv.get().add(command);
            return command.get(1).equals("-u") ? result("502\n") : result("501\n");
        });

        assertThat(reader.read()).isEqualTo(new RuntimeIdentity(502, 501));
        assertThat(argv.get()).containsExactly(
                java.util.List.of("/usr/bin/id", "-u"),
                java.util.List.of("/usr/bin/id", "-ru"));
    }

    @Test
    void identityCommandAllowlistRejectsEveryOtherIdArgvBeforeStart() {
        AtomicInteger starts = new AtomicInteger();
        FixedOsCommandRunner commands = new FixedOsCommandRunner(builder -> {
            starts.incrementAndGet();
            return builder.start();
        });

        assertThatThrownBy(() -> commands.run(java.util.List.of("/usr/bin/id", "-r")))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThatThrownBy(() -> commands.run(java.util.List.of("/usr/bin/id", "-u", "extra")))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(starts).hasValue(0);
    }

    @Test
    void unixIdentityRejectsMalformedOrFailedEitherCommand() {
        OsCommandResult valid = result("501\n");
        assertIdentityProbeRejected(result("0501\n"), valid);
        assertIdentityProbeRejected(valid, result("501"));
        assertIdentityProbeRejected(result("501\nignored\n"), valid);
        assertIdentityProbeRejected(new OsCommandResult(
                0, ascii("501\n"), new byte[0], true, false, false), valid);
        assertIdentityProbeRejected(valid, new OsCommandResult(
                1, new byte[0], ascii("failed\n"), false, false, false));
        assertIdentityProbeRejected(new OsCommandResult(
                0, ascii("501\n"), ascii("warning\n"), false, false, false), valid);
    }

    @Test
    void aclProofParsesMacAndLinuxAndRejectsAclOrUnknownOutput() {
        Path path = MYSQL;
        requireNoAcl(SupportedOperatingSystem.MACOS,
                "-rwxr-xr-x@  1 root  wheel  1 Sep  6 00:00 " + path + "\n", path);
        requireNoAcl(SupportedOperatingSystem.LINUX,
                "-rwxr-xr-x. 1 0 0 1 Sep 6 00:00 " + path + "\n", path);

        assertAclRejected(SupportedOperatingSystem.MACOS,
                result("-rwxr-xr-x+ 1 root wheel 1 Sep 6 00:00 " + path
                        + "\n 0: group:everyone allow write\n"), path);
        assertAclRejected(SupportedOperatingSystem.LINUX,
                result("-rwxr-xr-x+ 1 0 0 1 Sep 6 00:00 " + path + "\n"), path);
        assertAclRejected(SupportedOperatingSystem.MACOS,
                result("unrecognized " + path + "\n"), path);
        assertAclRejected(SupportedOperatingSystem.LINUX,
                result("-rwxr-xr-x 1 0 0 1 Sep 6 00:00 /usr/bin/other\n"), path);
        assertAclRejected(SupportedOperatingSystem.LINUX,
                new OsCommandResult(0, ascii("-rwxr-xr-x 1 0 0 1 x " + path + "\n"),
                        new byte[0], true, false, false), path);
        assertAclRejected(SupportedOperatingSystem.LINUX,
                new OsCommandResult(2, new byte[0], ascii("failed\n"),
                        false, false, false), path);
        assertThatThrownBy(() -> SupportedOperatingSystem.current("unsupported-os"))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
    }

    @Test
    void authorityHelperClearsInheritedEnvironment() {
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        FixedOsCommandRunner commands = new FixedOsCommandRunner(builder -> {
            environment.set(Map.copyOf(builder.environment()));
            return builder.start();
        });

        new UnixUserIdentityReader(commands).read();

        assertThat(environment).hasValue(Map.of("LC_ALL", "C", "LANG", "C"));
        assertThat(environment.get()).doesNotContainKeys(
                "PATH", "HOME", "TMPDIR", "LD_PRELOAD", "LD_LIBRARY_PATH",
                "DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH", "MYSQL_HOME",
                "LIBMYSQL_PLUGIN_DIR");
    }

    @Test
    void revalidationRejectsBeforePasswordReadOrProcessStart() {
        FakeMetadataReader metadata = new FakeMetadataReader();
        AtomicInteger passwordReads = new AtomicInteger();
        AtomicInteger processStarts = new AtomicInteger();
        FixedArgumentClient client = client(
                metadata, 501,
                database -> {
                    passwordReads.incrementAndGet();
                    return database.password();
                },
                builder -> {
                    processStarts.incrementAndGet();
                    return builder.start();
                });
        metadata.replace(Path.of("/usr/bin"), value -> metadata(
                value, false, true, false, false, 0, 0, 0777, false));

        assertThatThrownBy(() -> client.startDump(DATABASE))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(passwordReads).hasValue(0);
        assertThat(processStarts).hasValue(0);
    }

    @Test
    void trustedSystemExecutableRunsWithOnlyTheFixedEnvironment() throws Exception {
        Path trusted = Path.of("/usr/bin/true").toRealPath();
        String digest = sha256(trusted);
        AtomicReference<Map<String, String>> environment = new AtomicReference<>();
        AtomicInteger passwordReads = new AtomicInteger();
        FixedArgumentClient client = new FixedArgumentClient(
                trusted, digest, trusted, digest,
                TrustedExecutablePolicy.system(() -> new RuntimeIdentity(501, 501)),
                database -> {
                    passwordReads.incrementAndGet();
                    return database.password();
                },
                builder -> {
                    environment.set(Map.copyOf(builder.environment()));
                    return builder.start();
                });

        try (MySqlSnapshotProcess.DumpSession dump = client.startDump(DATABASE)) {
            dump.awaitSuccess();
        }

        assertThat(passwordReads).hasValue(1);
        assertThat(environment).hasValue(Map.of(
                "MYSQL_PWD", "secret",
                "LC_ALL", "C",
                "LANG", "C",
                "TZ", "UTC"));
        assertThat(environment.get()).doesNotContainKeys(
                "PATH", "HOME", "TMPDIR", "LD_PRELOAD", "LD_LIBRARY_PATH",
                "DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH", "MYSQL_HOME",
                "LIBMYSQL_PLUGIN_DIR");
    }

    private static void assertPolicyRejected(
            Consumer<FakeMetadataReader> mutation, long effectiveUserId) {
        FakeMetadataReader metadata = new FakeMetadataReader();
        mutation.accept(metadata);
        assertThatThrownBy(() -> client(
                metadata, effectiveUserId, Database::password, ProcessBuilder::start))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
    }

    private static void assertIdentityRejected(long effectiveUserId, long realUserId) {
        FakeMetadataReader metadata = new FakeMetadataReader();
        assertThatThrownBy(() -> policy(
                metadata, effectiveUserId, realUserId).verify(MYSQLDUMP, DIGEST))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(metadata.reads).isZero();
    }

    private static void assertIdentityProbeRejected(
            OsCommandResult effective, OsCommandResult real) {
        assertThatThrownBy(() -> new UnixUserIdentityReader(command ->
                command.get(1).equals("-u") ? effective : real).read())
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
    }

    private static void requireNoAcl(
            SupportedOperatingSystem operatingSystem, String output, Path path) {
        new OsAclVerifier(ignored -> result(output), operatingSystem).requireNoAcl(path);
    }

    private static void assertAclRejected(
            SupportedOperatingSystem operatingSystem, OsCommandResult result, Path path) {
        assertThatThrownBy(() -> new OsAclVerifier(
                ignored -> result, operatingSystem).requireNoAcl(path))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
    }

    private static OsCommandResult result(String stdout) {
        return new OsCommandResult(0, ascii(stdout), new byte[0], false, false, false);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static FixedArgumentClient client(
            FakeMetadataReader metadata,
            long effectiveUserId,
            FixedArgumentClient.PasswordReader passwordReader,
            FixedArgumentClient.ProcessStarter processStarter) {
        return new FixedArgumentClient(
                MYSQLDUMP, DIGEST, MYSQL, DIGEST,
                policy(metadata, effectiveUserId), passwordReader, processStarter);
    }

    private static TrustedExecutablePolicy policy(
            FakeMetadataReader metadata, long effectiveUserId) {
        return policy(metadata, effectiveUserId, effectiveUserId);
    }

    private static TrustedExecutablePolicy policy(
            FakeMetadataReader metadata, long effectiveUserId, long realUserId) {
        return new TrustedExecutablePolicy(
                metadata, () -> new RuntimeIdentity(effectiveUserId, realUserId),
                Set.of(Path.of("/usr/bin"), Path.of("/opt/ycsopen/mysql-client")));
    }

    private static PathMetadata metadata(
            PathMetadata value,
            boolean symbolicLink,
            boolean directory,
            boolean regularFile,
            boolean executable,
            long uid,
            long gid,
            int mode,
            boolean aclWrite) {
        return new PathMetadata(
                value.canonicalPath(), symbolicLink, directory, regularFile, executable,
                uid, gid, mode, value.size(), value.fileKey(), aclWrite);
    }

    private static String sha256(Path path) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static final class FakeMetadataReader implements FileMetadataReader {
        private final Map<Path, PathMetadata> values = new HashMap<>();
        private int reads;

        private FakeMetadataReader() {
            directory(Path.of("/"));
            directory(Path.of("/usr"));
            directory(Path.of("/usr/bin"));
            file(MYSQLDUMP);
            file(MYSQL);
        }

        @Override
        public PathMetadata read(Path path) {
            reads++;
            PathMetadata value = values.get(path);
            if (value == null) {
                throw SnapshotManifest.invalid();
            }
            return value;
        }

        @Override
        public String sha256(Path path, long expectedSize) {
            return DIGEST;
        }

        private void directory(Path path) {
            values.put(path, new PathMetadata(
                    path, false, true, false, false, 0, 0, 0755, 0,
                    path + ":inode", false));
        }

        private void file(Path path) {
            values.put(path, new PathMetadata(
                    path, false, false, true, true, 0, 0, 0755, 128,
                    path + ":inode", false));
        }

        private void replace(Path path, UnaryOperator<PathMetadata> replacement) {
            values.compute(path, (ignored, value) -> replacement.apply(value));
        }
    }
}
