package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/**
 * Parses one Kotlin file on a worker pool. A task that has not started when its compile is
 * cancelled does not start, so cancelling a compile drains the tasks still queued.
 */
final class KotlinParseTask implements Callable<KotlinModel.ParseOutcome> {

    private final ProjectFile file;
    private final int index;
    private final boolean withBodies;

    KotlinParseTask(final ProjectFile file, final int index, final boolean withBodies) {
        this.file = file;
        this.index = index;
        this.withBodies = withBodies;
    }

    @Override
    public KotlinModel.ParseOutcome call() {
        if (Thread.currentThread().isInterrupted()) {
            String path = "<unknown>";
            if (file != null) {
                path = file.path();
            }
            throw new CancellationException("Kotlin parse task for " + path + " cancelled before start.");
        }
        return KotlinFileParser.parseFile(file, index, withBodies);
    }
}
