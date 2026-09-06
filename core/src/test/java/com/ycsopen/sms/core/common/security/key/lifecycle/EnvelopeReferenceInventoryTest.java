package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeReferenceInventoryTest {

    @Test
    void fixedDatabaseEnvelopeSourcesExactlyMatchReviewedInventory() throws Exception {
        Set<String> reviewed = new HashSet<>();
        try (InputStream input = getClass().getResourceAsStream(
                "/security/protected-data-inventory.json")) {
            JsonNode targets = new ObjectMapper().readTree(input).path("targets");
            for (JsonNode target : targets) {
                if ("DATABASE_FIELD".equals(target.path("kind").asText())
                        && "INLINE_ENVELOPE".equals(
                        target.path("storage_representation").asText())) {
                    reviewed.add(target.path("id").asText());
                }
            }
        }

        assertThat(EnvelopeReferenceInventory.databaseFieldTargetIds())
                .hasSize(17)
                .containsExactlyInAnyOrderElementsOf(reviewed);
    }
}
