package com.gkcontas.vthreads.workload;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * The same critical section guarded two ways, which is the sharpest single
 * lesson in this project.
 * <p>
 * On Java 21 a virtual thread that blocks while holding a monitor cannot be
 * unmounted: it <em>pins</em> its carrier thread, and that carrier is lost to
 * every other virtual thread for the duration. Enough pinned carriers and the
 * scheduler starves - the application behaves worse than the platform pool it
 * replaced, with no error anywhere to explain it.
 * <p>
 * {@link ReentrantLock} is virtual-thread aware and parks without pinning. The
 * two methods below do identical work; only the lock type differs.
 * <p>
 * <b>Why an array of locks instead of one.</b> With a single shared lock both
 * methods would serialise every request and the two thread models would look
 * equally bad - the measurement would be about contention, which is a different
 * problem that virtual threads never claimed to solve. Spreading requests over
 * many locks makes contention negligible, so what remains is pinning alone.
 * They are real shared objects rather than one freshly allocated per call,
 * because a monitor the JIT can prove is thread-confined may be elided
 * entirely, and an elided lock pins nothing.
 * <p>
 * Worth knowing when reading this in a few years: JEP 491 removed this
 * limitation, so on Java 24 and later {@code synchronized} no longer pins. The
 * lesson does not disappear - it becomes a reason to know which JDK you run on.
 */
@Component
public class PinningWorkload {

    /** Comfortably above the concurrency the simulations drive. */
    private static final int STRIPES = 256;

    private final Object[] monitors = new Object[STRIPES];
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];
    private final AtomicInteger cursor = new AtomicInteger();

    public PinningWorkload() {
        for (int index = 0; index < STRIPES; index++) {
            monitors[index] = new Object();
            locks[index] = new ReentrantLock();
        }
    }

    /** Blocks inside {@code synchronized}: pins the carrier on Java 21. */
    public long insideMonitor(Duration delay) {
        synchronized (monitors[nextStripe()]) {
            return sleep(delay);
        }
    }

    /** Blocks inside a {@link ReentrantLock}: the carrier is released. */
    public long insideLock(Duration delay) {
        ReentrantLock lock = locks[nextStripe()];
        lock.lock();
        try {
            return sleep(delay);
        } finally {
            lock.unlock();
        }
    }

    private int nextStripe() {
        return Math.floorMod(cursor.getAndIncrement(), STRIPES);
    }

    private long sleep(Duration delay) {
        try {
            Thread.sleep(delay);
            return delay.toMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while holding the lock", e);
        }
    }
}
