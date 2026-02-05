package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.SkipReason;
import com.hadi.clarpse.compiler.SkippedFile;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptSkipTest {

    private static final String NODE_PATH_PROP = "clarpse.node.path";

    @Test
    public void typeScriptFilesAreSkippedWhenNodeMissing() throws Exception {
        System.setProperty(NODE_PATH_PROP, "/path/that/does/not/exist");
        try {
            final ProjectFiles projectFiles = new ProjectFiles();
            projectFiles.insertFile(new ProjectFile("/src/utils/date.ts",
                    "export function format() { return ''; }"));
            final CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

            assertEquals(0, result.model().size());
            assertEquals(0, result.failures().size());

            Collection<SkippedFile> skippedFiles = result.skipped();
            assertEquals(1, skippedFiles.size());
            SkippedFile skipped = skippedFiles.iterator().next();
            assertEquals(SkipReason.NODE_NOT_FOUND, skipped.reason());
            assertEquals("/src/utils/date.ts", skipped.file().path());
        } finally {
            System.clearProperty(NODE_PATH_PROP);
        }
    }

    @Test
    public void javaCompilationRemainsUnaffectedWhenNodeMissing() throws Exception {
        System.setProperty(NODE_PATH_PROP, "/path/that/does/not/exist");
        try {
            final ProjectFiles projectFiles = new ProjectFiles();
            projectFiles.insertFile(new ProjectFile("/src/utils/date.ts",
                    "export function format() { return ''; }"));
            projectFiles.insertFile(new ProjectFile("/src/com/foo/Hello.java",
                    "package com.foo; public class Hello {}"));
            final CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA).result();

            assertTrue(result.model().size() > 0);
            assertEquals(0, result.failures().size());
            assertEquals(0, result.skipped().size());
        } finally {
            System.clearProperty(NODE_PATH_PROP);
        }
    }
}
