package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/** The type arguments of a generic base are dependencies of the class; only the generic itself is extended. */
public class PythonGenericBaseArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("generic-base-arguments");
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static Component cls(final String name) {
        return model.copyOfComponent("src.sample." + name).orElseThrow();
    }

    private static List<String> extended(final String name) {
        return cls(name).references(TypeReferences.EXTENSION).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toList());
    }

    private static boolean dependsOn(final String name, final String target) {
        return cls(name).internalDependencies().stream().anyMatch(r -> r.invokedComponent().equals(target));
    }

    @Test
    public void aTypeArgumentOfTheBaseIsADependency() {
        Assert.assertTrue(dependsOn("Direct", "src.archive.Item"));
        Assert.assertTrue(dependsOn("Dotted", "src.archive.Item"));
        Assert.assertTrue(dependsOn("Mapped", "src.archive.Item"));
    }

    @Test
    public void onlyTheGenericItselfIsExtended() {
        Assert.assertEquals(List.of("src.sample.Base"), extended("Direct"));
        Assert.assertEquals(List.of("src.sample.Base"), extended("Dotted"));
        Assert.assertEquals(List.of("src.archive.Store"), extended("Mapped"));
    }
}
