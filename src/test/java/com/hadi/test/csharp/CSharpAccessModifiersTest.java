package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CSharpAccessModifiersTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Access.cs", """
                        namespace Demo;
                        public partial class User {
                          public string Name { get; set; }
                          private readonly Repo repo;
                          internal User(Repo repo) { this.repo = repo; }
                          protected static void Save(string message) {}
                        }
                        public class Repo {}
                        """)
        ).model();
    }

    @Test
    public void classModifiersAreCaptured() {
        assertTrue(model.copyOfComponent("Demo.User").get().modifiers().contains("public"));
        assertTrue(model.copyOfComponent("Demo.User").get().modifiers().contains("partial"));
    }

    @Test
    public void fieldModifiersAreCaptured() {
        assertTrue(model.copyOfComponent("Demo.User.repo").get().modifiers().contains("private"));
        assertTrue(model.copyOfComponent("Demo.User.repo").get().modifiers().contains("readonly"));
    }

    @Test
    public void constructorModifiersAreCaptured() {
        assertTrue(model.copyOfComponent("Demo.User.User(Repo)").get().modifiers().contains("internal"));
    }

    @Test
    public void methodModifiersAreCaptured() {
        assertTrue(model.copyOfComponent("Demo.User.Save(string)").get().modifiers().contains("protected"));
        assertTrue(model.copyOfComponent("Demo.User.Save(string)").get().modifiers().contains("static"));
    }
}
