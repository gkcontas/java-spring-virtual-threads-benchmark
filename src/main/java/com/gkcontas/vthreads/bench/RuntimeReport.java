package com.gkcontas.vthreads.bench;

import java.util.Map;

/**
 * What the process looks like right now. Reported alongside every benchmark run
 * so a number in the README can be traced back to the configuration that
 * produced it.
 *
 * @param virtualThreadsEnabled whether Spring is handing requests to virtual threads
 * @param carriers              the ForkJoinPool parallelism virtual threads are
 *                              scheduled onto - the real ceiling once pinning starts
 */
public record RuntimeReport(
        boolean virtualThreadsEnabled,
        int availableProcessors,
        int carriers,
        int tomcatMaxThreads,
        int platformThreadCount,
        int peakPlatformThreadCount,
        long heapUsedMb,
        long heapCommittedMb,
        Map<String, Integer> connectionPool) {
}
