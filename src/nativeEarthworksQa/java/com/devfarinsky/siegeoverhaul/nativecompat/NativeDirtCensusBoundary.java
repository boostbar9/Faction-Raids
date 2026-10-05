package com.devfarinsky.siegeoverhaul.nativecompat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** QA-only failure boundary. An optional sidecar cannot rewrite the genuine one-FILL outcome. */
final class NativeDirtCensusBoundary {
    static final int MAX_BYTES = 2_000_000;
    record Receipt(boolean enabled, String status, String artifactSha256, String failureType) {}
    record Payload(Path evidence, String json, String status) {}
    @FunctionalInterface interface Capture { Payload run() throws Exception; }
    private NativeDirtCensusBoundary() {}

    static Receipt capture(Capture operation) {
        try {
            // Includes all lifecycle/preflight checks, sampling, serialization and safe-export validation.
            Payload payload = operation.run();
            if (payload == null || payload.evidence() == null || payload.json() == null
                    || payload.json().length() > MAX_BYTES
                    || !("captured".equals(payload.status()) || "refused".equals(payload.status())))
                throw new IllegalArgumentException("Invalid bounded census payload");
            byte[] bytes = payload.json().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Census byte budget exceeded");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Files.write(payload.evidence().resolve("dirt-census.json"), bytes, StandardOpenOption.CREATE_NEW);
            return new Receipt(true, payload.status(), digest, "");
        } catch (Exception | AssertionError | LinkageError unavailable) {
            // No exception message, path or arbitrary plugin text crosses the boundary.
            String type = unavailable.getClass().getName();
            if (!type.matches("[A-Za-z0-9_.$]{1,256}")) type = "java.lang.Throwable";
            return new Receipt(true, "refused", "", type);
        }
    }
}
