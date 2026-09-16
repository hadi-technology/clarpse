package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptProjectReferencesTest {

    private static final String FIXTURE = "project-references";
    private static CompileResult result;
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    /**
     * The two libraries hold identical sources. `libs/plain` is reached by its own
     * `tsconfig.json`; `libs/refonly` is reachable only by following `references` from a
     * solution-style config to a `tsconfig.lib.json`, which is the name a tree walk for
     * `tsconfig.json` never sees.
     */
    @Test
    public void aReferencedProjectYieldsTheSameComponentsAsAPlainOne() {
        for (final String symbol : new String[]{"Alpha", "Alpha.id", "Beta", "Beta.run()", "helper"}) {
            assertTrue("missing from libs/plain: " + symbol,
                    model.containsComponent(name("libs/plain/src", symbol)));
            assertTrue("missing from libs/refonly: " + symbol,
                    model.containsComponent(name("libs/refonly/src", symbol)));
        }
    }

    /**
     * A config whose `extends` target does not exist reports TS5083. Treating that as a parse
     * failure discards the config, and with it every file its own `include` claimed.
     */
    @Test
    public void aConfigWithAnUnresolvableExtendsStillYieldsItsComponents() {
        assertTrue(model.containsComponent(name("libs/missingbase/src", "Alpha")));
        assertTrue(model.containsComponent(name("libs/missingbase/src", "Beta")));
    }

    @Test
    public void everyAnalysedFileEndsUpInAProgram() {
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    /**
     * A tsconfig variant at the repository root must survive into the persisted file set, or every
     * config that extends it fails to read it.
     */
    @Test
    public void aRootLevelTsconfigVariantIsKept() {
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/tsconfig.base.json", "{}"));
        projectFiles.insertFile(new ProjectFile("/libs/a/tsconfig.lib.json", "{}"));

        assertEquals(2, projectFiles.size());
        assertTrue(projectFiles.files().stream()
                .anyMatch(file -> file.path().endsWith("tsconfig.base.json")));
    }

    private static String name(final String packagePath, final String symbol) {
        return TypeScriptTestUtil.uniqueName(packagePath, "utils", symbol);
    }
}
