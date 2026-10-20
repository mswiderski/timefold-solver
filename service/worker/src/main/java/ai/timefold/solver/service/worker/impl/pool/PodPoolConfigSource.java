package ai.timefold.solver.service.worker.impl.pool;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import io.smallrye.config.EnvConfigSource;

/**
 * Holds the environment that a pod pool claim sends to the solver worker.
 * <p>
 * The source is empty until a claim. A claim fills it once and seals it.
 * Property names resolve the same way as for environment variables.
 * Thus {@code AI_TIMEFOLD_PLATFORM_TENANT_ID} also gives the value of {@code ai.timefold.platform.tenant-id}.
 * The ordinal is above the environment variables (300) and below the system properties (400).
 */
@NullMarked
public final class PodPoolConfigSource implements ConfigSource {

    public static final int ORDINAL = 350;
    public static final String NAME = "PodPoolConfigSource";

    private static volatile @Nullable EnvConfigSource claimEnvironment;

    /**
     * Applies the claim environment.
     *
     * @param environment environment variable names to values
     * @throws IllegalStateException if a claim environment is already applied
     */
    static synchronized void apply(Map<String, String> environment) {
        if (claimEnvironment != null) {
            throw new IllegalStateException("The pod pool claim environment is already applied.");
        }
        claimEnvironment = new EnvConfigSource(Map.copyOf(environment), ORDINAL);
    }

    static boolean isApplied() {
        return claimEnvironment != null;
    }

    // Only for tests.
    static synchronized void reset() {
        claimEnvironment = null;
    }

    @Override
    public Set<String> getPropertyNames() {
        var current = claimEnvironment;
        return current == null ? Set.of() : current.getPropertyNames();
    }

    @Override
    public @Nullable String getValue(String propertyName) {
        var current = claimEnvironment;
        return current == null ? null : current.getValue(propertyName);
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
