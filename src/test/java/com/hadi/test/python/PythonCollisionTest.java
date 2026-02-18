package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonCollisionTest {

    private static final String FIXTURE = "same-name-collision";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testSameNameDifferentModules() {
        String userA = PythonTestUtil.uniqueName("src/a", "models", "User");
        String userB = PythonTestUtil.uniqueName("src/b", "models", "User");
        Assert.assertTrue(model.containsComponent(userA));
        Assert.assertTrue(model.containsComponent(userB));
        Assert.assertNotEquals(userA, userB);
    }
}
