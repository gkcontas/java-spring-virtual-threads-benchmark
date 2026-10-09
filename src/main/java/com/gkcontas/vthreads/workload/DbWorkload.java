package com.gkcontas.vthreads.workload;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Blocking I/O, the case virtual threads exist for: the request thread is
 * parked inside the JDBC driver waiting on a socket, doing nothing.
 * <p>
 * The wait comes from {@code pg_sleep} rather than from a slow query on
 * purpose. A benchmark comparing two thread models needs the work itself to be
 * identical and the duration to be exact; a query whose cost depends on the
 * buffer cache would move under us between runs.
 */
@Component
public class DbWorkload {

    private final JdbcTemplate jdbc;

    public DbWorkload(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Blocks for {@code delay} inside the database, then does a real indexed
     * read so the connection is not only sleeping.
     *
     * @return how many products are in stock
     */
    public long query(Duration delay) {
        Long count = jdbc.queryForObject("""
                select (select count(*) from product where in_stock > 0) as in_stock_count
                  from (select pg_sleep(?)) as waited
                """, Long.class, delay.toMillis() / 1000.0);
        return count == null ? 0L : count;
    }
}
