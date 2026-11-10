package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The members of a C# 14 {@code extension(T x) { ... }} block belong to the static class that holds
 * the block and extend the receiver type, as the methods that take {@code this T x} do.
 */
public class CSharpExtensionBlockTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Shapes.cs", """
                        namespace App {
                          public interface IShape { void Draw(); }
                          public class Marker { }
                          public class Palette { }
                          public static class ShapeExt {
                            public static string Describe(this IShape s) { return ""; }
                            extension(IShape s) {
                              public bool TryGet(out Marker x) { x = null; return true; }
                              public Palette Colors => null;
                            }
                            extension(IShape) { public static Marker Empty => null; }
                          }
                          public static class ListExt {
                            extension<T>(List<T> items) where T : class {
                              public bool IsEmpty => items.Count == 0;
                              public T First() { return items[0]; }
                              public IShape Shape() { return null; }
                            }
                          }
                        }
                        """)).model();
    }

    private static Component component(final String name) {
        return model.copyOfComponent(name).orElseThrow(() -> new AssertionError("no component " + name));
    }

    private static boolean references(final String from, final String to) {
        return component(from).references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void blockIsNotAConstructor() {
        assertTrue(model.components().noneMatch(c -> c.componentType() == ComponentType.CONSTRUCTOR));
    }

    @Test
    public void instanceMethodTakesTheReceiverAsItsFirstParameter() {
        final Component method = component("App.ShapeExt.TryGet(IShape, Marker)");
        assertEquals(ComponentType.METHOD, method.componentType());
        assertTrue(references("App.ShapeExt.TryGet(IShape, Marker)", "App.IShape"));
        assertTrue(references("App.ShapeExt.TryGet(IShape, Marker)", "App.Marker"));
        assertTrue(model.containsComponent("App.ShapeExt.TryGet(IShape, Marker).s"));
        assertTrue(component("App.ShapeExt").children().contains("App.ShapeExt.TryGet(IShape, Marker)"));
    }

    @Test
    public void propertiesAndStaticMembersAreMembersOfTheHoldingClass() {
        assertEquals(ComponentType.FIELD, component("App.ShapeExt.Colors").componentType());
        assertTrue(references("App.ShapeExt.Colors", "App.Palette"));
        assertTrue(references("App.ShapeExt.Colors", "App.IShape"));
        assertTrue(references("App.ShapeExt.Empty", "App.Marker"));
        assertTrue(references("App.ShapeExt.Empty", "App.IShape"));
    }

    /**
     * The extension class depends on the type it extends, not the other way round: a type that
     * references its own extension methods closes a cycle with the class that references it.
     */
    @Test
    public void receiverTypeDoesNotReferenceTheMembersThatExtendIt() {
        assertFalse(references("App.IShape", "App.ShapeExt.Describe(IShape)"));
        assertFalse(references("App.IShape", "App.ShapeExt.TryGet(IShape, Marker)"));
        assertFalse(references("App.IShape", "App.ShapeExt.Colors"));
        assertFalse(references("App.IShape", "App.ShapeExt.Empty"));
        assertFalse(references("App.IShape", "App.ShapeExt"));
    }

    @Test
    public void genericBlockMembersAreModelledWithTheirTypeParametersKnown() {
        assertTrue(model.containsComponent("App.ListExt.IsEmpty"));
        assertTrue(model.containsComponent("App.ListExt.First(List<T>)"));
        assertTrue(model.containsComponent("App.ListExt.Shape(List<T>)"));
        assertTrue(references("App.ListExt.Shape(List<T>)", "App.IShape"));
        assertFalse(references("App.ListExt.First(List<T>)", "T"));
        assertFalse(references("App.ListExt", "App.T"));
    }
}
