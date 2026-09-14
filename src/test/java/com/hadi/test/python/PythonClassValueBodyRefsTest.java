package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * A class named in a function body is a dependency however it is used -- called on
 * ({@code User.create()}), read from ({@code User.LIMIT}), or handed around as a value
 * ({@code isinstance(x, User)}, {@code register(User)}, {@code handler = User},
 * {@code (User, Admin)}) -- and not only when it is instantiated. Only a class defined in the repo
 * counts: an imported function or constant used the same way is not a type reference.
 */
public class PythonClassValueBodyRefsTest {

    private static final String FIXTURE = "class-value-body-refs";
    private static final String CLIENT = "app.client.Client";
    private static final String USER = "pkg.models.User";
    private static final String ADMIN = "pkg.models.Admin";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void callOnAnImportedClass() {
        assertInternal(method(CLIENT, "class_method_call"), USER);
    }

    @Test
    public void attributeOfAnImportedClass() {
        assertInternal(method(CLIENT, "class_attribute"), USER);
    }

    /** Already resolved through the module binding; the class-qualified forms are measured against it. */
    @Test
    public void callOnAClassThroughAModuleAlias() {
        assertInternal(method(CLIENT, "alias_class_method_call"), USER);
    }

    @Test
    public void classAsTheSecondArgumentOfIsinstance() {
        assertInternal(method(CLIENT, "instance_check"), USER);
    }

    @Test
    public void classAsTheSecondArgumentOfIssubclass() {
        assertInternal(method(CLIENT, "subclass_check"), USER);
    }

    @Test
    public void classPassedAsAnArgument() {
        assertInternal(method(CLIENT, "passed_as_argument"), USER);
    }

    @Test
    public void classAssignedToALocal() {
        assertInternal(method(CLIENT, "assigned"), USER);
    }

    @Test
    public void classesInATuple() {
        assertInternal(method(CLIENT, "in_a_tuple"), USER, ADMIN);
    }

    @Test
    public void classDefinedInTheSameModule() {
        assertInternal(method(CLIENT, "same_module_class"), "app.client.Helper");
    }

    @Test
    public void moduleLevelFunctionBody() {
        assertInternal(method("app.client", "module_level_check"), ADMIN);
    }

    /** A name assigned anywhere in a function is that local throughout it, not the import. */
    @Test
    public void aLocalNamedLikeTheClassIsNotTheClass() {
        assertNoReferenceTo(method(CLIENT, "shadowed_by_local"), USER);
    }

    @Test
    public void aParameterNamedLikeTheClassIsNotTheClass() {
        assertNoReferenceTo(method(CLIENT, "shadowed_by_param"), USER);
    }

    @Test
    public void anAttributeOfAnInstanceIsNotTheClass() {
        assertNoReferenceTo(method(CLIENT, "through_instance"), USER);
    }

    @Test
    public void aKeywordArgumentNamedLikeTheClassIsNotTheClass() {
        assertNoReferenceTo(method(CLIENT, "keyword_named_like_a_class"), USER);
    }

    /** Imported, and used as a value, but a function: not a type reference, internal or external. */
    @Test
    public void anImportedFunctionUsedAsAValueIsNotAReference() {
        final Component method = method(CLIENT, "function_value");
        Assert.assertTrue(method.internalDependencies().isEmpty());
        assertNoReferenceTo(method, "pkg.models.make_user");
    }

    @Test
    public void anImportedConstantIsNotAReference() {
        final Component method = method(CLIENT, "constant_value");
        Assert.assertTrue(method.internalDependencies().isEmpty());
        assertNoReferenceTo(method, "pkg.models.DEFAULT_NAME");
    }

    private static void assertInternal(final Component method, final String... targets) {
        final Set<String> internal = invoked(method.internalDependencies());
        for (final String target : targets) {
            Assert.assertTrue(method.uniqueName() + " should reference " + target + ", has " + internal,
                    internal.contains(target));
        }
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

    private static Set<String> invoked(final Iterable<ComponentReference> refs) {
        final Set<String> names = new HashSet<>();
        refs.forEach(ref -> names.add(ref.invokedComponent()));
        return names;
    }
}
