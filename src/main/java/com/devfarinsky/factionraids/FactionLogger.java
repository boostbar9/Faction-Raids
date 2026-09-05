package com.devfarinsky.factionraids;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Single SLF4J logger for the mod.
 *
 * <p>All non-user-facing diagnostics should flow through here so log lines
 * are consistently tagged with the mod id and can be filtered by server admins
 * (e.g. via log4j config). This replaces empty {@code catch (Exception ignored)}
 * blocks and stray {@code System.out} calls.
 */
public final class FactionLogger {
    private FactionLogger() {}

    public static final Logger LOG = LogUtils.getLogger();

    /** Debug helper for swallowed command-source failures. */
    public static void debugCommandFailure(String context, Throwable t) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[{}] {} failed: {}", FactionRaids.MOD_ID, context, t.toString());
        }
    }
}
