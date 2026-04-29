package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpComponentExistTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Exist.cs", """
                        namespace Demo;
                        public class User {
                          private Repo repo;
                          public void Save(string message) { string local = message; }
                          public interface IRunner { void Run(string item); }
                          public enum Status { Ready }
                          public record Audit(string Id);
                        }
                        public class Repo {}
                        """)
        ).model();
    }

    @Test
    public void noCSharpFilesParsedMeansEmptyModel() throws Exception {
        assertEquals(0, CSharpTestUtil.compileInline().model().size());
    }

    @Test
    public void classExists() {
        assertTrue(model.containsComponent("Demo.User"));
    }

    @Test
    public void fieldExists() {
        assertTrue(model.containsComponent("Demo.User.repo"));
    }

    @Test
    public void methodExists() {
        assertTrue(model.containsComponent("Demo.User.Save(string)"));
    }

    @Test
    public void methodParamExists() {
        assertTrue(model.containsComponent("Demo.User.Save(string).message"));
    }

    @Test
    public void localExists() {
        assertTrue(model.containsComponent("Demo.User.Save(string).local"));
    }

    @Test
    public void nestedInterfaceExists() {
        assertTrue(model.containsComponent("Demo.User.IRunner"));
    }

    @Test
    public void nestedInterfaceMethodExists() {
        assertTrue(model.containsComponent("Demo.User.IRunner.Run(string)"));
    }

    @Test
    public void enumExists() {
        assertTrue(model.containsComponent("Demo.User.Status"));
    }

    @Test
    public void recordExists() {
        assertTrue(model.containsComponent("Demo.User.Audit"));
    }
}
