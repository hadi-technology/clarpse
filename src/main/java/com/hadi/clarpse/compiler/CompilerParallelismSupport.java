package com.hadi.clarpse.compiler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shared helpers for compiler worker-pool sizing and shutdown behavior.
 */
public final class CompilerParallelismSupport {

    public static final String PARALLELISM_ENV = "CLARPSE_PARALLELISM";

    private CompilerParallelismSupport() {
    }

    public static int resolveParallelism(final int fileCount) {
        if (fileCount <= 1) {
            return 1;
        }
        final String override = System.getenv(PARALLELISM_ENV);
        if (override != null) {
            try {
                final int requested = Integer.parseInt(override.trim());
                if (requested > 0) {
                    return Math.min(requested, fileCount);
                }
                return 1;
            } catch (NumberFormatException ignored) {
                // Fall back to processor count.
            }
        }
        return Math.min(Runtime.getRuntime().availableProcessors(), fileCount);
    }

    public static void shutdownExecutor(final ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
