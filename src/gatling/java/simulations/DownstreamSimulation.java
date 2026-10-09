package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * The headline comparison: requests that block on a slow downstream, with
 * nothing else scarce.
 * <p>
 * A platform pool can serve at most {@code threads / blockingTime} requests per
 * second - 200 threads against a 500 ms wait is 400/s, and arrivals above that
 * queue. Virtual threads have no such ceiling, so the same arrival rate should
 * pass through at the downstream's own latency.
 */
public class DownstreamSimulation extends BenchSimulation {

    private final ScenarioBuilder scenario = scenario("downstream " + delayMs() + "ms")
            .exec(http("downstream")
                    .get("/work/downstream?delayMs=" + delayMs())
                    .check(status().is(200)));

    {
        setUp(scenario.injectOpen(
                        constantUsersPerSec(rate()).during(durationSeconds())))
                .protocols(httpProtocol);
    }
}
