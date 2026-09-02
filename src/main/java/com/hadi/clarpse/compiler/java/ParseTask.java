package com.hadi.clarpse.compiler.java;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/**
 * A {@link Callable} task for parsing a single Java file in a parallel execution context.
 *
 * <p>This class is used by {@link com.hadi.clarpse.compiler.ClarpseJavaCompiler}
 * when parsing Java files in parallel. Each task is responsible for parsing one
 * file and returning the result as a {@link ParseOutcome}.
 *
 * <p>The task uses thread-local {@link ParserContext} to ensure thread safety,
 * as JavaParser instances are not thread-safe. The index parameter is used to
 * maintain deterministic ordering of results when parallel execution completes.
 *
 * @see java.util.concurrent.ExecutorService
 * @see java.util.concurrent.Callable
 */
public class ParseTask implements Callable<ParseOutcome> {
    private final ThreadLocal<ParserContext> context;
    private final ProjectFile file;
    private final int index;

    public ParseTask(final ThreadLocal<ParserContext> context, final ProjectFile file, final int index) {
        this.context = context;
        this.file = file;
        this.index = index;
    }

    @Override
    public ParseOutcome call() {
        // Cooperative cancellation. JavaParser never checks the interrupt flag once it is parsing, so
        // a task in flight cannot be stopped mid-file — but checking here means a shutdownNow() on a
        // cancelled compile drains the tasks that have not started yet instead of parsing every
        // remaining file after the result is already being discarded. See #178.
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Java parse task for "
                    + (file != null ? file.path() : "<unknown>") + " cancelled before start.");
        }
        final ParserContext parserContext = context.get();
        return FileParser.parseFile(parserContext.parser(), parserContext.typeSolver(), file, index);
    }
}
