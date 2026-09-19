package com.hadi.clarpse.compiler;

import java.util.Collection;
import java.util.Set;

/**
 * The first half of a one-level compile of one revision: the analysed files resolved, and their
 * level-one files discovered but not yet modelled.
 *
 * <p>It exists so a caller comparing two revisions can read each revision's level-one set, take the
 * union, and then compile each revision with it, without resolving any analysed file twice. It
 * holds what the compile will reuse until {@link #close()}: depending on the language a declaration
 * index, parsed files, the analysed files' model, or the discovered set. It never holds a resolver
 * process between calls, so several prepared analyses may be open at once, as a caller comparing
 * two revisions needs, however few resolver processes may run concurrently.
 *
 * <p>Lifetime: obtain it from {@link ClarpseProject#prepare()} in a try-with-resources block, call
 * {@link #levelOneFiles()} and then {@link #compile(Collection)}, and let the block close it. It is
 * not thread-safe. {@link #compile(Collection)} may be called more than once, each call modelling
 * level one afresh from the given paths. After {@link #close()} every method throws
 * {@link IllegalStateException}. The {@link CompileResult} it returns holds nothing of the prepared
 * state and outlives it.
 */
public interface PreparedAnalysis extends AutoCloseable {

    /**
     * The level-one files the analysed files reference, discovered without modelling them.
     *
     * @return The paths, sorted, in the form of {@link ProjectFile#path()}, excluding the analysed
     *         files.
     */
    Set<String> levelOneFiles();

    /**
     * Completes the compile: models level one, which is the discovered level-one files plus the
     * given ones, cut to the budget, and returns the model of the analysed and level-one files.
     *
     * @param additionalLevelOnePaths Level-one files required in addition to those discovered and to
     *                                those in the options, for example the union with another
     *                                revision's; may be {@code null}.
     * @return The compile result.
     */
    CompileResult compile(Collection<String> additionalLevelOnePaths) throws CompileException;

    /** Releases everything this analysis holds. Idempotent. */
    @Override
    void close();
}
