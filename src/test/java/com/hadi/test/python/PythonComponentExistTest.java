package com.hadi.test.python;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonComponentExistTest {

    private static final String FIXTURE = "method-components";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "service";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void noPythonFilesParsedTest() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles rawData = new ProjectFiles();
        OOPSourceCodeModel generatedSourceModel = new ClarpseProject(rawData, Lang.PYTHON).result().model();
        Assert.assertEquals(0, generatedSourceModel.components().count());
    }

    @Test
    public void classComponentExists() {
        Assert.assertTrue(model.containsComponent(name("Service")));
    }

    @Test
    public void fieldComponentExists() {
        Assert.assertTrue(model.containsComponent(name("Service.owner")));
    }

    @Test
    public void constructorComponentExists() {
        String ctor = name("Service." + PythonTestUtil.signature("__init__", "None", "user: User"));
        Assert.assertTrue(model.containsComponent(ctor));
    }

    @Test
    public void constructorParamComponentExists() {
        String ctor = PythonTestUtil.signature("__init__", "None", "user: User");
        String param = name("Service." + ctor + ".user");
        Assert.assertTrue(model.containsComponent(param));
    }

    @Test
    public void methodComponentExists() {
        String method = name("Service." + PythonTestUtil.signature("update", "User", "user: User"));
        Assert.assertTrue(model.containsComponent(method));
    }

    @Test
    public void methodParamComponentExists() {
        String method = PythonTestUtil.signature("update", "User", "user: User");
        String param = name("Service." + method + ".user");
        Assert.assertTrue(model.containsComponent(param));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
