package com.hadi.clarpse.compiler.kotlin;

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
import com.hadi.clarpse.compiler.java.JavaDeclarationIndex;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
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
 * Compiles Kotlin ({@code .kt}) files with JetBrains' standalone Kotlin parser.
 *
 * <p>Files are parsed in parallel and each file that cannot be parsed is reported in
 * {@link CompileResult#failures()} without stopping the others. Names are resolved against every
 * type the project declares on the JVM: the Kotlin types of every Kotlin file of the
 * {@link ProjectFiles}, read without their bodies when they are not analysed, and the Java types of
 * every Java file. A Kotlin reference to a Java type therefore names that Java type exactly, and
 * once the Kotlin and the Java models are merged it is internal.
 *
 * <p>The model's shape:
 * <ul>
 *   <li>A class, interface, object, enum class, annotation class or data class has the unique name a
 *       Java type of the same package and name has, nested types joined with a dot. A companion
 *       object is the nested type {@code Companion}, or the name it is given.</li>
 *   <li>Top-level functions and properties, extension functions among them, are members of the class
 *       the file compiles into on the JVM: {@code <File>Kt}, or the {@code @file:JvmName}. That class
 *       is modelled only for a file declaring one, so a Java reference to it resolves to it.</li>
 *   <li>A property, and a primary constructor parameter declared {@code val} or {@code var}, is a
 *       field; its accessors are not modelled apart from it. A {@code val} is {@code final}.</li>
 *   <li>A function is a method named by its parameter types, an extension function's receiver
 *       first.</li>
 *   <li>Every declaration but a parameter, a local and an enum entry carries its visibility,
 *       {@code public} when none is written; {@code internal} stays {@code internal}.</li>
 *   <li>In {@code class A : B(), C}, the entry with a constructor call is extended and the others are
 *       implemented; an interface extends its supertypes.</li>
 * </ul>
 *
 * <p>A one-level compile indexes every Kotlin file's declarations without parsing bodies, parses the
 * analysed files in full, and takes as level one the Kotlin files declaring what they reference.
 */
public final class ClarpseKotlinCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseKotlinCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles,
                                 final Collection<String> analyzedFilePaths) throws CompileException {
        final List<ProjectFile> analysed = ClarpseCompiler.analyzedFiles(projectFiles, Lang.KOTLIN, analyzedFilePaths);
        if (analysed.isEmpty()) {
            return new CompileResult(new OOPSourceCodeModel(), Set.of());
        }
        final Set<String> analysedPaths = new HashSet<>();
        analysed.forEach(file -> analysedPaths.add(file.path()));
        final Set<CompileFailure> failures = new HashSet<>();
        final List<KotlinFileModel> parsed = models(parseFiles(analysed, true), failures);
        final List<ProjectFile> others = new ArrayList<>();
        for (final ProjectFile file : projectFiles.files(Lang.KOTLIN)) {
            if (!analysedPaths.contains(file.path())) {
                others.add(file);
            }
        }
        final List<KotlinFileModel> indexed = new ArrayList<>(parsed);
        indexed.addAll(models(parseFiles(others, false), new HashSet<>()));
        final KotlinDeclarationIndex index = new KotlinDeclarationIndex(indexed, javaIndex(projectFiles));
        final OOPSourceCodeModel model = assemble(parsed, index);
        CompilerSupport.classifyReferences(model);
        return new CompileResult(model, failures);
    }

    @Override
    public PreparedAnalysis prepare(final ProjectFiles projectFiles,
                                    final Collection<String> analyzedFilePaths,
                                    final AnalysisOptions options) throws CompileException {
        return AbstractPreparedAnalysis.prepareCleanly(projectFiles,
                () -> prepareAnalysis(projectFiles, analyzedFilePaths, options));
    }

    private AbstractPreparedAnalysis prepareAnalysis(final ProjectFiles projectFiles,
                                                     final Collection<String> analyzedFilePaths,
                                                     final AnalysisOptions options) throws CompileException {
        final List<ProjectFile> analysed = ClarpseCompiler.analyzedFiles(projectFiles, Lang.KOTLIN, analyzedFilePaths);
        final List<ProjectFile> allFiles = new ArrayList<>(projectFiles.files(Lang.KOTLIN));
        final ParsedFiles files = new ParsedFiles(allFiles, javaIndex(projectFiles));
        final Set<String> focus = new TreeSet<>();
        analysed.forEach(file -> focus.add(file.path()));
        Set<String> discovered = Set.of();
        if (!focus.isEmpty()) {
            files.parse(focus);
            discovered = discoverLevelOne(files.assemble(focus), focus, files.index);
        }
        return new KotlinPreparedAnalysis(options, analysed, allFiles, discovered, files, focus);
    }

    /** The index of a one-level compile and the files it has parsed in full, each parsed once. */
    private final class ParsedFiles {

        private final Map<String, ProjectFile> byPath = new LinkedHashMap<>();
        private final Map<String, KotlinFileModel> parsed = new LinkedHashMap<>();
        private final Map<String, CompileFailure> failures = new LinkedHashMap<>();
        private final KotlinDeclarationIndex index;

        ParsedFiles(final List<ProjectFile> allFiles, final JavaDeclarationIndex javaIndex) throws CompileException {
            allFiles.forEach(file -> byPath.put(file.path(), file));
            this.index = new KotlinDeclarationIndex(models(parseFiles(allFiles, false), new HashSet<>()), javaIndex);
        }

        /** Parses those of the given files not parsed yet. */
        void parse(final Collection<String> paths) throws CompileException {
            final List<ProjectFile> toParse = new ArrayList<>();
            for (final String path : paths) {
                final ProjectFile file = byPath.get(path);
                if (file != null && !parsed.containsKey(path) && !failures.containsKey(path)) {
                    toParse.add(file);
                }
            }
            for (final KotlinModel.ParseOutcome outcome : parseFiles(toParse, true)) {
                if (outcome.failure() != null) {
                    failures.put(outcome.failure().file().path(), outcome.failure());
                } else if (outcome.fileModel() != null) {
                    parsed.put(outcome.fileModel().path(), outcome.fileModel());
                }
            }
        }

        OOPSourceCodeModel assemble(final Collection<String> paths) throws CompileException {
            final List<KotlinFileModel> models = new ArrayList<>();
            for (final String path : paths) {
                final KotlinFileModel model = parsed.get(path);
                if (model != null) {
                    models.add(model);
                }
            }
            return ClarpseKotlinCompiler.assemble(models, index);
        }

        List<ProjectFile> filesAt(final Collection<String> paths) {
            final List<ProjectFile> result = new ArrayList<>();
            for (final String path : paths) {
                final ProjectFile file = byPath.get(path);
                if (file != null) {
                    result.add(file);
                }
            }
            return result;
        }

        Set<CompileFailure> failuresOf(final Collection<String> paths) {
            final Set<CompileFailure> result = new HashSet<>();
            for (final String path : paths) {
                final CompileFailure failure = failures.get(path);
                if (failure != null) {
                    result.add(failure);
                }
            }
            return result;
        }

        void clear() {
            parsed.clear();
            failures.clear();
        }
    }

    /** A Kotlin one-level compile between discovering level one and modelling it. */
    private final class KotlinPreparedAnalysis extends AbstractPreparedAnalysis {

        private final ParsedFiles files;
        private Set<String> focus;

        KotlinPreparedAnalysis(final AnalysisOptions options, final List<ProjectFile> analysed,
                               final List<ProjectFile> allFiles, final Set<String> discovered,
                               final ParsedFiles files, final Set<String> focus) {
            super(options, analysed, allFiles, discovered);
            this.files = files;
            this.focus = focus;
        }

        @Override
        protected CompileResult complete(final LevelOneSelection selection) throws CompileException {
            if (focus.isEmpty()) {
                return new CompileResult(new OOPSourceCodeModel(), Set.of()).withLevelOne(
                        new LevelOneReport(List.of(), List.of(), List.of(), 0));
            }
            final Set<String> emitted = new TreeSet<>(focus);
            emitted.addAll(selection.modelledPaths());
            files.parse(emitted);
            final OOPSourceCodeModel model = files.assemble(emitted);
            CompilerSupport.classifyReferences(model,
                    reference -> files.index.declares(reference.invokedComponent()));
            CompilerSupport.markBoundary(model, selection.modelledPaths());
            return new CompileResult(model, files.failuresOf(emitted)).withLevelOne(
                    CompilerSupport.levelOneReport(model, selection, List.of()));
        }

        @Override
        protected Extension extend(final List<ProjectFile> added, final List<ProjectFile> focusFiles)
                throws CompileException {
            final Set<String> extended = new TreeSet<>(focus);
            added.forEach(file -> extended.add(file.path()));
            files.parse(extended);
            final Set<String> discovered = discoverLevelOne(files.assemble(extended), extended, files.index);
            this.focus = extended;
            return new Extension(files.filesAt(extended), discovered);
        }

        @Override
        protected void release() {
            files.clear();
        }
    }

    /** The Kotlin files declaring what the analysed components reference, less the analysed files. */
    private static Set<String> discoverLevelOne(final OOPSourceCodeModel focusModel, final Set<String> focus,
                                                final KotlinDeclarationIndex index) {
        final Set<String> levelOne = new TreeSet<>();
        focusModel.components()
                .filter(component -> component.sourceFile() != null && focus.contains(component.sourceFile()))
                .forEach(component -> component.references().forEach(
                        reference -> levelOne.addAll(index.kotlinFilesDeclaring(reference.invokedComponent()))));
        levelOne.removeAll(focus);
        return levelOne;
    }

    private static OOPSourceCodeModel assemble(final Collection<KotlinFileModel> files,
                                               final KotlinDeclarationIndex index) throws CompileException {
        final OOPSourceCodeModel model;
        try {
            model = KotlinModelAssembler.buildModel(files, index);
        } catch (final IllegalStateException e) {
            throw new CompileException("An error occurred while assembling Kotlin files.", e);
        }
        CompilerSupport.classifyClassCyclo(model, Set.of(ComponentType.CLASS, ComponentType.ENUM));
        return model;
    }

    private static JavaDeclarationIndex javaIndex(final ProjectFiles projectFiles) {
        final Collection<ProjectFile> javaFiles = projectFiles.files(Lang.JAVA);
        if (javaFiles.isEmpty()) {
            return null;
        }
        return JavaDeclarationIndex.of(javaFiles);
    }

    static List<KotlinFileModel> models(final List<KotlinModel.ParseOutcome> outcomes,
                                                final Set<CompileFailure> failures) {
        final List<KotlinFileModel> models = new ArrayList<>();
        for (final KotlinModel.ParseOutcome outcome : outcomes) {
            if (outcome.failure() != null) {
                failures.add(outcome.failure());
            } else if (outcome.fileModel() != null) {
                models.add(outcome.fileModel());
            }
        }
        return models;
    }

    static List<KotlinModel.ParseOutcome> parseFiles(final List<ProjectFile> files,
                                                             final boolean withBodies) throws CompileException {
        if (files.isEmpty()) {
            return List.of();
        }
        final int parallelism = CompilerParallelismSupport.resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing Kotlin files in parallel using " + parallelism + " threads.");
        }
        final ExecutorService executor = Executors.newFixedThreadPool(parallelism); // NOPMD CloseResource - closed in finally
        try {
            final List<Future<KotlinModel.ParseOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i += 1) {
                if (Thread.currentThread().isInterrupted()) {
                    futures.forEach(future -> future.cancel(true));
                    throw new CompileException("Interrupted while dispatching Kotlin parse tasks.",
                            new InterruptedException());
                }
                futures.add(executor.submit(new KotlinParseTask(files.get(i), i, withBodies)));
            }
            final List<KotlinModel.ParseOutcome> outcomes = new ArrayList<>();
            for (final Future<KotlinModel.ParseOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (final InterruptedException e) {
                    futures.forEach(pending -> pending.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new CompileException("Interrupted while parsing Kotlin files.", e);
                } catch (final ExecutionException e) {
                    throw new CompileException("Failed while parsing Kotlin files.", e);
                }
            }
            outcomes.sort((left, right) -> Integer.compare(left.index(), right.index()));
            return outcomes;
        } finally {
            CompilerParallelismSupport.shutdownExecutor(executor);
        }
    }
}
