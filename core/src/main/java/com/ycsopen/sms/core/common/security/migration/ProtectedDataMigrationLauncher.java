package com.ycsopen.sms.core.common.security.migration;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/** Explicit production composition seam for the {@code phase03-migration} process. */
public final class ProtectedDataMigrationLauncher {

    private ProtectedDataMigrationLauncher() {
    }

    /**
     * Loads exactly one runtime-owned service factory. The help path deliberately bypasses service
     * discovery, Spring, files, MySQL and PKCS11.
     */
    public static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
        if (args.length == 1 && "--help".equals(args[0])) {
            stdout.print(ProtectedDataMigrationCommand.HELP);
            return ProtectedDataMigrationCommand.Exit.ACCEPTED.code();
        }
        List<CommandServicesFactory> factories;
        try {
            factories = ServiceLoader.load(CommandServicesFactory.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
        } catch (RuntimeException | java.util.ServiceConfigurationError failure) {
            factories = List.of();
        }
        if (factories.size() != 1) {
            stderr.print("phase03-migration:error:key_or_provider\n");
            return ProtectedDataMigrationCommand.Exit.KEY_OR_PROVIDER.code();
        }
        return run(args, stdout, stderr, factories.getFirst().create());
    }

    /** Invokes the real command surface after an embedding runtime has explicitly composed it. */
    public static int run(
            String[] args,
            PrintStream stdout,
            PrintStream stderr,
            ProtectedDataMigrationCommand.CommandServices services) {
        return new ProtectedDataMigrationCommand(Objects.requireNonNull(services, "services"))
                .execute(args, stdout, stderr);
    }

    /** Runtime plugin boundary; implementations must construct production dependencies eagerly. */
    public interface CommandServicesFactory {
        ProtectedDataMigrationCommand.CommandServices create();
    }
}
