package ai.timefold.solver.service.worker.impl.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PodPoolConfigSourceTest {

    private final PodPoolConfigSource configSource = new PodPoolConfigSource();

    @AfterEach
    void resetClaim() {
        PodPoolConfigSource.reset();
    }

    @Test
    void isEmptyBeforeClaim() {
        assertThat(PodPoolConfigSource.isApplied()).isFalse();
        assertThat(configSource.getValue("AI_TIMEFOLD_JOB_ID")).isNull();
        assertThat(configSource.getPropertyNames()).isEmpty();
    }

    @Test
    void resolvesEnvironmentVariableNamesAndPropertyNamesAfterClaim() {
        PodPoolConfigSource.apply(Map.of(
                "AI_TIMEFOLD_JOB_ID", "job-1",
                "AI_TIMEFOLD_PLATFORM_TENANT_ID", "tenant-1"));

        assertThat(PodPoolConfigSource.isApplied()).isTrue();
        assertThat(configSource.getValue("AI_TIMEFOLD_JOB_ID")).isEqualTo("job-1");
        assertThat(configSource.getValue("ai.timefold.platform.tenant-id")).isEqualTo("tenant-1");
        assertThat(configSource.getValue("ai.timefold.platform.tenant.id")).isEqualTo("tenant-1");
        assertThat(configSource.getValue("AI_TIMEFOLD_STORAGE_BUCKET")).isNull();
    }

    @Test
    void appliesOnlyOnce() {
        PodPoolConfigSource.apply(Map.of("AI_TIMEFOLD_JOB_ID", "job-1"));

        assertThatThrownBy(() -> PodPoolConfigSource.apply(Map.of("AI_TIMEFOLD_JOB_ID", "job-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already applied");
        assertThat(configSource.getValue("AI_TIMEFOLD_JOB_ID")).isEqualTo("job-1");
    }

    @Test
    void ordinalIsBetweenEnvironmentVariablesAndSystemProperties() {
        assertThat(configSource.getOrdinal()).isGreaterThan(300).isLessThan(400);
    }
}
