package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CSharpComponentTypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Sample.cs", """
                        namespace Demo;
                        public class User {
                          public string Name { get; set; }
                          public User(Repo repo) { this.repo = repo; }
                          public void Save(string message) { var helper = new Helper(); }
                          public delegate void LogHandler(string value);
                          public record Audit(string Id);
                          public interface IRunner { void Run(string item); }
                          public enum Status { Ready }
                          private Repo repo;
                        }
                        public class Repo {}
                        public class Helper {}
                        """)
        ).model();
    }

    @Test
    public void classIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.getComponent("Demo.User").get().componentType());
    }

    @Test
    public void propertyIsMappedAsField() {
        assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.getComponent("Demo.User.Name").get().componentType());
    }

    @Test
    public void constructorIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR,
                model.getComponent("Demo.User.User(Repo)").get().componentType());
    }

    @Test
    public void methodIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.METHOD,
                model.getComponent("Demo.User.Save(string)").get().componentType());
    }

    @Test
    public void delegateIsMappedAsFunction() {
        assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION,
                model.getComponent("Demo.User.LogHandler").get().componentType());
    }

    @Test
    public void recordIsMappedAsClass() {
        assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.getComponent("Demo.User.Audit").get().componentType());
    }

    @Test
    public void interfaceIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.INTERFACE,
                model.getComponent("Demo.User.IRunner").get().componentType());
    }

    @Test
    public void enumIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.ENUM,
                model.getComponent("Demo.User.Status").get().componentType());
    }
}
