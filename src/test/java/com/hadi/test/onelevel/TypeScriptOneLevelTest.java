package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.hadi.test.onelevel.OneLevelTestSupport.component;
import static com.hadi.test.onelevel.OneLevelTestSupport.files;
import static com.hadi.test.onelevel.OneLevelTestSupport.json;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static com.hadi.test.onelevel.OneLevelTestSupport.referencePairs;
import static com.hadi.test.onelevel.OneLevelTestSupport.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One-level analysis of TypeScript: level one is found by module resolution, closed over re-exports,
 * and modelled in programs that hold only the planned files.
 */
public class TypeScriptOneLevelTest {

    private static final String A = "/src/a.ts";
    private static final String B = "/src/b.ts";
    private static final String C = "/src/deep/c.ts";
    private static final String SHAPE = "/src/shape.ts";
    private static final String BARREL = "/src/barrel.ts";
    private static final String IMPL = "/src/impl.ts";
    private static final String ALIASED = "/src/lib/aliased.ts";
    private static final String REMOTE = "/packages/other/src/remote.ts";

    private static CompileResult oneLevel;
    private static OOPSourceCodeModel whole;

    static Map<String, String> repository() {
        return files(
                "/tsconfig.json", "{ \"compilerOptions\": { \"target\": \"es2020\", \"module\": \"commonjs\","
                        + " \"strict\": true, \"baseUrl\": \".\", \"paths\": { \"@lib/*\": [\"src/lib/*\"] } },"
                        + " \"include\": [\"src/**/*.ts\", \"types/**/*.d.ts\"] }",
                "/packages/other/tsconfig.json", "{ \"compilerOptions\": { \"target\": \"es2020\","
                        + " \"module\": \"commonjs\", \"strict\": true }, \"include\": [\"src/**/*.ts\"] }",
                A, "import { B } from \"./b\";\n"
                        + "import type { Shape } from \"./shape\";\n"
                        + "import { Impl } from \"./barrel\";\n"
                        + "import { Aliased } from \"@lib/aliased\";\n"
                        + "import { Remote } from \"../packages/other/src/remote\";\n"
                        + "export class A extends B {\n"
                        + "  shape: Shape | undefined;\n"
                        + "  impl: Impl = new Impl();\n"
                        + "  aliased: Aliased = new Aliased();\n"
                        + "  remote: Remote = new Remote();\n"
                        + "  glob: GlobalThing | undefined;\n"
                        + "  loose: any[] = [];\n"
                        + "  run(): void { this.make(); }\n"
                        + "}\n",
                B, "import { C } from \"./deep/c\";\n"
                        + "export class B {\n  c: C = new C();\n  make(): C { return this.c; }\n}\n",
                C, "export class C { }\n",
                SHAPE, "export interface Shape { size: number; }\n",
                BARREL, "export * from \"./impl\";\n",
                IMPL, "export class Impl { }\n",
                ALIASED, "export class Aliased { }\n",
                "/types/global.d.ts", "interface GlobalThing { x: number; }\n",
                REMOTE, "export class Remote { }\n",
                "/src/unrelated.ts", "export class Unrelated { }\n");
    }

    @BeforeClass
    public static void compile() throws Exception {
        oneLevel = new ClarpseProject(project(repository()), Lang.TYPESCRIPT, List.of(A),
                AnalysisOptions.oneLevel()).result();
        whole = new ClarpseProject(project(repository()), Lang.TYPESCRIPT).result().model();
    }

    @Test
    public void levelOneIsImportsAliasesTypeImportsOtherConfigsAndReexports() {
        assertEquals(new TreeSet<>(Set.of(B, SHAPE, BARREL, IMPL, ALIASED, REMOTE)),
                new TreeSet<>(oneLevel.levelOne().levelOneFiles()));
        assertTrue(oneLevel.failures().toString(), oneLevel.failures().isEmpty());
    }

    @Test
    public void levelOneComponentsAreBoundaryAndNothingBeyondIsModelled() {
        final OOPSourceCodeModel model = oneLevel.model();
        assertFalse(component(model, "src.a.A").isBoundary());
        for (final String name : List.of("src.b.B", "src.shape.Shape", "src.impl.Impl", "src.lib.aliased.Aliased",
                "packages.other.src.remote.Remote")) {
            assertTrue(name, component(model, name).isBoundary());
        }
        assertFalse(model.containsComponent("src.deep.c.C"));
        assertFalse(model.containsComponent("src.unrelated.Unrelated"));
    }

