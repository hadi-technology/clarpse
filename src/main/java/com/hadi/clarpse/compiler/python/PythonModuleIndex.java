package com.hadi.clarpse.compiler.python;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the dotted module name of every repository Python file to the file.
 *
 * <p>A module's name is its path from the repository root with the extension dropped and the
 * separators turned into dots, so {@code /pkg/sub/mod.py} is {@code pkg.sub.mod} and
 * {@code /pkg/__init__.py} is {@code pkg.__init__}. The resolver names every repository symbol it
 * resolves by the same rule, the module's name followed by the symbol's, so the file declaring a
 * resolved name is the file of its longest prefix that names a module.
 */
final class PythonModuleIndex {

    private final Map<String, String> fileByModule = new HashMap<>();

    PythonModuleIndex(final Collection<ProjectFile> files) {
        for (final ProjectFile file : files) {
            final String module = moduleName(file.path());
            if (module != null) {
                fileByModule.putIfAbsent(module, file.path());
            }
        }
    }

    private static String moduleName(final String path) {
        if (path == null || !path.endsWith(".py")) {
            return null;
        }
        String relative = path.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative.substring(0, relative.length() - ".py".length()).replace('/', '.');
    }

    /**
     * The repository file declaring a resolved name: the file of its longest proper prefix that
     * names a module.
     *
     * @param name A dotted name as the resolver writes it.
     * @return The file's path, or {@code null} when no prefix names a repository module.
     */
    String fileDeclaring(final String name) {
        if (name == null) {
            return null;
        }
        String candidate = name;
        int dot = candidate.lastIndexOf('.');
        while (dot > 0) {
            candidate = candidate.substring(0, dot);
            final String file = fileByModule.get(candidate);
            if (file != null) {
                return file;
            }
            dot = candidate.lastIndexOf('.');
        }
        return null;
    }
}
