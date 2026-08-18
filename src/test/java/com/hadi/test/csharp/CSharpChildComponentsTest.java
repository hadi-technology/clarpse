package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CSharpChildComponentsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Children.cs", """
                        namespace Demo;
                        public class User {
                          public string Name { get; set; }
                          public void Save(string message) { string local = message; }
                          public interface IRunner { void Run(string item); }
                          public enum Status { Ready, Done }
                        }
                        """)
        ).model();
    }

    @Test
    public void classHasMethodChild() {
        assertTrue(model.copyOfComponent("Demo.User").get().children().contains("Demo.User.Save(string)"));
    }

    @Test
    public void classHasFieldChild() {
        assertTrue(model.copyOfComponent("Demo.User").get().children().contains("Demo.User.Name"));
    }

    @Test
    public void classHasNestedInterfaceChild() {
        assertTrue(model.copyOfComponent("Demo.User").get().children().contains("Demo.User.IRunner"));
    }

    @Test
    public void classHasNestedEnumChild() {
        assertTrue(model.copyOfComponent("Demo.User").get().children().contains("Demo.User.Status"));
    }

    @Test
    public void methodHasParameterChild() {
        assertTrue(model.copyOfComponent("Demo.User.Save(string)").get().children()
                .contains("Demo.User.Save(string).message"));
    }

    @Test
    public void methodHasLocalChild() {
        assertTrue(model.copyOfComponent("Demo.User.Save(string)").get().children()
                .contains("Demo.User.Save(string).local"));
    }

    @Test
    public void enumHasConstantChildren() {
        assertTrue(model.copyOfComponent("Demo.User.Status").get().children().contains("Demo.User.Status.Ready"));
        assertTrue(model.copyOfComponent("Demo.User.Status").get().children().contains("Demo.User.Status.Done"));
    }
}
