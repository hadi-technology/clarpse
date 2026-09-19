package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.hadi.test.onelevel.OneLevelTestSupport.component;
import static com.hadi.test.onelevel.OneLevelTestSupport.componentsOf;
import static com.hadi.test.onelevel.OneLevelTestSupport.files;
import static com.hadi.test.onelevel.OneLevelTestSupport.json;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static com.hadi.test.onelevel.OneLevelTestSupport.referencePairs;
import static com.hadi.test.onelevel.OneLevelTestSupport.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One-level analysis of Java: the analysed file is modelled in full, the files it references are
 * modelled and marked boundary, and nothing past them is modelled.
 */
public class JavaOneLevelTest {

    private static final String A = "/core/src/main/java/app/A.java";
    private static final String B = "/core/src/main/java/lib/B.java";
    private static final String C = "/core/src/main/java/deep/C.java";
    private static final String S = "/core/src/main/java/app/S.java";
    private static final String OUTER = "/core/src/main/java/lib/Outer.java";
    private static final String HOLDER = "/core/src/main/java/lib/Holder.java";
    private static final String OTHER_MODULE = "/other/src/main/java/ext/Remote.java";
    private static final String UNRELATED = "/core/src/main/java/app/Unrelated.java";

    private static Map<String, String> repository() {
        return files(
                A, "package app;\n"
                        + "import lib.B;\nimport lib.Outer;\nimport ext.Remote;\nimport java.util.List;\n"
                        + "public class A extends B {\n"
                        + "  S sibling;\n"
                        + "  Outer.Inner inner;\n"
                        + "  lib.Hidden hidden;\n"
                        + "  Remote remote;\n"
                        + "  List<String> names;\n"
                        + "  void run() { sibling.go(); }\n"
                        + "}\n",
                B, "package lib;\nimport deep.C;\npublic class B {\n  C c;\n  public C make() { return c; }\n}\n",
                C, "package deep;\npublic class C { }\n",
                S, "package app;\npublic class S { public void go() { } }\n",
                OUTER, "package lib;\npublic class Outer { public static class Inner { } }\n",
                HOLDER, "package lib;\npublic class Holder { }\nclass Hidden { }\n",
                OTHER_MODULE, "package ext;\npublic class Remote { }\n",
                UNRELATED, "package app;\npublic class Unrelated { }\n");
    }

    private static CompileResult oneLevel(final ProjectFiles files, final AnalysisOptions options)
            throws Exception {
        return new ClarpseProject(files, Lang.JAVA, List.of(A), options).result();
    }

    @Test
    public void levelOneFilesAreModelledAsBoundaryAndNothingBeyond() throws Exception {
        final CompileResult result = oneLevel(project(repository()), AnalysisOptions.oneLevel());
        final OOPSourceCodeModel model = result.model();

        assertEquals(new TreeSet<>(Set.of(B, S, OUTER, HOLDER, OTHER_MODULE)),
                new TreeSet<>(result.levelOne().levelOneFiles()));
        assertFalse(component(model, "app.A").isBoundary());
        assertTrue(component(model, "lib.B").isBoundary());
        assertTrue(component(model, "app.S").isBoundary());
        assertTrue(component(model, "lib.Outer.Inner").isBoundary());
        assertTrue(component(model, "lib.Hidden").isBoundary());
        assertTrue(component(model, "ext.Remote").isBoundary());
        assertFalse(model.containsComponent("deep.C"));
        assertFalse(model.containsComponent("app.Unrelated"));
    }

    @Test
    public void analysedReferencesResolveToLevelOneComponents() throws Exception {
        final OOPSourceCodeModel model = oneLevel(project(repository()), AnalysisOptions.oneLevel()).model();
        final Component a = component(model, "app.A");
        final Set<String> internal = targets(a.internalDependencies());
        assertTrue(internal.toString(), internal.containsAll(
                Set.of("lib.B", "app.S", "lib.Outer.Inner", "lib.Hidden", "ext.Remote")));
        assertTrue(targets(a.externalDependencies()).contains("java.util.List"));
        assertTrue(a.notLoadedDependencies().isEmpty());
    }

    @Test
    public void boundaryReferencesPastLevelOneAreNotLoadedNeverExternal() throws Exception {
        final CompileResult result = oneLevel(project(repository()), AnalysisOptions.oneLevel());
        final Component b = component(result.model(), "lib.B");
        assertEquals(Set.of("deep.C"), targets(b.notLoadedDependencies()));
        assertFalse(targets(b.externalDependencies()).contains("deep.C"));
        b.notLoadedDependencies().forEach(ref -> {
            assertTrue(ref.isNotLoaded());
            assertFalse(ref.isExternal());
        });
        assertTrue(result.levelOne().notLoadedReferences() > 0);
    }

    @Test
    public void analysedReferencesEqualThoseOfWholeRepositoryAnalysis() throws Exception {
        final Map<String, String> repository = repository();
        final OOPSourceCodeModel oneLevel = oneLevel(project(repository), AnalysisOptions.oneLevel()).model();
        final OOPSourceCodeModel whole = new ClarpseProject(project(repository), Lang.JAVA).result().model();
        assertEquals(referencePairs(whole, List.of(A)), referencePairs(oneLevel, List.of(A)));
        assertEquals(componentsOf(whole, List.of(B)), componentsOf(oneLevel, List.of(B)));
    }

