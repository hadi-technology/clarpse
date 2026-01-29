package com.hadi.clarpse.compiler.go;

import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents source files to be parsed.
 */
public class GoModule {

    private static final Logger LOGGER = LogManager.getLogger(GoModule.class);
    private final ProjectFiles projectFiles;
    private final String moduleName;

    public GoModule(ProjectFiles projectFiles, ProjectFile moduleFile) {
        this.moduleName = this.extractModuleName(moduleFile);
        if (this.moduleName.isEmpty()) {
            throw new IllegalArgumentException("Module name cannot be empty!");
        }
        this.projectFiles = new ProjectFiles();
        projectFiles.files(Lang.GOLANG).forEach(file -> {
            if (file.path().endsWith(".go")) {
                if (moduleFile.dir().equals("/")) {
                    this.projectFiles.insertFile(new ProjectFile(
                        file.path(),
                        file.content().replaceAll(this.moduleName + "/", "")
                            .replaceAll(this.moduleName, "")));
                } else {
                    // Non-root level module, transform file path to relative path
                    this.projectFiles.insertFile(new ProjectFile(
                        file.path().replace(moduleFile.dir(), ""),
                        file.content().replaceAll(this.moduleName + "/", "")
                            .replaceAll(this.moduleName, "")));
                }
            }
        });
        LOGGER.info("Go module " + this.moduleName + " contains " + this.projectFiles.size() + " files");
    }

    private String extractModuleName(ProjectFile moduleFile) {
        Pattern p = Pattern.compile("^ *module *(.*) *$", Pattern.MULTILINE);
        Matcher m = p.matcher(moduleFile.content());
        if (m.find()) {
            return m.group(1);
        } else {
            throw new IllegalArgumentException("Could not extract module name!");
        }
    }

    public ProjectFiles getProjectFiles() {
        return projectFiles;
    }

    public String moduleName() {
        return moduleName;
    }
}
