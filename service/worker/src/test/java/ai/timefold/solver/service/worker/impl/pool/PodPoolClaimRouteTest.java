package ai.timefold.solver.service.worker.impl.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import ai.timefold.solver.service.definition.internal.platform.PodPoolClaimed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class PodPoolClaimRouteTest {

    private final CountingEvent claimedEvent = new CountingEvent();
    private final PodPoolClaimRoute route = new PodPoolClaimRoute(claimedEvent);

    @AfterEach
    void resetClaim() {
        PodPoolConfigSource.reset();
    }

    @Test
    void firstClaimAppliesEnvironmentAndStartsJob() {
        var response = route.claim(claim(Map.of(
                "AI_TIMEFOLD_JOB_ID", "job-1",
                "AI_TIMEFOLD_ON_START_COMMAND", "IDLE",
                "AI_TIMEFOLD_IDLE_RUNTIME_TTL", "PT10M")));

        assertThat(response.status()).isEqualTo(204);
        assertThat(claimedEvent.fired).hasValue(1);
        assertThat(new PodPoolConfigSource().getValue("AI_TIMEFOLD_JOB_ID")).isEqualTo("job-1");
    }

    @Test
    void secondClaimIsRejectedWithConflict() {
        var environment = Map.of("AI_TIMEFOLD_JOB_ID", "job-1", "AI_TIMEFOLD_ON_START_COMMAND", "IDLE");
        assertThat(route.claim(claim(environment)).status()).isEqualTo(204);

        var response = route.claim(claim(Map.of("AI_TIMEFOLD_JOB_ID", "job-2", "AI_TIMEFOLD_ON_START_COMMAND", "IDLE")));

        assertThat(response.status()).isEqualTo(409);
        assertThat(claimedEvent.fired).hasValue(1);
        assertThat(new PodPoolConfigSource().getValue("AI_TIMEFOLD_JOB_ID")).isEqualTo("job-1");
    }

    @Test
    void invalidClaimIsRejectedAndDoesNotClaimThePod() {
        assertThat(route.claim(null).status()).isEqualTo(400);
        assertThat(route.claim(new JsonObject()).status()).isEqualTo(400);
        assertThat(route.claim(new JsonObject().put("environment", "IDLE")).status()).isEqualTo(400);
        assertThat(route.claim(claim(Map.of("AI_TIMEFOLD_JOB_ID", "job-1"))).status()).isEqualTo(400);
        assertThat(route.claim(claim(Map.of("AI_TIMEFOLD_ON_START_COMMAND", "UNKNOWN"))).status()).isEqualTo(400);
        assertThat(route.claim(claim(Map.of("AI_TIMEFOLD_ON_START_COMMAND", "POOLED"))).status()).isEqualTo(400);

        assertThat(PodPoolConfigSource.isApplied()).isFalse();
        assertThat(claimedEvent.fired).hasValue(0);
    }

    @Test
    void failureToStartTheJobIsAnError() {
        claimedEvent.failure = new IllegalStateException("storage is down");

        var response = route.claim(claim(Map.of("AI_TIMEFOLD_ON_START_COMMAND", "IDLE")));

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.message()).contains("storage is down");
    }

    private static JsonObject claim(Map<String, String> environment) {
        return new JsonObject().put("environment", new JsonObject(Map.copyOf(environment)));
    }

    private static final class CountingEvent implements Event<PodPoolClaimed> {

        private final AtomicInteger fired = new AtomicInteger();
        private RuntimeException failure;

        @Override
        public void fire(PodPoolClaimed event) {
            fired.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public <U extends PodPoolClaimed> CompletionStage<U> fireAsync(U event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends PodPoolClaimed> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event<PodPoolClaimed> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends PodPoolClaimed> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends PodPoolClaimed> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }
    }
}
