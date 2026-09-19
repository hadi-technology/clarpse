package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.AbstractPreparedAnalysis;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.InterruptWatchdog;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.LevelOneReport;
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
import java.util.TreeSet;
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
            for (final ProjectFile file : tsFiles) {
                modelFile(daemon, file, persistDir, false, srcModel, compileFailures, null);
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
     * @param inRepositoryNames Collects the names references resolved to inside the repository; may
     *                          be {@code null}.
     */
    private static void modelFile(final TypeScriptDaemon daemon, final ProjectFile file,
                                  final String persistDir, final boolean boundary,
                                  final OOPSourceCodeModel srcModel,
                                  final Set<CompileFailure> compileFailures,
                                  final Set<String> inRepositoryNames) throws CompileException {
        if (Thread.currentThread().isInterrupted()) {
            throw new CompileException("Interrupted while parsing TypeScript files.",
                    new InterruptedException());
        }
        final String diskPath = CompilerSupport.resolveFileOnDisk(persistDir, file.path());
        final TypeScriptFileModel fileModel;
        try {
            fileModel = daemon.getFileModel(diskPath, boundary);
        } catch (final TypeScriptDaemonException e) {
            if (isFileLevelFailure(e)) {
                compileFailures.add(new CompileFailure(file, e.getMessage(), e.code()));
                LOGGER.warn("TypeScript resolver failed for file {} (code={}).",
                        file.path(), e.code(), e);
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
        final Set<CompileFailure> failures = new HashSet<>();
        if (focusFiles.isEmpty()) {
            return new TypeScriptPreparedAnalysis(options, focusFiles, allFiles, List.of(), null, null, failures);
        }
        if (!NodeRuntime.isNodeAvailable()) {
            for (final ProjectFile file : focusFiles) {
                failures.add(new CompileFailure(file,
                        "Node.js not found. TypeScript parsing requires Node.js.",
                        TypeScriptDaemonException.CODE_NODE_NOT_FOUND));
            }
            return new TypeScriptPreparedAnalysis(options, focusFiles, allFiles, List.of(), null, null, failures);
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
            return new TypeScriptPreparedAnalysis(options, focusFiles, allFiles, discovered, diskPaths,
                    persistDir, failures);
        } catch (final TypeScriptDaemonException e) {
            for (final ProjectFile file : focusFiles) {
                failures.add(new CompileFailure(file, e.getMessage(), daemonFailureCode(e)));
            }
            LOGGER.warn("TypeScript one-level analysis failed (code={}).", daemonFailureCode(e), e);
            return new TypeScriptPreparedAnalysis(options, focusFiles, allFiles, List.of(), null, null, failures);
        }
    }

    /** A TypeScript one-level compile between discovering level one and modelling it. */
    private static final class TypeScriptPreparedAnalysis extends AbstractPreparedAnalysis {

        private final DiskPaths diskPaths;
        private final String persistDir;
        private final Set<CompileFailure> prepareFailures;

        TypeScriptPreparedAnalysis(final AnalysisOptions options, final List<ProjectFile> focusFiles,
                                   final List<ProjectFile> allFiles, final List<String> discovered,
                                   final DiskPaths diskPaths, final String persistDir,
                                   final Set<CompileFailure> prepareFailures) {
            super(options, focusFiles, allFiles, discovered);
            this.diskPaths = diskPaths;
            this.persistDir = persistDir;
            this.prepareFailures = prepareFailures;
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
                for (final ProjectFile file : analysed()) {
                    modelFile(daemon, file, persistDir, false, srcModel, compileFailures, inRepositoryNames);
                }
                for (final ProjectFile file : selection.modelled()) {
                    modelFile(daemon, file, persistDir, true, srcModel, compileFailures, inRepositoryNames);
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
        for (final TypeScriptDaemon.InvalidConfig invalidConfig : initResult.invalidConfigs()) {
            final String normalizedPath = relativeProjectPath(persistDir, invalidConfig.configPath());
            final String message = switch (invalidConfig.error()) {
                case "PROGRAM_CREATE_FAILED" -> "PROGRAM_CREATE_FAILED";
                case "CONFIG_READ_FAILED", "CONFIG_PARSE_FAILED" -> "CONFIG_PARSE_FAILED";
                default -> "CONFIG_INVALID";
            };
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
