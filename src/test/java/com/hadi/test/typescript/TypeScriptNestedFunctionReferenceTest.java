package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * A reference written inside a function expression or an arrow function belongs to the declaration
 * that encloses it, however deeply the function is nested, and a module-level binding references
 * what its initializer calls, whether the initializer is a function or any other expression.
 */
public class TypeScriptNestedFunctionReferenceTest {

    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = TypeScriptTestUtil.compileFixture("nested-function-references");
        model = result.model();
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    @Test
    public void aCallInAnArrowAssignedToALocalBelongsToTheEnclosingFunction() {
        assertEdge("callers", "component", "viaLocalArrow");
    }

    @Test
    public void aCallInACallbackArgumentBelongsToTheEnclosingFunction() {
        assertEdge("callers", "component", "viaCallback");
    }

    @Test
    public void aCallInsideSeveralLevelsOfArrowsBelongsToTheEnclosingFunction() {
        assertEdge("callers", "doubly", "viaDoublyNested");
    }

    @Test
    public void aCallInAFunctionExpressionInAMethodBelongsToTheEnclosingClass() {
        assertEdge("callers", "Widget", "viaFunctionExpression");
    }

    @Test
    public void aModuleBindingHoldingAnArrowReferencesWhatTheArrowCalls() {
        assertEdge("callers", "arrowBinding", "viaBindingArrow");
    }

    @Test
    public void aModuleBindingReferencesWhatItsInitializerCalls() {
        assertEdge("callers", "initialised", "viaBindingInitializer");
    }

    @Test
    public void aFunctionIsNotGivenWhatAnUnrelatedOneCalls() {
        assertTrue(!invoked(component("callers", "doubly")).contains(name("targets", "viaCallback")));
    }

    private static void assertEdge(final String module, final String from, final String to) {
        final List<String> invoked = invoked(component(module, from));
        assertTrue(from + " has no edge to " + to + ", has " + invoked,
                invoked.contains(name("targets", to)));
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
