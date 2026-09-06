package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Production composition seam for lifecycle and rewrap; intentionally has no provision/delete API. */
public final class CryptoKeyLifecycleFactory {

    private final KeyReferenceRepository references;
    private final KeyProtectionPort keyProtection;
    private final FieldReferencePublicationFence publicationFence;
    private final JdbcTemplate jdbc;
    private final EnvelopeReferenceInventory.Source snapshotSource;

    public CryptoKeyLifecycleFactory(
            KeyReferenceRepository references,
            KeyProtectionPort keyProtection,
            FieldReferencePublicationFence publicationFence,
            JdbcTemplate jdbc) {
        this(references, keyProtection, publicationFence, jdbc,
                EnvelopeReferenceInventory.unavailableSnapshotEnvelopeSource());
    }

    public CryptoKeyLifecycleFactory(
            KeyReferenceRepository references,
            KeyProtectionPort keyProtection,
            FieldReferencePublicationFence publicationFence,
            JdbcTemplate jdbc,
            EnvelopeReferenceInventory.Source snapshotSource) {
        this.references = Objects.requireNonNull(references, "references");
        this.keyProtection = Objects.requireNonNull(keyProtection, "keyProtection");
        this.publicationFence = Objects.requireNonNull(publicationFence, "publicationFence");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.snapshotSource = Objects.requireNonNull(snapshotSource, "snapshotSource");
    }

    public KeyLifecycleService lifecycle(
            Set<String> requiredSources, List<EnvelopeReferenceInventory.Source> sources) {
        List<EnvelopeReferenceInventory.Source> completeSources = new ArrayList<>(
                Objects.requireNonNull(sources, "sources"));
        List<EnvelopeReferenceInventory.Source> metadataSources =
                EnvelopeReferenceInventory.jdbcMetadataSources(jdbc);
        completeSources.addAll(metadataSources);
        completeSources.add(snapshotSource);
        Set<String> completeRequired = new HashSet<>(
                Objects.requireNonNull(requiredSources, "requiredSources"));
        metadataSources.forEach(source -> completeRequired.add(source.sourceId()));
        completeRequired.add(snapshotSource.sourceId());
        return new KeyLifecycleService(references,
                new EnvelopeReferenceInventory(completeRequired, completeSources));
    }

    public EnvelopeRewrapService rewrap(EnvelopeRewrapService.Store store) {
        return new EnvelopeRewrapService(references, new EnvelopeCodec(), keyProtection,
                Objects.requireNonNull(store, "store"), publicationFence);
    }
}
