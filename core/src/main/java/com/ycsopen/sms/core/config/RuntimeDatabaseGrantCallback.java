package com.ycsopen.sms.core.config;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Verifies the audit migration prerequisite and applies post-migration runtime grants. */
@Component
public class RuntimeDatabaseGrantCallback implements Callback {

    private static final Set<String> APPEND_ONLY_TABLES = Set.of(
            "privileged_operation_audits", "security_events");
    private final boolean enabled;
    private final String runtimeUser;
    private final String runtimeHost;

    public RuntimeDatabaseGrantCallback(
            @Value("${ycsopen.database.runtime-grants.enabled:false}") boolean enabled,
            @Value("${spring.datasource.username:}") String runtimeUser,
            @Value("${ycsopen.database.runtime-grants.host:%}") String runtimeHost) {
        this.enabled = enabled;
        this.runtimeUser = enabled ? runtimeUser(runtimeUser) : runtimeUser;
        this.runtimeHost = enabled ? runtimeHost(runtimeHost) : runtimeHost;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return isAuditStorageMigration(event, context) || enabled && event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            if (isAuditStorageMigration(event, context)) {
                verifyAuditRoutinePrerequisite(context.getConnection());
            } else {
                apply(context.getConnection());
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("audit migration prerequisite or runtime grants failed", failure);
        }
    }

    void verifyAuditRoutinePrerequisite(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null || !product.startsWith("MySQL")) {
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet values = statement.executeQuery(
                     "SELECT @@GLOBAL.log_bin, @@GLOBAL.log_bin_trust_function_creators")) {
            if (!values.next()) {
                throw new IllegalStateException("MySQL audit migration prerequisite query returned no row");
            }
            if (values.getBoolean(1) && !values.getBoolean(2)) {
                throw new IllegalStateException("Flyway migration V1500 requires MySQL "
                        + "log_bin_trust_function_creators=ON when binary logging is enabled; "
                        + "ask the database administrator to enable it before migration");
            }
        }
    }

    void apply(Connection connection) throws SQLException {
        String schema = identifier(connection.getCatalog(), "database");
        String principal = "'" + runtimeUser + "'@'" + runtimeHost + "'";
        removeSchemaWideGrant(connection, schema, principal);

        List<String> tables = new ArrayList<>();
        try (var statement = connection.prepareStatement("""
                SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """)) {
            statement.setString(1, schema);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) tables.add(identifier(rows.getString(1), "table"));
            }
        }
        if (tables.isEmpty()) {
            throw new IllegalStateException("runtime database grants found no migrated tables");
        }
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                String privileges = "flyway_schema_history".equals(table)
                        ? "SELECT"
                        : APPEND_ONLY_TABLES.contains(table)
                        ? "SELECT, INSERT"
                        : "SELECT, INSERT, UPDATE, DELETE";
                statement.execute("GRANT " + privileges + " ON `" + schema + "`.`" + table
                        + "` TO " + principal);
            }
            statement.execute("GRANT EXECUTE ON PROCEDURE `" + schema
                    + "`.`finalize_privileged_operation_audit` TO " + principal);
        }
    }

    private static void removeSchemaWideGrant(Connection connection, String schema, String principal)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("REVOKE ALL PRIVILEGES ON `" + schema + "`.* FROM " + principal);
        } catch (SQLException noSchemaGrant) {
            if (noSchemaGrant.getErrorCode() != 1141) {
                throw noSchemaGrant;
            }
        }
    }

    private static String runtimeUser(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("runtime user is invalid");
        }
        return value;
    }

    private static String runtimeHost(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_%.-]{1,64}")) {
            throw new IllegalArgumentException("runtime host is invalid");
        }
        return value;
    }

    private static String identifier(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9_]{1,64}")) {
            throw new IllegalStateException(label + " identifier is invalid");
        }
        return value;
    }

    private static boolean isAuditStorageMigration(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE
                && context.getMigrationInfo() != null
                && context.getMigrationInfo().getVersion() != null
                && "1500".equals(context.getMigrationInfo().getVersion().getVersion());
    }

    @Override
    public String getCallbackName() {
        return "runtime-database-least-privilege";
    }
}
