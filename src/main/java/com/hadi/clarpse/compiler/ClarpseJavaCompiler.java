package com.hadi.clarpse.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.hadi.clarpse.compiler.java.FileParser;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * JavaParser based compiler to process source code.
 */
public class ClarpseJavaCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseJavaCompiler.class);
    private static final String PARALLELISM_ENV = "CLARPSE_PARALLELISM";
    private static final int MIN_FILES_FOR_PARALLEL = 2;

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
                final ParseResults parseResults = parseJavaFiles(javaFiles, persistDir);
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

    private ParseResults parseJavaFiles(final List<ProjectFile> files, final String persistDir) {
        final int parallelism = resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing Java files in parallel using " + parallelism + " threads.");
            return parseJavaFilesParallel(files, persistDir, parallelism);
        }
        return parseJavaFilesSerial(files, persistDir);
    }

    private ParseResults parseJavaFilesSerial(final List<ProjectFile> files, final String persistDir) {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<CompileFailure> compileFailures = new HashSet<>();
        final CombinedTypeSolver typeSolver = JavaParserFactory.setupTypeSolver(persistDir);
        final JavaParser parser = new JavaParser(JavaParserFactory.setupParserConfig(typeSolver));
        for (final ProjectFile file : files) {
            final ParseOutcome outcome = FileParser.parseFile(parser, typeSolver, file, -1);
            srcModel.merge(outcome.model());
            if (outcome.failure() != null) {
                compileFailures.add(outcome.failure());
            }
        }
        return new ParseResults(srcModel, compileFailures);
    }

    @SuppressWarnings("PMD.CloseResource")
    private ParseResults parseJavaFilesParallel(final List<ProjectFile> files, final String persistDir,
                                                final int parallelism) {
        final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            final ThreadLocal<ParserContext> parserContext = ThreadLocal.withInitial(
                    () -> new ParserContext(persistDir));
            final List<Future<ParseOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                final ProjectFile file = files.get(i);
                futures.add(executor.submit(new ParseTask(parserContext, file, i)));
            }
            final List<ParseOutcome> outcomes = new ArrayList<>();
            for (final Future<ParseOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while parsing Java files.", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Failed while parsing Java files in parallel.", e);
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
            shutdownExecutor(executor);
        }
    }

    private void shutdownExecutor(final ExecutorService executor) {
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

    private int resolveParallelism(final int fileCount) {
        if (fileCount < MIN_FILES_FOR_PARALLEL) {
            return 1;
        }
        final String override = System.getenv(PARALLELISM_ENV);
        if (override != null) {
            try {
                final int requested = Integer.parseInt(override.trim());
                if (requested <= 1) {
                    return 1;
                }
                return Math.min(requested, fileCount);
            } catch (NumberFormatException ignored) {
                // Use default parallelism
            }
        }
        final int available = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(available, fileCount));
    }
}
