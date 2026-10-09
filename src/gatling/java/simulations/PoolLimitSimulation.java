package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Blocking inside the connection pool. Expected to be indistinguishable between
 * the two thread models, because the ceiling is the pool and not the threads -
 * the result that keeps the project honest.
 */
public class PoolLimitSimulation extends BenchSimulation {

    private final ScenarioBuilder scenario = scenario("db " + intProperty("bench.delayMs", 50) + "ms")
            .exec(http("db")
                    .get("/work/db?delayMs=" + intProperty("bench.delayMs", 50))
                    .check(status().in(200, 500)));

    {
        setUp(scenario.injectOpen(
                        constantUsersPerSec(rate()).during(durationSeconds())))
                .protocols(httpProtocol);
    }
}
