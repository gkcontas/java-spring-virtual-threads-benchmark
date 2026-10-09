package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * The identical wait, taken inside a {@code synchronized} block. Under virtual
 * threads each request pins a carrier, so the effective concurrency drops to
 * the number of carriers - normally one per core.
 */
public class PinnedSimulation extends BenchSimulation {

    private final ScenarioBuilder scenario = scenario("pinned " + delayMs() + "ms")
            .exec(http("pinned")
                    .get("/work/pinned?delayMs=" + delayMs())
                    .check(status().is(200)));

    {
        setUp(scenario.injectOpen(
                        constantUsersPerSec(rate()).during(durationSeconds())))
                .protocols(httpProtocol);
    }
}
