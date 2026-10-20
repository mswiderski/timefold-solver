package ai.timefold.solver.service.maps.service.client.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Reception;
import jakarta.inject.Inject;

import ai.timefold.solver.service.definition.internal.platform.PlatformEnvironment;
import ai.timefold.solver.service.definition.internal.platform.PodPoolClaimed;
import ai.timefold.solver.service.maps.api.model.TransportType;
import ai.timefold.solver.service.maps.service.integration.internal.MapServiceOptions;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Supplies the map service options from the configuration.
 * <p>
 * A pod pool claim can set the options after startup; the supplier then reads them again.
 */
@ApplicationScoped
public class MapServiceOptionsSupplier {

    static final String PROVIDER_PROPERTY = "timefold.platform.map-service.provider";
    static final String LOCATION_PROPERTY = "timefold.platform.map-service.location";
    static final String MAX_DISTANCE_FROM_ROAD_PROPERTY = "timefold.platform.map-service.max-distance-from-road";
    static final String MODEL_PROPERTY = "timefold.model.id";
    static final String MODEL_VERSION_PROPERTY = "timefold.model.api-version";
    static final String MODEL_RESOURCE_PROPERTY = "timefold.model.rest-resource";
    static final String TENANT_ID_PROPERTY = "timefold.platform.tenant-id";
    static final String TRANSPORT_TYPE_PROPERTY = "timefold.platform.map-service.transport-type";
    static final String ALLOWED_TRANSPORT_TYPES_PROPERTY = "timefold.platform.map-service.allowed-transport-types";
    public static final String USE_TRAFFIC_PROPERTY = "timefold.platform.map-service.use-traffic";

    private volatile Optional<String> provider;

    private volatile Optional<String> location;

    private volatile Optional<String> model;

    private volatile Optional<String> modelVersion;

    private volatile Optional<String> modelResource;

    private volatile Optional<String> tenantId;

    private volatile Optional<Double> maxDistanceFromRoad;

    private volatile TransportType transportType;

    private volatile List<TransportType> allowedTransportTypes;

    private volatile boolean useTraffic;

    @Inject
    public MapServiceOptionsSupplier(
            @ConfigProperty(name = PROVIDER_PROPERTY) Optional<String> provider,
            @ConfigProperty(name = LOCATION_PROPERTY) Optional<String> location,
            @ConfigProperty(name = MAX_DISTANCE_FROM_ROAD_PROPERTY) Optional<Double> maxDistanceFromRoad,
            @ConfigProperty(name = MODEL_PROPERTY) Optional<String> model,
            @ConfigProperty(name = MODEL_VERSION_PROPERTY) Optional<String> modelVersion,
            @ConfigProperty(name = MODEL_RESOURCE_PROPERTY) Optional<String> modelResource,
            @ConfigProperty(name = TENANT_ID_PROPERTY) Optional<String> tenantId,
            @ConfigProperty(name = TRANSPORT_TYPE_PROPERTY) Optional<String> transportType,
            @ConfigProperty(name = ALLOWED_TRANSPORT_TYPES_PROPERTY) Optional<String> allowedTransportTypes,
            @ConfigProperty(name = USE_TRAFFIC_PROPERTY) Optional<Boolean> useTraffic) {
        apply(provider, location, maxDistanceFromRoad, model, modelVersion, modelResource, tenantId, transportType,
                allowedTransportTypes, useTraffic);
    }

    public MapServiceOptionsSupplier(
            Optional<String> provider, Optional<String> location, Optional<Double> maxDistanceFromRoad,
            Optional<String> model, Optional<String> modelVersion, Optional<String> modelResource,
            Optional<String> tenantId, Optional<String> transportType, Optional<String> allowedTransportTypes) {
        this(provider, location, maxDistanceFromRoad, model, modelVersion, modelResource, tenantId, transportType,
                allowedTransportTypes, Optional.empty());
    }

    void onPodPoolClaimed(
            @Observes(notifyObserver = Reception.IF_EXISTS) @Priority(PodPoolClaimed.REFRESH_PRIORITY) PodPoolClaimed event) {
        apply(PlatformEnvironment.get(PROVIDER_PROPERTY),
                PlatformEnvironment.get(LOCATION_PROPERTY),
                PlatformEnvironment.get(MAX_DISTANCE_FROM_ROAD_PROPERTY, Double.class),
                PlatformEnvironment.get(MODEL_PROPERTY),
                PlatformEnvironment.get(MODEL_VERSION_PROPERTY),
                PlatformEnvironment.get(MODEL_RESOURCE_PROPERTY),
                PlatformEnvironment.get(TENANT_ID_PROPERTY),
                PlatformEnvironment.get(TRANSPORT_TYPE_PROPERTY),
                PlatformEnvironment.get(ALLOWED_TRANSPORT_TYPES_PROPERTY),
                PlatformEnvironment.get(USE_TRAFFIC_PROPERTY, Boolean.class));
    }

