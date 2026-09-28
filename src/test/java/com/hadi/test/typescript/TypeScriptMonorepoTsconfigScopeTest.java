package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptMonorepoTsconfigScopeTest {

    private static final String FIXTURE = "monorepo-tsconfig-scope";

    /**
     * Each app's tsconfig includes `src` and excludes its tests and scripts, so those files belong
     * to no program. Asked to report such files, the compile reports them rather than dropping
     * them: a caller cannot otherwise tell a file its configuration left out from one that was
     * analysed and declared nothing. Compilation still succeeds and the included sources are
     * modelled.
     */
    @Test
    public void filesExcludedByNestedTsconfigsAreReportedAndDoNotStopCompilation() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileReportingUnownedFiles(FIXTURE);

        OOPSourceCodeModel model = result.model();
        assertTrue(model.copyOfComponent(
                TypeScriptTestUtil.uniqueName("apps/backend/src", "service", "Service")).isPresent());
        assertTrue(model.copyOfComponent(
                TypeScriptTestUtil.uniqueName("apps/frontend/src", "app", "App")).isPresent());

        assertEquals(3, result.failures().size());
        for (CompileFailure failure : result.failures()) {
            assertEquals(Integer.valueOf(TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM),
                    failure.errorCode());
        }
        Set<String> reported = result.failures().stream()
                .map(failure -> failure.file().path())
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                .collect(Collectors.toSet());
        assertEquals(Set.of("seed.ts", "service.test.ts", "app.test.ts"), reported);
    }

    /** Left to itself, the compile reads the files each config leaves out, and reports none. */
    @Test
    public void filesExcludedByNestedTsconfigsAreModelled() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        Set<String> modelled = result.model().components()
                .map(component -> component.sourceFile())
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                .collect(Collectors.toSet());
        assertTrue(modelled.toString(),
                modelled.containsAll(Set.of("service.ts", "app.tsx", "seed.ts")));
    }
}
