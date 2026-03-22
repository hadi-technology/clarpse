package com.hadi.clarpse.compiler;

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
     * @return See {@link CompileResult}
     */
    CompileResult compile(ProjectFiles projectFiles, Collection<String> analyzedFilePaths) throws CompileException;

    static List<ProjectFile> analyzedFiles(final ProjectFiles projectFiles,
                                           final Lang lang,
                                           final Collection<String> analyzedFilePaths) {
        final Collection<ProjectFile> files = projectFiles.files(lang);
        if (analyzedFilePaths == null || analyzedFilePaths.isEmpty()) {
            return new ArrayList<>(files);
        }
        final Set<String> includedPaths = new HashSet<>(analyzedFilePaths);
        final List<ProjectFile> result = new ArrayList<>();
        for (final ProjectFile file : files) {
            if (includedPaths.contains(file.path())) {
                result.add(file);
            }
        }
        return result;
    }
}
