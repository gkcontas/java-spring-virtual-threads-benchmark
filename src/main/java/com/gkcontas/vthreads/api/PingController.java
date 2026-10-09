package com.gkcontas.vthreads.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports which thread served the request. The cheapest possible proof that the
 * configuration switch actually took effect: a platform pool answers from
 * "http-nio-8080-exec-N", virtual threads from a "VirtualThread" with no name.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Thread current = Thread.currentThread();
        return Map.of(
                "thread", current.toString(),
                "virtual", current.isVirtual());
    }
}
