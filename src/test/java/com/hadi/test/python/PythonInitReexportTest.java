package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A name imported from a package whose {@code __init__.py} only re-exports it --
 * {@code from .responses import Redirect}, {@code from .bases import Base as PublicBase},
 * {@code from .extras import *} -- resolves to the module that declares it, not to a
 * component of the {@code __init__} module that does not exist.
 */
public class PythonInitReexportTest {

    private static final String FIXTURE = "init-reexport";
    private static final String PKG = "src/pkg";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void annotationResolvesThroughTheReexport() {
        Component method = method("go");
        String redirect = PythonTestUtil.uniqueName(PKG, "responses", "Redirect");
        Assert.assertTrue(containsInvokedName(method.internalDependencies(), redirect));
        Assert.assertFalse(containsInvokedName(method.references(),
                PythonTestUtil.uniqueName(PKG, "__init__", "Redirect")));
    }

    @Test
    public void fieldTypeResolvesThroughTheReexport() {
        Component field = model.copyOfComponent("src.views.View.target").orElseThrow();
        Assert.assertTrue(containsInvokedName(field.internalDependencies(),
                PythonTestUtil.uniqueName(PKG, "responses", "Redirect")));
    }

    @Test
    public void aliasedBaseClassResolvesToTheDeclaringModule() {
        Component view = model.copyOfComponent("src.views.View").orElseThrow();
        Assert.assertFalse(view.references(TypeReferences.EXTENSION).isEmpty());
        ComponentReference ref = view.references(TypeReferences.EXTENSION).get(0);
        Assert.assertEquals(PythonTestUtil.uniqueName(PKG, "bases", "Base"), ref.invokedComponent());
        Assert.assertFalse(ref.isExternal());
    }

    @Test
    public void starReexportResolvesInTheBody() {
        Assert.assertTrue(containsInvokedName(method("starred").internalDependencies(),
                PythonTestUtil.uniqueName(PKG, "extras", "Starred")));
    }

    @Test
    public void nestedPackageReexportIsFollowed() {
        Assert.assertTrue(containsInvokedName(method("deep").internalDependencies(),
                PythonTestUtil.uniqueName(PKG + "/chain", "inner", "Deep")));
    }

    @Test
    public void nameDeclaredInTheInitStillResolvesThere() {
        Assert.assertTrue(containsInvokedName(method("own").internalDependencies(),
                PythonTestUtil.uniqueName(PKG, "__init__", "Own")));
    }

    @Test
    public void reexportedFunctionIsRecordedAgainstItsModule() {
        Assert.assertTrue(containsInvokedName(method("call").references(),
                PythonTestUtil.uniqueName(PKG, "responses", "redirect")));
    }

    @Test
    public void moduleQualifiedUseResolvesThroughTheReexport() {
        Assert.assertTrue(containsInvokedName(method("qualified").internalDependencies(),
                PythonTestUtil.uniqueName(PKG, "responses", "Redirect")));
    }

    /** A re-export cycle declares nothing; resolution terminates and invents no target. */
    @Test
    public void reexportCycleTerminatesWithoutAnInternalTarget() {
        Component method = method("loop");
        for (ComponentReference ref : method.internalDependencies()) {
            Assert.assertFalse(ref.invokedComponent().startsWith("src.cyc."));
        }
    }

    private static Component method(final String name) {
        return model.components()
                .filter(c -> c.uniqueName().startsWith("src.views.View." + name + "("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no method " + name));
    }

    private static boolean containsInvokedName(final Iterable<ComponentReference> refs,
                                               final String invokedName) {
        for (ComponentReference ref : refs) {
            if (invokedName.equals(ref.invokedComponent())) {
                return true;
            }
        }
        return false;
    }
}
