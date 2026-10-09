package com.gkcontas.vthreads.api;

import com.gkcontas.vthreads.bench.PinningMonitor;
import com.gkcontas.vthreads.bench.RuntimeInspector;
import com.gkcontas.vthreads.bench.RuntimeReport;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads the state of the run: which thread model is active, what the process
 * looks like, and whether anything pinned. Called before and after every
 * simulation so the README's numbers can be tied to a configuration.
 */
@RestController
@RequestMapping("/bench")
public class BenchController {

    private final RuntimeInspector inspector;
    private final PinningMonitor pinning;

    public BenchController(RuntimeInspector inspector, PinningMonitor pinning) {
        this.inspector = inspector;
        this.pinning = pinning;
    }

    @GetMapping("/runtime")
    public RuntimeReport runtime() {
        return inspector.report();
    }

    @GetMapping("/pinning")
    public PinningMonitor.PinningReport pinning() {
        return pinning.report();
    }

    /** Zeroes the pinning counters, so each simulation starts from a clean slate. */
    @PostMapping("/pinning/reset")
    public Map<String, Object> reset() {
        pinning.reset();
        return Map.of("reset", true);
    }
}
