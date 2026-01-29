package com.hadi.clarpse.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StringProvider;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.hadi.clarpse.listener.JavaTreeListener;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
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
    public CompileResult compile(final ProjectFiles projectFiles) throws CompileException {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<ProjectFile> compileFailures = new HashSet<>();
        final List<ProjectFile> javaFiles = new ArrayList<>(projectFiles.files(Lang.JAVA));
        if (!javaFiles.isEmpty()) {
            String persistDir = null;
            try {
                persistDir = projectFiles.projectDir();
                final ParseResults parseResults = parseJavaFiles(javaFiles, persistDir);
                srcModel.merge(parseResults.model);
                compileFailures.addAll(parseResults.failures);
            } catch (Exception e) {
                throw new CompileException("An error occurred while parsing!", e);
            } finally {
                if (persistDir != null && !persistDir.isEmpty() && projectFiles.isTempProjectDir()) {
                    FileUtils.deleteQuietly(new File(persistDir));
                }
            }
            // Classify component references as internal/external
            classifyRefs(srcModel);
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
        final Set<ProjectFile> compileFailures = new HashSet<>();
        final CombinedTypeSolver typeSolver = setupTypeSolver(persistDir);
        final ParserConfiguration parserConfiguration = setupParserConfig(typeSolver);
        final JavaParser parser = new JavaParser(parserConfiguration);
        for (final ProjectFile file : files) {
            final ParseOutcome outcome = parseSingleFile(parser, typeSolver, file);
            srcModel.merge(outcome.model);
            if (outcome.failure != null) {
                compileFailures.add(outcome.failure);
            }
        }
        return new ParseResults(srcModel, compileFailures);
    }

    private ParseResults parseJavaFilesParallel(final List<ProjectFile> files, final String persistDir,
                                                final int parallelism) {
        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
            final ThreadLocal<ParserContext> parserContext = ThreadLocal.withInitial(
                    () -> new ParserContext(persistDir));
            final List<Future<ParseOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                final int index = i;
                final ProjectFile file = files.get(i);
                futures.add(executor.submit(new ParseTask(parserContext, file, index)));
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
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            outcomes.sort((a, b) -> Integer.compare(a.index, b.index));
            final OOPSourceCodeModel mergedModel = new OOPSourceCodeModel();
            final Set<ProjectFile> compileFailures = new HashSet<>();
            for (final ParseOutcome outcome : outcomes) {
                mergedModel.merge(outcome.model);
                if (outcome.failure != null) {
                    compileFailures.add(outcome.failure);
                }
            }
            return new ParseResults(mergedModel, compileFailures);
        }
    }

    private ParseOutcome parseSingleFile(final JavaParser parser,
                                         final CombinedTypeSolver typeSolver,
                                         final ProjectFile file) {
        final OOPSourceCodeModel localModel = new OOPSourceCodeModel();
        ProjectFile failure = null;
        try {
            final CompilationUnit cu = parser.parse(ParseStart.COMPILATION_UNIT,
                    new StringProvider(file.content())).getResult().get();
            if (cu.getParsed() == Node.Parsedness.UNPARSABLE || file.content().isEmpty()) {
                LOGGER.warn("Compilation unit (" + file.path() + ") is unparseable!");
                failure = file;
            }
            new JavaTreeListener(localModel, file, typeSolver).visit(cu, null);
        } catch (final Exception e) {
            LOGGER.error("Failed to parse file " + file.path() + ".", e);
            failure = file;
        }
        return new ParseOutcome(-1, localModel, failure);
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
            }
        }
        final int available = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(available, fileCount));
    }

    private void classifyRefs(OOPSourceCodeModel srcModel) {
        srcModel.components().forEach(component -> {
            final Set<ComponentReference> internalReferences = new LinkedHashSet<>();
            final Set<ComponentReference> externalReferences = new LinkedHashSet<>();
            component.references().forEach(componentReference -> {
                final boolean isInternal = srcModel.containsComponent(componentReference.invokedComponent());
                componentReference.setExternal(!isInternal);
                if (isInternal) {
                    internalReferences.add(componentReference);
                } else {
                    externalReferences.add(componentReference);
                }
            });
            component.setReferenceClassification(internalReferences, externalReferences);
        });
        LOGGER.debug("Classified component references as internal/external.");
    }

    private static CombinedTypeSolver setupTypeSolver(String persistDir) {
        final CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(persistDir));
        return typeSolver;
    }

    private static ParserConfiguration setupParserConfig(CombinedTypeSolver typeSolver) {
        final ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        parserConfiguration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        parserConfiguration.setIgnoreAnnotationsWhenAttributingComments(true);
        return parserConfiguration;
    }

    private static final class ParserContext {
        private final CombinedTypeSolver typeSolver;
        private final JavaParser parser;

        private ParserContext(final String persistDir) {
            this.typeSolver = setupTypeSolver(persistDir);
            this.parser = new JavaParser(setupParserConfig(this.typeSolver));
        }
    }

    private static final class ParseResults {
        private final OOPSourceCodeModel model;
        private final Set<ProjectFile> failures;

        private ParseResults(final OOPSourceCodeModel model, final Set<ProjectFile> failures) {
            this.model = model;
            this.failures = failures;
        }
    }

    private static final class ParseOutcome {
        private final int index;
        private final OOPSourceCodeModel model;
        private final ProjectFile failure;

        private ParseOutcome(final int index, final OOPSourceCodeModel model, final ProjectFile failure) {
            this.index = index;
            this.model = model;
            this.failure = failure;
        }
    }

    private static final class ParseTask implements Callable<ParseOutcome> {
        private final ThreadLocal<ParserContext> context;
        private final ProjectFile file;
        private final int index;

        private ParseTask(final ThreadLocal<ParserContext> context, final ProjectFile file, final int index) {
            this.context = context;
            this.file = file;
            this.index = index;
        }

        @Override
        public ParseOutcome call() {
            final ParserContext parserContext = context.get();
            final OOPSourceCodeModel localModel = new OOPSourceCodeModel();
            ProjectFile failure = null;
            try {
                final CompilationUnit cu = parserContext.parser.parse(ParseStart.COMPILATION_UNIT,
                        new StringProvider(file.content())).getResult().get();
                if (cu.getParsed() == Node.Parsedness.UNPARSABLE || file.content().isEmpty()) {
                    LOGGER.warn("Compilation unit (" + file.path() + ") is unparseable!");
                    failure = file;
                }
                new JavaTreeListener(localModel, file, parserContext.typeSolver).visit(cu, null);
            } catch (final Exception e) {
                LOGGER.error("Failed to parse file " + file.path() + ".", e);
                failure = file;
            }
            return new ParseOutcome(index, localModel, failure);
        }
    }
}