    @Test
    public void analysedReferencesResolveThroughBarrelsAliasesAndConfigs() {
        final Set<String> internal = targets(component(oneLevel.model(), "src.a.A").internalDependencies());
        assertTrue(internal.toString(), internal.containsAll(Set.of("src.b.B", "src.shape.Shape", "src.impl.Impl",
                "src.lib.aliased.Aliased", "packages.other.src.remote.Remote")));
    }

    @Test
    public void ambientDeclarationsStillResolveIntoTheRepository() {
        final Component glob = component(oneLevel.model(), "src.a.A.glob");
        assertTrue(targets(glob.notLoadedDependencies()).toString(),
                targets(glob.notLoadedDependencies()).contains("types.global.d.GlobalThing"));
    }

    @Test
    public void boundaryReferencesPastLevelOneAreNotLoaded() {
        final Component c = component(oneLevel.model(), "src.b.B.c");
        assertEquals(Set.of("C"), targets(c.notLoadedDependencies()));
        assertTrue(c.externalDependencies().isEmpty());
    }

    @Test
    public void anyNestedInAnotherTypeIsNotLoadedNotExternal() {
        final Component loose = component(oneLevel.model(), "src.a.A.loose");
        assertFalse(loose.notLoadedDependencies().isEmpty());
        for (final ComponentReference reference : loose.notLoadedDependencies()) {
            assertEquals(ResolutionKind.UNRESOLVED, reference.resolutionKind());
        }
        assertTrue(targets(loose.externalDependencies()).toString(), loose.externalDependencies().isEmpty());
    }

    /**
     * The analysed file's references equal those of whole-repository analysis, except a type reached
     * only by inference through a level-one type and declared past it: {@code this.make()} returns a
     * {@code C}, and {@code C} lives in a file the one-level program does not hold. That reference is
     * kept by name and not loaded.
     */
    @Test
    public void analysedReferencesMatchWholeRepositoryAnalysisBarInferredLevelTwoTypes() {
        final Set<String> expected = referencePairs(whole, List.of(A));
        final Set<String> actual = referencePairs(oneLevel.model(), List.of(A));
        final Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        final Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        assertEquals(Set.of("src.a.A -> src.deep.c.C", "src.a.A.run() -> src.deep.c.C"), missing);
        assertEquals(Set.of("src.a.A -> C", "src.a.A.run() -> C"), extra);
        assertTrue(targets(component(oneLevel.model(), "src.a.A.run()").notLoadedDependencies()).contains("C"));
    }

    @Test
    public void budgetHoldsLevelOneFilesBack() throws Exception {
        final CompileResult held = new ClarpseProject(project(repository()), Lang.TYPESCRIPT, List.of(A),
                AnalysisOptions.oneLevel().withLevelOneBudget(2)).result();
        assertEquals(2, held.levelOne().levelOneFiles().size());
        assertEquals(4, held.levelOne().heldByBudget().size());
        assertTrue(held.levelOne().budgetHit());
    }

    @Test
    public void levelOneFilesCanBeDiscoveredWithoutModelling() throws Exception {
        assertEquals(new TreeSet<>(Set.of(B, SHAPE, BARREL, IMPL, ALIASED, REMOTE)),
                new ClarpseProject(project(repository()), Lang.TYPESCRIPT, List.of(A),
                        AnalysisOptions.oneLevel()).levelOneFiles());
    }

    @Test
    public void defaultModeIsUnchangedByTheOptionsApi() throws Exception {
        final OOPSourceCodeModel plain = new ClarpseProject(project(repository()), Lang.TYPESCRIPT, List.of(A))
                .result().model();
        final OOPSourceCodeModel full = new ClarpseProject(project(repository()), Lang.TYPESCRIPT, List.of(A),
                AnalysisOptions.full()).result().model();
        assertEquals(json(plain), json(full));
        assertFalse(json(plain).contains("notLoaded"));
    }
}
