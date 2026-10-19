package com.hadi.test.typescript;

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
 * A TypeScript decorator keeps the literal values of its arguments, in written order, and records an
 * argument that is not a literal as present but unresolved. A call whose only argument is an object
 * of plain properties contributes those properties as its arguments.
 */
public class TypeScriptAnnotationArgumentTest {

    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = TypeScriptTestUtil.compileFixture("decorator-arguments");
        model = result.model();
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    @Test
    public void aSingleUnnamedArgumentIsNamedValue() {
        final AnnotationArgument value = arguments("Orders").get("value");
        assertEquals(Kind.STRING, value.kind());
        assertEquals("/orders", value.value());
    }

    @Test
    public void severalUnnamedArgumentsAreNamedByPositionAndKeepTheirKinds() {
        final Map<String, AnnotationArgument> arguments = arguments("Orders.list");
        assertEquals(Arrays.asList("0", "1", "2"), new ArrayList<>(arguments.keySet()));
        assertEquals("/list", arguments.get("0").value());
        assertEquals(Kind.ENUM, arguments.get("1").kind());
        assertEquals("Verb.Post", arguments.get("1").value());
        assertEquals(Kind.CLASS, arguments.get("2").kind());
        assertEquals(TypeScriptTestUtil.uniqueName(PACKAGE_PATH, "decorators", "Service"),
                arguments.get("2").value());
    }

    @Test
    public void literalsKeepTheirKindAndValue() {
        final Map<String, AnnotationArgument> arguments = arguments("Orders.literals");
        assertEquals("/a/b", arguments.get("0").value());
        assertEquals(Kind.NUMBER, arguments.get("1").kind());
        assertEquals("12", arguments.get("1").value());
        assertEquals("-3", arguments.get("2").value());
        assertEquals(Kind.BOOLEAN, arguments.get("3").kind());
        assertEquals("true", arguments.get("3").value());
        assertEquals(Kind.ARRAY, arguments.get("4").kind());
        assertEquals("y", arguments.get("4").elements().get(1).value());
    }

    @Test
    public void anArgumentThatIsNotALiteralIsPresentButUnresolved() {
        final Map<String, AnnotationArgument> arguments = arguments("Orders.computed");
        assertEquals(Arrays.asList("0", "1", "2", "3"), new ArrayList<>(arguments.keySet()));
        assertEquals(Kind.UNRESOLVED, arguments.get("0").kind());
        assertEquals("tpl", arguments.get("1").value());
        assertEquals(Kind.UNRESOLVED, arguments.get("2").kind());
        assertEquals(Kind.UNRESOLVED, arguments.get("3").kind());
    }

    @Test
    public void aDecoratorWithoutArgumentsIsReadAsHavingNone() {
        assertTrue(annotation(component("Orders.bare"), "Bare").arguments().isEmpty());
        assertTrue(arguments("Orders.empty").isEmpty());
    }

    @Test
    public void aDecoratorWhoseCallIsChainedIsNotRead() {
        assertNull(annotation(component("Orders.chained"), "Chained").arguments());
    }

    @Test
    public void anObjectOfPlainPropertiesContributesItsPropertiesAsArguments() {
        final Map<String, AnnotationArgument> options = arguments("Orders.optioned");
        assertEquals(Arrays.asList("name", "unique", "verb", "items"), new ArrayList<>(options.keySet()));
        assertEquals("orders", options.get("name").value());
        assertEquals("true", options.get("unique").value());
        assertEquals("Verb.Get", options.get("verb").value());
        assertEquals(Kind.CLASS, options.get("items").elements().get(0).kind());
        final Map<String, AnnotationArgument> entity = annotation(component("Widget"), "Entity").arguments();
        assertEquals("widgets", entity.get("table").value());
    }

    @Test
    public void anObjectThatIsNotPlainIsOneUnreadArgument() {
        assertEquals(Kind.UNRESOLVED, arguments("Widget.dynamic").get("name").kind());
        final Map<String, AnnotationArgument> spread = arguments("Widget.spread");
        assertEquals(Arrays.asList("value"), new ArrayList<>(spread.keySet()));
        assertEquals(Kind.UNRESOLVED, spread.get("value").kind());
        final Map<String, AnnotationArgument> several = arguments("Widget.several");
        assertEquals(Arrays.asList("0", "1"), new ArrayList<>(several.keySet()));
        assertEquals(Kind.UNRESOLVED, several.get("0").kind());
    }

    private static Map<String, AnnotationArgument> arguments(final String symbol) {
        final Component component = component(symbol);
        final Map<String, AnnotationArgument> arguments = annotation(component, "Route").arguments();
        assertNotNull("arguments were not read on " + symbol, arguments);
        return arguments;
    }

    private static Component component(final String symbol) {
        final String unique = TypeScriptTestUtil.uniqueName(PACKAGE_PATH, "controllers", symbol);
        return model.components()
                .filter(component -> component.uniqueName().equals(unique)
                        || component.uniqueName().startsWith(unique + "("))
                .findFirst().orElseThrow(() -> new AssertionError("no component named " + unique));
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
