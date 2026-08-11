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

    @SuppressWarnings("PMD.CloseResource")
    private ParseResults parseJavaFiles(final List<ProjectFile> files, final String persistDir) {
        final int parallelism = CompilerParallelismSupport.resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing Java files in parallel using " + parallelism + " threads.");
        }

        final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            final Set<String> sourceRoots = sourceRoots(files, persistDir);
            final ThreadLocal<ParserContext> parserContext = ThreadLocal.withInitial(
                    () -> new ParserContext(persistDir, sourceRoots));
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
