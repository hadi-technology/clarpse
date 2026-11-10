package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/**
 * Parses a single C# file on the shared worker pool. Mirrors the Java
 * {@link com.hadi.clarpse.compiler.java.ParseTask}: C# parses in-process and CPU-bound, so a task
 * already in flight cannot be stopped mid-file — but checking the interrupt flag at the start means a
 * {@code shutdownNow()} on a cancelled compile drains the tasks that have not started yet instead of
 * parsing every remaining file after the result is already being discarded. See clarpse #180.
 *
 * <p>A failure in one file, a runtime exception or a stack overflow, is that file's outcome: it is
 * reported as a {@code PARSE_FAILED} {@link CompileFailure}, contributes no components, and the other
 * files are parsed as usual.
 */
final class CSharpParseTask implements Callable<CSharpModel.ParseOutcome> {

    /** Reads one file into its outcome. */
    @FunctionalInterface
    interface FileParse {
        CSharpModel.ParseOutcome parse(ProjectFile file, int index, List<String> implicitNamespaces);
    }

    private final FileParse parse;
    private final ProjectFile file;
    private final int index;
    private final List<String> implicitNamespaces;

    CSharpParseTask(final ProjectFile file, final int index) {
        this(file, index, List.of());
    }

    CSharpParseTask(final ProjectFile file, final int index, final List<String> implicitNamespaces) {
        this(file, index, implicitNamespaces, CSharpFileParser::parseFile);
    }

    CSharpParseTask(final ProjectFile file, final int index, final List<String> implicitNamespaces,
                    final FileParse parse) {
        this.parse = parse;
        this.file = file;
        this.index = index;
        this.implicitNamespaces = implicitNamespaces;
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
        try {
            return parse.parse(file, index, implicitNamespaces);
        } catch (final RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            return failed(String.valueOf(e.getMessage()));
        } catch (final StackOverflowError e) {
            // A construct nested deeper than the parser's recursion can follow fails its file only.
            return failed("C# source nested too deeply to parse.");
        }
    }

    /** The outcome of a file that could not be parsed: no model, and the failure that stands for it. */
    private CSharpModel.ParseOutcome failed(final String message) {
        return new CSharpModel.ParseOutcome(index, null, new CompileFailure(file, message, FailureCode.PARSE_FAILED));
    }
}
