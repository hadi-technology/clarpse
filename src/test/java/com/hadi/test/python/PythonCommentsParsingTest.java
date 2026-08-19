package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonCommentsParsingTest {

    private static final String FIXTURE = "comments-parsing";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "comments";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void classLevelCommentIsCaptured() {
        String className = name("Test");
        Component cmp = model.copyOfComponent(className).orElseThrow();
        Assert.assertTrue(cmp.comment().contains("Class doc for Test."));
    }

    @Test
    public void classWithNoDocstringHasNoComment() {
        String className = name("NoComment");
        Component cmp = model.copyOfComponent(className).orElseThrow();
        Assert.assertEquals("", cmp.comment());
    }

    @Test
    public void methodLevelDocstringIsCaptured() {
        String methodName = name("Test." + PythonTestUtil.signature("test", "str", "method_param: str"));
        Component cmp = model.copyOfComponent(methodName).orElseThrow();
        Assert.assertTrue(cmp.comment().contains("method doc for test."));
    }

    @Test
    public void classmethodIsParsedAndSkipsClsParameter() {
        String methodName = name("Factory." + PythonTestUtil.signature("build", "None", "name: str"));
        Component cmp = model.copyOfComponent(methodName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD, cmp.componentType());
        Assert.assertTrue(cmp.comment().contains("classmethod doc for build."));
        Assert.assertFalse(cmp.codeFragment().contains("cls:"));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
