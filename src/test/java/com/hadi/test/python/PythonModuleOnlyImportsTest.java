package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A file that declares no class gives its module-level imports to its module-level functions and
 * variables. A file that declares a class keeps them on its classes.
 */
public class PythonModuleOnlyImportsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture("module-only-imports");
        Assert.assertTrue(result.failures().toString(), result.failures().isEmpty());
        model = result.model();
    }

    private static Component component(final String symbol) {
        final List<Component> found = model.components()
                .filter(c -> c.uniqueName().equals(symbol) || c.uniqueName().startsWith(symbol + "("))
                .filter(c -> !c.componentType().isVariableComponent() || c.uniqueName().equals(symbol))
                .collect(Collectors.toList());
        Assert.assertEquals(symbol + " in " + model.components().map(Component::uniqueName)
                .collect(Collectors.toCollection(TreeSet::new)), 1, found.size());
        return found.get(0);
    }

    private static Set<String> imports(final String symbol) {
        return new TreeSet<>(component(symbol).imports());
    }

    @Test
    public void aModuleLevelFunctionOfAFileWithoutAClassHoldsTheFilesImports() {
        final Set<String> imports = imports("pkg.helpers.make");
        Assert.assertTrue(imports.toString(), imports.contains("os"));
        Assert.assertTrue(imports.toString(), imports.contains("pkg.models.Model"));
    }

    @Test
    public void everyModuleLevelFunctionOfThatFileHoldsThem() {
        Assert.assertEquals(imports("pkg.helpers.make"), imports("pkg.helpers.describe"));
    }

    @Test
    public void aModuleLevelVariableOfAFileWithoutAClassHoldsTheFilesImports() {
        final Set<String> imports = imports("pkg.helpers.DEFAULT_PATH");
        Assert.assertTrue(imports.toString(), imports.contains("os"));
        Assert.assertTrue(imports.toString(), imports.contains("pkg.models.Model"));
    }

    @Test
    public void aClassKeepsTheImportsOfItsFile() {
        final Set<String> imports = imports("pkg.service.Service");
        Assert.assertTrue(imports.toString(), imports.contains("json"));
        Assert.assertTrue(imports.toString(), imports.contains("pkg.models.Model"));
    }

    @Test
    public void inAFileWithAClassTheModuleLevelFunctionsAndVariablesHoldNone() {
        Assert.assertTrue(imports("pkg.service.build").isEmpty());
        Assert.assertTrue(imports("pkg.service.SERVICE_NAME").isEmpty());
    }
}
