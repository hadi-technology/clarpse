package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;

public final class CSharpTestUtil {

    private CSharpTestUtil() {
    }

    public static CompileResult compileInline(final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, Lang.CSHARP).result();
    }
}
