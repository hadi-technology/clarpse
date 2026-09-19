package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class named in a function body through a module binding -- {@code pkg.models.User()} after
 * {@code import pkg.models}, {@code models.User()} after {@code from pkg import models},
 * {@code m.User} after {@code import pkg.models as m} -- is the same dependency as the annotation
 * {@code user: models.User}, and as {@code User()} after {@code from pkg.models import User}.
 */
public class PythonModuleQualifiedBodyRefsTest {

    private static final String FIXTURE = "module-qualified-body-refs";
    private static final String CLIENT = "app.client.Client";
    private static final String USER = "pkg.models.User";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void callThroughAFullyQualifiedModuleImport() {
        assertInternal(method(CLIENT, "dotted_call"), USER);
    }

    @Test
    public void callThroughAnImportedModule() {
        assertInternal(method(CLIENT, "module_call"), USER);
    }

    @Test
    public void callThroughAModuleAlias() {
        assertInternal(method(CLIENT, "alias_call"), USER);
    }

    @Test
    public void classAttributeThroughAModule() {
        assertInternal(method(CLIENT, "class_attribute"), USER);
    }

    @Test
    public void classPassedAsAValueThroughAModule() {
        assertInternal(method(CLIENT, "type_check"), USER);
    }

    @Test
    public void moduleLevelFunctionBody() {
        assertInternal(method("app.client", "top_level"), USER);
    }

    /** The annotation form, which already resolved: the body forms are measured against it. */
    @Test
    public void annotationThroughAModule() {
        assertInternal(method(CLIENT, "annotated"), USER);
    }

    @Test
    public void baseClassThroughAModule() {
        Assert.assertTrue(invoked(component("app.client.Derived").internalDependencies())
                .contains("pkg.models.Base"));
    }

    /** A local assigned anywhere in the function is that local everywhere in it, not the module. */
    @Test
    public void aLocalNamedLikeTheModuleIsNotTheModule() {
        assertNoReferenceTo(method(CLIENT, "shadowed_by_local"), USER);
    }

    @Test
    public void aLoopVariableNamedLikeTheModuleIsNotTheModule() {
        assertNoReferenceTo(method(CLIENT, "shadowed_by_loop"), USER);
    }

    @Test
    public void aParameterNamedLikeTheModuleIsNotTheModule() {
        assertNoReferenceTo(method(CLIENT, "shadowed_by_param"), USER);
    }

    @Test
    public void anAttributeOfAnInstanceIsNotTheModule() {
        assertNoReferenceTo(method(CLIENT, "through_instance"), USER);
    }

    /** A member the module does not define names no class in the model. */
    @Test
    public void anUndefinedModuleMemberIsNotAnInternalReference() {
        final Component method = method(CLIENT, "unknown_member");
        Assert.assertTrue(method.internalDependencies().isEmpty());
    }

    /** A call of a repo function through a module is a reference to that function's component. */
    @Test
    public void aModuleFunctionResolvesToItsComponent() {
        assertInternal(method(CLIENT, "module_function"), "pkg.models.make_user() : Any");
    }

    @Test
    public void aThirdPartyModuleCallResolvesToNothingInternal() {
        Assert.assertTrue(method(CLIENT, "third_party").internalDependencies().isEmpty());
    }

    private static void assertInternal(final Component method, final String target) {
        final Set<String> internal = invoked(method.internalDependencies());
        Assert.assertTrue(method.uniqueName() + " should reference " + target + ", has " + internal,
                internal.contains(target));
    }

    private static void assertNoReferenceTo(final Component method, final String target) {
        final Set<String> all = invoked(method.references());
        Assert.assertFalse(method.uniqueName() + " should not reference " + target + ", has " + all,
                all.contains(target));
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
        final Set<String> names = new java.util.HashSet<>();
        refs.forEach(ref -> names.add(ref.invokedComponent()));
        return names;
    }
}
