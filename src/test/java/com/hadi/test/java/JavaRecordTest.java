package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
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
 * Records are modelled as classes whose record components are fields, with the canonical constructor
 * present however it was declared.
 */
public class JavaRecordTest {

    private static final String ORDER = String.join("\n",
            "package app;",
            "import java.util.List;",
            "/** An order. */",
            "public record Order(Price price, List<String> tags) implements Comparable<Order> {",
            "    static final int MAX = 10;",
            "    public Order {",
            "        if (price == null) { throw new IllegalArgumentException(); }",
            "    }",
            "    public int compareTo(Order other) { return 0; }",
            "    record Nested(int a) { }",
            "}");

    private static final String PRICE = "package app;\npublic class Price { }\n";

    private static final String POINT = String.join("\n",
            "package app;",
            "public record Point(int x, int y) {",
            "    public Point(int x, int y) { this.x = x; this.y = y; }",
            "    public Point(int both) { this(both, both); }",
            "}");

    private static final String HOLDER = String.join("\n",
            "package app;",
            "public class Holder {",
            "    public record Inner(String s) { }",
            "}");

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/app/Order.java", ORDER));
        files.insertFile(new ProjectFile("/app/Price.java", PRICE));
        files.insertFile(new ProjectFile("/app/Point.java", POINT));
        files.insertFile(new ProjectFile("/app/Holder.java", HOLDER));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @Test
    public void recordIsModelledAsAClass() {
        final Component order = component("app.Order");
        assertEquals(ComponentType.CLASS, order.componentType());
        assertTrue(order.modifiers().contains("public"));
        assertTrue("a record is implicitly final", order.modifiers().contains("final"));
        assertTrue(order.comment().contains("An order."));
    }

    @Test
    public void recordComponentsAreFields() {
        final Component price = component("app.Order.price");
        assertEquals(ComponentType.FIELD, price.componentType());
        assertEquals("price : Price", price.codeFragment());
        assertTrue(price.modifiers().contains("private"));
        assertTrue(price.modifiers().contains("final"));
        assertEquals("tags : List<String>", component("app.Order.tags").codeFragment());
    }

    @Test
    public void recordComponentTypesReachTheDependencyGraph() {
        assertTrue(references(component("app.Order.price")).contains("app.Price"));
        assertTrue(references(component("app.Order")).contains("app.Price"));
    }

    @Test
    public void compactConstructorIsModelledAsTheCanonicalConstructor() {
        final Component ctor = component("app.Order.Order(Price, List<String>)");
        assertEquals(ComponentType.CONSTRUCTOR, ctor.componentType());
        assertTrue(ctor.modifiers().contains("public"));
        assertTrue(ctor.children().contains("app.Order.Order(Price, List<String>).price"));
        assertTrue(ctor.children().contains("app.Order.Order(Price, List<String>).tags"));
        assertEquals(ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                component("app.Order.Order(Price, List<String>).price").componentType());
    }

    @Test
    public void compactConstructorBodyReachesTheDependencyGraph() {
        assertTrue(references(component("app.Order.Order(Price, List<String>)"))
                .contains("java.lang.IllegalArgumentException"));
    }

    @Test
    public void implicitCanonicalConstructorIsModelled() {
        final Component ctor = component("app.Holder.Inner.Inner(String)");
        assertEquals(ComponentType.CONSTRUCTOR, ctor.componentType());
        assertTrue(ctor.modifiers().contains("public"));
        assertTrue(ctor.children().contains("app.Holder.Inner.Inner(String).s"));
    }

    @Test
    public void explicitCanonicalConstructorIsNotDuplicated() {
        final Component ctor = component("app.Point.Point(int, int)");
        assertEquals(ComponentType.CONSTRUCTOR, ctor.componentType());
        assertTrue(model.containsComponent("app.Point.Point(int)"));
        assertEquals(2, model.components()
                .filter(cmp -> cmp.componentType() == ComponentType.CONSTRUCTOR)
                .filter(cmp -> cmp.uniqueName().startsWith("app.Point."))
                .count());
        assertTrue(ctor.children().contains("app.Point.Point(int, int).x"));
    }

    @Test
    public void recordMembersAreModelledAlongsideRecordComponents() {
        assertEquals(ComponentType.FIELD, component("app.Order.MAX").componentType());
        assertEquals(ComponentType.METHOD, component("app.Order.compareTo(Order)").componentType());
    }

    @Test
    public void nestedRecordsAreModelledUnderTheirEnclosingType() {
        assertTrue(component("app.Order").children().contains("app.Order.Nested"));
        assertEquals(ComponentType.CLASS, component("app.Order.Nested").componentType());
        assertEquals(ComponentType.FIELD, component("app.Order.Nested.a").componentType());
        assertTrue(component("app.Holder").children().contains("app.Holder.Inner"));
        assertEquals(ComponentType.CLASS, component("app.Holder.Inner").componentType());
    }

    @Test
    public void recordComponentsCarryDistinctCodeHashes() {
        assertFalse(component("app.Order.price").codeHash() == 0);
        assertFalse(component("app.Order.price").codeHash() == component("app.Order.tags").codeHash());
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName).orElseThrow(
                () -> new AssertionError("no component named " + uniqueName));
    }

    private static java.util.List<String> references(final Component component) {
        return component.references().stream().map(ComponentReference::invokedComponent).toList();
    }
}
