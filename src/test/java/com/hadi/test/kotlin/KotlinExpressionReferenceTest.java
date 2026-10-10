package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.internalTargets;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The types a Kotlin body names in expressions: class literals, callable references, constructor
 * calls, a member reached through an object or a companion object, and an object or companion
 * object used as a value on its own. A name in an expression counts only when it resolves to a type,
 * so variables, functions, properties and enum entries never become references, and neither does a
 * name a parameter, local or member shadows.
 */
public class KotlinExpressionReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/model/Model.kt", String.join("\n",
                        "package com.acme.model",
                        "",
                        "class Order(val id: Int) { fun lines(): List<Line> = emptyList() }",
                        "class Line",
                        "class Dto",
                        "class Outer { class Inner }",
                        "annotation class Uses(val value: kotlin.reflect.KClass<*>)",
                        "object Registry {",
                        "    const val NAME = \"registry\"",
                        "    fun lookup(): Order = Order(1)",
                        "}",
                        "object Adapters { val FACTORY: Any = Any() }",
                        "object Defaults",
                        "class Person {",
                        "    companion object { fun create(): Person = Person() }",
                        "    fun greet(): String = \"hi\"",
                        "}",
                        "class Settings { companion object Default }",
                        "enum class Mode { ON, OFF }")),
                new ProjectFile("/app/Client.kt", String.join("\n",
                        "package com.acme.app",
                        "",
                        "import com.acme.model.Adapters",
                        "import com.acme.model.Defaults",
                        "import com.acme.model.Dto",
                        "import com.acme.model.Line",
                        "import com.acme.model.Mode",
                        "import com.acme.model.Mode.ON",
                        "import com.acme.model.Order",
                        "import com.acme.model.Outer",
                        "import com.acme.model.Person",
                        "import com.acme.model.Registry",
                        "import com.acme.model.Settings",
                        "import com.acme.model.Uses",
                        "",
                        "class Client {",
                        "    fun literals(): Any = listOf(Order::class, Line::class.java, Outer.Inner::class)",
                        "    @Uses(Dto::class)",
                        "    fun annotated() {}",
                        "    fun callables(): Any = listOf(Order::id, ::Line, Order::lines)",
                        "    fun construct(): Any = Dto()",
                        "    fun objectMembers(): Any = listOf(Registry.lookup(), Registry.NAME, Adapters.FACTORY)",
                        "    fun companionCall(): Person = Person.create()",
                        "    fun objectArgument(): Any = listOf(Defaults)",
                        "    fun objectInLambda(): Any = run { Defaults }",
                        "    fun objectReturned(): Any { return Registry }",
                        "    fun companionAsValue(): Any = Settings",
                        "    fun objectCompared(x: Any): Boolean = x == Adapters",
                        "    fun whenOnObject(x: Any): Int = when (x) { Defaults -> 1; else -> 0 }",
                        "    fun enumEntries(): Any = listOf(ON, Mode.OFF)",
                        "    fun shadowedByAParameter(Defaults: Int): Any = listOf(Defaults)",
                        "    fun shadowedByALocal(): Any { val Adapters = 2; return listOf(Adapters) }",
                        "    fun callsOnAVariable(person: Person): String { val other = Person(); other.greet(); "
                                + "return person.greet() }",
                        "    fun variablesAndProperties(order: Order): Any = listOf(order, order.id, count)",
                        "    private val count = 0",
                        "    private val initialized = listOf(Defaults)",
                        "}"))
        ).model();
    }

    @Test
    public void aClassLiteralIsAReference() {
        final Set<String> targets = internalTargets(component(model, "com.acme.app.Client.literals()"));
        assertTrue(targets.toString(), targets.containsAll(Set.of("com.acme.model.Order", "com.acme.model.Line",
                "com.acme.model.Outer.Inner")));
    }

    @Test
    public void aClassLiteralInAnAnnotationArgumentIsAReference() {
        final Component annotated = component(model, "com.acme.app.Client.annotated()");
        assertTrue(annotated.references(TypeReferences.ANNOTATION).stream()
                .anyMatch(reference -> reference.invokedComponent().equals("com.acme.model.Uses")));
        assertTrue(internalTargets(annotated).contains("com.acme.model.Dto"));
    }

    @Test
    public void aCallableOrConstructorReferenceIsAReference() {
        assertEquals(Set.of("com.acme.model.Order", "com.acme.model.Line"),
                internalTargets(component(model, "com.acme.app.Client.callables()")));
    }

    @Test
    public void aConstructorCallIsAReference() {
        assertEquals(Set.of("com.acme.model.Dto"),
                internalTargets(component(model, "com.acme.app.Client.construct()")));
    }

    @Test
    public void aMemberReachedThroughAnObjectReferencesTheObject() {
        assertEquals(Set.of("com.acme.model.Registry", "com.acme.model.Adapters"),
                internalTargets(component(model, "com.acme.app.Client.objectMembers()")));
    }

    @Test
    public void aCompanionCallReferencesTheClass() {
        assertTrue(internalTargets(component(model, "com.acme.app.Client.companionCall()"))
                .contains("com.acme.model.Person"));
    }

    @Test
    public void anObjectPassedAsAnArgumentIsAReference() {
        assertEquals(Set.of("com.acme.model.Defaults"),
                internalTargets(component(model, "com.acme.app.Client.objectArgument()")));
    }

    @Test
    public void anObjectUsedInALambdaIsAReference() {
        assertEquals(Set.of("com.acme.model.Defaults"),
                internalTargets(component(model, "com.acme.app.Client.objectInLambda()")));
    }

    @Test
    public void anObjectReturnedComparedOrMatchedIsAReference() {
        assertEquals(Set.of("com.acme.model.Registry"),
                internalTargets(component(model, "com.acme.app.Client.objectReturned()")));
        assertEquals(Set.of("com.acme.model.Adapters"),
                internalTargets(component(model, "com.acme.app.Client.objectCompared(Any)")));
        assertEquals(Set.of("com.acme.model.Defaults"),
                internalTargets(component(model, "com.acme.app.Client.whenOnObject(Any)")));
    }

    @Test
    public void aCompanionObjectUsedAsAValueReferencesItsClass() {
        assertEquals(Set.of("com.acme.model.Settings"),
                internalTargets(component(model, "com.acme.app.Client.companionAsValue()")));
    }

    @Test
    public void anObjectInAPropertyInitializerIsAReference() {
        assertEquals(Set.of("com.acme.model.Defaults"),
                internalTargets(component(model, "com.acme.app.Client.initialized")));
    }

    @Test
    public void anEnumEntryIsAReferenceToItsEnumOnlyWhenQualifiedByIt() {
        assertEquals(Set.of("com.acme.model.Mode"), targets(component(model, "com.acme.app.Client.enumEntries()"))
                .stream().filter(target -> target.startsWith("com.acme")).collect(Collectors.toSet()));
        assertFalse(targets(component(model, "com.acme.app.Client.enumEntries()")).stream()
                .anyMatch(target -> target.endsWith(".ON") || target.endsWith(".OFF")));
    }

    @Test
    public void aParameterOrLocalNamedLikeAnObjectIsNotTheObject() {
        assertFalse(targets(component(model, "com.acme.app.Client.shadowedByAParameter(Int)"))
                .contains("com.acme.model.Defaults"));
        assertFalse(targets(component(model, "com.acme.app.Client.shadowedByALocal()"))
                .contains("com.acme.model.Adapters"));
    }

    @Test
    public void aCallOnAVariableInventsNoReference() {
        final Set<String> targets = targets(component(model, "com.acme.app.Client.callsOnAVariable(Person)"));
        assertEquals(Set.of("com.acme.model.Person", "kotlin.String"), targets);
    }

    @Test
    public void variablesAndPropertiesAreNotReferences() {
        assertEquals(Set.of("com.acme.model.Order"),
                targets(component(model, "com.acme.app.Client.variablesAndProperties(Order)")).stream()
                        .filter(target -> !target.startsWith("kotlin."))
                        .collect(Collectors.toSet()));
    }

    @Test
    public void constantsReachedThroughAnObjectAreNotTypes() {
        assertFalse(targets(component(model, "com.acme.app.Client.objectMembers()")).stream()
                .anyMatch(target -> target.endsWith("NAME") || target.endsWith("FACTORY")
                        || target.endsWith("lookup")));
    }
}
