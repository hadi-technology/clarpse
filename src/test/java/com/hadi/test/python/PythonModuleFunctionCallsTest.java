package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * A call to a module-level function in the repo is a reference to that function's component, whose
 * unique name carries its signature, however the call names it: imported by name, through the
 * module, or through a module alias.
 */
public class PythonModuleFunctionCallsTest {

    private static final String FIXTURE = "module-function-calls";
    private static final String VIEW = "app.views.View";
    private static final String CAPFIRST = "util.text.capfirst(x: Any) : Any";
    private static final String JOIN_ALL = "util.text.join_all(items: List[str], sep: str) : str";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void theFunctionComponentsCarryTheirSignatures() {
        Assert.assertTrue(model.containsComponent(CAPFIRST));
        Assert.assertTrue(model.containsComponent(JOIN_ALL));
    }

    @Test
    public void callOfAnImportedFunction() {
        assertInternal(method(VIEW, "title"), CAPFIRST);
    }

    @Test
    public void callOfAFunctionWhoseSignatureHasTypeArguments() {
        assertInternal(method(VIEW, "joined"), JOIN_ALL);
    }

    @Test
    public void callThroughTheModule() {
        assertInternal(method(VIEW, "qualified"), CAPFIRST);
    }

    @Test
    public void callThroughAModuleAlias() {
        assertInternal(method(VIEW, "aliased"), CAPFIRST);
    }

    @Test
    public void callFromAModuleLevelFunction() {
        assertInternal(method("app.views", "headline"), CAPFIRST);
    }

    @Test
    public void theEnclosingClassDependsOnTheFunction() {
        assertInternal(component(VIEW), CAPFIRST);
    }

    @Test
    public void noReferenceIsLeftUnderTheBareName() {
        Assert.assertFalse(invoked(method(VIEW, "title").references()).contains("util.text.capfirst"));
    }

    /** A recursive call is the function itself, not a dependency. */
    @Test
    public void aRecursiveCallIsNotAReference() {
        final Component countdown = method("util.text", "countdown");
        Assert.assertFalse(invoked(countdown.references()).contains(countdown.uniqueName()));
        Assert.assertFalse(invoked(countdown.references()).contains("util.text.countdown"));
    }

    /** A name two functions answer to could be either, so it is not settled on one of them. */
    @Test
    public void aRedefinedFunctionIsNotGuessedAt() {
        Assert.assertEquals(2, model.components()
                .filter(cmp -> cmp.componentType() == OOPSourceModelConstants.ComponentType.FUNCTION
                        && cmp.uniqueName().startsWith("util.text.redefined(")).count());
        final Component ambiguous = method(VIEW, "ambiguous");
        Assert.assertTrue(ambiguous.internalDependencies().isEmpty());
        Assert.assertTrue(invoked(ambiguous.externalDependencies()).contains("util.text.redefined"));
    }

    @Test
    public void aNameTheModuleDoesNotDefineStaysExternal() {
        Assert.assertTrue(method(VIEW, "unknown").internalDependencies().isEmpty());
    }

    private static void assertInternal(final Component cmp, final String target) {
        final Set<String> internal = invoked(cmp.internalDependencies());
        Assert.assertTrue(cmp.uniqueName() + " should reference " + target + ", has "
                + invoked(cmp.references()), internal.contains(target));
    }

    private static Component method(final String owner, final String name) {
        return model.components()
                .filter(cmp -> name.equals(cmp.name()) && cmp.uniqueName().startsWith(owner + "." + name + "("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no function " + owner + "." + name));
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName)
                .orElseThrow(() -> new AssertionError("no component " + uniqueName));
    }

    private static Set<String> invoked(final Iterable<ComponentReference> refs) {
        final Set<String> names = new HashSet<>();
        refs.forEach(ref -> names.add(ref.invokedComponent()));
        return names;
    }
}
