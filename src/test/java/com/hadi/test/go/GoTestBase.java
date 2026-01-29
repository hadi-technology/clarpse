package com.hadi.test.go;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Before;

public class GoTestBase {

    public ProjectFiles projectFiles;

    @Before
    public void setUp() {
        ProjectFile goModFile = new ProjectFile("/go.mod", "module module");
        projectFiles = new ProjectFiles();
        projectFiles.insertFile(goModFile);
    }

    ProjectFiles goLangProjectFilesFixture(String goModPath) {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile(goModPath + "/go.mod", "module/module/module"));
        return projectFiles;
    }
}
