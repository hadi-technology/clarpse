package com.hadi.clarpse.compiler.python;

import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.python.model.PythonFileModel;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
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
 * Python compiler backed by a Node-based resolver.
 */
public class ClarpsePythonCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpsePythonCompiler.class);
    private static final String PARALLELISM_ENV = "CLARPSE_PYTHON_PARALLELISM";
    private static final String PARALLELISM_PROP = "clarpse.python.parallelism";
    private static final int DEFAULT_MAX_PARALLELISM = 4;
    private static final int MIN_FILES_FOR_PARALLEL = 8;

    @Override
    public CompileResult compile(final ProjectFiles projectFiles) throws CompileException {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<CompileFailure> compileFailures = new HashSet<>();
        final List<ProjectFile> pyFiles = new ArrayList<>(projectFiles.files(Lang.PYTHON));

        if (pyFiles.isEmpty()) {
            return new CompileResult(srcModel, compileFailures);
        }

        if (!NodeRuntime.isNodeAvailable()) {
            for (final ProjectFile file : pyFiles) {
                compileFailures.add(new CompileFailure(file,
                        "Node.js not found. Python parsing requires Node.js.",
                        PythonDaemonException.CODE_NODE_NOT_FOUND));
            }
            return new CompileResult(srcModel, compileFailures);
        }

        final String persistDir = projectFiles.projectDir();
        final String pythonVersionOverride = System.getProperty("clarpse.python.version");
        final ParseResults parseResults = parsePythonFiles(pyFiles, persistDir, pythonVersionOverride);
        srcModel.merge(parseResults.model);
        compileFailures.addAll(parseResults.failures);
        CompilerSupport.classifyClassCyclo(srcModel, EnumSet.of(OOPSourceModelConstants.ComponentType.CLASS));
        CompilerSupport.classifyReferences(srcModel);
        return new CompileResult(srcModel, compileFailures);
    }

    private ParseResults parsePythonFiles(final List<ProjectFile> files,
                                          final String persistDir,
                                          final String pythonVersionOverride) throws CompileException {
        final int parallelism = resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing Python files in parallel using {} workers.", parallelism);
            return parsePythonFilesParallel(files, persistDir, pythonVersionOverride, parallelism);
        }
        return parsePythonFilesSerial(files, persistDir, pythonVersionOverride);
    }

    private ParseResults parsePythonFilesSerial(final List<ProjectFile> files,
                                                final String persistDir,
                                                final String pythonVersionOverride) throws CompileException {
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        final Set<CompileFailure> failures = new HashSet<>();
        try (PythonDaemon daemon = new PythonDaemon()) {
            daemon.start();
            daemon.initRepo(persistDir, pythonVersionOverride);
            for (int i = 0; i < files.size(); i += 1) {
                final ParseOutcome outcome = parseSingleFile(files.get(i), i, persistDir, daemon);
                model.merge(outcome.model);
                if (outcome.failure != null) {
                    failures.add(outcome.failure);
                }
            }
        } catch (final PythonDaemonException e) {
            throw new CompileException("Python resolver failed: " + e.getMessage(), e);
        }
        return new ParseResults(model, failures);
    }

    private ParseResults parsePythonFilesParallel(final List<ProjectFile> files,
                                                  final String persistDir,
                                                  final String pythonVersionOverride,
                                                  final int parallelism) throws CompileException {
        final List<List<IndexedProjectFile>> partitions = new ArrayList<>();
        for (int i = 0; i < parallelism; i += 1) {
            partitions.add(new ArrayList<>());
        }
        for (int i = 0; i < files.size(); i += 1) {
            partitions.get(i % parallelism).add(new IndexedProjectFile(i, files.get(i)));
        }

        try (CloseableExecutorService closeableExecutor = CloseableExecutorService.newFixedThreadPool(parallelism)) {
            final List<Future<List<ParseOutcome>>> futures = new ArrayList<>();
            for (final List<IndexedProjectFile> partition : partitions) {
                if (!partition.isEmpty()) {
                    futures.add(closeableExecutor.submit(new PartitionTask(partition,
                            persistDir,
                            pythonVersionOverride)));
                }
            }
            final List<ParseOutcome> outcomes = new ArrayList<>();
            for (final Future<List<ParseOutcome>> future : futures) {
                try {
                    outcomes.addAll(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompileException("Interrupted while parsing Python files.", e);
                } catch (ExecutionException e) {
                    throw new CompileException("Failed while parsing Python files in parallel.", e);
                }
            }
            outcomes.sort((a, b) -> Integer.compare(a.index, b.index));

            final OOPSourceCodeModel model = new OOPSourceCodeModel();
            final Set<CompileFailure> failures = new HashSet<>();
            for (final ParseOutcome outcome : outcomes) {
                model.merge(outcome.model);
                if (outcome.failure != null) {
                    failures.add(outcome.failure);
                }
            }
            return new ParseResults(model, failures);
        }
    }

    private ParseOutcome parseSingleFile(final ProjectFile file,
                                         final int index,
                                         final String persistDir,
                                         final PythonDaemon daemon) throws PythonDaemonException {
        final OOPSourceCodeModel localModel = new OOPSourceCodeModel();
        CompileFailure failure = null;
        final String diskPath = CompilerSupport.resolveFileOnDisk(persistDir, file.path());
        if (PythonModelAssembler.shouldSkipPath(diskPath)) {
            failure = new CompileFailure(file,
                    "Skipped excluded path.",
                    PythonDaemonException.CODE_FILE_EXCLUDED);
            return new ParseOutcome(index, localModel, failure);
        }
        final PythonFileModel fileModel;
        try {
            fileModel = daemon.getFileModel(diskPath);
        } catch (final PythonDaemonException e) {
            if (isFileLevelFailure(e)) {
                failure = new CompileFailure(file, e.getMessage(), e.code());
                LOGGER.warn("Python resolver failed for file {} (code={}): {}",
                        file.path(), e.code(), e.getMessage());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Python resolver stack trace for file-level failure.", e);
                }
                return new ParseOutcome(index, localModel, failure);
            }
            throw e;
        }
        final Package pkg = PythonModelAssembler.resolvePackage(fileModel);
        final String moduleName;
        if (fileModel.moduleName == null) {
            moduleName = "";
        } else {
            moduleName = fileModel.moduleName;
        }
        PythonModelAssembler.insertFileModel(pkg, moduleName, file.path(), fileModel, localModel);
        return new ParseOutcome(index, localModel, failure);
    }

    private int resolveParallelism(final int fileCount) {
        if (fileCount < MIN_FILES_FOR_PARALLEL) {
            return 1;
        }
        final String propertyOverride = System.getProperty(PARALLELISM_PROP);
        final String envOverride = System.getenv(PARALLELISM_ENV);
        final String override;
        if (propertyOverride != null) {
            override = propertyOverride;
        } else {
            override = envOverride;
        }
        if (override != null) {
            try {
                final int requested = Integer.parseInt(override.trim());
                if (requested <= 1) {
                    return 1;
                }
                return Math.min(requested, fileCount);
            } catch (NumberFormatException ignored) {
                LOGGER.warn("Invalid Python parallelism override '{}'.", override);
            }
        }
        final int available = Runtime.getRuntime().availableProcessors();
        final int maxDefault = Math.max(1, Math.min(DEFAULT_MAX_PARALLELISM, available));
        return Math.max(1, Math.min(maxDefault, fileCount));
    }

    private static boolean isFileLevelFailure(final PythonDaemonException e) {
        if (e == null) {
            return false;
        }
        return e.code() == PythonDaemonException.CODE_FILE_NOT_FOUND
                || e.code() == PythonDaemonException.CODE_PARSE_FAILED
                || e.code() == PythonDaemonException.CODE_FILE_EXCLUDED;
    }

    private static final class CloseableExecutorService implements AutoCloseable {

        private final ExecutorService delegate;

        private CloseableExecutorService(final ExecutorService delegate) {
            this.delegate = delegate;
        }

        static CloseableExecutorService newFixedThreadPool(final int threads) {
            return new CloseableExecutorService(Executors.newFixedThreadPool(threads));
        }

        <T> Future<T> submit(final Callable<T> task) {
            return delegate.submit(task);
        }

        @Override
        public void close() {
            delegate.shutdown();
            try {
                if (!delegate.awaitTermination(30, TimeUnit.SECONDS)) {
                    delegate.shutdownNow();
                }
            } catch (final InterruptedException e) {
                delegate.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class ParseResults {
        private final OOPSourceCodeModel model;
        private final Set<CompileFailure> failures;

        private ParseResults(final OOPSourceCodeModel model, final Set<CompileFailure> failures) {
            this.model = model;
            this.failures = failures;
        }
    }

    private static final class ParseOutcome {
        private final int index;
        private final OOPSourceCodeModel model;
        private final CompileFailure failure;

        private ParseOutcome(final int index, final OOPSourceCodeModel model, final CompileFailure failure) {
            this.index = index;
            this.model = model;
            this.failure = failure;
        }
    }

    private static final class IndexedProjectFile {
        private final int index;
        private final ProjectFile file;

        private IndexedProjectFile(final int index, final ProjectFile file) {
            this.index = index;
            this.file = file;
        }
    }

    private final class PartitionTask implements Callable<List<ParseOutcome>> {
        private final List<IndexedProjectFile> files;
        private final String persistDir;
        private final String pythonVersionOverride;

        private PartitionTask(final List<IndexedProjectFile> files,
                              final String persistDir,
                              final String pythonVersionOverride) {
            this.files = files;
            this.persistDir = persistDir;
            this.pythonVersionOverride = pythonVersionOverride;
        }

        @Override
        public List<ParseOutcome> call() {
            final List<ParseOutcome> outcomes = new ArrayList<>();
            try (PythonDaemon daemon = new PythonDaemon()) {
                daemon.start();
                daemon.initRepo(persistDir, pythonVersionOverride);
                for (final IndexedProjectFile indexedFile : files) {
                    outcomes.add(parseSingleFile(indexedFile.file, indexedFile.index, persistDir, daemon));
                }
                return outcomes;
            } catch (PythonDaemonException e) {
                throw new IllegalStateException("Python resolver failed: " + e.getMessage(), e);
            }
        }
    }
}
