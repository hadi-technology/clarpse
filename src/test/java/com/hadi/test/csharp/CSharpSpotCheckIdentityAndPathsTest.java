package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpSpotCheckIdentityAndPathsTest {

    private static final String ORDER_PART1 = "/src/orders/Order.Part1.cs";
    private static final String ORDER_PART2 = "/src/orders/Order.Part2.cs";
    private static final String LINE_ITEM = "/src/orders/LineItem.cs";
    private static final String CONFIG = "/src/fs/Config.cs";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile(ORDER_PART1, """
                        namespace Demo.Orders;
                        public partial class Order {
                          public string Id { get; set; }
                          public void Add(LineItem item) { LineItem local = item; }
                          public class Metadata { public int Version; }
                        }
                        """),
                new ProjectFile(ORDER_PART2, """
                        namespace Demo.Orders;
                        public partial class Order {
                          public event OrderSaved Saved;
                          public delegate void OrderSaved(string value);
                        }
                        """),
                new ProjectFile(LINE_ITEM, """
                        namespace Demo.Orders;
                        public class LineItem {}
                        """),
                new ProjectFile(CONFIG, """
                        namespace Demo.FileScoped;
                        public class Config { public string Key { get; set; } }
                        """)
        ).model();
    }

    @Test
    public void partialTypeHasPropertyFromFirstPart() {
        assertTrue(model.containsComponent("Demo.Orders.Order.Id"));
    }

    @Test
    public void partialTypeHasEventFromSecondPart() {
        assertTrue(model.containsComponent("Demo.Orders.Order.Saved"));
    }

    @Test
    public void partialTypeHasDelegateFromSecondPart() {
        assertTrue(model.containsComponent("Demo.Orders.Order.OrderSaved"));
    }

    @Test
    public void propertySourceFilePathComesFromFirstPart() {
        final Component component = model.copyOfComponent("Demo.Orders.Order.Id").get();
        assertEquals(ORDER_PART1, component.sourceFile());
    }

    @Test
    public void eventSourceFilePathComesFromSecondPart() {
        final Component component = model.copyOfComponent("Demo.Orders.Order.Saved").get();
        assertEquals(ORDER_PART2, component.sourceFile());
    }

    @Test
    public void delegateSourceFilePathComesFromSecondPart() {
        final Component component = model.copyOfComponent("Demo.Orders.Order.OrderSaved").get();
        assertEquals(ORDER_PART2, component.sourceFile());
    }

    @Test
    public void fileScopedNamespaceBecomesPackage() {
        final Component component = model.copyOfComponent("Demo.FileScoped.Config").get();
        assertEquals("Demo.FileScoped", component.pkg().name());
    }

    @Test
    public void fileScopedModuleUsesFilename() {
        final Component component = model.copyOfComponent("Demo.FileScoped.Config").get();
        assertEquals("Config", component.module());
    }

    @Test
    public void orderModuleUsesFilenameWithoutExtension() {
        final Component component = model.copyOfComponent("Demo.Orders.Order").get();
        assertEquals("Order.Part1", component.module());
    }

    @Test
    public void partialTypeChildrenIncludeNestedClass() {
        assertTrue(model.copyOfComponent("Demo.Orders.Order").get().children()
                .contains("Demo.Orders.Order.Metadata"));
    }

    @Test
    public void partialTypeChildrenIncludeMethod() {
        assertTrue(model.copyOfComponent("Demo.Orders.Order").get().children()
                .contains("Demo.Orders.Order.Add(LineItem)"));
    }

    @Test
    public void nestedClassFieldTypeIsCaptured() {
        assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent("Demo.Orders.Order.Metadata.Version").get().componentType());
    }

    @Test
    public void methodParamSourceFilePathComesFromFirstPart() {
        final Component component = model.copyOfComponent("Demo.Orders.Order.Add(LineItem).item").get();
        assertEquals(ORDER_PART1, component.sourceFile());
    }

    @Test
    public void methodLocalSourceFilePathComesFromFirstPart() {
        final Component component = model.copyOfComponent("Demo.Orders.Order.Add(LineItem).local").get();
        assertEquals(ORDER_PART1, component.sourceFile());
    }

    @Test
    public void methodParamAndLocalTypeReferencesResolve() {
        assertEquals("Demo.Orders.LineItem", model.copyOfComponent("Demo.Orders.Order.Add(LineItem).item").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
        assertEquals("Demo.Orders.LineItem", model.copyOfComponent("Demo.Orders.Order.Add(LineItem).local").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }
}
