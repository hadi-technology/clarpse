package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.AbstractPreparedAnalysis;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.CompilerParallelismSupport;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.LevelOneReport;
import com.hadi.clarpse.compiler.LevelOneSelection;
import com.hadi.clarpse.compiler.PreparedAnalysis;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Entry point for C# compilation in Clarpse. It parallelizes per-file parsing,
 * collects parse failures without aborting the full language pass, and then
 * delegates to the assembler for partial merging, resolution, and final source
 * model construction.
 */
public final class ClarpseCSharpCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseCSharpCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles,
                                 final Collection<String> analyzedFilePaths) throws CompileException {
        final List<ProjectFile> csharpFiles = ClarpseCompiler.analyzedFiles(projectFiles, Lang.CSHARP, analyzedFilePaths);
        if (csharpFiles.isEmpty()) {
            return new CompileResult(new OOPSourceCodeModel(), Set.of());
        }
        final List<CSharpModel.ParseOutcome> parseOutcomes = parseFiles(csharpFiles);
        final List<CSharpModel.CSharpFileModel> successfulFiles = new ArrayList<>();
        final Set<CompileFailure> failures = new HashSet<>();
        for (final CSharpModel.ParseOutcome outcome : parseOutcomes) {
            if (outcome.failure() != null) {
                failures.add(outcome.failure());
            } else if (outcome.fileModel() != null) {
                successfulFiles.add(outcome.fileModel());
            }
        }
        final OOPSourceCodeModel model = CSharpModelAssembler.buildModel(successfulFiles);
        CompilerSupport.classifyClassCyclo(model, Set.of(
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.CLASS,
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.STRUCT,
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.ENUM
        ));
        CompilerSupport.classifyReferences(model);
        return new CompileResult(model, failures);
    }

    /**
     * Resolves the analysed files of a one-level compile and discovers their level-one files.
     *
     * <p>A {@link CSharpDeclarationIndex} of every C# file, read without parsing, lets the assembler
     * resolve names against the whole repository through declaration-only stubs. The analysed files,
     * the other parts of their partial types and every file with {@code global using} directives are
     * parsed once and kept; level one is the set of files declaring what the analysed components
     * reference, closed over the parts of partial types. Completing the compile parses only the
     * level-one files not yet parsed, and assembles copies of the kept file models with them, so no
     * file is parsed twice. Only the analysed files, the parts of their partial types and the
     * level-one files are emitted, and the level-one components are marked boundary.
     */
    @Override
    public PreparedAnalysis prepare(final ProjectFiles projectFiles,
                                    final Collection<String> analyzedFilePaths,
                                    final AnalysisOptions options) throws CompileException {
        final List<ProjectFile> analysed = ClarpseCompiler.analyzedFiles(projectFiles, Lang.CSHARP, analyzedFilePaths);
        final List<ProjectFile> allFiles = new ArrayList<>(projectFiles.files(Lang.CSHARP));
        final ParsedFiles files = new ParsedFiles(allFiles);
        final Set<String> focus = new TreeSet<>();
        for (final ProjectFile file : analysed) {
            focus.add(file.path());
            focus.addAll(files.index.partialParts(file.path()));
        }
        Set<String> discovered = Set.of();
        if (!focus.isEmpty()) {
            final Set<String> parsedPaths = files.withGlobalUsings(focus);
            files.parse(parsedPaths);
            discovered = discoverLevelOne(files.assemble(parsedPaths, focus), focus, files.index);
        }
        return new CSharpPreparedAnalysis(options, files.filesAt(focus), allFiles, discovered, files, focus);
    }

    /**
     * The declaration index of a one-level compile and the file models it has parsed, each parsed
     * once and assembled only through copies, so it can be assembled again with more files.
     */
    private final class ParsedFiles {

        private final CSharpDeclarationIndex index;
        private final Map<String, ProjectFile> byPath = new LinkedHashMap<>();
        private final Map<String, CSharpModel.CSharpFileModel> parsed = new LinkedHashMap<>();
        private final Map<String, CompileFailure> failures = new LinkedHashMap<>();

        ParsedFiles(final List<ProjectFile> allFiles) {
            allFiles.forEach(file -> byPath.put(file.path(), file));
            this.index = new CSharpDeclarationIndex(allFiles);
        }

        /** The given paths plus every file with {@code global using} directives. */
        Set<String> withGlobalUsings(final Set<String> paths) {
            final Set<String> withGlobal = new TreeSet<>(paths);
            withGlobal.addAll(index.globalUsingFiles());
            return withGlobal;
        }

        List<ProjectFile> filesAt(final Collection<String> paths) {
            return ClarpseCSharpCompiler.filesAt(paths, byPath);
        }

        /** Parses those of the given files not parsed yet. */
        void parse(final Set<String> paths) throws CompileException {
            final List<ProjectFile> toParse = new ArrayList<>();
            for (final ProjectFile file : filesAt(paths)) {
                if (!parsed.containsKey(file.path()) && !failures.containsKey(file.path())) {
                    toParse.add(file);
                }
            }
            for (final CSharpModel.ParseOutcome outcome : parseFiles(toParse)) {
                if (outcome.failure() != null) {
                    failures.put(outcome.failure().file().path(), outcome.failure());
                } else if (outcome.fileModel() != null) {
                    parsed.put(outcome.fileModel().sourceFile.path(), outcome.fileModel());
                }
            }
        }

        /**
         * Assembles copies of the parsed models of {@code parsedPaths} against stubs of every other
         * file, emitting the components of {@code emitted}.
         */
        OOPSourceCodeModel assemble(final Set<String> parsedPaths, final Set<String> emitted) {
            final List<CSharpModel.CSharpFileModel> models = new ArrayList<>();
            for (final String path : parsedPaths) {
                final CSharpModel.CSharpFileModel model = parsed.get(path);
                if (model != null) {
                    models.add(model.assemblyCopy());
                }
            }
            return CSharpModelAssembler.buildModel(models, index.stubs(parsedPaths), emitted);
        }

        /** The failures of the given files. */
        Set<CompileFailure> failuresOf(final Set<String> paths) {
            final Set<CompileFailure> result = new HashSet<>();
            for (final Map.Entry<String, CompileFailure> failure : failures.entrySet()) {
                if (paths.contains(failure.getKey())) {
                    result.add(failure.getValue());
                }
            }
            return result;
        }

        void clear() {
            parsed.clear();
            failures.clear();
        }
    }

    /**
     * A C# one-level compile between discovering level one and modelling it. It holds the
     * declaration index and the parsed file models, which {@link #release()} drops.
     */
    private final class CSharpPreparedAnalysis extends AbstractPreparedAnalysis {

        private final ParsedFiles files;
        private final Set<String> focus;

        CSharpPreparedAnalysis(final AnalysisOptions options, final List<ProjectFile> focusFiles,
                               final List<ProjectFile> allFiles, final Set<String> discovered,
                               final ParsedFiles files, final Set<String> focus) {
            super(options, focusFiles, allFiles, discovered);
            this.files = files;
            this.focus = focus;
        }

        @Override
        protected CompileResult complete(final LevelOneSelection selection) throws CompileException {
            final Set<String> emitted = new TreeSet<>(focus);
            emitted.addAll(selection.modelledPaths());
            if (focus.isEmpty()) {
                return new CompileResult(new OOPSourceCodeModel(), Set.of()).withLevelOne(
                        new LevelOneReport(List.of(), List.of(), List.of(), 0));
            }
            final Set<String> parsedPaths = files.withGlobalUsings(emitted);
            files.parse(parsedPaths);
            final OOPSourceCodeModel model = files.assemble(parsedPaths, emitted);
            CompilerSupport.classifyClassCyclo(model, Set.of(
                    com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.CLASS,
                    com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.STRUCT,
                    com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.ENUM
            ));
            CompilerSupport.classifyReferences(model,
                    reference -> files.index.declares(reference.invokedComponent()));
            CompilerSupport.markBoundary(model, selection.modelledPaths());
            return new CompileResult(model, files.failuresOf(parsedPaths)).withLevelOne(
                    CompilerSupport.levelOneReport(model, selection, List.of()));
        }

        @Override
        protected void release() {
            files.clear();
        }
    }

    /**
     * The files declaring what the analysed components reference, with every part of a partial type
     * among them, less the analysed files.
     */
    private static Set<String> discoverLevelOne(final OOPSourceCodeModel focusModel, final Set<String> focus,
                                                final CSharpDeclarationIndex index) {
        final Set<String> levelOne = new TreeSet<>();
        focusModel.components()
                .filter(component -> focus.contains(component.sourceFile()))
                .forEach(component -> component.references().forEach(
                        reference -> levelOne.addAll(index.filesDeclaring(reference.invokedComponent()))));
        for (final String path : new ArrayList<>(levelOne)) {
            levelOne.addAll(index.partialParts(path));
        }
        levelOne.removeAll(focus);
        return levelOne;
    }

    private static List<ProjectFile> filesAt(final Collection<String> paths, final Map<String, ProjectFile> byPath) {
        final List<ProjectFile> files = new ArrayList<>();
        for (final String path : paths) {
            final ProjectFile file = byPath.get(path);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    private List<CSharpModel.ParseOutcome> parseFiles(final List<ProjectFile> files) throws CompileException {
        final int parallelism = CompilerParallelismSupport.resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing C# files in parallel using " + parallelism + " threads.");
        }
        final ExecutorService executor = Executors.newFixedThreadPool(parallelism); // NOPMD CloseResource - closed in finally
        try {
            final List<Future<CSharpModel.ParseOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i += 1) {
                // Stop dispatching once cancelled: the submission loop itself is long on a large
                // repository, and every task queued past the interrupt is discarded work. Same
                // cooperative-cancellation contract as the Java compiler. See clarpse #180.
                if (Thread.currentThread().isInterrupted()) {
                    futures.forEach(f -> f.cancel(true));
                    throw new CompileException("Interrupted while dispatching C# parse tasks.",
                            new InterruptedException());
                }
                final ProjectFile file = files.get(i);
                futures.add(executor.submit(new CSharpParseTask(file, i)));
            }
            final List<CSharpModel.ParseOutcome> outcomes = new ArrayList<>();
            for (final Future<CSharpModel.ParseOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (final InterruptedException e) {
                    // The deadline fired. Cancel the tasks still queued or running so the pool tears
                    // down at once instead of parsing every remaining file after the result is
                    // discarded, then restore the flag and abort.
                    futures.forEach(f -> f.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new CompileException("Interrupted while parsing C# files.", e);
                } catch (final ExecutionException e) {
                    throw new CompileException("Failed while parsing C# files.", e);
                }
            }
            outcomes.sort((left, right) -> Integer.compare(left.index(), right.index()));
            return outcomes;
        } finally {
            CompilerParallelismSupport.shutdownExecutor(executor);
        }
    }
}
