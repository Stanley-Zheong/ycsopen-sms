package com.ycsopen.sms.core.common.security.migration;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable deployment trust and compatibility configuration for signed migration preflight. */
public record MigrationPreflightProperties(
        List<SignerAnchor> signerAnchors,
        Set<WriterIdentity> compatibleWriters,
        Set<String> recoveryKeyReferences) {

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public MigrationPreflightProperties {
        signerAnchors = List.copyOf(Objects.requireNonNull(signerAnchors, "signerAnchors"));
        compatibleWriters = Set.copyOf(Objects.requireNonNull(compatibleWriters, "compatibleWriters"));
        recoveryKeyReferences = Set.copyOf(
                Objects.requireNonNull(recoveryKeyReferences, "recoveryKeyReferences"));
        if (compatibleWriters.isEmpty() || recoveryKeyReferences.isEmpty()) {
            throw new IllegalArgumentException("migration compatibility sets must not be empty");
        }

        long active = signerAnchors.stream().filter(anchor -> anchor.state() == AnchorState.ACTIVE).count();
        if (active != 1) {
            throw new IllegalArgumentException("exactly one ACTIVE migration signer is required");
        }
        Set<String> versions = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (SignerAnchor anchor : signerAnchors) {
            if (!versions.add(anchor.version()) || !fingerprints.add(anchor.fingerprint())) {
                throw new IllegalArgumentException("migration signer versions and fingerprints must be unique");
            }
        }
    }

    public enum AnchorState {
        ACTIVE,
        RETIRING,
        RETIRED,
        REVOKED
    }

    public record SignerAnchor(
            String version,
            AnchorState state,
            String fingerprint,
            String x509PublicKeyBase64,
            Long maxSequence) {

        public SignerAnchor {
            require(VERSION, version, "signer version");
            Objects.requireNonNull(state, "state");
            require(SHA256, fingerprint, "signer fingerprint");
            if (x509PublicKeyBase64 == null || x509PublicKeyBase64.isBlank()
                    || x509PublicKeyBase64.length() > 1024) {
                throw new IllegalArgumentException("signer public key must be bounded X.509 Base64");
            }
            if (state == AnchorState.RETIRING && (maxSequence == null || maxSequence < 0)) {
                throw new IllegalArgumentException("RETIRING signer requires an unsigned maxSequence");
            }
            if (state != AnchorState.RETIRING && maxSequence != null) {
                throw new IllegalArgumentException("only RETIRING signer may declare maxSequence");
            }
        }
    }

    public record WriterIdentity(String artifactId, String version, String sourceDigest) {
        public WriterIdentity {
            require(TOKEN, artifactId, "writer artifactId");
            require(VERSION, version, "writer version");
            require(SHA256, sourceDigest, "writer sourceDigest");
        }
    }

    private static void require(Pattern pattern, String value, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not canonical");
        }
    }
}
