package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonConstructorAssignedFieldsTest {

    private static final String FIXTURE = "constructor-assigned-fields";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "sample";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void constructorAssignmentsAreModeledAsFields() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(name("Service.owner")).orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(name("Service.team")).orElseThrow().componentType());
    }

    @Test
    public void nonSelfLocalVariablesAreNotModeledAsFields() {
        Assert.assertFalse(model.copyOfComponent(name("Service.temporary")).isPresent());
    }

    @Test
    public void repeatedConstructorAssignmentsDoNotCreateExtraComponents() {
        long ownerCount = model.components()
                .filter(component -> component.uniqueName().equals(name("Service.owner")))
                .count();
        Assert.assertEquals(1, ownerCount);
    }

    @Test
    public void nestedClassConstructorAssignmentsAreModeledAsFields() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(name("Outer.Inner.inner_owner")).orElseThrow().componentType());
    }

    /**
     * Annotating a field and initialising it on the same line must not erase its type.
     *
     * <p>A bare {@code x: T} parses as an annotation node carrying the type directly, but
     * {@code x: T = make()} parses as an assignment whose LEFT expression is the annotation, so the
     * type sits one level down. The lookup only checked the statement, found nothing, and typed the
     * field {@code Any} -- contributing no dependency at all. Both forms are ordinary Python and the
     * broken one is the commoner of the two.
     */
    @Test
    public void annotatedAssignmentsKeepTheirDeclaredType() {
        Assert.assertTrue(containsInvoked(name("Annotated.owner"), typeName("User")));
        Assert.assertTrue(containsInvoked(name("Annotated.tag"), typeName("Team")));
    }

    /** Both forms are still fields, not dropped by the target-node change that found the name. */
    @Test
    public void annotatedAssignmentsAreStillModeledAsFields() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(name("Annotated.owner")).orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(name("Annotated.tag")).orElseThrow().componentType());
    }

    private static boolean containsInvoked(final String fieldName, final String targetName) {
        return model.copyOfComponent(fieldName).orElseThrow().internalDependencies().stream()
                .anyMatch(ref -> targetName.equals(ref.invokedComponent()));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }

    /** User and Team are declared in types.py, not in the module under test. */
    private static String typeName(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, "types", symbolPath);
    }
}
