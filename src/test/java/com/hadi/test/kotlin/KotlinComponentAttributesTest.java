package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * What every Kotlin component carries besides its references: the file it is declared in, its
 * package, the comment written before it, a code fragment, and a code hash that two parses of the
 * same source agree on and an edit to a function body changes.
 */
public class KotlinComponentAttributesTest {

    private static final String SVC_V1 = "package app\nclass Svc {\n    fun size(): Int { return 0 }\n}\n";
    private static final String SVC_V2 = "package app\nclass Svc {\n    fun size(): Int { return 42 }\n}\n";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(
                new ProjectFile("/src/main/kotlin/com/acme/Doc.kt", String.join("\n",
                        "/** Licensing header. */",
                        "package com.acme",
                        "",
                        "import kotlin.math.max",
                        "",
                        "/**",
                        " * A documented interface.",
                        " */",
                        "interface Doc {",
                        "    /** A documented function. */",
                        "    fun run()",
                        "    fun undocumented()",
                        "}",
                        "",
                        "class Plain",
                        "",
                        "/** A documented class. */",
                        "class Store(val name: String) {",
                        "    /** A documented property. */",
                        "    val size: Int = 0",
                        "    fun total(a: Int, b: Int): Int = max(a, b)",
                        "    class Shelf { fun count(): Int = 0 }",
                        "}",
                        "",
                        "fun helper(): Int = 1")),
                new ProjectFile("/src/main/kotlin/Rootless.kt", "class Rootless { fun go() {} }\n"));
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        model = result.model();
    }

    @Test
    public void everyComponentCarriesTheFileItIsDeclaredIn() {
        for (final String name : List.of("com.acme.Store", "com.acme.Store.size", "com.acme.Store.total(Int, Int)",
                "com.acme.Store.total(Int, Int).a", "com.acme.Store.Store(String)", "com.acme.Store.Shelf",
                "com.acme.Store.Shelf.count()", "com.acme.DocKt", "com.acme.DocKt.helper()")) {
            assertEquals(name, "/src/main/kotlin/com/acme/Doc.kt", component(model, name).sourceFile());
        }
        assertEquals("/src/main/kotlin/Rootless.kt", component(model, "Rootless.go()").sourceFile());
    }

    @Test
    public void everyComponentCarriesItsPackage() {
        assertEquals("com.acme", component(model, "com.acme.Store.Shelf.count()").pkg().name());
        assertEquals("com.acme", component(model, "com.acme.DocKt").pkg().name());
        assertEquals("", component(model, "Rootless").pkg().name());
    }

    @Test
    public void aDocCommentIsTheComponentsComment() {
        assertTrue(component(model, "com.acme.Doc").comment().contains("A documented interface."));
        assertEquals("/** A documented function. */", component(model, "com.acme.Doc.run()").comment());
        assertEquals("/** A documented class. */", component(model, "com.acme.Store").comment());
        assertEquals("/** A documented property. */", component(model, "com.acme.Store.size").comment());
    }

    @Test
    public void anUncommentedComponentHasAnEmptyComment() {
        assertEquals("", component(model, "com.acme.Doc.undocumented()").comment());
        assertEquals("", component(model, "com.acme.Store.total(Int, Int)").comment());
    }

    @Test
    public void aFileHeaderIsNotTheFirstTypesComment() {
        assertTrue(component(model, "com.acme.Doc").comment(),
                !component(model, "com.acme.Doc").comment().contains("Licensing"));
        assertEquals("", component(model, "com.acme.Plain").comment());
    }

    @Test
    public void codeFragmentsAreTheDeclarationsWithoutTheirBodies() {
        assertEquals("class Store(val name: String)", component(model, "com.acme.Store").codeFragment());
        assertEquals("total(Int, Int) : Int", component(model, "com.acme.Store.total(Int, Int)").codeFragment());
        assertEquals("size : Int", component(model, "com.acme.Store.size").codeFragment());
        assertEquals("Store(String)", component(model, "com.acme.Store.Store(String)").codeFragment());
        assertEquals("interface Doc", component(model, "com.acme.Doc").codeFragment());
    }

    @Test
    public void everyComponentCarriesACodeHash() {
        model.components().forEach(component ->
                assertNotEquals(component.uniqueName() + " has no code hash", 0, component.codeHash()));
    }

    @Test
    public void hashesAreStableAcrossParses() throws Exception {
        assertEquals(hashes(SVC_V1), hashes(SVC_V1));
    }

    @Test
    public void aBodyEditChangesTheFunctionsAndTheClassesHash() throws Exception {
        final Map<String, Integer> before = hashes(SVC_V1);
        final Map<String, Integer> after = hashes(SVC_V2);
        assertEquals(before.keySet(), after.keySet());
        assertNotEquals(before.get("app.Svc.size()"), after.get("app.Svc.size()"));
        assertNotEquals(before.get("app.Svc"), after.get("app.Svc"));
    }

    private static Map<String, Integer> hashes(final String source) throws Exception {
        final Map<String, Integer> hashes = new TreeMap<>();
        new ClarpseProject(KotlinTestUtil.projectOf(new ProjectFile("/app/Svc.kt", source)), Lang.KOTLIN)
                .result().model().components().forEach(component -> hashes.put(component.uniqueName(),
                        component.codeHash()));
        return hashes;
    }

    @Test
    public void aTypeWithoutAPackageHasItsSimpleNameAsItsUniqueName() {
        final Component rootless = component(model, "Rootless");
        assertEquals("Rootless", rootless.componentName());
        assertEquals("/src/main/kotlin/Rootless.kt", rootless.sourceFile());
    }
}
