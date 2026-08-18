package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptMonorepoTsconfigScopeTest {

    private static final String FIXTURE = "monorepo-tsconfig-scope";

    @Test
    public void filesExcludedByNestedTsconfigsDoNotFailCompilation() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

        OOPSourceCodeModel model = result.model();
        assertTrue(model.copyOfComponent(TypeScriptTestUtil.uniqueName("apps/backend/src", "service", "Service")).isPresent());
        assertTrue(model.copyOfComponent(TypeScriptTestUtil.uniqueName("apps/frontend/src", "app", "App")).isPresent());
        assertTrue(result.failures().isEmpty());
    }
}
