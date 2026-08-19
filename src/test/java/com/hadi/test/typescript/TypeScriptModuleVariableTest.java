package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptModuleVariableTest {

    private static final String FIXTURE = "module-vars";
    private static final String PACKAGE_PATH = "src/foo/bar";
    private static final String MODULE = "Config";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testTopLevelConstUniqueNameAndType() {
        Component cmp = model.copyOfComponent(name("API_URL")).orElseThrow();
        Assert.assertEquals(name("API_URL"), cmp.uniqueName());
        Assert.assertEquals(MODULE, cmp.module());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD.toString(),
                cmp.componentType().toString());
        assertTrue(cmp.modifiers().contains("export"));
        assertTrue(cmp.modifiers().contains("const"));
    }

    @Test
    public void testTopLevelLetUniqueNameAndType() {
        Component cmp = model.copyOfComponent(name("retryCount")).orElseThrow();
        Assert.assertEquals(name("retryCount"), cmp.uniqueName());
        Assert.assertEquals(MODULE, cmp.module());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD.toString(),
                cmp.componentType().toString());
        assertTrue(cmp.modifiers().contains("let"));
    }

    @Test
    public void testTopLevelVarUniqueNameAndType() {
        Component cmp = model.copyOfComponent(name("legacyFlag")).orElseThrow();
        Assert.assertEquals(name("legacyFlag"), cmp.uniqueName());
        Assert.assertEquals(MODULE, cmp.module());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD.toString(),
                cmp.componentType().toString());
        assertTrue(cmp.modifiers().contains("var"));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
