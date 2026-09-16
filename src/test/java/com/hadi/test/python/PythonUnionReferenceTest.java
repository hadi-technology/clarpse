package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A reference names a type. A union expression and `...` are neither of them a type name.
 */
public class PythonUnionReferenceTest {

    private static final String FIXTURE = "union-references";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "unions";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void noReferenceAnywhereIsAUnionExpressionOrEllipsis() {
        final List<String> offenders = new ArrayList<>();
        model.components().forEach(component -> component.references().forEach(reference -> {
            final String invoked = reference.invokedComponent();
            if (invoked.contains("|") || invoked.contains("...")) {
                offenders.add(component.uniqueName() + " -> " + invoked);
            }
        }));
        Assert.assertTrue("references that name no type: " + offenders, offenders.isEmpty());
    }

    /** `str | None` is a dependency on `str`; `None` is the absence of a value. */
    @Test
    public void aUnionWithNoneDependsOnTheOtherArmOnly() {
        final Component field = component("Example.maybe");
        Assert.assertTrue(invoked(field).contains("str"));
        Assert.assertFalse(invoked(field).contains("None"));
    }

    /** Each arm of a union is its own reference, or the edges into the later arms are lost. */
    @Test
    public void everyArmOfAUnionIsItsOwnReference() {
        final List<String> refs = invoked(component("Example.either"));
        Assert.assertTrue("first arm missing, has " + refs,
                refs.contains(PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Foo")));
        Assert.assertTrue("second arm missing, has " + refs,
                refs.contains(PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Bar")));
    }

    @Test
    public void aUnionReturnTypeSplitsTheSameWay() {
        final Component find = component("Example."
                + PythonTestUtil.signature("find", "Foo | None", "key: str"));
        Assert.assertTrue(invoked(find).contains(PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Foo")));
    }

    /** The annotation the author wrote is still what the field displays. */
    @Test
    public void theWrittenAnnotationIsPreservedInTheCodeFragment() {
        Assert.assertEquals("maybe : str | None", component("Example.maybe").codeFragment());
        Assert.assertEquals("either : Foo | Bar", component("Example.either").codeFragment());
    }

    @Test
    public void anEllipsisInATypeArgumentNamesNothing() {
        Assert.assertFalse(invoked(component("Example.pair")).contains("..."));
        Assert.assertTrue(invoked(component("Example.cb")).contains("typing.Callable"));
    }

    private static List<String> invoked(final Component component) {
        final List<String> names = new ArrayList<>();
        for (final ComponentReference reference : component.references()) {
            names.add(reference.invokedComponent());
        }
        return names;
    }

    private static Component component(final String symbolPath) {
        final String unique = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
        return model.copyOfComponent(unique).orElseThrow(
                () -> new AssertionError("no component named " + unique));
    }
}
