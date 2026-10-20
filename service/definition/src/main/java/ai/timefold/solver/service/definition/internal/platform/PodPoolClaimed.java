package ai.timefold.solver.service.definition.internal.platform;

import jakarta.interceptor.Interceptor;

/**
 * CDI event that is fired synchronously after a pod pool claim applies the environment of a job to the configuration.
 * <p>
 * Beans that keep platform values from the configuration read them again.
 * Their observers use {@link #REFRESH_PRIORITY}, so that they run before the observers that start the work for the job.
 * They also use {@code Reception.IF_EXISTS}, because a bean that is created after the claim already reads the new values.
 * The claim returns only after all observers complete.
 */
public record PodPoolClaimed() {

    /**
     * Observer priority for a refresh of platform values.
     */
    public static final int REFRESH_PRIORITY = Interceptor.Priority.PLATFORM_BEFORE;
}
