package com.hadi.test.adhoc;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import java.util.Comparator;

public class AdhocPythonParseTest {

    @Test
    public void parseMinimaxTestFile() throws Exception {
        java.nio.file.Path testPath = java.nio.file.Paths.get("/tmp/clarpse_test");
        if (!java.nio.file.Files.exists(testPath)) {
            return; // Skip test if test directory doesn't exist (e.g., in CI)
        }
        ProjectFiles projectFiles = new ProjectFiles("/tmp/clarpse_test");
        ClarpseProject project = new ClarpseProject(projectFiles, Lang.PYTHON);
        OOPSourceCodeModel model = project.result().model();

        System.out.println("\n========== COMPONENTS FROM PARSE ==========");
        model.components()
            .sorted(Comparator.comparing(Component::uniqueName))
            .forEach(cmp -> {
                System.out.println("\n" + cmp.uniqueName());
                System.out.println("  Type: " + cmp.componentType());
                System.out.println("  Package path: '" + cmp.pkg().path() + "'");
                System.out.println("  Package name: '" + cmp.pkg().name() + "'");
                System.out.println("  Module: '" + cmp.module() + "'");
                System.out.println("  Name: '" + cmp.name() + "'");
            });

        System.out.println("\n========== FAILURES ==========");
        project.result().failures().forEach(failure -> {
            System.out.println("File: " + failure.file());
            System.out.println("Message: " + failure.message());
        });

        System.out.println("\n========== SUMMARY ==========");
        System.out.println("Total components: " + model.components().count());
    }
}
