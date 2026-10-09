package com.gkcontas.vthreads.api;

import com.gkcontas.vthreads.workload.CpuWorkload;
import com.gkcontas.vthreads.workload.DbWorkload;
import com.gkcontas.vthreads.workload.DownstreamWorkload;
import com.gkcontas.vthreads.workload.FanOutWorkload;
import com.gkcontas.vthreads.workload.PinningWorkload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoints the load simulations drive. Each one isolates one kind of work,
 * so a difference between two runs can be attributed to something.
 */
@RestController
@RequestMapping("/work")
public class WorkloadController {

    private final DbWorkload db;
    private final DownstreamWorkload downstream;
    private final CpuWorkload cpu;
    private final PinningWorkload pinning;
    private final FanOutWorkload fanOut;

    public WorkloadController(DbWorkload db, DownstreamWorkload downstream, CpuWorkload cpu,
                              PinningWorkload pinning, FanOutWorkload fanOut) {
        this.db = db;
        this.downstream = downstream;
        this.cpu = cpu;
        this.pinning = pinning;
        this.fanOut = fanOut;
    }

    /**
     * Blocking on a slow downstream, with nothing else scarce. This is the
     * workload where virtual threads can win, and the one the headline numbers
     * come from.
     */
    @GetMapping("/downstream")
    public Map<String, Object> downstream(
            @RequestParam(defaultValue = "500") @Min(0) @Max(10000) int delayMs) {
        return Map.of("waitedMs", downstream.call(Duration.ofMillis(delayMs)),
                "virtual", isVirtual());
    }

    /**
     * Blocking I/O through the connection pool. Here the pool, not the thread
     * model, is the ceiling - which is the point of keeping both endpoints.
     */
    @GetMapping("/db")
    public Map<String, Object> blockingDatabase(
            @RequestParam(defaultValue = "50") @Min(0) @Max(5000) int delayMs) {
        long inStock = db.query(Duration.ofMillis(delayMs));
        return Map.of("inStock", inStock, "delayMs", delayMs, "virtual", isVirtual());
    }

    /** CPU-bound: the case virtual threads do nothing for. */
    @GetMapping("/cpu")
    public Map<String, Object> cpuBound(
            @RequestParam(defaultValue = "20000") @Min(1) @Max(5_000_000) int rounds) {
        return Map.of("digest", cpu.hash(rounds), "rounds", rounds, "virtual", isVirtual());
    }

    /** Blocks inside {@code synchronized}: pins the carrier on Java 21. */
    @GetMapping("/pinned")
    public Map<String, Object> pinned(
            @RequestParam(defaultValue = "50") @Min(0) @Max(5000) int delayMs) {
        return Map.of("sleptMs", pinning.insideMonitor(Duration.ofMillis(delayMs)),
                "guard", "synchronized", "virtual", isVirtual());
    }

    /** The identical critical section under a ReentrantLock: no pinning. */
    @GetMapping("/unpinned")
    public Map<String, Object> unpinned(
            @RequestParam(defaultValue = "50") @Min(0) @Max(5000) int delayMs) {
        return Map.of("sleptMs", pinning.insideLock(Duration.ofMillis(delayMs)),
                "guard", "ReentrantLock", "virtual", isVirtual());
    }

    /** Structured concurrency fan-out: all legs in parallel, one deadline. */
    @GetMapping("/fanout")
    public Map<String, Object> fanout(
            @RequestParam(defaultValue = "4") @Min(1) @Max(32) int legs,
            @RequestParam(defaultValue = "50") @Min(0) @Max(5000) int delayMs,
            @RequestParam(defaultValue = "5000") @Min(1) int timeoutMs) throws InterruptedException {
        var result = fanOut.gather(legs, Duration.ofMillis(delayMs), Duration.ofMillis(timeoutMs));
        return Map.of("legs", legs, "results", result.results(),
                "elapsedMs", result.elapsedMillis(), "virtual", isVirtual());
    }

    /**
     * One leg fails: the siblings are cancelled instead of running on. The
     * elapsed time is the proof - it tracks the failing leg, not the slow ones.
     */
    @GetMapping("/fanout-failure")
    public Map<String, Object> fanoutFailure(
            @RequestParam(defaultValue = "4") @Min(2) @Max(32) int legs,
            @RequestParam(defaultValue = "1000") @Min(0) @Max(10000) int delayMs,
            @RequestParam(defaultValue = "0") @Min(0) int failingLeg) throws InterruptedException {
        try {
            fanOut.gatherWithFailure(legs, Duration.ofMillis(delayMs), failingLeg,
                    Duration.ofMillis(delayMs * 4L + 1000));
            return Map.of("outcome", "unexpectedly succeeded");
        } catch (IllegalStateException e) {
            return Map.of("outcome", "short-circuited",
                    "reason", String.valueOf(e.getCause() == null ? e.getMessage()
                            : e.getCause().getMessage()));
        }
    }

    private boolean isVirtual() {
        return Thread.currentThread().isVirtual();
    }
}
