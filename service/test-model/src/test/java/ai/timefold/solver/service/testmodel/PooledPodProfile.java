package ai.timefold.solver.service.testmodel;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Starts the model as a pod in a pod pool, which waits for a claim.
 * <p>
 * The start command comes from the "pooled" configuration profile.
 * A config override would have a higher ordinal than the claim environment, unlike an environment variable.
 */
public class PooledPodProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "test,pooled";
    }

}
