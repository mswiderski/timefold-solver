package ai.timefold.solver.service.definition.internal.platform;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reads platform values at the time of use, so that a pod pool claim can supply them after startup.
 * <p>
 * Values come from the configuration.
 * The configuration includes the environment variables and the environment that a pod pool claim applies.
 * A name can be an environment variable name, such as {@link EnvironmentVars#ENV_TIMEFOLD_JOB_ID},
 * or a property name, such as {@code ai.timefold.platform.tenant-id}.
 */
@NullMarked
public final class PlatformEnvironment {

    private PlatformEnvironment() {
    }

    public static Optional<String> get(String name) {
        return get(name, String.class);
    }

    public static <T> Optional<T> get(String name, Class<T> type) {
        return ConfigProvider.getConfig().getOptionalValue(name, type);
    }

    public static @Nullable String getOrNull(String name) {
        return get(name).orElse(null);
    }

    public static Optional<OnStartCommand> onStartCommand() {
        return get(EnvironmentVars.ENV_TIMEFOLD_ON_START_COMMAND).map(OnStartCommand::valueOf);
    }
}
