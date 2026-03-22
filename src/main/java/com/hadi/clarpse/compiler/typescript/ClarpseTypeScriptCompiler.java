package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptFileModel;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            final TypeScriptDaemon.InitResult initResult = daemon.initRepo(persistDir);
            addInvalidConfigFailures(initResult, compileFailures, persistDir);
            for (final ProjectFile file : tsFiles) {
                final String diskPath = CompilerSupport.resolveFileOnDisk(persistDir, file.path());
                final TypeScriptFileModel fileModel;
                try {
                    fileModel = daemon.getFileModel(diskPath);
                } catch (final TypeScriptDaemonException e) {
                    if (e.code() == TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM) {
                        LOGGER.debug("Skipping TypeScript file outside program scope: {}", file.path());
                        continue;
                    }
                    if (isFileLevelFailure(e)) {
                        compileFailures.add(new CompileFailure(file, e.getMessage(), e.code()));
                        LOGGER.warn("TypeScript resolver failed for file {} (code={}).",
                                file.path(), e.code(), e);
                        continue;
                    }
                    throw new CompileException("TypeScript resolver failed: " + e.getMessage(), e);
                }
                final Package pkg = TypeScriptModelAssembler.resolvePackage(persistDir, diskPath);
                final String moduleName = CompilerSupport.moduleNameForFile(diskPath);
                TypeScriptModelAssembler.insertFileModel(pkg, moduleName, file.path(), persistDir, fileModel, srcModel);
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

    private static boolean isFileLevelFailure(final TypeScriptDaemonException e) {
        if (e == null) {
            return false;
        }
        return e.code() == TypeScriptDaemonException.CODE_FILE_NOT_FOUND;
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
