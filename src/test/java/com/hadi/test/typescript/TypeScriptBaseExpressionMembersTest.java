package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptBaseExpressionMembersTest {

    private static final String FIXTURE = "base-expression-members";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "models";
    private static CompileResult result;
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    /**
     * `class User extends fields({ id: "", email: "" }) {}` has an empty body, so walking
     * `node.members` finds nothing and the class looks like one that declares no members.
     */
    @Test
    public void membersDeclaredInAClassProducingBaseExpressionAreAttributedToTheClass() {
        final Component user = component("User");
        assertTrue(user.children().contains(name("User.id")));
        assertTrue(user.children().contains(name("User.email")));
        assertEquals(ComponentType.FIELD, component("User.id").componentType());
        assertEquals(ComponentType.FIELD, component("User.email").componentType());
    }

    /** Members declared in the base call's type argument, rather than in an object literal. */
    @Test
    public void membersDeclaredInABaseTypeArgumentAreAttributedToTheClass() {
        final Component repo = component("Repo");
        assertTrue(repo.children().contains(name("Repo.find(string)")));
        assertEquals(ComponentType.METHOD, component("Repo.find(string)").componentType());
    }

    /** A class that declares members of its own keeps them, and gains only what it does not declare. */
    @Test
    public void declaredMembersAreNotDuplicatedByInheritedOnes() {
        final Component named = component("Named");
        assertTrue(named.children().contains(name("Named.label")));
        assertTrue(named.children().contains(name("Named.describe()")));
        assertEquals(1, named.children().stream()
                .filter(child -> child.equals(name("Named.describe()")))
                .count());
    }

    /** Already worked before this change; kept so it stays working. */
    @Test
    public void optionalMethodSignaturesAreRecordedAsMethods() {
        assertEquals(ComponentType.METHOD, component("Plugin.init()").componentType());
        assertEquals(ComponentType.METHOD, component("Plugin.run()").componentType());
    }

    /**
     * The anonymous object type a mixin factory returns is named `__object` by TypeScript. It names
     * no type a consumer can look up, so recording it as a base invents an edge.
     */
    @Test
    public void typeScriptInternalSymbolNamesAreNotRecordedAsReferences() {
        model.components().forEach(component -> {
            for (final ComponentReference reference : component.references()) {
                assertTrue("invented reference " + reference.invokedComponent()
                                + " on " + component.uniqueName(),
                        !reference.invokedComponent().contains("__object"));
            }
        });
    }

    /** Every base here resolves, so nothing may be reported as unresolved. */
    @Test
    public void resolvableBaseExpressionsAreNotReportedAsUnresolved() {
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    private static Component component(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow(
                () -> new AssertionError("no component named " + name(symbolPath)));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
