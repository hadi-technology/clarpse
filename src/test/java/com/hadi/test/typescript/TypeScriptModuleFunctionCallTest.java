package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A call is a dependency on what it calls. Recording only the type a call evaluates to left two
 * files whose functions call each other with no edge between them.
 */
public class TypeScriptModuleFunctionCallTest {

    private static final String FIXTURE = "module-function-calls";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    @Test
    public void aCallToAnImportedModuleFunctionIsRecorded() {
        final Component useIt = component("b", "useIt");
        assertTrue("no edge to the callee, has " + invoked(useIt),
                invoked(useIt).contains(name("a", "helper")));
    }

    @Test
    public void aCallToAFunctionInTheSameModuleIsRecorded() {
        assertTrue(invoked(component("a", "localCaller")).contains(name("a", "helper")));
    }

    @Test
    public void theCalleeIsRecordedOnceHoweverOftenItIsCalled() {
        final long edges = invoked(component("b", "useItTwice")).stream()
                .filter(invoked -> invoked.equals(name("a", "helper")))
                .count();
        assertEquals(1, edges);
    }

    /** The call's own type was all that was recorded before, and it is still recorded. */
    @Test
    public void theTypeTheCallEvaluatesToIsStillRecorded() {
        assertTrue(invoked(component("b", "useIt")).contains("number"));
    }

    @Test
    public void theCalleeEdgeIsInternal() {
        final Component useIt = component("b", "useIt");
        final List<String> internal = new ArrayList<>();
        useIt.internalDependencies().forEach(ref -> internal.add(ref.invokedComponent()));
        assertTrue("the callee is a component of this model, so the edge is internal: " + internal,
                internal.contains(name("a", "helper")));
    }

    private static List<String> invoked(final Component component) {
        final List<String> names = new ArrayList<>();
        for (final ComponentReference reference : component.references()) {
            names.add(reference.invokedComponent());
        }
        return names;
    }

    private static Component component(final String module, final String function) {
        final String unique = name(module, function);
        return model.copyOfComponent(unique).orElseThrow(
                () -> new AssertionError("no component named " + unique));
    }

    private static String name(final String module, final String function) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, module, function);
    }
}
