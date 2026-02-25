package com.hadi.test.python;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class PythonRelativeProjectPathTest {

    @Test
    public void testRelativeProjectPathParsesPythonFiles() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles files = new ProjectFiles("src/test/resources/python/component-types");
        CompileResult result = new ClarpseProject(files, Lang.PYTHON).result();

        String serviceName = PythonTestUtil.uniqueName("src", "sample", "Service");
        Assert.assertTrue(result.model().containsComponent(serviceName));
        Assert.assertTrue(result.failures().isEmpty());
    }
}
