package com.gkcontas.vthreads.bench;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the state of the running process: thread model, carrier count, heap and
 * connection pool.
 * <p>
 * The connection pool numbers are here because they are the answer to the
 * question this project keeps running into. Virtual threads remove the thread
 * as the scarce resource; they do not create connections. A request that used
 * to queue for a Tomcat thread now queues for a connection instead, and the
 * queue is just somewhere else.
 */
@Component
public class RuntimeInspector {

    private final DataSource dataSource;
    private final boolean virtualThreadsEnabled;
    private final int tomcatMaxThreads;

    public RuntimeInspector(DataSource dataSource,
                            @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled,
                            @Value("${server.tomcat.threads.max:200}") int tomcatMaxThreads) {
        this.dataSource = dataSource;
        this.virtualThreadsEnabled = virtualThreadsEnabled;
        this.tomcatMaxThreads = tomcatMaxThreads;
    }

    public RuntimeReport report() {
        var threads = ManagementFactory.getThreadMXBean();
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        return new RuntimeReport(
                virtualThreadsEnabled,
                Runtime.getRuntime().availableProcessors(),
                carrierParallelism(),
                tomcatMaxThreads,
                // Virtual threads are invisible to ThreadMXBean by design, so
                // this is the count of real OS threads - which is the number
                // that matters for memory.
                threads.getThreadCount(),
                threads.getPeakThreadCount(),
                memory.getUsed() / (1024 * 1024),
                memory.getCommitted() / (1024 * 1024),
                pool());
    }

    /**
     * The carrier pool's parallelism, which defaults to the number of
     * processors and can be raised with
     * {@code -Djdk.virtualThreadScheduler.parallelism}.
     */
    private int carrierParallelism() {
        String configured = System.getProperty("jdk.virtualThreadScheduler.parallelism");
        if (configured != null) {
            try {
                return Integer.parseInt(configured);
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return Runtime.getRuntime().availableProcessors();
    }

    private Map<String, Integer> pool() {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            var mx = hikari.getHikariPoolMXBean();
            values.put("max", hikari.getMaximumPoolSize());
            values.put("active", mx.getActiveConnections());
            values.put("idle", mx.getIdleConnections());
            values.put("awaitingConnection", mx.getThreadsAwaitingConnection());
        }
        return values;
    }
}
