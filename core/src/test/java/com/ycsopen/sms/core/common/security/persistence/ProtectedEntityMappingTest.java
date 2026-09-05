package com.ycsopen.sms.core.common.security.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantApiKey;
import com.ycsopen.sms.core.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProtectedEntityMappingTest {

    private static final byte[] USER_PHONE = opaqueBytes(0x10);
    private static final byte[] LEGAL_REP_ID = opaqueBytes(0x20);
    private static final byte[] CONTACT_ID = opaqueBytes(0x30);
    private static final byte[] CONTACT_PHONE = opaqueBytes(0x40);
    private static final byte[] BLACKLIST_MOBILE = opaqueBytes(0x50);
    private static final byte[] API_SECRET = opaqueBytes(0x60);

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void mapsEveryCurrentNonMessageVarbinaryAsOpaqueBytesWithoutPublicRawAccessors() throws Exception {
        assertOpaqueField(User.class, "phoneEncrypted", "phone_encrypted");
        assertOpaqueField(Tenant.class, "legalRepIdNoEncrypted", "legal_rep_id_no_encrypted");
        assertOpaqueField(Tenant.class, "contactIdNoEncrypted", "contact_id_no_encrypted");
        assertOpaqueField(Tenant.class, "contactPhoneEncrypted", "contact_phone_encrypted");
        assertOpaqueField(BlacklistEntry.class, "mobileEncrypted", "mobile_encrypted");
        assertOpaqueField(TenantApiKey.class, "appSecretEncrypted", "app_secret_encrypted");

        for (Class<?> entityType : List.of(User.class, Tenant.class, BlacklistEntry.class, TenantApiKey.class)) {
            assertThat(publicRawByteMethods(entityType)).isEmpty();
        }
    }

    @Test
    void hidesProtectedBytesAndOpaqueObjectReferencesFromJsonAndStringRendering() throws Exception {
        User user = new User();
        Tenant tenant = new Tenant();
        BlacklistEntry blacklistEntry = new BlacklistEntry();
        TenantApiKey apiKey = new TenantApiKey();

        setField(user, "phoneEncrypted", USER_PHONE);
        setField(tenant, "legalRepIdNoEncrypted", LEGAL_REP_ID);
        setField(tenant, "contactIdNoEncrypted", CONTACT_ID);
        setField(tenant, "contactPhoneEncrypted", CONTACT_PHONE);
        setField(tenant, "businessLicenseObjectId", "pobj_v1_business_canary");
        setField(tenant, "shortlinkDomainProofObjectId", "pobj_v1_shortlink_canary");
        setField(tenant, "trademarkProofObjectId", "pobj_v1_trademark_canary");
        setField(blacklistEntry, "mobileEncrypted", BLACKLIST_MOBILE);
        setField(apiKey, "appSecretEncrypted", API_SECRET);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (Object entity : List.of(user, tenant, blacklistEntry, apiKey)) {
            String json = mapper.writeValueAsString(entity);
            String rendered = entity.toString();
            assertThat(json)
                    .doesNotContain("Encrypted")
                    .doesNotContain("encrypted")
                    .doesNotContain("ObjectId")
                    .doesNotContain("pobj_v1_");
            assertThat(rendered).doesNotContain("pobj_v1_");
        }

        assertThat(publicNoArgMethods(Tenant.class))
                .doesNotContain("getBusinessLicenseUrl", "getBusinessLicenseObjectId",
                        "getShortlinkDomainProofUrl", "getShortlinkDomainProofObjectId",
                        "getTrademarkProofUrl", "getTrademarkProofObjectId");
    }

    @Test
    void preservesExactOpaqueBytesWhenOnlyUnrelatedEntityStateChanges() throws Exception {
        insertOpaqueRows();
        entityManager.clear();

        User user = entityManager.find(User.class, 8101L);
        Tenant tenant = entityManager.find(Tenant.class, 8102L);
        BlacklistEntry blacklistEntry = entityManager.find(BlacklistEntry.class, 8103L);
        TenantApiKey apiKey = entityManager.find(TenantApiKey.class, 8104L);

        assertThat(readBytes(user, "phoneEncrypted")).containsExactly(USER_PHONE);
        assertThat(readBytes(tenant, "legalRepIdNoEncrypted")).containsExactly(LEGAL_REP_ID);
        assertThat(readBytes(tenant, "contactIdNoEncrypted")).containsExactly(CONTACT_ID);
        assertThat(readBytes(tenant, "contactPhoneEncrypted")).containsExactly(CONTACT_PHONE);
        assertThat(readBytes(blacklistEntry, "mobileEncrypted")).containsExactly(BLACKLIST_MOBILE);
        assertThat(readBytes(apiKey, "appSecretEncrypted")).containsExactly(API_SECRET);

        user.setFailedLoginCount(4);
        tenant.setTrialQuotaUsed(7);
        blacklistEntry.setReason("state-only update");
        apiKey.setRateLimitPerSec(17);
        entityManager.flush();
        entityManager.clear();

        assertColumnBytes("users", "phone_encrypted", 8101L, USER_PHONE);
        assertColumnBytes("tenants", "legal_rep_id_no_encrypted", 8102L, LEGAL_REP_ID);
        assertColumnBytes("tenants", "contact_id_no_encrypted", 8102L, CONTACT_ID);
        assertColumnBytes("tenants", "contact_phone_encrypted", 8102L, CONTACT_PHONE);
        assertColumnBytes("blacklist_entries", "mobile_encrypted", 8103L, BLACKLIST_MOBILE);
        assertColumnBytes("tenant_api_keys", "app_secret_encrypted", 8104L, API_SECRET);
    }

    private void insertOpaqueRows() {
        jdbc.update("INSERT INTO users (id, username, password_hash, phone_encrypted, user_type) VALUES (?, ?, ?, ?, ?)",
                8101L, "opaque-user", "password-hash-unchanged", USER_PHONE, "ADMIN");
        jdbc.update("""
                        INSERT INTO tenants
                            (id, tenant_no, short_name, full_name, unified_social_credit_code,
                             business_license_url, legal_rep_id_no_encrypted, contact_id_no_encrypted,
                             contact_phone_encrypted, shortlink_domain_proof_url, trademark_proof_url)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                8102L, "T-OPAQUE", "opaque", "Opaque Tenant", "91310000OPAQUE8102",
                "pobj_v1_business", LEGAL_REP_ID, CONTACT_ID, CONTACT_PHONE,
                "pobj_v1_shortlink", "pobj_v1_trademark");
        jdbc.update("""
                        INSERT INTO blacklist_entries
                            (id, tenant_id, mobile_encrypted, mobile_hash, list_type, source, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                8103L, 8102L, BLACKLIST_MOBILE, "legacy-locator", "BLACK", "MANUAL", "ACTIVE");
        jdbc.update("""
                        INSERT INTO tenant_api_keys
                            (id, tenant_id, app_key, app_secret_encrypted, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                8104L, 8102L, "opaque-app-key", API_SECRET, "ACTIVE");
    }

    private void assertColumnBytes(String table, String column, long id, byte[] expected) {
        byte[] stored = jdbc.queryForObject(
                "SELECT " + column + " FROM " + table + " WHERE id = ?", byte[].class, id);
        assertThat(stored).containsExactly(expected);
    }

    private static void assertOpaqueField(Class<?> entityType, String fieldName, String columnName)
            throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        assertThat(field.getType()).isEqualTo(byte[].class);
        assertThat(field.getAnnotation(Column.class).name()).isEqualTo(columnName);
    }

    private static List<String> publicRawByteMethods(Class<?> entityType) {
        return List.of(entityType.getMethods()).stream()
                .filter(method -> method.getDeclaringClass() != Object.class)
                .filter(method -> method.getReturnType() == byte[].class
                        || List.of(method.getParameterTypes()).contains(byte[].class))
                .map(Method::getName)
                .toList();
    }

    private static List<String> publicNoArgMethods(Class<?> entityType) {
        return List.of(entityType.getMethods()).stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0)
                .map(Method::getName)
                .toList();
    }

    private static byte[] readBytes(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((byte[]) field.get(target)).clone();
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value instanceof byte[] bytes ? bytes.clone() : value);
    }

    private static byte[] opaqueBytes(int marker) {
        return new byte[]{0, (byte) 0xff, (byte) marker, (byte) 0x80, 1, 2, 3, 0};
    }
}
