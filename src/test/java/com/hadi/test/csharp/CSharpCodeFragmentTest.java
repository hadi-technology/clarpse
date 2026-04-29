package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CSharpCodeFragmentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Fragments.cs", """
                        namespace Demo;
                        public class User {
                          public System.Collections.Generic.List<string> Names { get; set; }
                          public void Save(string message) {}
                          public User(Repo repo) {}
                          public delegate void LogHandler(string value);
                          public record Audit(string Id);
                        }
                        public class Repo {}
                        """)
        ).model();
    }

    @Test
    public void classCodeFragmentIsCaptured() {
        assertEquals("public class User", model.getComponent("Demo.User").get().codeFragment());
    }

    @Test
    public void propertyCodeFragmentIsCaptured() {
        assertEquals("Names : System.Collections.Generic.List<string>",
                model.getComponent("Demo.User.Names").get().codeFragment());
    }

    @Test
    public void methodCodeFragmentIsCaptured() {
        assertEquals("public void Save(string message)",
                model.getComponent("Demo.User.Save(string)").get().codeFragment());
    }

    @Test
    public void constructorCodeFragmentIsCaptured() {
        assertEquals("public User(Repo repo)",
                model.getComponent("Demo.User.User(Repo)").get().codeFragment());
    }

    @Test
    public void delegateCodeFragmentIsCaptured() {
        assertEquals("public delegate void LogHandler(string value)",
                model.getComponent("Demo.User.LogHandler").get().codeFragment());
    }
}
