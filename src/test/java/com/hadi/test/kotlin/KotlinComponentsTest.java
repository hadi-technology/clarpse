package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The components a pure Kotlin codebase is modelled as: each kind of type, under the unique name a
 * Java type of the same package and name has; members; companion objects; and the file classes
 * that hold top-level functions and properties.
 */
public class KotlinComponentsTest {

    private static OOPSourceCodeModel model;
    private static CompileResult result;

    @BeforeClass
    public static void compile() throws Exception {
        result = KotlinTestUtil.compileInline(
                new ProjectFile("/src/main/kotlin/com/acme/shop/Person.kt", String.join("\n",
                        "package com.acme.shop",
                        "",
                        "/** A customer. */",
                        "data class Person(val name: String, private var age: Int = 0) {",
                        "    internal val tags: MutableList<Tag> = mutableListOf()",
                        "    companion object {",
                        "        const val MAX = 3",
                        "        fun create(): Person = Person(\"x\")",
                        "    }",
                        "    fun birthday(times: Int): Int {",
                        "        val before = age",
                        "        if (times > 0 && before >= 0) { age += times }",
                        "        return when (times) { 1 -> 1; 2 -> 2; else -> 0 }",
                        "    }",
                        "    class Address(val street: String)",
                        "    inner class Wallet",
                        "}")),
                new ProjectFile("/src/main/kotlin/com/acme/shop/Shapes.kt", String.join("\n",
                        "package com.acme.shop",
                        "",
                        "interface Shape { fun area(): Double }",
                        "object Registry : Shape { override fun area() = 0.0 }",
                        "enum class Color(val rgb: Int) { RED(1), GREEN(2) }",
                        "annotation class Audited",
                        "sealed class Tag",
                        "fun interface Action { fun run() }")),
                new ProjectFile("/src/main/kotlin/com/acme/shop/greetings.kt", String.join("\n",
                        "package com.acme.shop",
                        "",
                        "val greetingCount: Int = 0",
                        "fun Person.greet(prefix: String): String = prefix + name",
                        "fun hello(person: Person): String = person.greet(\"hi\")")),
                new ProjectFile("/src/main/kotlin/com/acme/shop/Pricing.kt", String.join("\n",
                        "@file:JvmName(\"PriceRules\")",
                        "package com.acme.shop",
                        "",
                        "fun discount(person: Person): Double = 0.1")),
                new ProjectFile("/src/main/kotlin/Root.kt", "class Root\n"));
        model = result.model();
    }

    @Test
    public void compilesWithoutFailures() {
        assertTrue(result.failures().toString(), result.failures().isEmpty());
    }

    @Test
    public void typesGetTheUniqueNameAJavaTypeOfThatPackageAndNameGets() {
        assertEquals(ComponentType.CLASS, component(model, "com.acme.shop.Person").componentType());
        assertEquals(ComponentType.INTERFACE, component(model, "com.acme.shop.Shape").componentType());
        assertEquals(ComponentType.CLASS, component(model, "com.acme.shop.Registry").componentType());
        assertEquals(ComponentType.ENUM, component(model, "com.acme.shop.Color").componentType());
        assertEquals(ComponentType.ANNOTATION, component(model, "com.acme.shop.Audited").componentType());
        assertEquals(ComponentType.CLASS, component(model, "com.acme.shop.Tag").componentType());
        assertEquals(ComponentType.INTERFACE, component(model, "com.acme.shop.Action").componentType());
        assertEquals(ComponentType.CLASS, component(model, "Root").componentType());
    }

    @Test
    public void typeComponentsCarryNamePackageAndSourceFile() {
        final Component person = component(model, "com.acme.shop.Person");
        assertEquals("Person", person.name());
        assertEquals("Person", person.componentName());
        assertEquals("com.acme.shop", person.pkg().name());
        assertEquals("/src/main/kotlin/com/acme/shop/Person.kt", person.sourceFile());
        assertEquals("/** A customer. */", person.comment());
        assertTrue(person.codeFragment().startsWith("data class Person("));
        assertTrue(person.codeHash() != 0);
    }

    @Test
    public void nestedTypesUseTheJavaSeparator() {
        assertEquals(ComponentType.CLASS, component(model, "com.acme.shop.Person.Address").componentType());
        assertEquals(ComponentType.CLASS, component(model, "com.acme.shop.Person.Wallet").componentType());
        assertEquals("Person.Address", component(model, "com.acme.shop.Person.Address").componentName());
        assertTrue(component(model, "com.acme.shop.Person").children()
                .contains("com.acme.shop.Person.Address"));
    }

    @Test
    public void aCompanionObjectIsTheNestedTypeCompanionAndHoldsItsMembers() {
        final Component companion = component(model, "com.acme.shop.Person.Companion");
        assertTrue(companion.modifiers().contains("companion"));
        assertEquals(List.of("com.acme.shop.Person.Companion.MAX", "com.acme.shop.Person.Companion.create()"),
                companion.children());
        assertEquals(ComponentType.FIELD, component(model, "com.acme.shop.Person.Companion.MAX").componentType());
        assertTrue(component(model, "com.acme.shop.Person.Companion.MAX").modifiers().contains("const"));
        assertEquals(ComponentType.METHOD,
                component(model, "com.acme.shop.Person.Companion.create()").componentType());
    }

