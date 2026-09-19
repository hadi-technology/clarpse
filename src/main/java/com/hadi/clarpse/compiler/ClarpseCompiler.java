package com.hadi.clarpse.compiler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles source code into an object-oriented representation of the original code.
 */
public interface ClarpseCompiler {

    /**
     * Compiles source code.
     *
     * @param projectFiles Files to compile.
     * @return See {@link CompileResult}
     */
    default CompileResult compile(final ProjectFiles projectFiles) throws CompileException {
        return compile(projectFiles, null);
    }

    /**
     * Compiles source code with an optional file scope.
     *
     * @param projectFiles Files to compile.
     * @param analyzedFilePaths Absolute or project-relative file paths to include in analysis.
     *                          Pass {@code null} to analyze all files. Pass an empty collection
     *                          to analyze no files.
     * @return See {@link CompileResult}
     */
    CompileResult compile(ProjectFiles projectFiles, Collection<String> analyzedFilePaths) throws CompileException;

    /**
     * Compiles source code with an optional file scope and the given options.
     *
     * <p>A compile that is not one-level (see {@link AnalysisOptions#isOneLevel(Collection)}) is
     * exactly {@link #compile(ProjectFiles, Collection)}.
     *
     * @param projectFiles      Files to compile.
     * @param analyzedFilePaths File paths to analyse, {@code null} for all files.
     * @param options           Analysis options; {@code null} is read as {@link AnalysisOptions#full()}.
     * @return See {@link CompileResult}
     */
    default CompileResult compile(final ProjectFiles projectFiles,
                                  final Collection<String> analyzedFilePaths,
                                  final AnalysisOptions options) throws CompileException {
        if (options == null || !options.isOneLevel(analyzedFilePaths)) {
            return compile(projectFiles, analyzedFilePaths);
        }
        throw new UnsupportedOperationException("One-level analysis is not supported by " + getClass().getSimpleName());
    }

    /**
     * The level-one files of the given analysed files, without modelling them.
     *
     * @param projectFiles      Files to compile.
     * @param analyzedFilePaths File paths to analyse; must not be {@code null}.
     * @param options           Analysis options; only the budget-independent discovery is used.
     * @return The discovered level-one paths, sorted, in the form of {@link ProjectFile#path()},
     *         excluding the analysed files.
     */
    default Set<String> levelOneFiles(final ProjectFiles projectFiles,
                                      final Collection<String> analyzedFilePaths,
                                      final AnalysisOptions options) throws CompileException {
        throw new UnsupportedOperationException("One-level analysis is not supported by " + getClass().getSimpleName());
    }

    /**
     * Filters project files based on the provided file paths.
     *
     * @param projectFiles The project files to filter.
     * @param lang The language to filter by.
     * @param analyzedFilePaths File paths to include in analysis. Paths are normalized to support
     *                           both forward-slash and backslash separators, with or without
     *                           leading/trailing slashes. Pass {@code null} to analyze all files,
     *                           or an empty collection to analyze no files.
     * @return List of project files matching the provided paths.
     */
    static List<ProjectFile> analyzedFiles(final ProjectFiles projectFiles,
                                           final Lang lang,
                                           final Collection<String> analyzedFilePaths) {
        final Collection<ProjectFile> files = projectFiles.files(lang);
        if (analyzedFilePaths == null) {
            return new ArrayList<>(files);
        }
        if (analyzedFilePaths.isEmpty()) {
            return List.of();
        }
        final Set<String> normalizedIncludedPaths = new HashSet<>();
        for (String path : analyzedFilePaths) {
            normalizedIncludedPaths.add(normalizeForComparison(path));
        }
        final List<ProjectFile> result = new ArrayList<>();
        for (final ProjectFile file : files) {
            if (normalizedIncludedPaths.contains(normalizeForComparison(file.path()))) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Normalizes a file path for comparison purposes. This method ensures consistent
     * path comparison across different operating systems by:
     * <ul>
     *   <li>Converting backslashes to forward slashes</li>
     *   <li>Removing leading and trailing slashes</li>
     *   <li>Normalizing the path using {@link Path#normalize()}</li>
     * </ul>
     *
     * @param path The path to normalize.
     * @return The normalized path string.
     */
    static String normalizeForComparison(final String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        // Convert backslashes to forward slashes
        String normalized = path.replace('\\', '/');
        // Remove leading slashes for comparison (ProjectFile paths always start with /)
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // Remove trailing slashes
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // Use Path.normalize() to handle . and .. segments
        try {
            return Paths.get(normalized).normalize().toString().replace('\\', '/');
        } catch (Exception e) {
            return normalized;
        }
    }
}
