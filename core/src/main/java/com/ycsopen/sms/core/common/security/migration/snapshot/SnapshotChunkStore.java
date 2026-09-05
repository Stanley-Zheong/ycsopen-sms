package com.ycsopen.sms.core.common.security.migration.snapshot;

import com.ycsopen.sms.core.common.security.migration.EncryptedSnapshotVerifier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Atomic persistence boundary for complete encrypted snapshot chunks and restore completion. */
public interface SnapshotChunkStore {

    /**
     * Atomically reserves a snapshot identifier before any recovery-key envelope is created.
     * An incomplete reservation is deliberately visible to retained inventory as a failure.
     */
    default void beginSnapshot(String snapshotId) {
        throw SnapshotManifest.invalid();
    }

    void putComplete(String snapshotId, int index, byte[] completeEnvelope);

    List<StoredChunk> inventory(String snapshotId);

    InputStream open(String snapshotId, int index, long expectedEnvelopeSize);

    void markRecoveryComplete(String snapshotId, String targetSchema, String snapshotDigest);

    boolean recoveryComplete(String snapshotId, String targetSchema, String snapshotDigest);

    void deleteSnapshot(String snapshotId);

    /** Atomically publishes the complete canonical manifest after dump success. */
    default void putManifest(String snapshotId, byte[] canonicalManifest) {
        throw SnapshotManifest.invalid();
    }

    /** Complete retained manifests; partial chunk sets are an inventory failure, not snapshots. */
    default List<RetainedManifest> retainedManifests() {
        throw SnapshotManifest.invalid();
    }

    /** Loads the exact canonical manifest retained under one snapshot identifier. */
    default RetainedManifest retainedManifest(String snapshotId) {
        List<RetainedManifest> matches = retainedManifests().stream()
                .filter(manifest -> manifest.snapshotId().equals(snapshotId)).toList();
        if (matches.size() != 1) {
            throw SnapshotManifest.invalid();
        }
        return matches.getFirst();
    }

    record RetainedManifest(String snapshotId, byte[] canonicalManifest) {
        public RetainedManifest {
            if (snapshotId == null || canonicalManifest == null) {
                throw SnapshotManifest.invalid();
            }
            canonicalManifest = canonicalManifest.clone();
        }

        @Override
        public byte[] canonicalManifest() {
            return canonicalManifest.clone();
        }
    }

    record StoredChunk(int index, long envelopeSize) {
        public StoredChunk {
            if (index < 0 || index >= EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT
                    || envelopeSize < 1
                    || envelopeSize > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES) {
                throw SnapshotManifest.invalid();
            }
        }
    }

    /**
     * Run-owned local encrypted store. Every chunk is written to a private temporary file and
     * admitted by an atomic no-replace move; no plaintext path exists in this implementation.
     */
    final class FileStore implements SnapshotChunkStore {
        private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
        private static final Pattern SCHEMA = Pattern.compile("[A-Za-z0-9_]{1,64}");
        private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
        private static final Pattern CHUNK_NAME = Pattern.compile("chunk-([0-9]{6})\\.ycse");
        private static final String CHUNKS = "chunks";
        private static final String COMPLETIONS = "completions";
        private static final String MANIFEST = "manifest.json";

        private final Path root;
        private final CompletionDirectoryObserver completionDirectoryObserver;

        public FileStore(Path root) {
            this(root, CompletionDirectoryObserver.NONE);
        }

        FileStore(Path root, CompletionDirectoryObserver completionDirectoryObserver) {
            Objects.requireNonNull(root, "root");
            this.completionDirectoryObserver = Objects.requireNonNull(
                    completionDirectoryObserver, "completionDirectoryObserver");
            try {
                Path absolute = root.toAbsolutePath().normalize();
                if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(absolute)) {
                    throw SnapshotManifest.invalid();
                }
                this.root = absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
                createPrivateDirectory(this.root.resolve(CHUNKS));
                createPrivateDirectory(this.root.resolve(COMPLETIONS));
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            }
        }

