package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ResolutionKind;
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
import static com.hadi.test.kotlin.KotlinTestUtil.reference;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * How names written in Kotlin resolve: imports and import aliases, star imports, the file's own
 * package, Kotlin's default imports, nested types, supertypes, type aliases, and the file classes
 * of called top-level functions.
 */
public class KotlinResolutionTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/base/Base.kt", String.join("\n",
                        "package com.acme.base",
                        "open class Base(val id: Long)",
                        "interface Marker",
                        "interface Named : Marker",
                        "annotation class Audited",
                        "class Box<T>(val value: T)",
                        "class Outer { class Inner }",
                        "class List")),
                new ProjectFile("/util/Strings.kt", String.join("\n",
                        "package com.acme.util",
                        "class Formatter",
                        "fun shout(text: String): String = text.uppercase()",
                        "fun whisper(text: String): String = text.lowercase()")),
                new ProjectFile("/app/Aliases.kt", String.join("\n",
                        "package com.acme.app",
                        "import com.acme.base.Box",
                        "typealias Crate = Box<Order>")),
                new ProjectFile("/app/Order.kt", String.join("\n",
                        "package com.acme.app",
                        "",
                        "import com.acme.base.Base",
                        "import com.acme.base.Marker as Tagged",
                        "import com.acme.base.Outer.Inner",
                        "import com.acme.util.*",
                        "import com.acme.util.shout",
                        "",
                        "@com.acme.base.Audited",
                        "class Order(id: Long) : Base(id), Tagged, Comparable<Order> {",
                        "    private val lines: List<Line> = listOf()",
                        "    private val inner: Inner? = null",
                        "    private val crate: Crate? = null",
                        "    private val formatter = Formatter()",
                        "    private val stamp: java.time.Instant? = null",
                        "    private val ghost: Ghost? = null",
                        "    override fun compareTo(other: Order): Int = 0",
                        "    fun <T> wrap(value: T): T = value",
                        "    fun label(): String {",
                        "        val upper = shout(\"order\")",
                        "        if (lines.isEmpty()) { return Status.EMPTY.name }",
                        "        return upper + MAX_LINES + Line.DEFAULT_NAME",
                        "    }",
                        "    companion object { const val MAX_LINES = 10 }",
                        "}")),
                new ProjectFile("/app/Line.kt", String.join("\n",
                        "package com.acme.app",
                        "class Line { companion object { const val DEFAULT_NAME = \"line\" } }",
                        "enum class Status { EMPTY, FULL }",
                        "interface Priced",
                        "class Discounted : Priced",
                        "abstract class Plan",
                        "class Premium : Plan { constructor() : super() }")),
                new ProjectFile("/app/Tools.kt", String.join("\n",
                        "package com.acme.app",
                        "import com.acme.util.*",
                        "fun ship(order: Order): String = whisper(order.label())")),
                new ProjectFile("/other/Elsewhere.kt", String.join("\n",
                        "package com.acme.other",
                        "class Line"))
        ).model();
    }

    private static Component order() {
        return component(model, "com.acme.app.Order");
    }

    @Test
    public void anExplicitImportResolvesExactly() {
        assertEquals(ResolutionKind.EXACT, reference(order(), "com.acme.base.Base").resolutionKind());
    }

    @Test
    public void anImportAliasResolvesToTheImportedType() {
        assertTrue(order().references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("com.acme.base.Marker")));
        assertFalse(targets(order()).contains("Tagged"));
    }

    @Test
    public void anImportedNestedTypeResolves() {
        assertTrue(internalTargets(order()).contains("com.acme.base.Outer.Inner"));
    }

    @Test
    public void aStarImportResolvesARepositoryTypeOfThatPackage() {
        assertEquals(ResolutionKind.EXACT, reference(order(), "com.acme.util.Formatter").resolutionKind());
        assertTrue(internalTargets(order()).contains("com.acme.util.Formatter"));
    }

    @Test
    public void aTypeOfTheSamePackageResolvesWithoutAnImport() {
        assertTrue(internalTargets(order()).contains("com.acme.app.Line"));
        assertFalse(targets(order()).contains("com.acme.other.Line"));
    }

    @Test
    public void defaultImportsResolveToTheKotlinTypeAndNeverToARepositoryTypeOfTheSameName() {
        assertTrue(targets(order()).contains("kotlin.collections.List"));
        assertTrue(targets(order()).contains("kotlin.Comparable"));
        assertTrue(targets(order()).contains("kotlin.String"));
        assertFalse("kotlin.collections.List bound to a repository class named List",
                targets(order()).contains("com.acme.base.List"));
        assertTrue(order().externalDependencies().contains(new SimpleTypeReference("kotlin.collections.List")));
    }

    @Test
    public void aFullyQualifiedNameResolvesAsWritten() {
        assertTrue(order().references(TypeReferences.ANNOTATION)
                .contains(new AnnotationReference("com.acme.base.Audited")));
        assertTrue(targets(order()).contains("java.time.Instant"));
    }

    @Test
    public void aNameNothingInScopeDeclaresIsKeptAsWrittenAndMarkedUnresolved() {
        assertEquals(ResolutionKind.UNRESOLVED, reference(order(), "Ghost").resolutionKind());
    }

    @Test
    public void typeParametersAreNotTypeReferences() {
        assertFalse(targets(order()).contains("T"));
    }

    @Test
    public void aConstructorCallMarksTheSuperclassAndTheOtherEntriesAreImplemented() {
        assertEquals(Set.of(new TypeExtensionReference("com.acme.base.Base")),
                Set.copyOf(order().references(TypeReferences.EXTENSION)));
        assertTrue(order().references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("kotlin.Comparable")));
    }

    @Test
    public void aSupertypeWithoutACallIsImplementedUnlessItIsADeclaredClass() {
        assertTrue(component(model, "com.acme.app.Discounted").references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("com.acme.app.Priced")));
        assertTrue(component(model, "com.acme.app.Premium").references(TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("com.acme.app.Plan")));
    }

    @Test
    public void anInterfaceExtendsItsSupertypes() {
        assertTrue(component(model, "com.acme.base.Named").references(TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("com.acme.base.Marker")));
    }

    @Test
    public void aTypeAliasResolvesToTheTypeItAliases() {
        assertTrue(internalTargets(order()).contains("com.acme.base.Box"));
        assertFalse(targets(order()).contains("Crate"));
    }

    @Test
    public void typesNamedInBodiesAreReferences() {
        final Component label = component(model, "com.acme.app.Order.label()");
        assertTrue(targets(label).contains("com.acme.app.Status"));
        assertTrue(targets(label).contains("com.acme.app.Line"));
    }

    @Test
    public void constantsAndUnresolvedCallsAreNotTypeReferences() {
        final Set<String> targets = targets(component(model, "com.acme.app.Order.label()"));
        assertTrue(targets.toString(), targets.stream().noneMatch(target -> target.endsWith("MAX_LINES")
                || target.endsWith("EMPTY") || target.endsWith("DEFAULT_NAME") || target.endsWith("isEmpty")
                || target.endsWith("listOf")));
    }

    @Test
    public void aCallToAnImportedTopLevelFunctionReferencesItsFileClass() {
        assertTrue(internalTargets(component(model, "com.acme.app.Order.label()"))
                .contains("com.acme.util.StringsKt"));
    }

    @Test
    public void aCallToAStarImportedTopLevelFunctionReferencesItsFileClass() {
        assertTrue(internalTargets(component(model, "com.acme.app.ToolsKt.ship(Order)"))
                .contains("com.acme.util.StringsKt"));
    }

    @Test
    public void aSamePackageCallInALambdaIsNotTakenForTheTopLevelFunction() throws Exception {
        final OOPSourceCodeModel calls = KotlinTestUtil.compileInline(
                new ProjectFile("/app/setup.kt", "package app\nfun configure() { }\n"),
                new ProjectFile("/app/Direct.kt", "package app\nclass Direct { fun go() { configure() } }\n"),
                new ProjectFile("/app/InLambda.kt", String.join("\n",
                        "package app",
                        "class InLambda { fun go(builder: StringBuilder) { builder.apply { configure() } } }"))
        ).model();

        assertTrue(targets(component(calls, "app.Direct.go()")).contains("app.SetupKt"));
        assertFalse("a receiver of the lambda may declare configure()",
                targets(component(calls, "app.InLambda.go(StringBuilder)")).contains("app.SetupKt"));
    }

    @Test
    public void typeComponentsCarryTheFileImports() {
        assertTrue(order().imports().contains("com.acme.base.Base"));
        assertTrue(order().imports().contains("com.acme.base.Marker"));
        assertTrue(order().imports().contains("com.acme.util.*"));
        assertTrue(component(model, "com.acme.app.Order.label()").imports().isEmpty());
    }
}
