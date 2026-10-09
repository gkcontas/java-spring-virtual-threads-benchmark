package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * CPU-bound work. Expected to be identical between the two models: a thread
 * that never blocks never yields, so the ceiling is the number of cores either
 * way.
 */
public class CpuSimulation extends BenchSimulation {

    private final ScenarioBuilder scenario = scenario("cpu")
            .exec(http("cpu")
                    .get("/work/cpu?rounds=" + intProperty("bench.rounds", 50000))
                    .check(status().is(200)));

    {
        setUp(scenario.injectOpen(
                        constantUsersPerSec(rate()).during(durationSeconds())))
                .protocols(httpProtocol);
    }
}