        /** Canonical store identity shared by production commands and lifecycle inventory. */
        public Path canonicalRoot() {
            return root;
        }

        @Override
        public void beginSnapshot(String snapshotId) {
            requireId(snapshotId);
            Path directory = root.resolve(CHUNKS).resolve(snapshotId);
            try {
                Files.createDirectory(directory,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
                snapshotDirectory(snapshotId, false);
            } catch (IOException | RuntimeException failure) {
                throw SnapshotManifest.invalid();
            }
        }

        @Override
        public void putComplete(String snapshotId, int index, byte[] completeEnvelope) {
            requireId(snapshotId);
            if (index < 0 || index >= EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT
                    || completeEnvelope == null || completeEnvelope.length < 1
                    || completeEnvelope.length
                    > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES) {
                throw SnapshotManifest.invalid();
            }
            Path directory = snapshotDirectory(snapshotId, false);
            Path destination = directory.resolve(chunkName(index));
            Path temporary = null;
            try {
                temporary = Files.createTempFile(directory, ".pending-", ".ycse",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.write(temporary, completeEnvelope, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                atomicMoveNew(temporary, destination);
                temporary = null;
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            } finally {
                deleteQuietly(temporary);
            }
        }

        @Override
        public List<StoredChunk> inventory(String snapshotId) {
            requireId(snapshotId);
            Path directory = snapshotDirectory(snapshotId, false);
            List<StoredChunk> chunks = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path path : stream) {
                    if (MANIFEST.equals(path.getFileName().toString())) {
                        continue;
                    }
                    if (Files.isSymbolicLink(path)
                            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw SnapshotManifest.invalid();
                    }
                    Matcher matcher = CHUNK_NAME.matcher(path.getFileName().toString());
                    if (!matcher.matches()) {
                        throw SnapshotManifest.invalid();
                    }
                    int index = Integer.parseInt(matcher.group(1));
                    chunks.add(new StoredChunk(index, Files.size(path)));
                }
            } catch (IOException | NumberFormatException exception) {
                throw SnapshotManifest.invalid();
            }
            chunks.sort(Comparator.comparingInt(StoredChunk::index));
            return List.copyOf(chunks);
        }

        @Override
        public InputStream open(String snapshotId, int index, long expectedEnvelopeSize) {
            requireId(snapshotId);
            if (index < 0 || index >= EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT
                    || expectedEnvelopeSize < 1
                    || expectedEnvelopeSize
                    > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES) {
                throw SnapshotManifest.invalid();
            }
            Path path = snapshotDirectory(snapshotId, false).resolve(chunkName(index));
            try {
                if (Files.isSymbolicLink(path)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(path) != expectedEnvelopeSize) {
                    throw SnapshotManifest.invalid();
                }
                return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            }
        }

        @Override
        public void markRecoveryComplete(
                String snapshotId, String targetSchema, String snapshotDigest) {
            requireId(snapshotId);
            require(SCHEMA, targetSchema);
            require(DIGEST, snapshotDigest);
            Path directory = completionDirectory(snapshotId, true);
            Path destination = directory.resolve(completionName(targetSchema));
            Path temporary = null;
            try {
                temporary = Files.createTempFile(directory, ".pending-", ".complete",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.writeString(temporary, snapshotDigest, StandardCharsets.US_ASCII,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isSymbolicLink(destination)
                            && snapshotDigest.equals(Files.readString(
                            destination, StandardCharsets.US_ASCII))) {
                        return;
                    }
                    throw SnapshotManifest.invalid();
                }
                atomicMoveNew(temporary, destination);
                temporary = null;
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            } finally {
                deleteQuietly(temporary);
            }
        }

        @Override
        public boolean recoveryComplete(
                String snapshotId, String targetSchema, String snapshotDigest) {
            requireId(snapshotId);
            require(SCHEMA, targetSchema);
            require(DIGEST, snapshotDigest);
            Path directory = root.resolve(COMPLETIONS).resolve(snapshotId);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory)) {
                return false;
            }
            Path path = directory.resolve(completionName(targetSchema));
            try {
                return !Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && snapshotDigest.equals(Files.readString(path, StandardCharsets.US_ASCII));
            } catch (IOException exception) {
                return false;
            }
        }

        @Override
        public void deleteSnapshot(String snapshotId) {
            requireId(snapshotId);
            Path directory = root.resolve(CHUNKS).resolve(snapshotId);
            try {
                if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(directory)
                            || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                        throw SnapshotManifest.invalid();
                    }
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                        for (Path path : stream) {
                            if (Files.isSymbolicLink(path)
                                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                                throw SnapshotManifest.invalid();
                            }
                            Files.delete(path);
                        }
                    }
                    Files.delete(directory);
                }
                deleteCompletionDirectory(snapshotId);
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            }
        }

        @Override
        public void putManifest(String snapshotId, byte[] canonicalManifest) {
            requireId(snapshotId);
            SnapshotManifest manifest = SnapshotManifest.parse(canonicalManifest);
            if (!snapshotId.equals(manifest.snapshotId())) {
                throw SnapshotManifest.invalid();
            }
            Path directory = snapshotDirectory(snapshotId, false);
            Path destination = directory.resolve(MANIFEST);
            Path temporary = null;
            try {
                temporary = Files.createTempFile(directory, ".pending-manifest-", ".json",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")));
                Files.write(temporary, canonicalManifest, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                atomicMoveNew(temporary, destination);
                temporary = null;
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            } finally {
                deleteQuietly(temporary);
            }
        }

        @Override
        public List<RetainedManifest> retainedManifests() {
            List<RetainedManifest> retained = new ArrayList<>();
            Path directory = root.resolve(CHUNKS);
            try (DirectoryStream<Path> snapshots = Files.newDirectoryStream(directory)) {
                for (Path snapshot : snapshots) {
                    if (Files.isSymbolicLink(snapshot)
                            || !Files.isDirectory(snapshot, LinkOption.NOFOLLOW_LINKS)) {
                        throw SnapshotManifest.invalid();
                    }
                    String snapshotId = snapshot.getFileName().toString();
                    requireId(snapshotId);
                    Path manifestPath = snapshot.resolve(MANIFEST);
                    if (Files.isSymbolicLink(manifestPath)
                            || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                        throw SnapshotManifest.invalid();
                    }
                    long size = Files.size(manifestPath);
                    if (size < 1 || size > EncryptedSnapshotVerifier.MAXIMUM_MANIFEST_BYTES) {
                        throw SnapshotManifest.invalid();
                    }
                    byte[] bytes = readExactManifest(manifestPath, Math.toIntExact(size));
                    SnapshotManifest manifest = SnapshotManifest.parse(bytes);
                    if (!snapshotId.equals(manifest.snapshotId())) {
                        throw SnapshotManifest.invalid();
                    }
                    retained.add(new RetainedManifest(snapshotId, bytes));
                }
            } catch (IOException | RuntimeException failure) {
                throw SnapshotManifest.invalid();
            }
            retained.sort(Comparator.comparing(RetainedManifest::snapshotId));
            return List.copyOf(retained);
        }

        @Override
        public RetainedManifest retainedManifest(String snapshotId) {
            requireId(snapshotId);
            Path manifestPath = snapshotDirectory(snapshotId, false).resolve(MANIFEST);
            try {
                if (Files.isSymbolicLink(manifestPath)
                        || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw SnapshotManifest.invalid();
                }
                long size = Files.size(manifestPath);
                if (size < 1 || size > EncryptedSnapshotVerifier.MAXIMUM_MANIFEST_BYTES) {
                    throw SnapshotManifest.invalid();
                }
                byte[] bytes = readExactManifest(manifestPath, Math.toIntExact(size));
                SnapshotManifest manifest = SnapshotManifest.parse(bytes);
                if (!snapshotId.equals(manifest.snapshotId())) {
                    throw SnapshotManifest.invalid();
                }
                return new RetainedManifest(snapshotId, bytes);
            } catch (IOException | RuntimeException failure) {
                throw SnapshotManifest.invalid();
            }
        }

        private static byte[] readExactManifest(Path path, int size) throws IOException {
            byte[] bytes = new byte[size];
            try (InputStream input = Files.newInputStream(
                    path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) {
                        throw SnapshotManifest.invalid();
                    }
                    offset += read;
                }
                if (input.read() != -1) {
                    throw SnapshotManifest.invalid();
                }
                return bytes;
            }
        }

        private Path snapshotDirectory(String snapshotId, boolean create) {
            Path directory = root.resolve(CHUNKS).resolve(snapshotId);
            try {
                if (create && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.createDirectory(directory,
                                PosixFilePermissions.asFileAttribute(
                                        PosixFilePermissions.fromString("rwx------")));
                    } catch (FileAlreadyExistsException concurrentCreator) {
                        // An exact-directory revalidation below distinguishes the one safe race
                        // from a symlink, file or attacker-controlled replacement.
                    }
                }
                if (Files.isSymbolicLink(directory)
                        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || !directory.toRealPath(LinkOption.NOFOLLOW_LINKS)
                        .startsWith(root.resolve(CHUNKS))) {
                    throw SnapshotManifest.invalid();
                }
                return directory;
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            }
        }

        private Path completionDirectory(String snapshotId, boolean create) {
            requireId(snapshotId);
            Path directory = root.resolve(COMPLETIONS).resolve(snapshotId);
            try {
                if (create && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    completionDirectoryObserver.beforeCreate(directory);
                    try {
                        Files.createDirectory(directory,
                                PosixFilePermissions.asFileAttribute(
                                        PosixFilePermissions.fromString("rwx------")));
                    } catch (FileAlreadyExistsException concurrentCreator) {
                        // The creator raced only on this exact snapshot directory. The no-follow
                        // type and canonical containment checks below remain authoritative.
                    }
                }
                if (Files.isSymbolicLink(directory)
                        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || !directory.toRealPath(LinkOption.NOFOLLOW_LINKS)
                        .startsWith(root.resolve(COMPLETIONS))) {
                    throw SnapshotManifest.invalid();
                }
                return directory;
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            }
        }

        @FunctionalInterface
        interface CompletionDirectoryObserver {
            CompletionDirectoryObserver NONE = path -> { };

            void beforeCreate(Path path);
        }

        private void deleteCompletionDirectory(String snapshotId) throws IOException {
            Path directory = root.resolve(COMPLETIONS).resolve(snapshotId);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw SnapshotManifest.invalid();
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path path : stream) {
                    if (Files.isSymbolicLink(path)
                            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw SnapshotManifest.invalid();
                    }
                    Files.delete(path);
                }
            }
            Files.delete(directory);
        }

        private static void createPrivateDirectory(Path directory) throws IOException {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(directory,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            }
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw SnapshotManifest.invalid();
            }
        }

        private static void atomicMoveNew(Path source, Path destination) throws IOException {
            Path claim = destination.resolveSibling(destination.getFileName() + ".admission");
            Files.createFile(claim,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
            try {
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    throw SnapshotManifest.invalid();
                }
                try {
                    Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(source, destination);
                }
            } finally {
                Files.deleteIfExists(claim);
            }
        }

        private static String chunkName(int index) {
            return "chunk-%06d.ycse".formatted(index);
        }

        private static String completionName(String schema) {
            return schema + ".complete";
        }

        private static void requireId(String value) {
            require(ID, value);
        }

        private static void require(Pattern pattern, String value) {
            if (value == null || !pattern.matcher(value).matches()) {
                throw SnapshotManifest.invalid();
            }
        }

        private static void deleteQuietly(Path path) {
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The original atomic-store failure remains primary.
                }
            }
        }
    }
}
