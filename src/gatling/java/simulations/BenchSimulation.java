package simulations;

import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Shared setup for every scenario.
 * <p>
 * All simulations use an <b>open</b> model - a fixed arrival rate, not a fixed
 * number of users. That choice decides what the benchmark can show: with a
 * fixed number of users the load throttles itself to whatever the server can
 * handle, so a saturated server looks the same as a fast one. With a fixed
 * arrival rate, a server that cannot keep up shows it in the latency
 * percentiles, which is the thing being compared.
 */
abstract class BenchSimulation extends Simulation {

    protected final HttpProtocolBuilder httpProtocol = http
            .baseUrl(property("bench.baseUrl", "http://localhost:8080"))
            .acceptHeader("application/json")
            .shareConnections();

    protected static int intProperty(String name, int fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    protected static String property(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    protected int rate() {
        return intProperty("bench.rate", 800);
    }

    protected int durationSeconds() {
        return intProperty("bench.duration", 30);
    }

    protected int delayMs() {
        return intProperty("bench.delayMs", 500);
    }
}
