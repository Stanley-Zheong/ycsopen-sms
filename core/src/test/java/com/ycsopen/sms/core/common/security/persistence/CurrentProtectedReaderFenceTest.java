package com.ycsopen.sms.core.common.security.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.HmacSignatureVerifier;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.domain.entity.ComplaintRatioStats;
import com.ycsopen.sms.core.domain.entity.TenantApiKey;
import com.ycsopen.sms.core.repository.BlacklistEntryRepository;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.ComplaintRatioStatsRepository;
import com.ycsopen.sms.core.repository.ComplaintRepository;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import com.ycsopen.sms.core.repository.TenantApiKeyRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.service.complaint.ComplaintRatioService;
import com.ycsopen.sms.core.service.routing.BlacklistChecker;
import com.ycsopen.sms.core.service.routing.RoutingContext;
import com.ycsopen.sms.core.service.routing.ThirdPartyBlacklistClient;
import com.ycsopen.sms.core.web.interceptor.HmacAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
class CurrentProtectedReaderFenceTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path MANIFEST = ROOT.resolve("core/src/main/resources/security/protected-data-inventory.json");
    private static final Set<String> CURRENT_SURFACES = Set.of(
            "message-submit-persistence",
            "tenant-registration-persistence",
            "auth-user-hydration-save",
            "hmac-api-key-hydration",
            "blacklist-lookup-hydration",
            "tenant-lifecycle-analytics-hydration-save");
    private static final Set<String> DEDICATED_WRITER_BLOCKERS = Set.of(
            "tenant-registration-persistence");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TenantApiKeyRepository tenantApiKeyRepository;

    @Autowired
    BlacklistEntryRepository blacklistEntryRepository;

    @Autowired
    TenantRepository tenantRepository;

    @Test
    void closedRepositoryProjectionsReturnRequiredCompatibilityDataWithoutProtectedBytes() {
        jdbc.update("""
                INSERT INTO tenants
                    (id, tenant_no, short_name, full_name, unified_social_credit_code,
                     legal_rep_id_no_encrypted, contact_id_no_encrypted, contact_phone_encrypted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, 8250L, "T-FENCE", "fence", "Fence Tenant", "91310000FENCE8250",
                opaqueBytes(0x11), opaqueBytes(0x12), opaqueBytes(0x13));
        jdbc.update("""
                INSERT INTO tenant_api_keys (id, tenant_id, app_key, app_secret_encrypted, status)
                VALUES (?, ?, ?, ?, ?)
                """, 8251L, 8250L, "fenced-app-key", opaqueBytes(0x21), "ACTIVE");
        jdbc.update("""
                INSERT INTO blacklist_entries
                    (id, tenant_id, mobile_encrypted, mobile_hash, list_type, source, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, 8252L, 8250L, opaqueBytes(0x31), "legacy-compatibility-index", "BLACK", "MANUAL", "ACTIVE");

        TenantApiKeyRepository.AuthenticationProjection authentication = tenantApiKeyRepository
                .findAuthenticationByAppKey("fenced-app-key").orElseThrow();
        assertThat(authentication.getId()).isEqualTo(8251L);
        assertThat(authentication.getTenantId()).isEqualTo(8250L);
        assertThat(authentication.getStatus()).isEqualTo(TenantApiKey.Status.ACTIVE);

        BlacklistEntryRepository.LookupProjection blacklist = blacklistEntryRepository
                .findTenantLegacyCompatibilityMatches(
                        "legacy-compatibility-index", 8250L, BlacklistEntry.Status.ACTIVE)
                .getFirst();
        assertThat(blacklist.getId()).isEqualTo(8252L);
        assertThat(blacklist.getTenantId()).isEqualTo(8250L);
        assertThat(blacklist.getStatus()).isEqualTo(BlacklistEntry.Status.ACTIVE);
        assertThat(blacklist.getListType()).isEqualTo(BlacklistEntry.ListType.BLACK);
        assertThat(blacklist.getLegacyIndex()).isEqualTo("legacy-compatibility-index");

        assertThat(tenantRepository.findAllIds())
                .extracting(TenantRepository.IdProjection::getId)
                .contains(8250L);

        assertThat(publicProjectionMethods(TenantApiKeyRepository.AuthenticationProjection.class))
                .containsExactlyInAnyOrder("getId", "getStatus", "getTenantId");
        assertThat(publicProjectionMethods(BlacklistEntryRepository.LookupProjection.class))
                .containsExactlyInAnyOrder("getId", "getStatus", "getListType", "getTenantId", "getLegacyIndex");
        assertThat(publicProjectionMethods(TenantRepository.IdProjection.class)).containsExactly("getId");
    }

    @Test
    void authBlacklistAndAnalyticsBehaviorsConsumeOnlyTheirSafeProjections() throws Exception {
        TenantApiKeyRepository apiKeys = mock(TenantApiKeyRepository.class);
        HmacSignatureVerifier signatures = mock(HmacSignatureVerifier.class);
        TenantApiKeyRepository.AuthenticationProjection authentication = mock(
                TenantApiKeyRepository.AuthenticationProjection.class);
        when(authentication.getTenantId()).thenReturn(42L);
        when(authentication.getStatus()).thenReturn(TenantApiKey.Status.ACTIVE);
        when(apiKeys.findAuthenticationByAppKey("app-key")).thenReturn(Optional.of(authentication));
        when(signatures.verifyTimestamp(anyLong())).thenReturn(true);
        when(signatures.checkAndRecordNonce("nonce-1")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-App-Key", "app-key");
        request.addHeader("X-Timestamp", Long.toString(Instant.now().getEpochSecond()));
        request.addHeader("X-Nonce", "nonce-1");
        request.addHeader("X-Signature", "not-read-by-current-incomplete-path");
        assertThat(new HmacAuthInterceptor(apiKeys, signatures)
                .preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(HmacAuthInterceptor.ATTR_TENANT_ID)).isEqualTo(42L);

        BlindIndexLookupService lookup = mock(BlindIndexLookupService.class);
        ThirdPartyBlacklistClient thirdParty = mock(ThirdPartyBlacklistClient.class);
        when(lookup.lookupBlacklist(eq(42L), any(), eq(BlacklistEntry.Status.ACTIVE)))
                .thenReturn(BlindIndexLookupService.BlacklistLookupResult.whitelisted());
        BlacklistChecker.Result result = new BlacklistChecker(lookup, thirdParty).check(
                RoutingContext.builder().tenantId(42L).build());
        assertThat(result.blocked()).isFalse();
        verifyNoInteractions(thirdParty);

        TenantRepository tenants = mock(TenantRepository.class);
        TenantRepository.IdProjection tenantId = mock(TenantRepository.IdProjection.class);
        when(tenantId.getId()).thenReturn(42L);
        when(tenants.findAllIds()).thenReturn(List.of(tenantId));
        ChannelRepository channels = mock(ChannelRepository.class);
        when(channels.findAll()).thenReturn(List.of());
        MessageTaskRepository messages = mock(MessageTaskRepository.class);
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        ComplaintRatioStatsRepository stats = mock(ComplaintRatioStatsRepository.class);
        when(messages.countByTenantIdAndCreatedAtBetween(eq(42L), any(), any())).thenReturn(20L);
        when(complaints.countByTenantIdAndCreatedAtBetween(eq(42L), any(), any())).thenReturn(1L);
        when(stats.findByStatMonthAndDimensionTypeAndDimensionId(any(), any(), eq(42L)))
                .thenReturn(Optional.empty());
        when(stats.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new ComplaintRatioService(tenants, channels, messages, complaints, stats, 0.003)
                .recalculate(YearMonth.of(2026, 8));

        verify(messages).countByTenantIdAndCreatedAtBetween(eq(42L), any(), any());
        verify(complaints).countByTenantIdAndCreatedAtBetween(eq(42L), any(), any());
    }

    @Test
    void sourceAndInventoryFenceExactCurrentSurfaceAndImplementedBoundaries() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(MANIFEST.toFile());
        Map<String, JsonNode> surfaces = new LinkedHashMap<>();
        manifest.path("source_surfaces").forEach(surface -> surfaces.put(surface.path("id").asText(), surface));

        assertThat(surfaces.keySet()).containsExactlyInAnyOrderElementsOf(CURRENT_SURFACES);
        assertThat(surfaces.entrySet().stream()
                .filter(entry -> entry.getValue().path("obligation_blocking").asBoolean())
                .map(Map.Entry::getKey))
                .containsExactlyInAnyOrderElementsOf(DEDICATED_WRITER_BLOCKERS);
        assertThat(toTextSet(manifest.path("obligation_readiness").path("blocking_surface_ids")))
                .containsExactlyInAnyOrderElementsOf(DEDICATED_WRITER_BLOCKERS);

        JsonNode messageSubmit = surfaces.get("message-submit-persistence");
        assertThat(messageSubmit.path("disposition").asText())
                .isEqualTo("ADOPTED_PROTECTED_ADAPTER_ATOMIC_ENVELOPE_AND_VERSIONED_INDEX_WRITE");
        assertThat(messageSubmit.path("obligation_blocking").asBoolean()).isFalse();
        assertThat(readSource("core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java"))
                .contains("messageTaskProtectionAdapter.prepare",
                        ".mobileQueryIndexes(preparedMobile.queryIndexes())",
                        ".legacyMobileLookupToken(preparedMobile.legacyLookupToken())",
                        "messageTaskProtectionAdapter.save")
                .doesNotContain("messageTaskRepository", "setMobileEncrypted", "setMobileHash");
        assertThat(readSource("core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingContext.java"))
                .contains("BlindIndexPort.OrderedIndexes mobileQueryIndexes",
                        "LegacyMobileLookupToken legacyMobileLookupToken")
                .doesNotContain("phoneNumber", "mobileHash", "String legacy");

        for (JsonNode surface : surfaces.values()) {
            assertThat(surface.path("disposition").asText()).doesNotContain("REQUIRED");
            for (JsonNode source : surface.path("sources")) {
                Path path = ROOT.resolve(source.path("path").asText()).normalize();
                assertThat(path).isRegularFile();
                String content = Files.readString(path);
                for (JsonNode token : source.path("tokens")) {
                    assertThat(content).contains(token.asText());
                }
            }
        }

        assertDeclaredAccessPaths(surfaces, "hmac-api-key-hydration", "tenantApiKeyRepository.");
        assertDeclaredAccessPaths(surfaces, "blacklist-lookup-hydration", "blacklistEntryRepository.");
        assertDeclaredAccessPaths(surfaces, "auth-user-hydration-save", "userRepository.");

        Set<String> tenantPaths = new LinkedHashSet<>(sourcePaths(surfaces.get("tenant-registration-persistence")));
        tenantPaths.addAll(sourcePaths(surfaces.get("tenant-lifecycle-analytics-hydration-save")));
        assertThat(productionFilesContaining("tenantRepository.")).isSubsetOf(tenantPaths.toArray(String[]::new));

        Set<String> messagePaths = sourcePaths(surfaces.get("message-submit-persistence"));
        assertThat(productionFilesContaining("messageTaskRepository.save"))
                .isSubsetOf(messagePaths.toArray(String[]::new));
    }

    @Test
    void repositoryQueriesAndCallSitesCannotSelectOrExposeProtectedState() throws Exception {
        assertSafeQuery(TenantApiKeyRepository.class, "findAuthenticationByAppKey",
                Set.of("id", "tenantId", "status"), "appSecretEncrypted", String.class);
        assertSafeQuery(BlacklistEntryRepository.class, "findSystemLegacyCompatibilityMatches",
                Set.of("id", "status", "listType", "tenantId", "mobileHash"), "mobileEncrypted",
                String.class, BlacklistEntry.Status.class);
        assertSafeQuery(BlacklistEntryRepository.class, "findTenantLegacyCompatibilityMatches",
                Set.of("id", "status", "listType", "tenantId", "mobileHash"), "mobileEncrypted",
                String.class, Long.class, BlacklistEntry.Status.class);
        assertSafeQuery(TenantRepository.class, "findAllIds", Set.of("id"),
                "legalRepIdNoEncrypted", new Class<?>[0]);

        assertThat(readSource("core/src/main/java/com/ycsopen/sms/core/web/interceptor/HmacAuthInterceptor.java"))
                .contains("findAuthenticationByAppKey")
                .doesNotContain("findByAppKey", "getAppSecretEncrypted");
        assertThat(readSource("core/src/main/java/com/ycsopen/sms/core/service/routing/BlacklistChecker.java"))
                .contains("blindIndexLookupService.lookupBlacklist")
                .doesNotContain("blacklistEntryRepository", "List<BlacklistEntry>", "getMobileEncrypted");
        assertThat(readSource("core/src/main/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioService.java"))
                .contains("tenantRepository.findAllIds()")
                .doesNotContain("tenantRepository.findAll()", "import com.ycsopen.sms.core.domain.entity.Tenant;");

        assertOpaquePreservationSource(
                "core/src/main/java/com/ycsopen/sms/core/service/account/AuthService.java",
                "core/src/main/java/com/ycsopen/sms/core/domain/entity/User.java",
                "private byte[] phoneEncrypted");
        assertOpaquePreservationSource(
                "core/src/main/java/com/ycsopen/sms/core/service/tenant/TenantService.java",
                "core/src/main/java/com/ycsopen/sms/core/domain/entity/Tenant.java",
                "private byte[] legalRepIdNoEncrypted",
                "private byte[] contactIdNoEncrypted",
                "private byte[] contactPhoneEncrypted");
    }

    private static void assertSafeQuery(Class<?> repository, String methodName, Set<String> requiredSelections,
                                        String forbiddenSelection, Class<?>... parameterTypes) throws Exception {
        Method method = repository.getMethod(methodName, parameterTypes);
        String query = method.getAnnotation(Query.class).value();
        assertThat(query).doesNotContain(forbiddenSelection);
        requiredSelections.forEach(selection -> assertThat(query).contains("." + selection));
    }

    private static void assertOpaquePreservationSource(String servicePath, String entityPath,
                                                       String... opaqueDeclarations) throws Exception {
        String service = readSource(servicePath);
        assertThat(service).doesNotContain("getPhoneEncrypted", "getLegalRepIdNoEncrypted",
                "getContactIdNoEncrypted", "getContactPhoneEncrypted");
        assertThat(readSource(entityPath)).contains(opaqueDeclarations);
    }

    private static List<String> publicProjectionMethods(Class<?> projection) {
        return Arrays.stream(projection.getMethods()).map(Method::getName).sorted().toList();
    }

    private static Set<String> toTextSet(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static void assertDeclaredAccessPaths(Map<String, JsonNode> surfaces, String surfaceId, String token)
            throws Exception {
        assertThat(productionFilesContaining(token))
                .isSubsetOf(sourcePaths(surfaces.get(surfaceId)).toArray(String[]::new));
    }

    private static Set<String> sourcePaths(JsonNode surface) {
        Set<String> paths = new LinkedHashSet<>();
        surface.path("sources").forEach(source -> paths.add(source.path("path").asText()));
        return paths;
    }

    private static Set<String> productionFilesContaining(String token) throws Exception {
        Path sourceRoot = ROOT.resolve("core/src/main/java");
        Set<String> paths = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> fileContains(path, token))
                    .map(ROOT::relativize)
                    .map(Path::toString)
                    .forEach(paths::add);
        }
        return paths;
    }

    private static boolean fileContains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (Exception error) {
            throw new IllegalStateException("cannot inspect protected reader source", error);
        }
    }

    private static String readSource(String relativePath) throws Exception {
        return Files.readString(ROOT.resolve(relativePath));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("core/pom.xml"))
                    && Files.isRegularFile(current.resolve(".planning/PROJECT.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private static byte[] opaqueBytes(int marker) {
        return new byte[]{0, (byte) 0xff, (byte) marker, (byte) 0x80, 1, 2, 3, 0};
    }
}
