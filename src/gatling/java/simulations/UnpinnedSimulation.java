package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.http.HttpDsl.*;

/** The same critical section under a ReentrantLock. The control for PinnedSimulation. */
public class UnpinnedSimulation extends BenchSimulation {

    private final ScenarioBuilder scenario = scenario("unpinned " + delayMs() + "ms")
            .exec(http("unpinned")
                    .get("/work/unpinned?delayMs=" + delayMs())
                    .check(status().is(200)));

    {
        setUp(scenario.injectOpen(
                        constantUsersPerSec(rate()).during(durationSeconds())))
                .protocols(httpProtocol);
    }
}