    private synchronized void apply(Optional<String> provider, Optional<String> location,
            Optional<Double> maxDistanceFromRoad, Optional<String> model, Optional<String> modelVersion,
            Optional<String> modelResource, Optional<String> tenantId, Optional<String> transportType,
            Optional<String> allowedTransportTypes, Optional<Boolean> useTraffic) {
        this.provider = provider;
        this.location = location;
        this.model = model;
        this.modelVersion = modelVersion;
        this.modelResource = modelResource;
        this.tenantId = tenantId;
        this.maxDistanceFromRoad = maxDistanceFromRoad;
        this.transportType = resolveTransportType(transportType);
        this.allowedTransportTypes = resolveAllowedTransportTypes(allowedTransportTypes);
        this.useTraffic = useTraffic.orElse(false);
    }

    public boolean isUseTraffic() {
        return useTraffic;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public boolean isAutoSelectTransportType() {
        return transportType.isAutoSelect();
    }

    public List<TransportType> getAllowedTransportTypes() {
        return allowedTransportTypes;
    }

    public boolean isAllowed(TransportType transportType) {
        if (transportType == null || transportType.isAutoSelect()) {
            return false;
        }
        return allowedTransportTypes.isEmpty() || allowedTransportTypes.contains(transportType);
    }

    public TransportType getDefaultTransportType() {
        if (!isAutoSelectTransportType()) {
            return transportType;
        }
        if (isAllowed(TransportType.defaultType())) {
            return TransportType.defaultType();
        }
        return allowedTransportTypes.getFirst();
    }

    public String getOptions() {
        return options().build();
    }

    public OptionsBuilder options() {
        return new OptionsBuilder();
    }

    private static TransportType resolveTransportType(Optional<String> transportType) {
        return transportType
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TransportType::of)
                .orElse(TransportType.defaultType());
    }

    private static List<TransportType> resolveAllowedTransportTypes(Optional<String> allowedTransportTypes) {
        List<TransportType> allowed = allowedTransportTypes
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .map(TransportType::of)
                        .distinct()
                        .toList())
                .orElseGet(List::of);
        if (allowed.stream().anyMatch(TransportType::isAutoSelect)) {
            throw new IllegalArgumentException(
                    "The transport type (%s) cannot be part of the allowed transport types (%s); it is not a routing profile."
                            .formatted(TransportType.AUTO_SELECT, allowed));
        }
        return allowed;
    }

    public final class OptionsBuilder {

        private String locationSetName;

        private TransportType transportType = getDefaultTransportType();

        private OptionsBuilder() {
        }

        public OptionsBuilder locationSetName(String locationSetName) {
            this.locationSetName = locationSetName;
            return this;
        }

        public OptionsBuilder transportType(TransportType transportType) {
            this.transportType = transportType;
            return this;
        }

        public String build() {
            String providerOption = provider.map(MapServiceOptions::getProviderOption).orElse("");
            String locationOption = location.map(MapServiceOptions::getLocationOption).orElse("");
            String modelOption = model.map(MapServiceOptions::getModelOption).orElse("");
            String modelVersionOption = modelVersion.map(MapServiceOptions::getModelVersionOption).orElse("");
            String modelResourceOption = modelResource.map(MapServiceOptions::getModelResourceOption).orElse("");
            String tenantIdOption = tenantId.map(MapServiceOptions::getTenantIdOption).orElse("");
            String locationSetNameOption =
                    locationSetName == null ? "" : MapServiceOptions.getLocationSetNameOption(locationSetName);
            String maxDistanceFromRoadOption =
                    maxDistanceFromRoad.map(MapServiceOptions::getMaxDistanceFromRoadOption).orElse("");
            String transportTypeOption = transportType == null || transportType.isAutoSelect()
                    ? ""
                    : MapServiceOptions.getTransportTypeOption(transportType.value());
            return Stream
                    .of(providerOption, locationOption, modelOption, modelVersionOption, modelResourceOption,
                            tenantIdOption, locationSetNameOption, maxDistanceFromRoadOption, transportTypeOption)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }
    }
}
