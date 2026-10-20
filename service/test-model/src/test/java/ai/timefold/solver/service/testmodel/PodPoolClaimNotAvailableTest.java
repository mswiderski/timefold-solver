package ai.timefold.solver.service.testmodel;

import static io.restassured.RestAssured.given;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PodPoolClaimNotAvailableTest {

    @TestHTTPResource("/internal/pod-pool/claim")
    URL claimUrl;

    @Test
    void claimRouteDoesNotExistOutsideAPodPool() {
        given().body("{\"environment\":{\"AI_TIMEFOLD_ON_START_COMMAND\":\"IDLE\"}}")
                .post(claimUrl)
                .then()
                .statusCode(404);
    }
}