    @Test
    public void propertiesAndConstructorPropertiesAreFields() {
        assertEquals(ComponentType.FIELD, component(model, "com.acme.shop.Person.name").componentType());
        assertEquals(ComponentType.FIELD, component(model, "com.acme.shop.Person.age").componentType());
        assertEquals(ComponentType.FIELD, component(model, "com.acme.shop.Person.tags").componentType());
        assertEquals("tags : MutableList<Tag>", component(model, "com.acme.shop.Person.tags").codeFragment());
        assertTrue(component(model, "com.acme.shop.Person").children().containsAll(List.of(
                "com.acme.shop.Person.name", "com.acme.shop.Person.age", "com.acme.shop.Person.tags")));
    }

    @Test
    public void thePrimaryConstructorIsAConstructorWithItsParameters() {
        final Component constructor = component(model, "com.acme.shop.Person.Person(String, Int)");
        assertEquals(ComponentType.CONSTRUCTOR, constructor.componentType());
        assertEquals(List.of("com.acme.shop.Person.Person(String, Int).name",
                "com.acme.shop.Person.Person(String, Int).age"), constructor.children());
        assertEquals(ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                component(model, "com.acme.shop.Person.Person(String, Int).age").componentType());
    }

    @Test
    public void functionsAreMethodsNamedByTheirParameterTypes() {
        final Component birthday = component(model, "com.acme.shop.Person.birthday(Int)");
        assertEquals(ComponentType.METHOD, birthday.componentType());
        assertEquals("birthday(Int) : Int", birthday.codeFragment());
        assertEquals(ComponentType.METHOD_PARAMETER_COMPONENT,
                component(model, "com.acme.shop.Person.birthday(Int).times").componentType());
        assertEquals(ComponentType.LOCAL,
                component(model, "com.acme.shop.Person.birthday(Int).before").componentType());
    }

    @Test
    public void cyclomaticComplexityCountsBranchesAndConditions() {
        // 1 + if + && + two non-else when entries
        assertEquals(5, component(model, "com.acme.shop.Person.birthday(Int)").cyclo());
    }

    @Test
    public void enumEntriesAreEnumConstants() {
        assertEquals(ComponentType.ENUM_CONSTANT, component(model, "com.acme.shop.Color.RED").componentType());
        assertEquals(ComponentType.ENUM_CONSTANT, component(model, "com.acme.shop.Color.GREEN").componentType());
    }

    @Test
    public void topLevelDeclarationsBelongToTheFileClass() {
        final Component fileClass = component(model, "com.acme.shop.GreetingsKt");
        assertEquals(ComponentType.CLASS, fileClass.componentType());
        assertEquals("/src/main/kotlin/com/acme/shop/greetings.kt", fileClass.sourceFile());
        assertEquals(List.of("com.acme.shop.GreetingsKt.greetingCount",
                "com.acme.shop.GreetingsKt.greet(Person, String)",
                "com.acme.shop.GreetingsKt.hello(Person)"), fileClass.children());
        assertEquals(ComponentType.FIELD, component(model, "com.acme.shop.GreetingsKt.greetingCount").componentType());
    }

    @Test
    public void anExtensionFunctionBelongsToItsFileWithItsReceiverAsFirstParameter() {
        final Component greet = component(model, "com.acme.shop.GreetingsKt.greet(Person, String)");
        assertEquals("Person.greet(String) : String", greet.codeFragment());
        assertTrue(targets(greet).contains("com.acme.shop.Person"));
        assertFalse(component(model, "com.acme.shop.Person").children().stream()
                .anyMatch(child -> child.contains("greet")));
    }

    @Test
    public void jvmNameNamesTheFileClass() {
        assertTrue(model.containsComponent("com.acme.shop.PriceRules"));
        assertTrue(model.containsComponent("com.acme.shop.PriceRules.discount(Person)"));
        assertFalse(model.containsComponent("com.acme.shop.PricingKt"));
    }

    @Test
    public void aFileWithoutTopLevelFunctionsOrPropertiesHasNoFileClass() {
        assertFalse(model.containsComponent("com.acme.shop.PersonKt"));
        assertFalse(model.containsComponent("com.acme.shop.ShapesKt"));
        assertFalse(model.containsComponent("RootKt"));
    }

    @Test
    public void filesCompilingIntoOneMultifileClassShareItsComponent() throws Exception {
        final OOPSourceCodeModel shared = KotlinTestUtil.compileInline(
                new ProjectFile("/a/Strings.kt", String.join("\n",
                        "@file:JvmName(\"Texts\")", "@file:JvmMultifileClass", "package util",
                        "fun trim(text: String): String = text")),
                new ProjectFile("/a/Numbers.kt", String.join("\n",
                        "@file:JvmName(\"Texts\")", "@file:JvmMultifileClass", "package util",
                        "fun pad(number: Int): String = number.toString()"))).model();

        final Component texts = component(shared, "util.Texts");
        assertTrue(texts.children().contains("util.Texts.trim(String)"));
        assertTrue(texts.children().contains("util.Texts.pad(Int)"));
    }

    @Test
    public void everyChildNamedByAComponentExists() {
        model.components().forEach(component -> component.children().forEach(child ->
                assertTrue(component.uniqueName() + " names missing child " + child,
                        model.containsComponent(child))));
    }
}
