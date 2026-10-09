package com.gkcontas.vthreads.workload;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import org.springframework.stereotype.Component;

/**
 * Structured concurrency: one request fanning out to several dependencies and
 * waiting for all of them.
 * <p>
 * Before virtual threads this was the shape nobody wrote with threads, because
 * a thread per dependency per request was unaffordable - so it became a
 * {@code CompletableFuture} chain, and the stack trace stopped telling you
 * anything. With cheap threads the blocking version is affordable again, and
 * {@link StructuredTaskScope} adds what a bare executor does not give you:
 * <ul>
 *   <li>the scope cannot be left before its children finish, so no task
 *       outlives the request that started it;</li>
 *   <li>the first failure cancels the siblings instead of letting them run to
 *       completion for a result nobody will read;</li>
 *   <li>a deadline applies to the whole fan-out, not to each leg.</li>
 * </ul>
 * This is a preview API on Java 21 - see the note in build.gradle.kts about
 * what that costs.
 */
@Component
public class FanOutWorkload {

    public record Fanout(List<String> results, long elapsedMillis) {
    }

    /**
     * Calls {@code legs} dependencies in parallel, each taking {@code delay}.
     * The whole thing takes about one {@code delay}, not {@code legs} of them.
     */
    public Fanout gather(int legs, Duration delay, Duration timeout) throws InterruptedException {
        long startedAt = System.nanoTime();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<String>> subtasks = java.util.stream.IntStream
                    .range(0, legs)
                    .mapToObj(leg -> scope.<String>fork(() -> dependency(leg, delay)))
                    .toList();

            scope.joinUntil(Instant.now().plus(timeout));
            scope.throwIfFailed(cause ->
                    new IllegalStateException("a dependency failed, siblings were cancelled", cause));

            return new Fanout(
                    subtasks.stream().map(StructuredTaskScope.Subtask::get).toList(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(
                    "the fan-out did not finish within " + timeout + "; every leg was cancelled", e);
        }
    }

    /**
     * One leg fails on purpose when {@code failingLeg} matches, so the
     * short-circuit is observable rather than described.
     */
    public Fanout gatherWithFailure(int legs, Duration delay, int failingLeg, Duration timeout)
            throws InterruptedException {
        long startedAt = System.nanoTime();
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int leg = 0; leg < legs; leg++) {
                int current = leg;
                scope.fork(() -> {
                    if (current == failingLeg) {
                        throw new IllegalStateException("dependency " + current + " is down");
                    }
                    return dependency(current, delay);
                });
            }
            scope.joinUntil(Instant.now().plus(timeout));
            scope.throwIfFailed(cause ->
                    new IllegalStateException("a dependency failed, siblings were cancelled", cause));
            return new Fanout(List.of(), (System.nanoTime() - startedAt) / 1_000_000);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("the fan-out timed out", e);
        }
    }

    private String dependency(int leg, Duration delay) throws InterruptedException {
        Thread.sleep(delay);
        return "leg-" + leg;
    }
}
