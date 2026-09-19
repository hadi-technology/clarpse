package com.hadi.clarpse.compiler;

import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.hadi.clarpse.compiler.java.IndexedTypeSolver;
import com.hadi.clarpse.compiler.java.JavaDeclarationIndex;
import com.hadi.clarpse.compiler.java.JavaLoadTracker;
import com.hadi.clarpse.compiler.java.JavaParserFactory;
import com.hadi.clarpse.compiler.java.ParseOutcome;
import com.hadi.clarpse.compiler.java.ParseResults;
import com.hadi.clarpse.compiler.java.ParseTask;
import com.hadi.clarpse.compiler.java.ParserContext;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaParser based compiler to process source code.
 */
public class ClarpseJavaCompiler implements ClarpseCompiler {

    /** The package declaration, used to derive a file's source root from its own path. */
    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Logger LOGGER = LogManager.getLogger(ClarpseJavaCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles,
                                 final Collection<String> analyzedFilePaths) throws CompileException {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<CompileFailure> compileFailures = new HashSet<>();
        final List<ProjectFile> javaFiles = ClarpseCompiler.analyzedFiles(projectFiles, Lang.JAVA, analyzedFilePaths);
        if (!javaFiles.isEmpty()) {
            String persistDir = null;
            try {
                persistDir = projectFiles.projectDir();
                final String projectDir = persistDir;
                final Set<String> sourceRoots = sourceRoots(javaFiles, projectDir);
                final ParseResults parseResults = parseJavaFiles(javaFiles,
                        () -> new ParserContext(projectDir, sourceRoots), false);
                srcModel.merge(parseResults.model());
                compileFailures.addAll(parseResults.failures());
            } catch (Exception e) {
                throw new CompileException("An error occurred while parsing!", e);
            } finally {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("ProjectFiles cleanup handled by caller.");
                }
            }
            CompilerSupport.classifyReferences(srcModel);
        }
        return new CompileResult(srcModel, compileFailures);
    }

    /**
     * The directories under which package paths begin, read off each file's own package declaration
     * rather than assumed from convention.
     *
     * <p>A file at {@code /src/main/java/a/b/C.java} declaring {@code package a.b;} puts its source
     * root at {@code /src/main/java}. Deriving it this way covers Maven, Gradle, multi-module
     * layouts and anything unconventional, and costs one regex per file. Guessing
     * {@code src/main/java} by name would have missed the second half of that list.
     */
    private static Set<String> sourceRoots(final List<ProjectFile> files, final String persistDir) {
        final Set<String> roots = new HashSet<>();
        for (final ProjectFile file : files) {
            final String path = file.path();
            if (path == null || file.content() == null) {
                continue;
            }
            final Matcher matcher = PACKAGE_DECLARATION.matcher(file.content());
            if (!matcher.find()) {
                continue;
            }
            final String packagePath = "/" + matcher.group(1).trim().replace('.', '/') + "/";
            final int index = path.lastIndexOf(packagePath);
            if (index >= 0) {
                roots.add(persistDir + path.substring(0, index));
            } else if (path.lastIndexOf('/') >= 0) {
                // A file whose directory does not match its package: the source root cannot be
                // derived, so leave it to the project-directory solver rather than invent one.
                continue;
            }
        }
        return roots;
    }

    /**
     * Compiles the analysed files and, for a one-level compile, the files they reference.
     *
     * <p>A one-level compile resolves repository types only through an {@link IndexedTypeSolver}
     * over a {@link JavaDeclarationIndex} of every Java file, so it never scans a directory and
     * needs no project directory on disk. The analysed files are resolved in full; the level-one
     * files their references lead to are then parsed with method calls attributed from written names
     * only, and their components marked boundary.
     */
    @Override
    public CompileResult compile(final ProjectFiles projectFiles,
                                 final Collection<String> analyzedFilePaths,
                                 final AnalysisOptions options) throws CompileException {
        if (options == null || !options.isOneLevel(analyzedFilePaths)) {
            return compile(projectFiles, analyzedFilePaths);
        }
        final List<ProjectFile> allFiles = new ArrayList<>(projectFiles.files(Lang.JAVA));
        final List<ProjectFile> focusFiles = ClarpseCompiler.analyzedFiles(projectFiles, Lang.JAVA, analyzedFilePaths);
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<CompileFailure> compileFailures = new HashSet<>();
        if (focusFiles.isEmpty()) {
            return new CompileResult(srcModel, compileFailures).withLevelOne(
                    new LevelOneReport(List.of(), List.of(), List.of(), 0));
        }
        final OneLevelResolution resolution = new OneLevelResolution(allFiles, options);
        try {
            final ParseResults focusResults = parseJavaFiles(focusFiles, resolution::newContext, false);
            final LevelOneSelection selection = LevelOneSelection.select(
                    discoverLevelOne(focusResults.model(), focusFiles, resolution.index()), options,
                    focusFiles, allFiles);
            final ParseResults levelOneResults =
                    parseJavaFiles(selection.modelled(), resolution::newContext, true);
            srcModel.merge(focusResults.model());
            srcModel.merge(levelOneResults.model());
            compileFailures.addAll(focusResults.failures());
            compileFailures.addAll(levelOneResults.failures());
            CompilerSupport.classifyReferences(srcModel,
                    reference -> resolution.index().declares(reference.invokedComponent()));
            CompilerSupport.markBoundary(srcModel, selection.modelledPaths());
            final Set<String> beyond = resolution.tracker().loaded();
            focusFiles.forEach(file -> beyond.remove(file.path()));
            beyond.removeAll(selection.modelledPaths());
            return new CompileResult(srcModel, compileFailures).withLevelOne(
                    CompilerSupport.levelOneReport(srcModel, selection, beyond));
        } catch (final IllegalStateException e) {
            throw new CompileException("An error occurred while parsing!", e);
        }
    }

    @Override
    public Set<String> levelOneFiles(final ProjectFiles projectFiles,
                                     final Collection<String> analyzedFilePaths,
                                     final AnalysisOptions options) throws CompileException {
        final List<ProjectFile> allFiles = new ArrayList<>(projectFiles.files(Lang.JAVA));
        final List<ProjectFile> focusFiles = ClarpseCompiler.analyzedFiles(projectFiles, Lang.JAVA, analyzedFilePaths);
        if (focusFiles.isEmpty()) {
            return Set.of();
        }
        final OneLevelResolution resolution = new OneLevelResolution(allFiles, options);
        try {
            final ParseResults focusResults = parseJavaFiles(focusFiles, resolution::newContext, false);
            return discoverLevelOne(focusResults.model(), focusFiles, resolution.index());
        } catch (final IllegalStateException e) {
            throw new CompileException("An error occurred while parsing!", e);
        }
    }

    /**
     * The files declaring what the analysed files' components reference, less the analysed files.
     * Every reference the listener records is a fully qualified name, so the index maps it to its
     * declaring file directly; a name the repository does not declare is a library type and leads
     * nowhere.
     */
    private static Set<String> discoverLevelOne(final OOPSourceCodeModel focusModel,
                                                final List<ProjectFile> focusFiles,
                                                final JavaDeclarationIndex index) {
        final Set<String> focusPaths = new HashSet<>();
        focusFiles.forEach(file -> focusPaths.add(file.path()));
        final Set<String> levelOne = new TreeSet<>();
        focusModel.components()
                .filter(component -> focusPaths.contains(component.sourceFile()))
                .forEach(component -> component.references().forEach(
                        reference -> levelOne.addAll(index.filesDeclaring(reference.invokedComponent()))));
        levelOne.removeAll(focusPaths);
        return levelOne;
    }

    /**
     * What every parser thread of one one-level compile shares: the declaration index, the source
     * text the solvers read, and the tracker capping how many files they load.
     */
    private static final class OneLevelResolution {

        /** Solvers may load at most this many files per level-one file the budget allows. */
        private static final int LOADS_PER_BUDGETED_FILE = 4;

        /** The fewest files solvers may load, so a small budget still lets analysed files resolve. */
        private static final int MIN_LOAD_CAP = 1000;

        private final JavaDeclarationIndex index;
        private final Map<String, String> contentByPath = new HashMap<>();
        private final JavaLoadTracker tracker;

        OneLevelResolution(final List<ProjectFile> allFiles, final AnalysisOptions options) {
            this.index = JavaDeclarationIndex.of(allFiles);
            for (final ProjectFile file : allFiles) {
                if (file.path() != null && file.content() != null) {
                    contentByPath.put(file.path(), file.content());
                }
            }
            this.tracker = new JavaLoadTracker(
                    Math.max(MIN_LOAD_CAP, LOADS_PER_BUDGETED_FILE * options.levelOneBudget()));
        }

        ParserContext newContext() {
            return new ParserContext(JavaParserFactory.setupIndexedTypeSolver(
                    new IndexedTypeSolver(index, contentByPath, tracker)));
        }

        JavaDeclarationIndex index() {
            return index;
        }

        JavaLoadTracker tracker() {
            return tracker;
        }
    }

    @SuppressWarnings("PMD.CloseResource")
    private ParseResults parseJavaFiles(final List<ProjectFile> files,
                                        final Supplier<ParserContext> contextFactory,
                                        final boolean shallow) {
        final int parallelism = CompilerParallelismSupport.resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing Java files in parallel using " + parallelism + " threads.");
        }

        final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            final ThreadLocal<ParserContext> parserContext = ThreadLocal.withInitial(contextFactory);
            final List<Future<ParseOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                // Stop dispatching once cancelled: on a large repository the submission loop itself
                // is long, and every task queued past the interrupt is work that will be discarded.
                // See #178.
                if (Thread.currentThread().isInterrupted()) {
                    futures.forEach(f -> f.cancel(true));
                    throw new IllegalStateException("Interrupted while dispatching Java parse tasks.");
                }
                final ProjectFile file = files.get(i);
                futures.add(executor.submit(new ParseTask(parserContext, file, i, shallow)));
            }
            final List<ParseOutcome> outcomes = new ArrayList<>();
            for (final Future<ParseOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (InterruptedException e) {
                    // The deadline fired. Cancel the tasks still queued or running so the pool tears
                    // down now instead of waiting out CPU-bound work whose result is discarded, then
                    // restore the flag and abort. The finally's awaitTermination then returns at once
                    // because this thread's interrupt flag is set, so shutdownNow() runs immediately.
                    futures.forEach(f -> f.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while parsing Java files.", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Failed while parsing Java files.", e);
                }
            }
            outcomes.sort((a, b) -> Integer.compare(a.index(), b.index()));
            final OOPSourceCodeModel mergedModel = new OOPSourceCodeModel();
            final Set<CompileFailure> compileFailures = new HashSet<>();
            for (final ParseOutcome outcome : outcomes) {
                mergedModel.merge(outcome.model());
                if (outcome.failure() != null) {
                    compileFailures.add(outcome.failure());
                }
            }
            return new ParseResults(mergedModel, compileFailures);
        } finally {
            CompilerParallelismSupport.shutdownExecutor(executor);
            // JavaParserFacade keeps a private static Map<TypeSolver, JavaParserFacade> that
            // get() writes to during symbol resolution and nothing ever prunes. Every entry
            // strongly retains its solver, which retains that solver's cache of parsed
            // CompilationUnits, which retains a whole AST -- so without this the process holds
            // every AST it has ever parsed. The key is the solver instance and we build a fresh
            // one per thread per compile, so the map can never hit a cache; it only grows,
            // measured at one entry per parser thread per compile.
            //
            // Shutting the executor down is not enough. That ends the threads and releases the
            // ThreadLocal, but the static map holds the solvers independently of the thread that
            // made them, which is why this leaked while looking like it was cleaned up.
            //
            // Nothing of value is dropped: these solvers belong to the compile that is ending.
            // The registry is process-wide, so a compile running concurrently in the same JVM
            // loses its facade's resolution cache here -- get() rebuilds it lazily and the
            // results are unchanged, so the cost is recomputation, not correctness. See #170.
            JavaParserFacade.clearInstances();
        }
    }
}
