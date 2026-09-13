package com.ycsopen.sms.core.config;

import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.domain.entity.ComplaintRatioStats;
import com.ycsopen.sms.core.domain.entity.FixedWidthCharConverter;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantProtocolCredential;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseSchemaCompatibilityTest {

    @Test
    void mapsFixedWidthMigrationColumnsWithTheirExactMysqlTypes() throws Exception {
        assertFixedWidthColumn(BlacklistEntry.class, "mobileHash", "mobile_hash", 64, "char(64)");
        assertFixedWidthColumn(MessageTask.class, "mobileHash", "mobile_hash", 64, "char(64)");
        assertFixedWidthColumn(ComplaintRatioStats.class, "statMonth", "stat_month", 7, "char(7)");
        assertFixedWidthColumn(Tenant.class, "inspectionCreditCode", "inspection_credit_code", 18, "char(18)");
    }

    @Test
    void mapsInspectionConfidenceAsTheMigrationDecimalType() throws Exception {
        Field field = Tenant.class.getDeclaredField("inspectionConfidence");
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(field.getType()).isEqualTo(BigDecimal.class);
        assertThat(column.name()).isEqualTo("inspection_confidence");
        assertThat(column.precision()).isEqualTo(5);
        assertThat(column.scale()).isEqualTo(4);
        assertThat(column.columnDefinition()).isEqualTo("decimal(5,4)");
    }

    @Test
    void mapsTenantCustomerLevelAsTheMigrationTinyintType() throws Exception {
        Column column = Tenant.class.getDeclaredField("customerLevel").getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("tinyint");
    }

    @Test
    void mapsProtocolCredentialNativeEnumsAsEnums() throws Exception {
        assertStringEnum(TenantProtocolCredential.class, "protocol");
        assertStringEnum(TenantProtocolCredential.class, "status");
    }

    @Test
    void removesJdbcPaddingWithoutChangingStoredValues() {
        FixedWidthCharConverter converter = new FixedWidthCharConverter();

        assertThat(converter.convertToDatabaseColumn("p3c1_token"))
                .isEqualTo("p3c1_token");
        assertThat(converter.convertToEntityAttribute("p3c1_token   "))
                .isEqualTo("p3c1_token");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    private static void assertFixedWidthColumn(
            Class<?> entityType, String fieldName, String columnName, int length, String columnDefinition)
            throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        Convert convert = field.getAnnotation(Convert.class);

        assertThat(column).isNotNull();
        assertThat(convert).isNotNull();
        assertThat(convert.converter()).isEqualTo(FixedWidthCharConverter.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.columnDefinition()).isEqualTo(columnDefinition);
    }

    private static void assertStringEnum(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        Enumerated enumerated = field.getAnnotation(Enumerated.class);

        assertThat(field.getType().isEnum()).isTrue();
        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
    }
}
