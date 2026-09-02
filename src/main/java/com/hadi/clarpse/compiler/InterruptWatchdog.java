package com.hadi.clarpse.compiler;

/**
 * Runs an action once when a watched thread's interrupt flag trips.
 *
 * <p>For the out-of-process daemon path (Python/TypeScript): the owning thread blocks in
 * {@code BufferedReader.readLine()} over the daemon process's stdout, and {@code Thread.interrupt()}
 * cannot unblock a pipe read — nor does interrupting help while the external process keeps computing.
 * This watchdog runs on a separate thread, notices the owner has been interrupted, and runs the given
 * action (destroy the daemon), which closes the pipe and unblocks the read. See clarpse #180.
 *
 * <p>Close it in a finally block; it is a daemon thread and best-effort, so a missed close cannot
 * hang the JVM.
 */
public final class InterruptWatchdog implements AutoCloseable {

    private static final long DEFAULT_POLL_MS = 200L;

    private final Thread watcher;
    private volatile boolean stopped;

    public InterruptWatchdog(final Thread owner, final Runnable onInterrupt) {
        this(owner, onInterrupt, DEFAULT_POLL_MS);
    }

    InterruptWatchdog(final Thread owner, final Runnable onInterrupt, final long pollMs) {
        this.watcher = new Thread(() -> {
            while (!stopped) {
                if (owner.isInterrupted()) {
                    try {
                        onInterrupt.run();
                    } catch (final RuntimeException ignored) {
                        // Best-effort teardown; nothing here should mask the original cancellation.
                    }
                    return;
                }
                try {
                    Thread.sleep(pollMs);
                } catch (final InterruptedException e) {
                    return; // close() interrupts us; that is the stop signal.
                }
            }
        }, "clarpse-interrupt-watchdog");
        this.watcher.setDaemon(true);
        this.watcher.start();
    }

    @Override
    public void close() {
        stopped = true;
        watcher.interrupt();
    }
}
