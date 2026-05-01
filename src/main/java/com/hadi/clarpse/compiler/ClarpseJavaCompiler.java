package com.hadi.clarpse.compiler;

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

/**
 * JavaParser based compiler to process source code.
 */
public class ClarpseJavaCompiler implements ClarpseCompiler {

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

    @SuppressWarnings("PMD.CloseResource")
    private ParseResults parseJavaFiles(final List<ProjectFile> files, final String persistDir) {
        final int parallelism = CompilerParallelismSupport.resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing Java files in parallel using " + parallelism + " threads.");
        }

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
        }
    }
}
