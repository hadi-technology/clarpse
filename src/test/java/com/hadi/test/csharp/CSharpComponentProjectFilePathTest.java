package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CSharpComponentProjectFilePathTest {

    private static OOPSourceCodeModel model;
    private static final String FILE_A = "/src/demo/User.cs";
    private static final String FILE_B = "/src/demo/admin/Admin.cs";

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile(FILE_A, """
                        namespace Demo;
                        public class User {
                          public string Name { get; set; }
                          public void Save(string message) {}
                        }
                        """),
                new ProjectFile(FILE_B, """
                        namespace Demo.Admin;
                        public class Admin {
                          public void Run() {}
                        }
                        """)
        ).model();
    }

    @Test
    public void classHasCorrectSourcePath() {
        final Component component = model.copyOfComponent("Demo.User").get();
        assertEquals(FILE_A, component.sourceFile());
    }

    @Test
    public void fieldHasCorrectSourcePath() {
        final Component component = model.copyOfComponent("Demo.User.Name").get();
        assertEquals(FILE_A, component.sourceFile());
    }

    @Test
    public void methodHasCorrectSourcePath() {
        final Component component = model.copyOfComponent("Demo.User.Save(string)").get();
        assertEquals(FILE_A, component.sourceFile());
    }

    @Test
    public void nestedNamespaceClassHasCorrectSourcePath() {
        final Component component = model.copyOfComponent("Demo.Admin.Admin").get();
        assertEquals(FILE_B, component.sourceFile());
    }

    @Test
    public void nestedNamespaceMethodHasCorrectSourcePath() {
        final Component component = model.copyOfComponent("Demo.Admin.Admin.Run()").get();
        assertEquals(FILE_B, component.sourceFile());
    }
}
