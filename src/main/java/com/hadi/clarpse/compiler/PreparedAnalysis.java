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
 * level one afresh from the given paths. Between compiles, {@link #extendFocus(Collection)} adds
 * analysed files, reusing everything already resolved; what the analysis holds grows with its
 * analysed files and stays until it is closed. After {@link #close()} every method throws
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

    /**
     * Completes the compile as {@link #compile(Collection)} does, with this compile's own level-one
     * budget in place of the one the analysis was prepared with. The budget applies to the
     * discovered files, the options' files and the given ones together, cut in path order, as the
     * prepared budget would.
     *
     * <p>A caller that adds files of its own to level one, beside the discovered ones, sets the
     * budget per compile to bound each set exactly: prepare with the largest budget any compile
     * will use, then pass the discovered set's budget plus the number of its own files it adds.
     *
     * @param additionalLevelOnePaths Level-one files required in addition to those discovered and to
     *                                those in the options; may be {@code null}.
     * @param levelOneBudget          The most level-one files this compile models; must not be
     *                                negative.
     * @return The compile result.
     */
    default CompileResult compile(Collection<String> additionalLevelOnePaths, int levelOneBudget)
            throws CompileException {
        throw new UnsupportedOperationException(
                "A per-compile level-one budget is not supported by " + getClass().getSimpleName());
    }

    /**
     * Adds analysed files, resolving only the added ones and reusing the declaration index, the
     * files already parsed and the analysed files' model. Afterwards the analysis is the one
     * {@link ClarpseProject#prepare()} would give for the analysed files so far plus these:
     * {@link #levelOneFiles()} is rediscovered over all of them, and a level-one file that becomes
     * analysed is modelled in full from then on and is no longer boundary. The level-one budget
     * applies to the whole level-one set. Paths already analysed, and paths that are not files of the
     * analysis's language, are ignored.
     *
     * <p>If it throws, the analysis is unchanged and may still be compiled or closed.
     *
     * @param additionalFocusPaths The files to add, in the form accepted for the analysed paths.
     * @throws CompileException When the added files cannot be resolved; an interrupted extend may
     *                          instead throw {@link java.util.concurrent.CancellationException}, as an
     *                          interrupted compile does.
     */
    default void extendFocus(Collection<String> additionalFocusPaths) throws CompileException {
        throw new UnsupportedOperationException("Extending is not supported by " + getClass().getSimpleName());
    }

    /** Releases everything this analysis holds. Idempotent. */
    @Override
    void close();
}
