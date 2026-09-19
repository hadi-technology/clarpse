package com.hadi.clarpse.compiler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Ends a resolver daemon process and waits for it to exit, so none outlives the call that owned it.
 */
public final class DaemonProcesses {

    private DaemonProcesses() {
    }

    /**
     * Waits for a process asked to shut down, kills it if it has not exited within the timeout, and
     * waits again for it to be gone. A pending interrupt of the calling thread is kept but does not
     * cut the wait short, since returning early is what would leave the process behind.
     *
     * @param process The process; {@code null} is ignored.
     * @param timeout How long to wait each time.
     */
    public static void terminate(final Process process, final Duration timeout) {
        if (process == null) {
            return;
        }
        final boolean interrupted = Thread.interrupted();
        try {
            if (!waitFor(process, timeout)) {
                process.destroyForcibly();
                waitFor(process, timeout);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean waitFor(final Process process, final Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }
}
