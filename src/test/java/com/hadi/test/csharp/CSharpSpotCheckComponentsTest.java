package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpSpotCheckComponentsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/src/spot/Shapes.cs", """
                        namespace Demo.Shapes;
                        /// <summary>Represents a shape registry.</summary>
                        public abstract class ShapeRegistry : BaseRegistry, ITrackable {
                          private readonly Repo _repo;
                          public string Name { get; set; }
                          public event ChangedHandler Changed;
                          public delegate void ChangedHandler(string value);
                          public struct Point { public int X; }
                          public interface IWorker { void Run(); }
                          public enum Status { Ready, Done }
                          public record Audit(string Id);
                          protected ShapeRegistry(Repo repo) { _repo = repo; }
                          public Helper Build(string id) { Helper helper = new Helper(); return helper; }
                        }
                        public class Repo {}
                        public class Helper {}
                        public abstract class BaseRegistry {}
                        public interface ITrackable {}
                        """)
        ).model();
    }

    @Test
    public void abstractClassIsMappedAsClass() {
        assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry").get().componentType());
    }

    @Test
    public void abstractModifierIsCaptured() {
        assertTrue(model.copyOfComponent("Demo.Shapes.ShapeRegistry").get().modifiers().contains("abstract"));
    }

    @Test
    public void readonlyFieldModifierIsCaptured() {
        assertTrue(model.copyOfComponent("Demo.Shapes.ShapeRegistry._repo").get().modifiers().contains("readonly"));
    }

    @Test
    public void privateFieldModifierIsCaptured() {
        assertTrue(model.copyOfComponent("Demo.Shapes.ShapeRegistry._repo").get().modifiers().contains("private"));
    }

    @Test
    public void propertyIsMappedAsField() {
        assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Name").get().componentType());
    }

    @Test
    public void eventIsMappedAsField() {
        assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Changed").get().componentType());
    }

    @Test
    public void delegateIsMappedAsFunction() {
        assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.ChangedHandler").get().componentType());
    }

    @Test
    public void nestedStructIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.STRUCT,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Point").get().componentType());
    }

    @Test
    public void nestedInterfaceIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.INTERFACE,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.IWorker").get().componentType());
    }

    @Test
    public void nestedEnumIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.ENUM,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Status").get().componentType());
    }

    @Test
    public void recordIsMappedAsClass() {
        assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Audit").get().componentType());
    }

    @Test
    public void enumMemberIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.ENUM_CONSTANT,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Status.Ready").get().componentType());
    }

    @Test
    public void constructorIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.ShapeRegistry(Repo)").get().componentType());
    }

    @Test
    public void protectedConstructorModifierIsCaptured() {
        assertTrue(model.copyOfComponent("Demo.Shapes.ShapeRegistry.ShapeRegistry(Repo)").get()
                .modifiers().contains("protected"));
    }

    @Test
    public void methodIsMapped() {
        assertEquals(OOPSourceModelConstants.ComponentType.METHOD,
                model.copyOfComponent("Demo.Shapes.ShapeRegistry.Build(string)").get().componentType());
    }
}
