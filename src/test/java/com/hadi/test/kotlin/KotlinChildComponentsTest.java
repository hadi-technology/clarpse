package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which Kotlin declarations are components, and whose children they are: a type's children are its
 * nested types, constructors, properties and functions in the order they are written; a function's
 * are its parameters and then its locals. Code that is not a declaration of the type -- an
 * {@code init} block, a property accessor, a local function or class, an object expression -- is
 * not a component of its own.
 */
public class KotlinChildComponentsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/a/Shop.kt", String.join("\n",
                        "package a",
                        "",
                        "interface Port { fun open(): Boolean; val name: String }",
                        "class Order",
                        "",
                        "class Shop(val owner: String) {",
                        "    init { println(owner) }",
                        "    val size: Int = 0",
                        "    var counter: Int = 0",
                        "        get() = field",
                        "        set(value) { field = value }",
                        "    constructor(owner: String, size: Int) : this(owner)",
                        "    fun sell(order: Order, count: Int): Int {",
                        "        val total = count * 2",
                        "        var left = total",
                        "        fun helper(): Int = left",
                        "        class Receipt(val order: Order)",
                        "        val port = object : Port {",
                        "            override fun open() = true",
                        "            override val name = \"anon\"",
                        "        }",
                        "        return helper()",
                        "    }",
                        "    class Shelf { class Slot }",
                        "    object Clerk { fun greet() {} }",
                        "    companion object { fun create(): Shop = Shop(\"x\") }",
                        "}",
                        "",
                        "enum class Size(val label: String) {",
                        "    SMALL(\"s\"), LARGE(\"l\");",
                        "    fun describe(): String = label",
                        "}",
                        "",
                        "abstract class Shape { abstract fun area(): Double; open val sides: Int = 0 }")),
                new ProjectFile("/a/util.kt", String.join("\n",
                        "package a",
                        "",
                        "const val LIMIT = 3",
                        "fun first(): Int = 1",
                        "fun second(x: Int): Int = x",
                        "class NotAMember"))
        ).model();
    }

    @Test
    public void aClassesChildrenAreItsDeclarationsInWrittenOrder() {
        assertEquals(List.of("a.Shop.Shop(String)", "a.Shop.owner", "a.Shop.size", "a.Shop.counter",
                        "a.Shop.Shop(String, Int)", "a.Shop.sell(Order, Int)", "a.Shop.Shelf", "a.Shop.Clerk",
                        "a.Shop.Companion"),
                component(model, "a.Shop").children());
    }

    @Test
    public void aSecondaryConstructorIsAConstructorWithItsParameters() {
        final Component constructor = component(model, "a.Shop.Shop(String, Int)");
        assertEquals(ComponentType.CONSTRUCTOR, constructor.componentType());
        assertEquals(List.of("a.Shop.Shop(String, Int).owner", "a.Shop.Shop(String, Int).size"),
                constructor.children());
    }

    @Test
    public void aFunctionsChildrenAreItsParametersThenItsLocals() {
        assertEquals(List.of("a.Shop.sell(Order, Int).order", "a.Shop.sell(Order, Int).count",
                        "a.Shop.sell(Order, Int).total", "a.Shop.sell(Order, Int).left",
                        "a.Shop.sell(Order, Int).port"),
                component(model, "a.Shop.sell(Order, Int)").children());
    }

    @Test
    public void localFunctionsLocalClassesAndObjectExpressionsAreNotComponents() {
        assertTrue(model.components().noneMatch(component -> component.uniqueName().contains("helper")
                || component.uniqueName().contains("Receipt")
                || component.uniqueName().startsWith("a.Shop.sell(Order, Int).port.")));
    }

    @Test
    public void initBlocksAndAccessorsAreNotComponents() {
        assertTrue(model.components().noneMatch(component -> component.uniqueName().contains("init")
                || component.uniqueName().contains("get(") || component.uniqueName().contains("set(")));
        assertEquals(ComponentType.FIELD, component(model, "a.Shop.counter").componentType());
        assertTrue(component(model, "a.Shop.counter").children().isEmpty());
    }

    @Test
    public void nestedTypesAndObjectsAreChildrenOfTheirEnclosingType() {
        assertEquals(List.of("a.Shop.Shelf.Slot"), component(model, "a.Shop.Shelf").children());
        assertEquals(ComponentType.CLASS, component(model, "a.Shop.Clerk").componentType());
        assertEquals(List.of("a.Shop.Clerk.greet()"), component(model, "a.Shop.Clerk").children());
        assertEquals(List.of("a.Shop.Companion.create()"), component(model, "a.Shop.Companion").children());
    }

    @Test
    public void anInterfacesChildrenAreItsAbstractMembers() {
        assertEquals(List.of("a.Port.open()", "a.Port.name"), component(model, "a.Port").children());
        assertEquals(ComponentType.METHOD, component(model, "a.Port.open()").componentType());
        assertEquals(ComponentType.FIELD, component(model, "a.Port.name").componentType());
    }

    @Test
    public void anEnumsChildrenAreItsConstructorEntriesAndMembers() {
        final List<String> children = component(model, "a.Size").children();
        assertTrue(children.toString(), children.containsAll(List.of("a.Size.SMALL", "a.Size.LARGE",
                "a.Size.label", "a.Size.describe()")));
        assertTrue(children.indexOf("a.Size.SMALL") < children.indexOf("a.Size.describe()"));
    }

    @Test
    public void abstractMembersAreComponents() {
        assertEquals(List.of("a.Shape.area()", "a.Shape.sides"), component(model, "a.Shape").children());
        assertTrue(component(model, "a.Shape.area()").modifiers().contains("abstract"));
    }

    @Test
    public void aFileClassesChildrenAreTheFilesTopLevelFunctionsAndPropertiesOnly() {
        assertEquals(List.of("a.UtilKt.LIMIT", "a.UtilKt.first()", "a.UtilKt.second(Int)"),
                component(model, "a.UtilKt").children());
        assertFalse(component(model, "a.UtilKt").children().contains("a.NotAMember"));
        assertEquals(ComponentType.CLASS, component(model, "a.NotAMember").componentType());
    }

    @Test
    public void everyChildIsAComponentOfTheModel() {
        model.components().forEach(component -> component.children().forEach(child ->
                assertTrue(component.uniqueName() + " lists missing child " + child,
                        model.containsComponent(child))));
    }
}
