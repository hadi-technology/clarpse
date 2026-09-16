package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

public class PythonClassAttributesTest {

    private static final String FIXTURE = "class-attributes";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "account";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    /** `kind = "standard"` declares a field as much as `limit: int = 10` does. */
    @Test
    public void unannotatedClassAttributesAreFields() {
        final Component kind = component("Account.kind");
        Assert.assertEquals(ComponentType.FIELD, kind.componentType());
        Assert.assertTrue(kind.modifiers().contains("public"));
    }

    @Test
    public void unannotatedClassAttributesFollowTheVisibilityConvention() {
        Assert.assertTrue(component("Account._private").modifiers().contains("protected"));
    }

    @Test
    public void annotatedClassAttributesStillWork() {
        Assert.assertEquals(ComponentType.FIELD, component("Account.limit").componentType());
    }

    /**
     * A class body holds more than declarations. The docstring must not become a field, which is
     * what pins the members of the class to exactly those it declares.
     */
    @Test
    public void onlyAssignmentsBecomeFields() {
        Assert.assertEquals(
                Set.of(name("Account.kind"),
                        name("Account.limit"),
                        name("Account._private"),
                        name("Account." + PythonTestUtil.signature("balance", "int")),
                        name("Account." + PythonTestUtil.signature("history", "Any")),
                        name("Account." + PythonTestUtil.signature("close", "None"))),
                Set.copyOf(component("Account").children()));
        Assert.assertTrue("the docstring is the class comment, not a member",
                component("Account").comment().contains("An account."));
    }

    /** Already worked before this change; kept so they stay working. */
    @Test
    public void propertiesAndModuleFunctionsAreModelled() {
        Assert.assertEquals(ComponentType.METHOD,
                component("Account." + PythonTestUtil.signature("balance", "int")).componentType());
        Assert.assertEquals(ComponentType.METHOD,
                component("Account." + PythonTestUtil.signature("history", "Any")).componentType());
        Assert.assertEquals(ComponentType.FUNCTION,
                component(PythonTestUtil.signature("load_account", "Any", "path: Any")).componentType());
    }

    private static Component component(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow(
                () -> new AssertionError("no component named " + name(symbolPath)));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
