package com.hadi.clarpse.compiler.internal;

import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.ProjectFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates between project file paths and the canonical on-disk paths a resolver process reports,
 * which may differ from a naive join where the temporary directory is reached through a symbolic
 * link.
 *
 * <p>Only the files given at construction have a project path: {@link #toProjectPaths(List)} leaves
 * out any on-disk path that is not one of them, rather than guessing a project path for it.
 *
 * <p><b>Internal: not part of the public API.</b> It is public only so the language compilers in
 * sibling packages can share it. It may change or move in any release.
 */
public final class DiskPaths {

    private final String persistDir;
    private final Map<String, String> projectPathByDisk = new HashMap<>();

    /**
     * Creates the translation for a set of project files persisted under a directory.
     *
     * @param persistDir The directory the project files are persisted under.
     * @param files      The project files that can be translated.
     */
    public DiskPaths(final String persistDir, final Collection<ProjectFile> files) {
        this.persistDir = persistDir;
        for (final ProjectFile file : files) {
            projectPathByDisk.put(canonical(CompilerSupport.resolveFileOnDisk(persistDir, file.path())),
                    file.path());
        }
    }

    /**
     * The on-disk paths of project files.
     *
     * @param files The project files.
     * @return The path of each file under the persisted directory, in order.
     */
    public List<String> toDisk(final List<ProjectFile> files) {
        final List<String> disk = new ArrayList<>();
        for (final ProjectFile file : files) {
            disk.add(CompilerSupport.resolveFileOnDisk(persistDir, file.path()));
        }
        return disk;
    }

    /**
     * The project paths of on-disk files.
     *
     * @param diskPaths On-disk paths; {@code null} is none.
     * @return The project path of each on-disk path that is a project file, in order; the others
     *         are left out.
     */
    public List<String> toProjectPaths(final List<String> diskPaths) {
        final List<String> paths = new ArrayList<>();
        if (diskPaths == null) {
            return paths;
        }
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
            return new File(path).getCanonicalPath();
        } catch (final IOException e) {
            return Paths.get(path).toAbsolutePath().normalize().toString();
        }
    }
}
