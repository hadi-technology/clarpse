package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/**
 * Parses a single C# file on the shared worker pool. Mirrors the Java
 * {@link com.hadi.clarpse.compiler.java.ParseTask}: C# parses in-process and CPU-bound, so a task
 * already in flight cannot be stopped mid-file — but checking the interrupt flag at the start means a
 * {@code shutdownNow()} on a cancelled compile drains the tasks that have not started yet instead of
 * parsing every remaining file after the result is already being discarded. See clarpse #180.
 */
final class CSharpParseTask implements Callable<CSharpModel.ParseOutcome> {

    private final ProjectFile file;
    private final int index;

    CSharpParseTask(final ProjectFile file, final int index) {
        this.file = file;
        this.index = index;
    }

    @Override
    public CSharpModel.ParseOutcome call() {
        if (Thread.currentThread().isInterrupted()) {
            String path = "<unknown>";
            if (file != null) {
                path = file.path();
            }
            throw new CancellationException("C# parse task for " + path + " cancelled before start.");
        }
        return CSharpFileParser.parseFile(file, index);
    }
}
