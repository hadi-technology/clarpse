package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.internalTargets;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The types a Kotlin declaration names in type positions: property, parameter, return and local
 * types, nullable and function types, type arguments at any depth, projections and bounds, casts,
 * type checks and caught exceptions. Each is a reference to the erased type, never to the written
 * text with its arguments, and a type parameter is never one.
 */
public class KotlinTypeReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/model/Model.kt", String.join("\n",
                        "package com.acme.model",
                        "",
                        "open class Base",
                        "interface Port<T>",
                        "open class Order(val id: Int)",
                        "class Line",
                        "class Dto",
                        "class Fault : Exception()",
                        "class Box<T>(val value: T)",
                        "class Outer { class Inner { class Deep } }",
                        "interface Repo<K, V> { fun find(key: K): V? }")),
                new ProjectFile("/app/Client.kt", String.join("\n",
                        "package com.acme.app",
                        "",
                        "import com.acme.model.Base",
                        "import com.acme.model.Box",
                        "import com.acme.model.Dto",
                        "import com.acme.model.Fault",
                        "import com.acme.model.Line",
                        "import com.acme.model.Order",
                        "import com.acme.model.Outer",
                        "import com.acme.model.Port",
                        "import com.acme.model.Repo",
                        "",
                        "class Client : Base(), Port<Dto> {",
                        "    private val boxes: Map<String, Box<Order>> = emptyMap()",
                        "    private val maybe: Line? = null",
                        "    private val fn: (Order) -> Line = { Line() }",
                        "    private val deep: Outer.Inner.Deep? = null",
                        "    private val qualified: com.acme.model.Outer.Inner? = null",
                        "    fun nested(): Map<String, List<Repo<Dto, Outer.Inner>>> = emptyMap()",
                        "    fun receivers(block: Order.() -> Unit, s: suspend (Line) -> Unit, n: ((Dto) -> Unit)?) {}",
                        "    fun varargs(vararg orders: Order) {}",
                        "    fun arrays(a: Array<out Order>) {}",
                        "    fun projections(p: Port<*>, q: Port<out Order>, s: Port<in Line>) {}",
                        "    fun <R : Line> bounded(r: R): R = r",
                        "    fun <T> constrained(t: T) where T : Order, T : Comparable<T> {}",
                        "    fun checks(x: Any): Int {",
                        "        if (x is Order) return 1",
                        "        val line = x as Line",
                        "        val dto = x as? Dto",
                        "        return 0",
                        "    }",
                        "    fun catches() { try { } catch (f: Fault) { } }",
                        "    fun local() { val box: Box<Dto>? = null }",
                        "    fun entries(e: Map.Entry<Order, Line>) {}",
                        "}",
                        "",
                        "class Holder<E> { inner class Slot { val item: E? = null; val line: Line? = null } }"))
        ).model();
    }

    private static Component client() {
        return component(model, "com.acme.app.Client");
    }

    @Test
    public void aPropertyTypeAndItsTypeArgumentsAreEachAReference() {
        final Component boxes = component(model, "com.acme.app.Client.boxes");
        assertEquals(Set.of("kotlin.collections.Map", "kotlin.String", "com.acme.model.Box", "com.acme.model.Order"),
                targets(boxes));
    }

    @Test
    public void aReferenceNamesTheErasedTypeNeverTheWrittenText() {
        model.components().forEach(component -> targets(component).forEach(target ->
                assertFalse(component.uniqueName() + " references " + target,
                        target.contains("<") || target.contains("?") || target.contains("(")
                                || target.contains(" "))));
    }

    @Test
    public void aNullableTypeIsAReferenceToTheType() {
        assertEquals(Set.of("com.acme.model.Line"), targets(component(model, "com.acme.app.Client.maybe")));
    }

    @Test
    public void aFunctionTypeReferencesItsParameterAndReturnTypes() {
        assertEquals(Set.of("com.acme.model.Order", "com.acme.model.Line"),
                targets(component(model, "com.acme.app.Client.fn")));
    }

    @Test
    public void aReceiverSuspendOrNullableFunctionTypeReferencesTheTypesItNames() {
        final String method = "com.acme.app.Client.receivers(Order.() -> Unit, suspend (Line) -> Unit, "
                + "((Dto) -> Unit)?)";
        assertTrue(targets(component(model, method + ".block")).contains("com.acme.model.Order"));
        assertTrue(targets(component(model, method + ".s")).contains("com.acme.model.Line"));
        assertTrue(targets(component(model, method + ".n")).contains("com.acme.model.Dto"));
    }

    @Test
    public void nestedTypeArgumentsAreEachAReference() {
        final Set<String> targets = targets(component(model,
                "com.acme.app.Client.nested()"));
        assertTrue(targets.toString(), targets.containsAll(Set.of("kotlin.collections.Map",
                "kotlin.collections.List", "com.acme.model.Repo", "com.acme.model.Dto", "com.acme.model.Outer.Inner")));
    }

    @Test
    public void aNestedTypeNamedThroughItsImportedOuterTypeResolves() {
        assertEquals(Set.of("com.acme.model.Outer.Inner.Deep"), targets(component(model, "com.acme.app.Client.deep")));
    }

    @Test
    public void aFullyQualifiedNestedTypeResolvesAsWritten() {
        assertEquals(Set.of("com.acme.model.Outer.Inner"), targets(component(model, "com.acme.app.Client.qualified")));
    }

    @Test
    public void aVarargParameterReferencesItsElementType() {
        assertEquals(Set.of("com.acme.model.Order"),
                targets(component(model, "com.acme.app.Client.varargs(Order).orders")));
    }

    @Test
    public void anArrayReferencesItsElementType() {
        assertEquals(Set.of("kotlin.Array", "com.acme.model.Order"),
                targets(component(model, "com.acme.app.Client.arrays(Array<out Order>).a")));
    }

    @Test
    public void aBoundedProjectionIsAReferenceAndAStarProjectionIsNot() {
        final String method = "com.acme.app.Client.projections(Port<*>, Port<out Order>, Port<in Line>)";
        assertEquals(Set.of("com.acme.model.Port"), targets(component(model, method + ".p")));
        assertEquals(Set.of("com.acme.model.Port", "com.acme.model.Order"), targets(component(model, method + ".q")));
        assertEquals(Set.of("com.acme.model.Port", "com.acme.model.Line"), targets(component(model, method + ".s")));
    }

    @Test
    public void aTypeParameterBoundIsAReferenceAndTheParameterIsNot() {
        final Component bounded = component(model, "com.acme.app.Client.bounded(R)");
        assertTrue(targets(bounded).contains("com.acme.model.Line"));
        assertFalse(targets(bounded).contains("R"));
        assertTrue(targets(component(model, "com.acme.app.Client.bounded(R).r")).isEmpty());
        final Set<String> constrained = targets(component(model, "com.acme.app.Client.constrained(T)"));
        assertTrue(constrained.toString(),
                constrained.containsAll(Set.of("com.acme.model.Order", "kotlin.Comparable")));
        assertFalse(constrained.contains("T"));
    }

    @Test
    public void anEnclosingTypesParameterIsNotAReference() {
        final Component item = component(model, "com.acme.app.Holder.Slot.item");
        assertTrue(targets(item).toString(), targets(item).isEmpty());
        assertEquals(Set.of("com.acme.model.Line"), targets(component(model, "com.acme.app.Holder.Slot.line")));
    }

    @Test
    public void typeChecksAndCastsAreReferences() {
        final Set<String> targets = targets(component(model, "com.acme.app.Client.checks(Any)"));
        assertTrue(targets.toString(), targets.containsAll(Set.of("com.acme.model.Order", "com.acme.model.Line",
                "com.acme.model.Dto")));
        assertEquals(Set.of("com.acme.model.Line"), targets(component(model, "com.acme.app.Client.checks(Any).line")));
    }

    @Test
    public void aCaughtExceptionTypeIsAReference() {
        assertTrue(internalTargets(component(model, "com.acme.app.Client.catches()")).contains("com.acme.model.Fault"));
    }

    @Test
    public void aLocalsDeclaredTypeIsAReference() {
        assertEquals(Set.of("com.acme.model.Box", "com.acme.model.Dto"),
                targets(component(model, "com.acme.app.Client.local().box")));
    }

    @Test
    public void aQualifiedDefaultImportResolvesToTheKotlinType() {
        assertTrue(targets(component(model, "com.acme.app.Client.entries(Map.Entry<Order, Line>).e"))
                .contains("kotlin.collections.Map.Entry"));
    }

    @Test
    public void aSupertypesTypeArgumentIsAPlainReferenceNotHeritage() {
        assertEquals(Set.of(new TypeExtensionReference("com.acme.model.Base")),
                Set.copyOf(client().references(TypeReferences.EXTENSION)));
        assertEquals(Set.of(new TypeImplementationReference("com.acme.model.Port")),
                Set.copyOf(client().references(TypeReferences.IMPLEMENTATION)));
        assertTrue(client().references().contains(new SimpleTypeReference("com.acme.model.Dto")));
    }

    @Test
    public void everyRepositoryTypeNamedResolvesInternally() {
        assertTrue(internalTargets(client()).containsAll(Set.of("com.acme.model.Base", "com.acme.model.Port",
                "com.acme.model.Dto", "com.acme.model.Box", "com.acme.model.Order", "com.acme.model.Line",
                "com.acme.model.Outer.Inner", "com.acme.model.Outer.Inner.Deep", "com.acme.model.Repo",
                "com.acme.model.Fault")));
    }
}
