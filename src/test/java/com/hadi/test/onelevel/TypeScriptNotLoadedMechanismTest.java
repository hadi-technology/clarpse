package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static com.hadi.test.onelevel.OneLevelTestSupport.component;
import static com.hadi.test.onelevel.OneLevelTestSupport.files;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static com.hadi.test.onelevel.OneLevelTestSupport.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * In a one-level TypeScript analysis a type lost to {@code any} because a file was not loaded is
 * reported as not loaded, and is not {@code UNRESOLVED}. {@code UNRESOLVED} keeps its one meaning:
 * no declaration is visible under the name, which stays external.
 */
public class TypeScriptNotLoadedMechanismTest {

    private static CompileResult result;

    @BeforeClass
    public static void compile() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        result = new ClarpseProject(project(files(
                "/tsconfig.json", "{ \"compilerOptions\": { \"target\": \"es2020\", \"module\": \"commonjs\","
                        + " \"strict\": true, \"experimentalDecorators\": true }, \"include\": [\"src/**/*.ts\"] }",
                "/src/a.ts", "import { B } from \"./b\";\n"
                        + "@Mystery()\n"
                        + "export class A extends B {\n"
                        + "  loose: any[] = [];\n"
                        + "}\n",
                "/src/b.ts", "import { C } from \"./c\";\nexport class B {\n  c: C = new C();\n}\n",
                "/src/c.ts", "export class C { }\n")), Lang.TYPESCRIPT, List.of("/src/a.ts"),
                AnalysisOptions.oneLevel()).result();
    }

    @Test
    public void aTypeLostToAnyIsNotLoadedAndNotUnresolved() {
        final Component loose = component(result.model(), "src.a.A.loose");
        assertFalse(loose.notLoadedDependencies().isEmpty());
        for (final ComponentReference reference : loose.notLoadedDependencies()) {
            assertTrue(reference.isNotLoaded());
            assertFalse(reference.toString(), reference.resolutionKind() == ResolutionKind.UNRESOLVED);
        }
        assertTrue(targets(loose.externalDependencies()).toString(), loose.externalDependencies().isEmpty());
    }

    @Test
    public void aNameNothingDeclaresIsUnresolvedAndExternal() {
        final Component a = component(result.model(), "src.a.A");
        final ComponentReference mystery = a.references().stream()
                .filter(reference -> reference.invokedComponent().equals("Mystery")).findFirst().orElseThrow();
        assertEquals(ResolutionKind.UNRESOLVED, mystery.resolutionKind());
        assertTrue(mystery.isExternal());
        assertFalse(mystery.isNotLoaded());
    }
}
