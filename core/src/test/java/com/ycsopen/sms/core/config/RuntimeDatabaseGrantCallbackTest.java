package com.ycsopen.sms.core.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeDatabaseGrantCallbackTest {

    private final RuntimeDatabaseGrantCallback callback =
            new RuntimeDatabaseGrantCallback(false, "", "%");

    @Test
    void rejectsBinloggedMysqlWithoutTrustedRoutineCreators() throws Exception {
        Connection connection = mysqlConnection(true, false);

        assertThatThrownBy(() -> callback.verifyAuditRoutinePrerequisite(connection))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("log_bin_trust_function_creators=ON")
                .hasMessageContaining("V1500");
    }

    @Test
    void acceptsMysqlWhenBinlogIsDisabledOrRoutineCreatorsAreTrusted() throws Exception {
        assertThatCode(() -> callback.verifyAuditRoutinePrerequisite(mysqlConnection(false, false)))
                .doesNotThrowAnyException();
        assertThatCode(() -> callback.verifyAuditRoutinePrerequisite(mysqlConnection(true, true)))
                .doesNotThrowAnyException();
    }

    private static Connection mysqlConnection(boolean logBin, boolean trustCreators) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(
                "SELECT @@GLOBAL.log_bin, @@GLOBAL.log_bin_trust_function_creators"))
                .thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(logBin);
        when(result.getBoolean(2)).thenReturn(trustCreators);
        return connection;
    }
}
