package com.hadi.clarpse.compiler;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Bounds how many Node.js compiler daemons (TypeScript, Python) may run concurrently in
 * this JVM.
 *
 * <p>Each daemon is spawned with {@code --max-old-space-size=clarpse.node.heapSize}
 * (4 GiB by default) — a heap size the daemon genuinely needs for large repositories, so
 * it cannot simply be lowered. What a memory-constrained host <em>can</em> control is how
 * many such processes exist at once: with the default permit count of 1, the worst-case
 * native footprint of node compilers is one daemon's heap instead of one per concurrent
 * parse. Inside a container whose cgroup also hosts the JVM heap, that difference is what
 * keeps the container under its memory limit.
 *
 * <p>Configure via system property or environment variable
 * {@code clarpse.node.maxConcurrentDaemons} / {@code CLARPSE_NODE_MAX_CONCURRENT_DAEMONS}
 * (falls back to the bundled clarpse.properties, default 1). Acquisition waits up to
 * {@code clarpse.node.daemonPermitTimeoutSeconds} (default 600) so a leaked daemon fails
 * loudly instead of stalling every future parse.
 */
public final class NodeDaemonGate {

    private static final String MAX_CONCURRENT_PROP = "clarpse.node.maxConcurrentDaemons";
    private static final String MAX_CONCURRENT_ENV = "CLARPSE_NODE_MAX_CONCURRENT_DAEMONS";
    private static final String PERMIT_TIMEOUT_PROP = "clarpse.node.daemonPermitTimeoutSeconds";

    private static final Semaphore PERMITS = new Semaphore(resolveMaxConcurrent(), true);

    private NodeDaemonGate() {
    }

    private static int resolveMaxConcurrent() {
        final String systemProp = System.getProperty(MAX_CONCURRENT_PROP);
        if (systemProp != null && !systemProp.trim().isEmpty()) {
            return Math.max(1, Integer.parseInt(systemProp.trim()));
        }
        final String envVar = System.getenv(MAX_CONCURRENT_ENV);
        if (envVar != null && !envVar.trim().isEmpty()) {
            return Math.max(1, Integer.parseInt(envVar.trim()));
        }
        return Math.max(1, ClarpseProperties.getInt(MAX_CONCURRENT_PROP, 1));
    }

    private static long permitTimeoutSeconds() {
        final String systemProp = System.getProperty(PERMIT_TIMEOUT_PROP);
        if (systemProp != null && !systemProp.trim().isEmpty()) {
            return Long.parseLong(systemProp.trim());
        }
        return ClarpseProperties.getInt(PERMIT_TIMEOUT_PROP, 600);
    }

    /**
     * Blocks until a daemon slot is free. Throws {@link IllegalStateException} if none
     * frees up within the configured timeout — a symptom of a leaked daemon, which should
     * fail loudly rather than silently serialize every parse behind it forever.
     */
    public static void acquire() {
        final long timeout = permitTimeoutSeconds();
        try {
            if (!PERMITS.tryAcquire(timeout, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out after " + timeout + "s waiting for a node daemon slot ("
                                + MAX_CONCURRENT_PROP + "=" + PERMITS.availablePermits()
                                + " available). A previous daemon may have leaked without close().");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a node daemon slot.", e);
        }
    }

    public static void release() {
        PERMITS.release();
    }
}
