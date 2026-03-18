package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.python.PythonDaemonException;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FailureCodeContractTest {

    private static final String NODE_PATH_PROP = "clarpse.node.path";

    @Test
    public void sharedNodeMissingCodeIsConsistentAcrossResolvers() throws Exception {
        assertEquals(TypeScriptDaemonException.CODE_NODE_NOT_FOUND, PythonDaemonException.CODE_NODE_NOT_FOUND);
        assertEquals(FailureCode.NODE_RUNTIME_NOT_FOUND, TypeScriptDaemonException.CODE_NODE_NOT_FOUND);

        System.setProperty(NODE_PATH_PROP, "/path/that/does/not/exist");
        try {
            ProjectFiles projectFiles = new ProjectFiles();
            projectFiles.insertFile(new ProjectFile("/src/app.py", "class App:\n    pass\n"));
            CompileResult result = new ClarpseProject(projectFiles, Lang.PYTHON).result();
            assertEquals(1, result.failures().size());
            CompileFailure failure = result.failures().iterator().next();
            assertEquals(Integer.valueOf(FailureCode.NODE_RUNTIME_NOT_FOUND), failure.errorCode());
        } finally {
            System.clearProperty(NODE_PATH_PROP);
        }
    }

    @Test
    public void javaParseFailureUsesLanguageAgnosticCode() throws Exception {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/Broken.java", "class Broken {"));

        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA).result();
        assertEquals(1, result.failures().size());
        CompileFailure failure = result.failures().iterator().next();
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), failure.errorCode());
    }
}
