package ai.timefold.solver.service.worker.impl.metrics;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import ai.timefold.solver.service.definition.internal.platform.PlatformEnvironment;
import ai.timefold.solver.service.definition.internal.platform.PodPoolClaimed;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.micrometer.runtime.MeterFilterConstraint;

/**
 * Common tags to be applied to timefold metrics when they are executed as part of the platform.
 * <p>
 * A pod pool claim can set the tag values after startup.
 * Meters that were registered before the claim keep the tags they had.
 */
@Singleton
public class PlatformMetricsMeterFilter {

    static final String MODEL_PROPERTY = "timefold.model.id";
    static final String TENANT_ID_PROPERTY = "timefold.platform.tenant-id";

    private volatile List<Tag> tags = readTags();

    @Produces
    @Singleton
    @MeterFilterConstraint(applyTo = PrometheusMeterRegistry.class)
    public MeterFilter timefoldCommonMetrics() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (id.getName().startsWith("timefold")) {
                    return id.replaceTags(Tags.concat(tags, id.getTagsAsIterable()));
                } else {
                    return id;
                }
            }
        };
    }

    void onPodPoolClaimed(
            @Observes(notifyObserver = Reception.IF_EXISTS) @Priority(PodPoolClaimed.REFRESH_PRIORITY) PodPoolClaimed event) {
        tags = readTags();
    }

    static List<Tag> readTags() {
        var tags = new ArrayList<Tag>();
        PlatformEnvironment.get(MODEL_PROPERTY).ifPresent(model -> tags.add(Tag.of("model", model)));
        PlatformEnvironment.get(TENANT_ID_PROPERTY).ifPresent(tenantId -> tags.add(Tag.of("tenant.id", tenantId)));
        return List.copyOf(tags);
    }
}
