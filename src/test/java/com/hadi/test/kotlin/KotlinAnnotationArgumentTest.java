package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.AnnotationArgument;
import com.hadi.clarpse.reference.AnnotationArgument.Kind;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A Kotlin annotation keeps the literal values of its arguments, in written order, and records an
 * argument that is not a literal as present but unresolved.
 */
public class KotlinAnnotationArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/lib/Lib.kt", String.join("\n",
                        "package com.acme.lib",
                        "annotation class Route(val value: String)",
                        "annotation class Doc(val collection: String)",
                        "annotation class Marker",
                        "enum class Verb { GET, POST }",
                        "object Limits { const val PREFIX = \"/api\" }")),
                new ProjectFile("/app/Orders.kt", String.join("\n",
                        "package com.acme.app",
                        "import com.acme.lib.*",
                        "@Route(\"/orders\")",
                        "@Doc(collection = \"orders\")",
                        "@Marker",
                        "@Many(\"a\", \"b\")",
                        "@Rich(unique = true, size = 12, ratio = -1.5, ch = 'c', verb = Verb.POST,",
                        "      type = Orders::class, tags = [\"x\", \"y\"], more = arrayOf(\"p\"),",
                        "      joined = \"/a\" + \"/b\", escaped = \"tab\\t\", raw = \"\"\"line\"\"\")",
                        "@Computed(path = Limits.PREFIX, name = \"a${'$'}{x}\", plain = PREFIX, call = compute())",
                        "class Orders {",
                        "    @Route(\"/list\")",
                        "    fun list() {}",
                        "}",
                        "annotation class Many(vararg val values: String)",
                        "annotation class Rich(val unique: Boolean)",
                        "annotation class Computed(val path: String)"))).model();
    }

    @Test
    public void aSingleUnnamedArgumentIsNamedValue() {
        assertEquals("/orders", arguments("com.acme.app.Orders", "Route").get("value").value());
    }

    @Test
    public void aNamedArgumentKeepsItsName() {
        final AnnotationArgument collection = arguments("com.acme.app.Orders", "Doc").get("collection");
        assertEquals(Kind.STRING, collection.kind());
        assertEquals("orders", collection.value());
    }

    @Test
    public void severalUnnamedArgumentsAreNamedByPosition() {
        final Map<String, AnnotationArgument> many = arguments("com.acme.app.Orders", "Many");
        assertEquals(Arrays.asList("0", "1"), new ArrayList<>(many.keySet()));
        assertEquals("b", many.get("1").value());
    }

    @Test
    public void literalsKeepTheirKindAndValue() {
        final Map<String, AnnotationArgument> rich = arguments("com.acme.app.Orders", "Rich");
        assertEquals(Arrays.asList("unique", "size", "ratio", "ch", "verb", "type", "tags", "more",
                "joined", "escaped", "raw"), new ArrayList<>(rich.keySet()));
        assertEquals(Kind.BOOLEAN, rich.get("unique").kind());
        assertEquals("true", rich.get("unique").value());
        assertEquals(Kind.NUMBER, rich.get("size").kind());
        assertEquals("12", rich.get("size").value());
        assertEquals("-1.5", rich.get("ratio").value());
        assertEquals("c", rich.get("ch").value());
        assertEquals(Kind.ENUM, rich.get("verb").kind());
        assertEquals("Verb.POST", rich.get("verb").value());
        assertEquals(Kind.CLASS, rich.get("type").kind());
        assertEquals("com.acme.app.Orders", rich.get("type").value());
        assertEquals(2, rich.get("tags").elements().size());
        assertEquals("y", rich.get("tags").elements().get(1).value());
        assertEquals("p", rich.get("more").elements().get(0).value());
        assertEquals("/a/b", rich.get("joined").value());
        assertEquals("tab\t", rich.get("escaped").value());
        assertEquals("line", rich.get("raw").value());
    }

    @Test
    public void anArgumentThatIsNotALiteralIsPresentButUnresolved() {
        final Map<String, AnnotationArgument> computed = arguments("com.acme.app.Orders", "Computed");
        for (final String name : Arrays.asList("path", "name", "plain", "call")) {
            assertNotNull(name + " should be present", computed.get(name));
            assertEquals(name, Kind.UNRESOLVED, computed.get(name).kind());
        }
    }

    @Test
    public void anAnnotationWithoutArgumentsIsReadAsHavingNone() {
        assertTrue(arguments("com.acme.app.Orders", "Marker").isEmpty());
    }

    @Test
    public void aMemberAnnotationKeepsItsArguments() {
        final Component list = model.components()
                .filter(component -> component.uniqueName().startsWith("com.acme.app.Orders.list"))
                .findFirst().orElseThrow(() -> new AssertionError("no list member"));
        assertEquals("/list", annotation(list, "Route").arguments().get("value").value());
    }

    private static Map<String, AnnotationArgument> arguments(final String owner, final String simpleName) {
        final Map<String, AnnotationArgument> arguments =
                annotation(component(model, owner), simpleName).arguments();
        assertNotNull("arguments were not read for " + simpleName, arguments);
        return arguments;
    }

    private static AnnotationReference annotation(final Component component, final String simpleName) {
        for (final ComponentReference reference : component.references()) {
            if (reference instanceof AnnotationReference
                    && reference.invokedComponent().endsWith(simpleName)) {
                return (AnnotationReference) reference;
            }
        }
        throw new AssertionError("no @" + simpleName + " on " + component.uniqueName());
    }
}
