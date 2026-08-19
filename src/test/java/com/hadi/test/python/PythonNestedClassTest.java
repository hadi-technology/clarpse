package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonNestedClassTest {

    private static final String FIXTURE = "nested-classes";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "nested";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testNestedClassComponentNames() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent(name("Outer")).orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent(name("Outer.Inner")).orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent(name("Outer.Inner.Deep")).orElseThrow().componentType());
    }

    @Test
    public void testNestedMethodNamesUseNestedClassPath() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD,
                model.copyOfComponent(name("Outer." + PythonTestUtil.signature("top", "None")))
                        .orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD,
                model.copyOfComponent(name("Outer.Inner." + PythonTestUtil.signature("build", "None")))
                        .orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD,
                model.copyOfComponent(name("Outer.Inner.Deep." + PythonTestUtil.signature("ping", "int")))
                        .orElseThrow().componentType());
    }

    @Test
    public void testNestedClassHierarchy() {
        String outerName = name("Outer");
        String innerName = name("Outer.Inner");
        String deepName = name("Outer.Inner.Deep");
        String innerMethod = name("Outer.Inner." + PythonTestUtil.signature("build", "None"));
        String deepMethod = name("Outer.Inner.Deep." + PythonTestUtil.signature("ping", "int"));

        Component outer = model.copyOfComponent(outerName).orElseThrow();
        Component inner = model.copyOfComponent(innerName).orElseThrow();
        Component deep = model.copyOfComponent(deepName).orElseThrow();

        Assert.assertTrue(outer.children().contains(innerName));
        Assert.assertEquals(outerName, inner.parentUniqueName());
        Assert.assertTrue(inner.children().contains(deepName));
        Assert.assertTrue(inner.children().contains(innerMethod));
        Assert.assertEquals(innerName, deep.parentUniqueName());
        Assert.assertTrue(deep.children().contains(deepMethod));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
