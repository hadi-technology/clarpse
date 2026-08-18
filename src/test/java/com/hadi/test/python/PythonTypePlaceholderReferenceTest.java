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
import java.util.Locale;
import java.util.Set;

/**
 * {@code Any} is what clarpse displays for a missing annotation, and {@code None} is Python's way of
 * spelling "returns nothing". Neither is a dependency on anything, so neither may reach
 * {@code externalDependencies()} - otherwise a rule like "this must not depend on anything external"
 * fires on essentially every Python component.
 */
public class PythonTypePlaceholderReferenceTest {

    private static final String FIXTURE = "type-placeholders";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "svc";
    private static final Set<String> PLACEHOLDERS = Set.of(
            "any", "typing.any", "none", "nonetype", "noreturn", "ellipsis");

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        Assert.assertTrue(result.failures().isEmpty());
        model = result.model();
    }

    @Test
    public void noComponentDependsOnATypeSystemPlaceholder() {
        final List<String> offenders = new ArrayList<>();
        model.components().forEach(component -> component.references().forEach(reference -> {
            if (PLACEHOLDERS.contains(reference.invokedComponent().toLowerCase(Locale.ROOT))) {
                offenders.add(component.uniqueName() + " -> " + reference.invokedComponent());
            }
        }));
        Assert.assertTrue("type-system placeholders recorded as dependencies: " + offenders,
                offenders.isEmpty());
    }

    @Test
    public void anUnannotatedMethodHasNoTypeDependency() {
        final Component method = component("Svc." + PythonTestUtil.signature("untyped", "Any", "n: Any"));
        Assert.assertTrue(method.references().isEmpty());
    }

    @Test
    public void anExplicitAnyAnnotationIsNotADependency() {
        Assert.assertTrue(component("Svc.anything").references().isEmpty());
    }

    @Test
    public void aNoneReturnIsNotADependency() {
        Assert.assertTrue(component("Svc." + PythonTestUtil.signature("returns_none", "None"))
                .references().isEmpty());
    }

    @Test
    public void realAnnotationsStillResolve() {
        final Component typed = component("Svc." + PythonTestUtil.signature("typed", "Helper", "n: int"));
        Assert.assertTrue("internal return type", invoked(typed).contains("src.helper.Helper"));
        Assert.assertTrue("builtin parameter type", invoked(typed).contains("int"));
        Assert.assertTrue("third-party return type", invoked(component("Svc." + PythonTestUtil.signature("external", "requests.Response"))).contains("requests.Response"));
        Assert.assertTrue("generic container", invoked(component("Svc.items")).contains("typing.List"));
        Assert.assertTrue("type inside Optional[...]", invoked(component("Svc." + PythonTestUtil.signature("optional", "Optional[Helper]"))).contains("src.helper.Helper"));
    }

    @Test
    public void missingAnnotationsStillDisplayAsAny() {
        Assert.assertTrue("Any remains in the signature, it just is not a dependency",
                model.containsComponent(name("Svc." + PythonTestUtil.signature("untyped", "Any", "n: Any"))));
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
