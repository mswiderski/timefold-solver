package ai.timefold.solver.service.testmodel;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import ai.timefold.solver.service.definition.internal.platform.PlatformEnvironment;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PooledPodProfile.class)
class PodPoolClaimTest {

    private static final String CLAIM_BODY = """
            {
              "environment": {
                "AI_TIMEFOLD_JOB_ID": "job-1",
                "AI_TIMEFOLD_PLATFORM_TENANT_ID": "tenant-1",
                "AI_TIMEFOLD_TENANT_NAME": "Tenant One",
                "AI_TIMEFOLD_ON_START_COMMAND": "IDLE",
                "AI_TIMEFOLD_IDLE_RUNTIME_TTL": "PT1H"
              }
            }
            """;

    // The claim path is relative to the server root, not to the REST path.
    @TestHTTPResource("/internal/pod-pool/claim")
    URL claimUrl;

    @Test
    void claimAppliesTheEnvironmentOnlyOnce() {
        assertThat(PlatformEnvironment.get("AI_TIMEFOLD_JOB_ID")).isEmpty();
        assertThat(PlatformEnvironment.get("timefold.platform.tenant-id")).isEmpty();

        // The platform sends no content type.
        given().body(CLAIM_BODY)
                .post(claimUrl)
                .then()
                .statusCode(204);

        assertThat(PlatformEnvironment.get("AI_TIMEFOLD_JOB_ID")).hasValue("job-1");
        assertThat(PlatformEnvironment.get("AI_TIMEFOLD_TENANT_NAME")).hasValue("Tenant One");
        assertThat(PlatformEnvironment.get("timefold.platform.tenant-id")).hasValue("tenant-1");
        assertThat(PlatformEnvironment.onStartCommand()).hasValueSatisfying(
                command -> assertThat(command.name()).isEqualTo("IDLE"));

        given().body(CLAIM_BODY.replace("job-1", "job-2"))
                .post(claimUrl)
                .then()
                .statusCode(409);
        assertThat(PlatformEnvironment.get("AI_TIMEFOLD_JOB_ID")).hasValue("job-1");
    }
}
