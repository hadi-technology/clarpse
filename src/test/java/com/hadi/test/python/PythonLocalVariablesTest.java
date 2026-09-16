package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Python local variables. Java, C# and TypeScript all model them; Python modelled none, so a Python
 * model could not answer what a body declares.
 */
public class PythonLocalVariablesTest {

    private static final String FIXTURE = "local-variables";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "vars";
    private static final String BUILD =
            PythonTestUtil.signature("build", "Helper", "count: int");
    private static final String RUN = "Service." + PythonTestUtil.signature("run", "None");
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void anAssignmentBindsALocal() {
        final Component total = component(BUILD + ".total");
        Assert.assertEquals(ComponentType.LOCAL, total.componentType());
        Assert.assertEquals("total : Any", total.codeFragment());
    }

    @Test
    public void anAnnotatedLocalKeepsItsDeclaredType() {
        Assert.assertEquals("label : str", component(BUILD + ".label").codeFragment());
    }

    @Test
    public void anAnnotatedLocalReachesTheDependencyGraph() {
        final Component helper = component(BUILD + ".helper");
        Assert.assertTrue(invoked(helper).contains(
                PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Helper")));
    }

    /** `total` is assigned three times in one body. Python has one variable there, so the model has one. */
    @Test
    public void aReboundNameIsOneLocal() {
        Assert.assertEquals(1, component(BUILD).children().stream()
                .filter(child -> child.equals(name(BUILD + ".total")))
                .count());
    }

    @Test
    public void everyBindingFormIsRecognised() {
        for (final String local : new String[]{"item", "handle", "error", "first", "second", "found"}) {
            Assert.assertEquals("binding form not recognised: " + local,
                    ComponentType.LOCAL, component(BUILD + "." + local).componentType());
        }
    }

    /** A comprehension has a scope of its own, so its iteration variable is not the function's. */
    @Test
    public void aComprehensionVariableIsNotALocalOfTheEnclosingFunction() {
        Assert.assertTrue(model.containsComponent(name(BUILD + ".squares")));
        Assert.assertFalse(model.containsComponent(name(BUILD + ".n")));
    }

    /** `self.cached = 1` binds no name; `inner` is a definition, and its body is its own scope. */
    @Test
    public void attributeTargetsAndNestedDefinitionsAreNotLocals() {
        Assert.assertEquals(ComponentType.LOCAL, component(RUN + ".value").componentType());
        Assert.assertFalse(model.containsComponent(name(RUN + ".cached")));
        Assert.assertFalse(model.containsComponent(name(RUN + ".inner")));
        Assert.assertFalse(model.containsComponent(name(RUN + ".hidden")));
    }

    /** A parameter is already a component of its own and must not be duplicated as a local. */
    @Test
    public void aParameterIsNotAlsoALocal() {
        Assert.assertEquals(ComponentType.METHOD_PARAMETER_COMPONENT,
                component(BUILD + ".count").componentType());
    }

    @Test
    public void aLocalIsAChildOfTheBodyThatBindsIt() {
        Assert.assertTrue(component(BUILD).children().contains(name(BUILD + ".total")));
        Assert.assertEquals(name(BUILD), component(BUILD + ".total").parentUniqueName());
    }

    /** Visibility says whether a member can be reached from outside; a local cannot be. */
    @Test
    public void aLocalCarriesNoVisibility() {
        Assert.assertTrue(component(BUILD + ".total").modifiers().isEmpty());
    }

    private static List<String> invoked(final Component component) {
        final List<String> names = new ArrayList<>();
        for (final ComponentReference reference : component.references()) {
            names.add(reference.invokedComponent());
        }
        return names;
    }

    private static Component component(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow(
                () -> new AssertionError("no component named " + name(symbolPath)));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
