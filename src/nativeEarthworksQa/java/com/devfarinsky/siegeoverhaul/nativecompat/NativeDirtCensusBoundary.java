package com.devfarinsky.siegeoverhaul.nativecompat;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** QA-only failure boundary. An optional sidecar cannot rewrite the genuine one-FILL outcome. */
final class NativeDirtCensusBoundary {
    static final int MAX_BYTES = 2_000_000;
    record Receipt(boolean enabled, String status, String artifactSha256, String failureType, String failurePhase) {}
    record Payload(Path evidence, String json, String status) {}
    @FunctionalInterface interface Capture { Payload run() throws Exception; }
    @FunctionalInterface interface ReadStep<T> { T run() throws Exception; }
    record ReadWindow<T>(T value, String readFailureType, String postFailureType, boolean postStateCaptured) {}

    /** Always attempts the paired post-state, even when the read refuses by throwing. No retries. */
    static <T> ReadWindow<T> readWindow(ReadStep<T> read, ReadStep<?> postState) {
        T value = null; String readFailure = "", postFailure = ""; boolean captured = false;
        try { value = read.run(); }
        catch (Exception | AssertionError | LinkageError unavailable) { readFailure = safeType(unavailable); }
        try { postState.run(); captured = true; }
        catch (Exception | AssertionError | LinkageError unavailable) { postFailure = safeType(unavailable); }
        return new ReadWindow<>(value, readFailure, postFailure, captured);
    }

    /** Exact Java string identity, including unpaired surrogates; no raw pack text is exported. */
    static String packIdentity(String value) throws Exception {
        if (value == null || value.length() > 4096) throw new IllegalArgumentException("Pack identity exceeds its bound");
        ByteBuffer bytes = ByteBuffer.allocate(4 + 2 * value.length()).putInt(value.length());
        for (int i = 0; i < value.length(); i++) bytes.putChar(value.charAt(i));
        return "utf16-sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    }
    static String safeType(Throwable unavailable) {
        String type = unavailable.getClass().getName();
        return type.matches("[A-Za-z0-9_.$]{1,256}") ? type : "java.lang.Throwable";
    }
    private NativeDirtCensusBoundary() {}

    static Receipt capture(Capture operation) {
        String phase = "CAPTURE";
        try {
            // Includes all lifecycle/preflight checks, sampling, serialization and safe-export validation.
            Payload payload = operation.run();
            phase = "PAYLOAD_BOUNDS";
            if (payload == null || payload.evidence() == null || payload.json() == null
                    || payload.json().length() > MAX_BYTES
                    || !("captured".equals(payload.status()) || "refused".equals(payload.status())))
                throw new IllegalArgumentException("Invalid bounded census payload");
            byte[] bytes = payload.json().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Census byte budget exceeded");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            phase = "OUTPUT_WRITE";
            Files.write(payload.evidence().resolve("dirt-census.json"), bytes, StandardOpenOption.CREATE_NEW);
            return new Receipt(true, payload.status(), digest, "", "");
        } catch (Exception | AssertionError | LinkageError unavailable) {
            // No exception message, path or arbitrary plugin text crosses the boundary.
            return new Receipt(true, "refused", "", safeType(unavailable), phase);
        }
    }
}
