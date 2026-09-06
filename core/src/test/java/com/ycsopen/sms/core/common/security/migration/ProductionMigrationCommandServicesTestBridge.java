package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess;
import java.nio.file.Path;
import java.util.Objects;

/** Test-source bridge that substitutes only the external MySQL process boundary. */
public final class ProductionMigrationCommandServicesTestBridge {

    private ProductionMigrationCommandServicesTestBridge() {
    }

    public static ProtectedDataMigrationCommand.CommandServices create(
            Path signedConfiguration,
            MySqlSnapshotProcess snapshotProcess) {
        Objects.requireNonNull(signedConfiguration, "signedConfiguration");
        Objects.requireNonNull(snapshotProcess, "snapshotProcess");
        ProductionMigrationCommandServicesFactory factory =
                new ProductionMigrationCommandServicesFactory();
        return factory.compose(
                ProductionMigrationCommandServicesFactory.loadConfiguration(signedConfiguration),
                snapshotProcess);
    }
}
