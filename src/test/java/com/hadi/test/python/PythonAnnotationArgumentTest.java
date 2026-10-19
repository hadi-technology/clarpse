package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A Python decorator keeps the literal values of its arguments, in written order, and records an
 * argument that is not a literal as present but unresolved.
 */
public class PythonAnnotationArgumentTest {

    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture("decorator-arguments");
        model = result.model();
    }

    @Test
    public void aSingleUnnamedArgumentIsNamedValue() {
        final AnnotationArgument value = arguments(function("single")).get("value");
        assertEquals(Kind.STRING, value.kind());
        assertEquals("/orders", value.value());
    }

    @Test
    public void severalUnnamedArgumentsAreNamedByPosition() {
        final Map<String, AnnotationArgument> arguments = arguments(function("positional"));
        assertEquals(Arrays.asList("0", "1"), new ArrayList<>(arguments.keySet()));
        assertEquals("/b", arguments.get("1").value());
    }

    @Test
    public void keywordArgumentsKeepTheirKindAndValueInWrittenOrder() {
        final Map<String, AnnotationArgument> arguments = arguments(function("keyword"));
        assertEquals(Arrays.asList("path", "unique", "size", "ratio", "verb"), new ArrayList<>(arguments.keySet()));
        assertEquals("/x", arguments.get("path").value());
        assertEquals(Kind.BOOLEAN, arguments.get("unique").kind());
        assertEquals("true", arguments.get("unique").value());
        assertEquals(Kind.NUMBER, arguments.get("size").kind());
        assertEquals("12", arguments.get("size").value());
        assertEquals("-1.5", arguments.get("ratio").value());
        assertEquals(Kind.ENUM, arguments.get("verb").kind());
        assertEquals("Verb.POST", arguments.get("verb").value());
    }

    @Test
    public void listsTuplesAndConcatenatedStringsAreRead() {
        final Map<String, AnnotationArgument> arguments = arguments(function("literals"));
        assertEquals(Kind.ARRAY, arguments.get("tags").kind());
        assertEquals("b", arguments.get("tags").elements().get(1).value());
        assertEquals("d", arguments.get("pair").elements().get(1).value());
        assertEquals("pq", arguments.get("joined").value());
        assertEquals("rs", arguments.get("plus").value());
        assertEquals("a\\d", arguments.get("text").value());
        assertEquals("t\tab", arguments.get("esc").value());
    }

    @Test
    public void aClassTheRepositoryDeclaresIsAClassLiteral() {
        final AnnotationArgument kind = arguments(function("unresolved")).get("kind");
        assertEquals(Kind.CLASS, kind.kind());
        assertTrue(kind.value(), kind.value().endsWith("enums.Plain"));
    }

    @Test
    public void anArgumentThatIsNotALiteralIsPresentButUnresolved() {
        final Map<String, AnnotationArgument> arguments = arguments(function("unresolved"));
        for (final String name : Arrays.asList("limit", "plain", "call", "fmt", "missing")) {
            assertNotNull(name + " should be present", arguments.get(name));
            assertEquals(name, Kind.UNRESOLVED, arguments.get(name).kind());
        }
    }

    @Test
    public void aDecoratorWithoutAnArgumentListIsReadAsHavingNone() {
        assertTrue(arguments(function("bare")).isEmpty());
        assertTrue(arguments(function("empty")).isEmpty());
    }

    @Test
    public void aDecoratorWhoseCallIsChainedIsNotRead() {
        assertNull(annotation(function("chained")).arguments());
    }

    @Test
    public void methodAndClassDecoratorsKeepTheirArguments() {
        final Component list = model.components()
                .filter(component -> component.uniqueName().contains("Controller.list"))
                .findFirst().orElseThrow(() -> new AssertionError("no list method"));
        final Map<String, AnnotationArgument> arguments = arguments(list);
        assertEquals("/list", arguments.get("value").value());
        assertEquals("GET", arguments.get("methods").elements().get(0).value());
        final Component widget = component(PythonTestUtil.uniqueName(PACKAGE_PATH, "routes", "Widget"));
        assertEquals("widget", arguments(widget).get("name").value());
    }

    private static Component function(final String name) {
        return model.components()
                .filter(component -> component.uniqueName().contains("routes." + name + "("))
                .findFirst().orElseThrow(() -> new AssertionError("no function " + name));
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName)
                .orElseThrow(() -> new AssertionError("no component " + uniqueName));
    }

    private static Map<String, AnnotationArgument> arguments(final Component component) {
        final Map<String, AnnotationArgument> arguments = annotation(component).arguments();
        assertNotNull("arguments were not read for " + component.uniqueName(), arguments);
        return arguments;
    }

    private static AnnotationReference annotation(final Component component) {
        for (final ComponentReference reference : component.references()) {
            if (reference instanceof AnnotationReference && reference.invokedComponent().equals("register")) {
                return (AnnotationReference) reference;
            }
        }
        throw new AssertionError("no @register on " + component.uniqueName());
    }
}
