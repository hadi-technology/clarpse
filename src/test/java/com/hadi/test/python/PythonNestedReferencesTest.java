package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What a nested function or class, a parameter default or a decorator argument refers to is a
 * reference of the module-level function or class, or the method, that encloses it.
 */
public class PythonNestedReferencesTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture("nested-references");
        Assert.assertTrue(result.failures().toString(), result.failures().isEmpty());
        model = result.model();
    }

    /** The component whose unique name is the symbol, or the symbol followed by a signature. */
    private static Component component(final String symbol) {
        final List<Component> found = model.components()
                .filter(c -> c.uniqueName().equals(symbol) || c.uniqueName().startsWith(symbol + "("))
                .filter(c -> !c.componentType().isVariableComponent() || c.uniqueName().equals(symbol))
                .collect(Collectors.toList());
        Assert.assertEquals(symbol + " in " + names(), 1, found.size());
        return found.get(0);
    }

    private static Set<String> names() {
        return model.components().map(Component::uniqueName).collect(Collectors.toCollection(TreeSet::new));
    }

    private static void assertReferences(final String from, final String target) {
        final Set<String> invoked = component(from).references().stream()
                .map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
        Assert.assertTrue(from + " should refer to " + target + " but refers to " + invoked,
                invoked.stream().anyMatch(name -> name.equals(target) || name.startsWith(target + "(")));
    }

    @Test
    public void aFunctionInsideAFunctionIsAReferenceOfTheEnclosingFunction() {
        assertReferences("pkg.api.outer", "pkg.deps.Db");
    }

    @Test
    public void aClassInsideAFunctionIsAReferenceOfTheEnclosingFunction() {
        assertReferences("pkg.api.outer", "pkg.deps.Base");
    }

    @Test
    public void functionsNestedSeveralLevelsDeepAreReferencesOfTheModuleLevelFunction() {
        assertReferences("pkg.api.deep", "pkg.deps.Cache");
    }

    @Test
    public void aFunctionInsideAMethodIsAReferenceOfTheMethod() {
        assertReferences("pkg.api.Service.method", "pkg.deps.Mixin");
    }

    @Test
    public void aParameterDefaultCallIsAReferenceOfTheFunction() {
        assertReferences("pkg.api.handler", "pkg.deps.Depends");
    }

    @Test
    public void anArgumentOfAParameterDefaultIsAReferenceOfTheFunction() {
        assertReferences("pkg.api.handler", "pkg.deps.get_db");
    }

    @Test
    public void aParameterDefaultOfAMethodIsAReferenceOfTheMethod() {
        assertReferences("pkg.api.Service.with_default", "pkg.deps.Depends");
        assertReferences("pkg.api.Service.with_default", "pkg.deps.get_db");
    }

    @Test
    public void aDecoratorCallIsAReferenceOfTheDecoratedFunction() {
        assertReferences("pkg.api.decorated", "pkg.deps.tagged");
    }

    @Test
    public void aDecoratorArgumentIsAReferenceOfTheDecoratedFunction() {
        assertReferences("pkg.api.decorated", "pkg.deps.Marker");
    }
}