    @Test
    public void budgetHoldsBackFilesAndLeavesTheirReferencesNotLoaded() throws Exception {
        final CompileResult result = oneLevel(project(repository()),
                AnalysisOptions.oneLevel().withLevelOneBudget(0));
        assertTrue(result.levelOne().budgetHit());
        assertTrue(result.levelOne().levelOneFiles().isEmpty());
        assertEquals(5, result.levelOne().heldByBudget().size());
        final Component a = component(result.model(), "app.A");
        assertTrue(targets(a.notLoadedDependencies()).contains("lib.B"));
        assertFalse(result.model().containsComponent("lib.B"));
    }

    @Test
    public void unionOfRevisionsGivesBothCompilesTheSameLevelOneSet() throws Exception {
        final Map<String, String> base = repository();
        final Map<String, String> head = repository();
        head.put(A, "package app;\nimport lib.Outer;\npublic class A { Outer.Inner inner; deep.C c; }\n");
        final Set<String> union = new TreeSet<>();
        union.addAll(new ClarpseProject(project(base), Lang.JAVA, List.of(A), AnalysisOptions.oneLevel())
                .levelOneFiles());
        union.addAll(new ClarpseProject(project(head), Lang.JAVA, List.of(A), AnalysisOptions.oneLevel())
                .levelOneFiles());
        final AnalysisOptions options = AnalysisOptions.oneLevel().withLevelOnePaths(union);
        final CompileResult baseResult = oneLevel(project(base), options);
        final CompileResult headResult = oneLevel(project(head), options);
        assertEquals(baseResult.levelOne().levelOneFiles(), headResult.levelOne().levelOneFiles());
        assertTrue(headResult.levelOne().levelOneFiles().contains(B));
        assertTrue(baseResult.levelOne().levelOneFiles().contains(C));
    }

    @Test
    public void duplicateDeclarationsAreAllLevelOneInPathOrder() throws Exception {
        final Map<String, String> repository = repository();
        repository.put("/copy/src/main/java/lib/B.java", "package lib;\npublic class B { }\n");
        final CompileResult result = oneLevel(project(repository), AnalysisOptions.oneLevel());
        assertTrue(result.levelOne().levelOneFiles().contains(B));
        assertTrue(result.levelOne().levelOneFiles().contains("/copy/src/main/java/lib/B.java"));
    }

    @Test
    public void defaultModeIsUnchangedByTheOptionsApi() throws Exception {
        final Map<String, String> repository = repository();
        final OOPSourceCodeModel plain = new ClarpseProject(project(repository), Lang.JAVA, List.of(A))
                .result().model();
        final CompileResult withFull = new ClarpseProject(project(repository), Lang.JAVA, List.of(A),
                AnalysisOptions.full()).result();
        assertEquals(json(plain), json(withFull.model()));
        assertFalse(json(plain).contains("boundary"));
        assertFalse(json(plain).contains("notLoaded"));
        assertTrue(withFull.levelOne().levelOneFiles().isEmpty());
    }

    /**
     * A boundary file's method calls are attributed from the names the source writes: a type named
     * as the receiver, qualified or imported. A call on a variable ({@code c.go()}) is not attributed
     * to the variable's type, which only resolving the receiver would give; the field {@code c}
     * itself still references it. The targets lie past level one, so they are kept by name and not
     * loaded.
     */
    @Test
    public void boundaryMethodCallsAreAttributedFromWrittenNames() throws Exception {
        final Map<String, String> repository = files(
                A, "package app;\nimport lib.B;\npublic class A { B b; }\n",
                B, "package lib;\nimport deep.C;\nimport deep.D;\n"
                        + "public class B {\n"
                        + "  C c;\n"
                        + "  void run() { c.go(); deep.D.make(); D.make(); local(); }\n"
                        + "  void local() { }\n"
                        + "}\n",
                C, "package deep;\npublic class C { public void go() { } }\n",
                "/core/src/main/java/deep/D.java", "package deep;\npublic class D { public static D make() { return null; } }\n");
        final CompileResult result = oneLevel(project(repository), AnalysisOptions.oneLevel());
        final Component run = component(result.model(), "lib.B.run()");
        assertEquals(Set.of("deep.D"), targets(run.notLoadedDependencies()));
        assertEquals(Set.of("deep.C"), targets(component(result.model(), "lib.B.c").notLoadedDependencies()));
        assertTrue(Set.of(C, "/core/src/main/java/deep/D.java")
                .containsAll(result.levelOne().loadedBeyondLevelOne()));
        assertFalse(result.model().containsComponent("deep.C"));
        assertFalse(result.model().containsComponent("deep.D"));
    }

    @Test
    public void theLoadTrackerAdmitsRepeatsButNothingPastItsCap() {
        final com.hadi.clarpse.compiler.java.JavaLoadTracker tracker =
                new com.hadi.clarpse.compiler.java.JavaLoadTracker(1);
        assertTrue(tracker.admit("/a.java"));
        assertTrue(tracker.admit("/a.java"));
        assertFalse(tracker.admit("/b.java"));
        assertEquals(Set.of("/a.java"), tracker.loaded());
    }

    @Test
    public void aCompilerWithoutOneLevelSupportSaysSo() throws Exception {
        final com.hadi.clarpse.compiler.ClarpseCompiler plain = (files, paths) ->
                new CompileResult(new OOPSourceCodeModel());
        assertTrue(plain.compile(project(repository()), List.of(A), null).model().size() == 0);
        org.junit.Assert.assertThrows(UnsupportedOperationException.class,
                () -> plain.compile(project(repository()), List.of(A), AnalysisOptions.oneLevel()));
        org.junit.Assert.assertThrows(UnsupportedOperationException.class,
                () -> plain.levelOneFiles(project(repository()), List.of(A), AnalysisOptions.oneLevel()));
    }
}
