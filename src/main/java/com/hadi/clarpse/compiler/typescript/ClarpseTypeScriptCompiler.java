package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.AbstractPreparedAnalysis;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.ClarpseProperties;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.InterruptWatchdog;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.LevelOneSelection;
import com.hadi.clarpse.compiler.PreparedAnalysis;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptFileModel;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TypeScript compiler backed by the Node daemon bridge.
 */
public class ClarpseTypeScriptCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseTypeScriptCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles,
                                 final Collection<String> analyzedFilePaths) throws CompileException {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<CompileFailure> compileFailures = new HashSet<>();
        final List<ProjectFile> tsFiles = ClarpseCompiler.analyzedFiles(projectFiles,
                Lang.TYPESCRIPT,
                analyzedFilePaths);

        if (tsFiles.isEmpty()) {
            return new CompileResult(srcModel, compileFailures);
        }

        if (!NodeRuntime.isNodeAvailable()) {
            for (final ProjectFile file : tsFiles) {
                compileFailures.add(new CompileFailure(
                        file,
                        "Node.js not found. TypeScript parsing requires Node.js.",
                        TypeScriptDaemonException.CODE_NODE_NOT_FOUND));
            }
            return new CompileResult(srcModel, compileFailures);
        }

        final String persistDir = projectFiles.projectDir();
        try (TypeScriptDaemon daemon = new TypeScriptDaemon();
                // TypeScript parses single-threaded on this thread, blocking in the daemon's
                // readLine(), which Thread.interrupt() cannot unblock. The watchdog destroys the
                // daemon when this thread is interrupted (e.g. an analysis deadline), unblocking the
                // read; the between-files check below covers interrupts that land between files. #180.
                InterruptWatchdog watchdog =
                        new InterruptWatchdog(Thread.currentThread(), daemon::forceStop)) {
            daemon.start();
            final TypeScriptDaemon.InitResult initResult = daemon.initRepo(persistDir);
            addInvalidConfigFailures(initResult, compileFailures, persistDir);
            final ModellingBudget budget = new ModellingBudget();
            for (final ProjectFile file : inOwnerOrder(daemon, persistDir, tsFiles, budget)) {
                modelFile(daemon, file, persistDir, false,
                        new ModelSink(srcModel, compileFailures, null), budget);
            }
            CompilerSupport.classifyClassCyclo(srcModel, EnumSet.of(
                    OOPSourceModelConstants.ComponentType.CLASS,
                    OOPSourceModelConstants.ComponentType.ENUM));
            CompilerSupport.classifyReferences(srcModel);
        } catch (final TypeScriptDaemonException e) {
            final int code;
            if (e.code() == 0) {
                code = TypeScriptDaemonException.CODE_DAEMON_ERROR;
            } else {
                code = e.code();
            }
            for (final ProjectFile file : tsFiles) {
                compileFailures.add(new CompileFailure(file, e.getMessage(), code));
            }
            LOGGER.warn("TypeScript resolver initialization failed (code={}).", code, e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ProjectFiles cleanup handled by caller.");
            }
        }
        return new CompileResult(srcModel, compileFailures);
    }

    /**
     * Models one file through the daemon into the given model. A failure confined to the file is
     * recorded and the file skipped; any other daemon failure aborts the compile.
     *
     * @param boundary          Whether the file is a level-one file of a one-level analysis, whose
     *                          bodies are not read.
     * @param sink              Where the file's model, and the failures confined to it, are put.
     */
    private static void modelFile(final TypeScriptDaemon daemon, final ProjectFile file,
                                  final String persistDir, final boolean boundary,
                                  final ModelSink sink,
                                  final ModellingBudget budget) throws CompileException {
        final OOPSourceCodeModel srcModel = sink.model();
        final Set<CompileFailure> compileFailures = sink.failures();
        final Set<String> inRepositoryNames = sink.inRepositoryNames();
        if (Thread.currentThread().isInterrupted()) {
            throw new CompileException("Interrupted while parsing TypeScript files.",
                    new InterruptedException());
        }
        if (budget.spent()) {
            compileFailures.add(new CompileFailure(file,
                    "TIME_BUDGET_EXCEEDED: the file was not analysed, so nothing is known about what it declares.",
                    FailureCode.TIME_BUDGET_EXCEEDED));
            return;
        }
        final String diskPath = CompilerSupport.resolveFileOnDisk(persistDir, file.path());
        final TypeScriptFileModel fileModel;
        try {
            fileModel = daemon.getFileModel(diskPath, boundary);
        } catch (final TypeScriptDaemonException e) {
            if (e.code() == TypeScriptDaemonException.CODE_REQUEST_TIMEOUT) {
                // The daemon was killed for sitting on this file. The file is recorded as not
                // analysed - it is neither absent nor empty - and the rest carries on in a fresh
                // daemon while restarts remain.
                compileFailures.add(new CompileFailure(file, "TIME_BUDGET_EXCEEDED: " + e.getMessage(),
                        FailureCode.TIME_BUDGET_EXCEEDED));
                LOGGER.warn("TypeScript file {} was not analysed: {}", file.path(), e.getMessage());
                budget.recoverFrom(daemon);
                return;
            }
            if (isFileLevelFailure(e)) {
                compileFailures.add(new CompileFailure(file, e.getMessage(), e.code()));
                logFileLevelFailure(file, e);
                return;
            }
            throw new CompileException("TypeScript resolver failed: " + e.getMessage(), e);
        }
        final Package pkg = TypeScriptModelAssembler.resolvePackage(persistDir, diskPath);
        final String moduleName = CompilerSupport.moduleNameForFile(diskPath);
        TypeScriptModelAssembler.insertFileModel(pkg, moduleName, file.path(), persistDir, fileModel, srcModel,
                inRepositoryNames);
        addUnresolvedBaseFailures(file, fileModel, compileFailures);
    }

    /**
     * Discovers the level-one files of a one-level compile.
     *
     * <p>Level one is found by module resolution alone (see {@code discoverLevelOne} in the daemon).
     * Completing the compile plans the analysed and chosen level-one files into programs built with
     * {@code noResolve}, models the analysed files in full and the level-one files without reading
     * their bodies, and marks the level-one components boundary. A reference is left not loaded
     * when it resolved into the repository but its component was not modelled, or when its type was
     * lost. Each phase runs in a daemon session of its own that ends with the phase, so a prepared
     * analysis holds no daemon slot and several may be open at once.
     */
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
        final List<ProjectFile> focusFiles = ClarpseCompiler.analyzedFiles(projectFiles, Lang.TYPESCRIPT,
                analyzedFilePaths);
        final List<ProjectFile> allFiles = new ArrayList<>(projectFiles.files(Lang.TYPESCRIPT));
        return new TypeScriptPreparedAnalysis(options, focusFiles, allFiles,
                Discovery.of(projectFiles, focusFiles, allFiles));
    }

    /**
     * What discovering the level-one files of a set of analysed files yields. With no project
     * directory, discovery could not run and the failures say why.
     *
     * @param discovered The level-one paths.
     * @param diskPaths  Translation between project and on-disk paths, or {@code null}.
     * @param persistDir The project directory the daemon reads, or {@code null}.
     * @param failures   The failures discovering.
     */
    private record Discovery(List<String> discovered, DiskPaths diskPaths, String persistDir,
                             Set<CompileFailure> failures) {

        /** Discovers the level-one files of the given analysed files in a daemon session of its own. */
        static Discovery of(final ProjectFiles projectFiles, final List<ProjectFile> focusFiles,
                            final List<ProjectFile> allFiles) throws CompileException {
            final Set<CompileFailure> failures = new HashSet<>();
            if (focusFiles.isEmpty()) {
                return new Discovery(List.of(), null, null, failures);
            }
            if (!NodeRuntime.isNodeAvailable()) {
                for (final ProjectFile file : focusFiles) {
                    failures.add(new CompileFailure(file,
                            "Node.js not found. TypeScript parsing requires Node.js.",
                            TypeScriptDaemonException.CODE_NODE_NOT_FOUND));
                }
                return new Discovery(List.of(), null, null, failures);
            }
            final String persistDir = projectFiles.projectDir();
            final DiskPaths diskPaths = new DiskPaths(persistDir, allFiles);
            try (TypeScriptDaemon daemon = new TypeScriptDaemon();
                    InterruptWatchdog watchdog =
                            new InterruptWatchdog(Thread.currentThread(), daemon::forceStop)) {
                daemon.start();
                daemon.initRepo(persistDir);
                final List<String> discovered =
                        diskPaths.toProjectPaths(daemon.discoverLevelOne(diskPaths.toDisk(focusFiles)));
                return new Discovery(discovered, diskPaths, persistDir, failures);
            } catch (final TypeScriptDaemonException e) {
                for (final ProjectFile file : focusFiles) {
                    failures.add(new CompileFailure(file, e.getMessage(), daemonFailureCode(e)));
                }
                LOGGER.warn("TypeScript one-level analysis failed (code={}).", daemonFailureCode(e), e);
                return new Discovery(List.of(), null, null, failures);
            }
        }
    }

    /** A TypeScript one-level compile between discovering level one and modelling it. */
    private static final class TypeScriptPreparedAnalysis extends AbstractPreparedAnalysis {

        private final List<ProjectFile> allFiles;
        private DiskPaths diskPaths;
        private String persistDir;
        private Set<CompileFailure> prepareFailures;

        TypeScriptPreparedAnalysis(final AnalysisOptions options, final List<ProjectFile> focusFiles,
                                   final List<ProjectFile> allFiles, final Discovery discovery) {
            super(options, focusFiles, allFiles, discovery.discovered());
            this.allFiles = allFiles;
            this.diskPaths = discovery.diskPaths();
            this.persistDir = discovery.persistDir();
            this.prepareFailures = discovery.failures();
        }

        /**
         * Discovers level one again over every analysed file, in a daemon session of its own. Discovery
         * resolves module specifiers without building a program, and the programs are built by each
         * compile for the configs that own its planned files, so nothing held needs rebuilding.
         */
        @Override
        protected Extension extend(final List<ProjectFile> added, final List<ProjectFile> focus)
                throws CompileException {
            final Discovery discovery = Discovery.of(projectFiles(), focus, allFiles);
            this.diskPaths = discovery.diskPaths();
            this.persistDir = discovery.persistDir();
            this.prepareFailures = discovery.failures();
            return new Extension(focus, discovery.discovered());
        }

        @Override
        protected CompileResult complete(final LevelOneSelection selection) throws CompileException {
            final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
            final Set<CompileFailure> compileFailures = new HashSet<>(prepareFailures);
            if (diskPaths == null) {
                return new CompileResult(srcModel, compileFailures);
            }
            final Set<String> inRepositoryNames = new HashSet<>();
            try (TypeScriptDaemon daemon = new TypeScriptDaemon();
                    InterruptWatchdog watchdog =
                            new InterruptWatchdog(Thread.currentThread(), daemon::forceStop)) {
                daemon.start();
                addInvalidConfigFailures(daemon.initRepo(persistDir), compileFailures, persistDir);
                daemon.planOneLevel(diskPaths.toDisk(analysed()), diskPaths.toDisk(selection.modelled()));
                final ModellingBudget budget = new ModellingBudget();
                for (final ProjectFile file : inOwnerOrder(daemon, persistDir, analysed(), budget)) {
                    modelFile(daemon, file, persistDir, false,
                            new ModelSink(srcModel, compileFailures, inRepositoryNames), budget);
                }
                for (final ProjectFile file : inOwnerOrder(daemon, persistDir, selection.modelled(), budget)) {
                    modelFile(daemon, file, persistDir, true,
                            new ModelSink(srcModel, compileFailures, inRepositoryNames), budget);
                }
            } catch (final TypeScriptDaemonException e) {
                for (final ProjectFile file : analysed()) {
                    compileFailures.add(new CompileFailure(file, e.getMessage(), daemonFailureCode(e)));
                }
                LOGGER.warn("TypeScript one-level analysis failed (code={}).", daemonFailureCode(e), e);
                return new CompileResult(new OOPSourceCodeModel(), compileFailures);
            }
            CompilerSupport.classifyClassCyclo(srcModel, EnumSet.of(
                    OOPSourceModelConstants.ComponentType.CLASS,
                    OOPSourceModelConstants.ComponentType.ENUM));
            CompilerSupport.classifyReferences(srcModel,
                    reference -> reference.resolutionKind() == ResolutionKind.UNRESOLVED
                            || inRepositoryNames.contains(reference.invokedComponent()));
            CompilerSupport.markBoundary(srcModel, selection.modelledPaths());
            return new CompileResult(srcModel, compileFailures).withLevelOne(
                    CompilerSupport.levelOneReport(srcModel, selection, List.of()));
        }

        /** Holds no process. */
        @Override
        protected void release() {
        }
    }

    private static int daemonFailureCode(final TypeScriptDaemonException e) {
        if (e.code() == 0) {
            return TypeScriptDaemonException.CODE_DAEMON_ERROR;
        }
        return e.code();
    }

    /**
     * Translates between project file paths and the canonical on-disk paths the daemon reports,
     * which may differ from a naive join where the temporary directory is reached through a
     * symbolic link.
     */
    private static final class DiskPaths {

        private final String persistDir;
        private final Map<String, String> projectPathByDisk = new HashMap<>();

        DiskPaths(final String persistDir, final List<ProjectFile> files) {
            this.persistDir = persistDir;
            for (final ProjectFile file : files) {
                projectPathByDisk.put(canonical(CompilerSupport.resolveFileOnDisk(persistDir, file.path())),
                        file.path());
            }
        }

        List<String> toDisk(final List<ProjectFile> files) {
            final List<String> disk = new ArrayList<>();
            for (final ProjectFile file : files) {
                disk.add(CompilerSupport.resolveFileOnDisk(persistDir, file.path()));
            }
            return disk;
        }

        List<String> toProjectPaths(final List<String> diskPaths) {
            final List<String> paths = new ArrayList<>();
            for (final String diskPath : diskPaths) {
                final String path = projectPathByDisk.get(canonical(diskPath));
                if (path != null) {
                    paths.add(path);
                }
            }
            return paths;
        }

        private static String canonical(final String path) {
            try {
                return new java.io.File(path).getCanonicalPath();
            } catch (final java.io.IOException e) {
                return Paths.get(path).toAbsolutePath().normalize().toString();
            }
        }
    }

    /**
     * Where a file's model and the failures confined to it go.
     *
     * @param model             The model being built.
     * @param failures          The failures recorded so far.
     * @param inRepositoryNames Collects the names references resolved to inside the repository; may
     *                          be {@code null}.
     */
    private record ModelSink(OOPSourceCodeModel model, Set<CompileFailure> failures,
                             Set<String> inRepositoryNames) {
    }

    /**
     * The files grouped by the program that holds them, and by path within a group. Only a few
     * programs are resident at once, and a program costs far more to build than a file costs to
     * model in it, so files asked for in an order that alternates between programs rebuild them
     * over and over. Where the daemon cannot say, the order is the path order.
     */
    private static List<ProjectFile> inOwnerOrder(final TypeScriptDaemon daemon, final String persistDir,
                                                  final List<ProjectFile> files,
                                                  final ModellingBudget budget) {
        final List<ProjectFile> ordered = new ArrayList<>(files);
        ordered.sort(java.util.Comparator.comparing(ProjectFile::path));
        if (ordered.size() < 2 || budget.spent()) {
            return ordered;
        }
        final Map<String, String> diskByPath = new HashMap<>();
        final List<String> disk = new ArrayList<>();
        for (final ProjectFile file : ordered) {
            final String diskPath = CompilerSupport.resolveFileOnDisk(persistDir, file.path());
            diskByPath.put(file.path(), diskPath);
            disk.add(diskPath);
        }
        final Map<String, Integer> owners;
        try {
            owners = daemon.ownersOf(disk);
        } catch (final TypeScriptDaemonException e) {
            LOGGER.warn("Could not group TypeScript files by program, using path order: {}", e.getMessage());
            if (e.code() == TypeScriptDaemonException.CODE_REQUEST_TIMEOUT) {
                budget.recoverFrom(daemon);
            }
            return ordered;
        }
        // A stable sort: files of one program stay in path order.
        ordered.sort(java.util.Comparator.comparingInt(
                (ProjectFile file) -> owners.getOrDefault(diskByPath.get(file.path()), -1)));
        return ordered;
    }

    /**
     * How long, and through how many daemon restarts, one analysis of TypeScript may go on. Once it
     * is spent, the files not yet modelled are recorded as not analysed instead of waited for.
     */
    private static final class ModellingBudget {

        private static final String BUDGET_PROP = "clarpse.typescript.compileBudgetSeconds";
        private static final String BUDGET_ENV = "CLARPSE_TS_COMPILE_BUDGET_SECONDS";
        private static final String RESTARTS_PROP = "clarpse.typescript.maxRestarts";
        private static final int DEFAULT_BUDGET_SECONDS = 420;
        private static final int DEFAULT_MAX_RESTARTS = 3;

        private final long deadlineNanos;
        private int restartsLeft = ClarpseProperties.getInt(RESTARTS_PROP, DEFAULT_MAX_RESTARTS);
        private boolean abandoned;

        ModellingBudget() {
            double seconds = DEFAULT_BUDGET_SECONDS;
            final String configured = firstNonBlank(System.getProperty(BUDGET_PROP), System.getenv(BUDGET_ENV));
            if (configured != null) {
                try {
                    seconds = Double.parseDouble(configured.trim());
                } catch (final NumberFormatException e) {
                    LOGGER.warn("Invalid TypeScript compile budget '{}', using the default.", configured);
                }
            } else {
                seconds = ClarpseProperties.getInt(BUDGET_PROP, DEFAULT_BUDGET_SECONDS);
            }
            if (seconds > 0) {
                this.deadlineNanos = System.nanoTime() + (long) (seconds * 1_000_000_000L);
            } else {
                this.deadlineNanos = 0L;
            }
        }

        boolean spent() {
            return abandoned || (deadlineNanos != 0L && System.nanoTime() - deadlineNanos >= 0L);
        }

        /** Replaces a daemon that was killed, or gives the rest of the analysis up. */
        void recoverFrom(final TypeScriptDaemon daemon) {
            if (restartsLeft <= 0) {
                abandoned = true;
                return;
            }
            restartsLeft--;
            try {
                daemon.restart();
            } catch (final TypeScriptDaemonException e) {
                LOGGER.warn("Could not restart the TypeScript daemon; the remaining files are not analysed.", e);
                abandoned = true;
            }
        }

        private static String firstNonBlank(final String first, final String second) {
            if (first != null && !first.trim().isEmpty()) {
                return first;
            }
            if (second != null && !second.trim().isEmpty()) {
                return second;
            }
            return null;
        }
    }

    /**
     * Records a failure for each class whose base expression the type checker could not see members
     * through. Such a class carries the members it declares itself and no others, which is
     * indistinguishable in the model from a class that declares none - so the difference has to be
     * carried where a caller can read it.
     *
     * @param file           The source file the class was declared in.
     * @param fileModel      The daemon's model for that file.
     * @param compileFailures Failure set to add to.
     */
    private static void addUnresolvedBaseFailures(final ProjectFile file,
                                                  final TypeScriptFileModel fileModel,
                                                  final Set<CompileFailure> compileFailures) {
        if (fileModel == null || fileModel.unresolvedBases == null) {
            return;
        }
        for (final String unresolvedBase : fileModel.unresolvedBases) {
            compileFailures.add(new CompileFailure(file,
                    "UNRESOLVED_BASE_EXPRESSION: " + unresolvedBase,
                    FailureCode.RESOLUTION_FAILED));
            LOGGER.warn("Could not resolve the base expression of {} in {}.",
                    unresolvedBase, file.path());
        }
    }

    /**
     * Logs a failure confined to one file, at the level the failure deserves.
     *
     * <p>A file that belongs to no program of the project is what a tsconfig that names its files,
     * or excludes tests, scripts or tooling, produces: one such file for every file it leaves out.
     * It is recorded as a failure because a caller cannot otherwise tell it from a file that was
     * analysed and declared nothing, but nothing went wrong and nobody acts on it, so it is a
     * single line naming the file and carries no stack trace.
     *
     * <p>A file that could not be found, or whose references could not be resolved, does say
     * something went wrong, and keeps its warning and its exception.
     *
     * @param file The file the failure is confined to.
     * @param e    The daemon failure recorded for it.
     */
    private static void logFileLevelFailure(final ProjectFile file,
                                            final TypeScriptDaemonException e) {
        if (e.code() == TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM) {
            LOGGER.debug("TypeScript file {} belongs to no program of this project (code={}).",
                    file.path(), e.code());
            return;
        }
        LOGGER.warn("TypeScript resolver failed for file {} (code={}).",
                file.path(), e.code(), e);
    }

    private static boolean isFileLevelFailure(final TypeScriptDaemonException e) {
        if (e == null) {
            return false;
        }
        return e.code() == TypeScriptDaemonException.CODE_FILE_NOT_FOUND
                || e.code() == TypeScriptDaemonException.CODE_RESOLUTION_FAILED
                // A file that belongs to no program was skipped at DEBUG and recorded nowhere, so
                // it reached a caller as a file that was parsed and declared nothing. Whether a
                // file was analysed at all is exactly what the failure list is for.
                || e.code() == TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM;
    }

    private static void addInvalidConfigFailures(final TypeScriptDaemon.InitResult initResult,
                                                 final Set<CompileFailure> compileFailures,
                                                 final String persistDir) {
        for (final TypeScriptDaemon.ConfigDiagnostic diagnostic : initResult.configDiagnostics()) {
            LOGGER.info("{} is used with the options the compiler could read; it said TS{}: {}",
                    relativeProjectPath(persistDir, diagnostic.configPath()), diagnostic.code(),
                    diagnostic.message());
        }
        if (initResult.unownedFileCount() > 0) {
            LOGGER.info("{} TypeScript file(s) are named by no config and are read in {} program(s) "
                    + "of default options.", initResult.unownedFileCount(),
                    initResult.unownedProgramCount());
        }
        for (final TypeScriptDaemon.InvalidConfig invalidConfig : initResult.invalidConfigs()) {
            final String normalizedPath = relativeProjectPath(persistDir, invalidConfig.configPath());
            final String kind = switch (invalidConfig.error()) {
                case "PROGRAM_CREATE_FAILED" -> "PROGRAM_CREATE_FAILED";
                case "CONFIG_READ_FAILED", "CONFIG_PARSE_FAILED" -> "CONFIG_PARSE_FAILED";
                default -> "CONFIG_INVALID";
            };
            final String message;
            if (invalidConfig.message().isEmpty()) {
                message = kind;
            } else {
                message = kind + ": TS" + invalidConfig.code() + " " + invalidConfig.message();
            }
            final Integer code = switch (invalidConfig.error()) {
                case "PROGRAM_CREATE_FAILED" -> TypeScriptDaemonException.CODE_PROGRAM_CREATE_FAILED;
                case "CONFIG_READ_FAILED", "CONFIG_PARSE_FAILED" -> TypeScriptDaemonException.CODE_CONFIG_PARSE_FAILED;
                default -> FailureCode.CONFIG_INVALID;
            };
            compileFailures.add(new CompileFailure(new ProjectFile(normalizedPath, ""), message, code));
        }
    }

    private static String relativeProjectPath(final String persistDir, final String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "/";
        }
        try {
            final Path projectRoot = Paths.get(persistDir).toAbsolutePath().normalize();
            final Path configPath = Paths.get(absolutePath).toAbsolutePath().normalize();
            return "/" + projectRoot.relativize(configPath).toString().replace('\\', '/');
        } catch (final Exception ignored) {
            return absolutePath.replace('\\', '/');
        }
    }
}
