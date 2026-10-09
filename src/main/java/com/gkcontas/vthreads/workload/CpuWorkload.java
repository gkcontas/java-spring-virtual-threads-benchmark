package com.gkcontas.vthreads.workload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * CPU-bound work, the case virtual threads do <em>not</em> help with.
 * <p>
 * A virtual thread that never blocks never yields its carrier, so the number of
 * threads able to make progress is still the number of cores. Having a million
 * of them changes nothing except the memory spent holding them. This endpoint
 * exists so that claim is measured rather than asserted.
 */
@Component
public class CpuWorkload {

    public String hash(int rounds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = "virtual-threads-benchmark".getBytes(StandardCharsets.UTF_8);
            for (int round = 0; round < rounds; round++) {
                value = digest.digest(value);
            }
            StringBuilder hex = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                hex.append("%02x".formatted(value[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }
}
