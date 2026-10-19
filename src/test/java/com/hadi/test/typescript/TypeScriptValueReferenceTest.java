package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A module binding or a class field references every class, function, enum or other module binding
 * its initializer names as a value, wherever in the initializer the name is written, and nothing
 * else: not a local, a parameter, a member, or a name the program does not model.
 */
public class TypeScriptValueReferenceTest {

    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = TypeScriptTestUtil.compileFixture("value-references");
        model = result.model();
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    @Test
    public void classesInAnArrayLiteralAreReferenced() {
        assertEdge("partList", "AlphaPart");
        assertEdge("partList", "BetaPart");
    }

    @Test
    public void classesFunctionsAndEnumsInAnObjectLiteralAreReferenced() {
        assertEdge("partTable", "GammaPart");
        assertEdge("partTable", "makeDelta");
        assertEdge("partTable", "Mode");
    }

    @Test
    public void aShorthandPropertyReferencesWhatItNames() {
        assertEdge("shorthandTable", "AlphaPart");
    }

    @Test
    public void aConstantUsedInAnExpressionIsReferenced() {
        assertEdge("TOTAL", "BASE_SIZE");
        assertEdge("TOTAL", "GAP");
    }

    @Test
    public void aDeclarationReachedThroughANamespaceImportIsReferenced() {
        assertEdge("viaNamespace", "GammaPart");
    }

    @Test
    public void aBindingIsNotGivenWhatItDoesNotName() {
        assertNoEdge("TOTAL", "UNUSED");
        assertNoEdge("partList", "GammaPart");
    }

    @Test
    public void aParameterShadowingAModelledNameIsNotAReference() {
        assertNoEdge("localsOnly", "AlphaPart");
    }

    @Test
    public void literalsReferenceNoDeclaration() {
        assertTrue(invoked(component("registry", "literalsOnly")).stream()
                .noneMatch(name -> name.contains("parts")));
    }

    @Test
    public void aClassFieldInitializerReferencesWhatItNames() {
        assertEdge("Holder", "BetaPart");
        assertEdge("Holder", "BASE_SIZE");
    }

    private static void assertEdge(final String from, final String to) {
        final List<String> invoked = invoked(component("registry", from));
        assertTrue(from + " has no edge to " + to + ", has " + invoked,
                invoked.contains(name("parts", to)));
    }

    private static void assertNoEdge(final String from, final String to) {
        assertFalse(from + " has an unexpected edge to " + to,
                invoked(component("registry", from)).contains(name("parts", to)));
    }

    private static List<String> invoked(final Component component) {
        final List<String> names = new ArrayList<>();
        for (final ComponentReference reference : component.references()) {
            names.add(reference.invokedComponent());
        }
        return names;
    }

    private static Component component(final String module, final String name) {
        final String unique = name(module, name);
        return model.copyOfComponent(unique).orElseThrow(
                () -> new AssertionError("no component named " + unique));
    }

    private static String name(final String module, final String symbol) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, module, symbol);
    }
}
