package ai.timefold.solver.service.worker.impl.pool;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.timefold.solver.service.definition.internal.platform.EnvironmentVars;
import ai.timefold.solver.service.definition.internal.platform.OnStartCommand;
import ai.timefold.solver.service.definition.internal.platform.PlatformEnvironment;
import ai.timefold.solver.service.definition.internal.platform.PodPoolClaimed;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Lets the platform claim a pod from a pod pool and send it the environment of a job.
 * <p>
 * The route exists only when the start command is {@link OnStartCommand#POOLED}.
 * The path is relative to the server root, not to the REST path.
 * A pod can be claimed only once.
 * The claim returns only after the worker can serve requests for the job.
 */
@NullMarked
@ApplicationScoped
public class PodPoolClaimRoute {

    public static final String PATH = "/internal/pod-pool/claim";

    private static final Logger LOGGER = LoggerFactory.getLogger(PodPoolClaimRoute.class);
    private static final String ENVIRONMENT_FIELD = "environment";

    private final Event<PodPoolClaimed> claimedEvent;

    @Inject
    public PodPoolClaimRoute(Event<PodPoolClaimed> claimedEvent) {
        this.claimedEvent = claimedEvent;
    }

    void registerRoute(@Observes Router router) {
        if (PlatformEnvironment.onStartCommand().orElse(null) != OnStartCommand.POOLED) {
            return;
        }
        LOGGER.info("Pod pool claim route is available at {}.", PATH);
        router.post(PATH)
                .handler(BodyHandler.create(false))
                .blockingHandler(this::handle);
    }

    private void handle(RoutingContext context) {
        JsonObject body;
        try {
            body = context.body().asJsonObject();
        } catch (RuntimeException e) {
            body = null;
        }
        var response = claim(body);
        if (response.status() >= 300) {
            LOGGER.warn("Pod pool claim is rejected with status {}: {}", response.status(), response.message());
        }
        context.response().setStatusCode(response.status()).end(response.message());
    }

    synchronized ClaimResponse claim(@Nullable JsonObject body) {
        if (PodPoolConfigSource.isApplied()) {
            return new ClaimResponse(409, "The pod is already claimed.");
        }
        Map<String, String> environment;
        OnStartCommand onStartCommand;
        try {
            environment = readEnvironment(body);
            onStartCommand = readOnStartCommand(environment);
        } catch (IllegalArgumentException e) {
            return new ClaimResponse(400, "Invalid claim request: " + e.getMessage());
        }

        PodPoolConfigSource.apply(environment);
        LOGGER.info("Pod is claimed for job ({}) with start command ({}).",
                environment.get(EnvironmentVars.ENV_TIMEFOLD_JOB_ID), onStartCommand);
        try {
            claimedEvent.fire(new PodPoolClaimed());
        } catch (RuntimeException e) {
            LOGGER.error("Unable to start the claimed job due to {}", e.getMessage(), e);
            return new ClaimResponse(500, "Unable to start the claimed job: " + e.getMessage());
        }
        return new ClaimResponse(204, "");
    }

    private static Map<String, String> readEnvironment(@Nullable JsonObject body) {
        if (body == null) {
            throw new IllegalArgumentException("The request body is not a JSON object.");
        }
        var environmentJson = body.getValue(ENVIRONMENT_FIELD);
        if (!(environmentJson instanceof JsonObject environmentObject)) {
            throw new IllegalArgumentException("The field (%s) is missing or not an object.".formatted(ENVIRONMENT_FIELD));
        }
        var environment = new HashMap<String, String>();
        environmentObject.forEach(entry -> {
            if (entry.getValue() != null) {
                environment.put(entry.getKey(), entry.getValue().toString());
            }
        });
        return environment;
    }

    private static OnStartCommand readOnStartCommand(Map<String, String> environment) {
        var value = environment.get(EnvironmentVars.ENV_TIMEFOLD_ON_START_COMMAND);
        if (value == null) {
            throw new IllegalArgumentException(
                    "The environment variable (%s) is missing.".formatted(EnvironmentVars.ENV_TIMEFOLD_ON_START_COMMAND));
        }
        OnStartCommand onStartCommand;
        try {
            onStartCommand = OnStartCommand.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The start command (%s) is unknown.".formatted(value), e);
        }
        if (onStartCommand == OnStartCommand.POOLED) {
            throw new IllegalArgumentException("The start command (%s) is not allowed in a claim.".formatted(value));
        }
        return onStartCommand;
    }

    record ClaimResponse(int status, String message) {
    }
}
