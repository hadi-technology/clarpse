package com.hadi.clarpse.compiler.go;

import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents source files to be parsed.
 */
public class GoModules {

    private static final Logger LOGGER = LogManager.getLogger(GoModules.class);

    private final List<GoModule> goModules;

    public GoModules(final ProjectFiles projectFiles) {
        this.goModules = generateModules(projectFiles);
        LOGGER.info(this.goModules.size() + " Go modules were detected.");
    }

    private List<GoModule> generateModules(final ProjectFiles projectFiles) {
        List<GoModule> goModules = new ArrayList<>();
        // Collected all go.mod files from the given list of all project files.
        final Map<ProjectFile, List<ProjectFile>> moduletoFilesMap =
                projectFiles.files(Lang.GOLANG).stream().filter(
                        projectFile -> projectFile.path().endsWith("go.mod")
                ).collect(Collectors.toMap(projectFile -> projectFile, projectFile -> new ArrayList<>()));
        // Group associated source files and modules together.
        projectFiles.files(Lang.GOLANG).forEach(projectFile -> {
            for (final Map.Entry<ProjectFile, List<ProjectFile>> moduleFileEntry
                    : moduletoFilesMap.entrySet()) {
                if (moduleFileEntry.getKey().dir().equals("/")
                        || projectFile.path().startsWith(moduleFileEntry.getKey().dir())) {
                    moduletoFilesMap.get(moduleFileEntry.getKey()).add(projectFile);
                }
            }
        });
        moduletoFilesMap.keySet().forEach(moduleFile -> goModules.add(new GoModule(
                new ProjectFiles(moduletoFilesMap.get(moduleFile)), moduleFile)));
        return goModules;
    }

    public List<GoModule> list() {
        return this.goModules;
    }
}
