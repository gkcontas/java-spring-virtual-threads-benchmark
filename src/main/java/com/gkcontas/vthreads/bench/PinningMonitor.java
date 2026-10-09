package com.gkcontas.vthreads.bench;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts carrier pinning while the application runs, by subscribing to the JFR
 * event {@code jdk.VirtualThreadPinned}.
 * <p>
 * This is what turns pinning from a warning in a blog post into a number. The
 * usual advice is {@code -Djdk.tracePinnedThreads=full}, which prints stack
 * traces to stdout: useful once, useless under load, and gone in later JDKs.
 * The JFR event is the supported route and can be read by the application
 * itself, so an endpoint can answer "did this configuration pin, and how
 * often".
 * <p>
 * {@code withoutThreshold()} matters: by default the event only fires when
 * pinning lasts long enough to be interesting, and a benchmark wants every
 * occurrence.
 */
@Component
public class PinningMonitor {

    private static final Logger log = LoggerFactory.getLogger(PinningMonitor.class);
    private static final String EVENT = "jdk.VirtualThreadPinned";
    private static final String APPLICATION_PACKAGE = "com.gkcontas.vthreads";

    private final AtomicLong events = new AtomicLong();
    private final AtomicLong totalMillis = new AtomicLong();
    private final Map<String, Long> byApplicationFrame = new ConcurrentHashMap<>();

    private volatile RecordingStream stream;
    private volatile boolean available;

    @PostConstruct
    void start() {
        try {
            RecordingStream recording = new RecordingStream();
            recording.enable(EVENT).withoutThreshold().withStackTrace();
            recording.onEvent(EVENT, this::record);
            // Keep only the newest events if the consumer falls behind: a lost
            // event costs accuracy, a blocked application costs the benchmark.
            recording.setMaxSize(32L * 1024 * 1024);
            recording.startAsync();
            this.stream = recording;
            this.available = true;
            log.info("Pinning monitor started, listening for {}", EVENT);
        } catch (RuntimeException e) {
            this.available = false;
            log.warn("Could not start the pinning monitor ({}); /bench/pinning will report "
                    + "unavailable instead of zero, which is not the same thing", e.getMessage());
        }
    }

    private void record(RecordedEvent event) {
        events.incrementAndGet();
        totalMillis.addAndGet(event.getDuration().toMillis());
        blameFrame(event).ifPresent(where -> byApplicationFrame.merge(where, 1L, Long::sum));
    }

    /**
     * The frame a developer can act on.
     * <p>
     * The top of a pinning stack trace is always
     * {@code VirtualThread.parkOnCarrierThread} - true, and useless: it says
     * pinning happened, not where the offending {@code synchronized} is. So the
     * stack is walked down to the first application frame, which is the line
     * that actually has to change. The JDK frame is kept only as a fallback,
     * for pinning that happens entirely inside a library.
     */
    private java.util.Optional<String> blameFrame(RecordedEvent event) {
        if (event.getStackTrace() == null || event.getStackTrace().getFrames().isEmpty()) {
            return java.util.Optional.empty();
        }
        var frames = event.getStackTrace().getFrames();
        return frames.stream()
                .filter(frame -> frame.getMethod().getType().getName().startsWith(APPLICATION_PACKAGE))
                .findFirst()
                .or(() -> java.util.Optional.of(frames.getFirst()))
                .map(frame -> frame.getMethod().getType().getName()
                        + "." + frame.getMethod().getName()
                        + ":" + frame.getLineNumber());
    }

    public PinningReport report() {
        return new PinningReport(
                available, events.get(), totalMillis.get(), Map.copyOf(byApplicationFrame));
    }

    public void reset() {
        events.set(0);
        totalMillis.set(0);
        byApplicationFrame.clear();
    }

    @PreDestroy
    void stop() {
        RecordingStream recording = this.stream;
        if (recording != null) {
            recording.close();
        }
    }

    /**
     * @param monitoringAvailable false when JFR could not be started - reported
     *                            explicitly so zero events is never confused
     *                            with "nothing was watching"
     */
    public record PinningReport(
            boolean monitoringAvailable,
            long pinnedEvents,
            long pinnedMillis,
            Map<String, Long> byApplicationFrame) {
    }
}
