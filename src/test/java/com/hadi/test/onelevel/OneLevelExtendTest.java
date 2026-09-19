package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.PreparedAnalysis;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.hadi.test.onelevel.OneLevelTestSupport.json;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Extending a prepared analysis with more analysed files gives exactly the analysis a fresh
 * preparation of all of them gives, in every language: the same model, boundary flags, reference
 * sets, level-one set, budget report and failures.
 */
public class OneLevelExtendTest {

    private static final String JAVA_A = "/core/src/main/java/app/A.java";
    private static final String JAVA_B = "/core/src/main/java/lib/B.java";
    private static final String JAVA_UNRELATED = "/core/src/main/java/app/Unrelated.java";

    private static final String PY_A = "/app/a.py";
    private static final String PY_B = "/lib/b.py";
    private static final String PY_UNRELATED = "/app/unrelated.py";

    private static final String TS_A = "/src/a.ts";
    private static final String TS_B = "/src/b.ts";
    private static final String TS_UNRELATED = "/src/unrelated.ts";

    private static final String CS_A = "/Core/A.cs";
    private static final String CS_B = "/Lib/B.cs";
    private static final String CS_UNRELATED = "/Unrelated.cs";

    /** One language's fixture: its repository and three of its files. */
    private record Fixture(Lang lang, Map<String, String> repository, String analysed, String levelOne,
                           String unrelated) {
    }

    private static List<Fixture> fixtures() {
        final List<Fixture> fixtures = new ArrayList<>();
        fixtures.add(new Fixture(Lang.JAVA, JavaOneLevelTest.repository(), JAVA_A, JAVA_B, JAVA_UNRELATED));
        fixtures.add(new Fixture(Lang.CSHARP, CSharpOneLevelTest.repository(), CS_A, CS_B, CS_UNRELATED));
        if (NodeRuntime.isNodeAvailable()) {
            fixtures.add(new Fixture(Lang.PYTHON, PythonOneLevelTest.repository(), PY_A, PY_B, PY_UNRELATED));
            fixtures.add(new Fixture(Lang.TYPESCRIPT, TypeScriptOneLevelTest.repository(), TS_A, TS_B,
                    TS_UNRELATED));
        }
        return fixtures;
    }

    @Test
    public void extendingWithAnUnrelatedFileEqualsAFreshPreparation() throws Exception {
        for (final Fixture fixture : fixtures()) {
            assertExtendedEqualsFresh(fixture, AnalysisOptions.oneLevel(), List.of(fixture.analysed()),
                    List.of(fixture.unrelated()));
        }
    }

    @Test
    public void aLevelOneFilePromotedToAnalysedIsModelledInFullAndNoLongerBoundary() throws Exception {
        for (final Fixture fixture : fixtures()) {
            final CompileResult extended = assertExtendedEqualsFresh(fixture, AnalysisOptions.oneLevel(),
                    List.of(fixture.analysed()), List.of(fixture.levelOne()));
            assertFalse(fixture.lang() + ": promoted file is still level one",
                    extended.levelOne().levelOneFiles().contains(fixture.levelOne()));
            final OOPSourceCodeModel model = extended.model();
            final List<Component> promoted = model.components()
                    .filter(c -> fixture.levelOne().equals(c.sourceFile())).toList();
            assertFalse(fixture.lang() + ": promoted file has no components", promoted.isEmpty());
            promoted.forEach(c -> assertFalse(fixture.lang() + ": " + c.uniqueName() + " is still boundary",
                    c.isBoundary()));
        }
    }

    @Test
    public void theBudgetAppliesToTheWholeLevelOneSetAcrossPasses() throws Exception {
        for (final Fixture fixture : fixtures()) {
            final CompileResult extended = assertExtendedEqualsFresh(fixture,
                    AnalysisOptions.oneLevel().withLevelOneBudget(1), List.of(fixture.analysed()),
                    List.of(fixture.levelOne()));
            assertTrue(fixture.lang() + ": the budget should hold files back", extended.levelOne().budgetHit());
            assertEquals(1, extended.levelOne().levelOneFiles().size());
        }
    }

    @Test
    public void aPerCompileBudgetEqualsPreparingWithThatBudget() throws Exception {
        for (final Fixture fixture : fixtures()) {
            final CompileResult expected;
            try (PreparedAnalysis narrow = prepare(fixture, AnalysisOptions.oneLevel().withLevelOneBudget(1),
                    List.of(fixture.analysed()))) {
                expected = narrow.compile(null);
            }
            assertTrue(fixture.lang() + ": the budget should hold files back", expected.levelOne().budgetHit());
            try (PreparedAnalysis wide = prepare(fixture, AnalysisOptions.oneLevel(), List.of(fixture.analysed()))) {
                final CompileResult actual = wide.compile(null, 1);
                assertEquals(fixture.lang() + " model", json(expected.model()), json(actual.model()));
                assertEquals(fixture.lang() + " level one", expected.levelOne().levelOneFiles(),
                        actual.levelOne().levelOneFiles());
                assertEquals(fixture.lang() + " held by budget", expected.levelOne().heldByBudget(),
                        actual.levelOne().heldByBudget());
                assertFalse(fixture.lang() + ": a plain compile keeps the prepared budget",
                        wide.compile(null).levelOne().budgetHit());
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativePerCompileBudgetIsRejected() throws Exception {
        final Fixture fixture = fixtures().get(0);
        try (PreparedAnalysis prepared = prepare(fixture, AnalysisOptions.oneLevel(), List.of(fixture.analysed()))) {
            prepared.compile(null, -1);
        }
    }

    @Test
    public void extendingWithFilesAlreadyAnalysedOrOfAnotherLanguageChangesNothing() throws Exception {
        for (final Fixture fixture : fixtures()) {
            try (PreparedAnalysis prepared = prepare(fixture, AnalysisOptions.oneLevel(),
                    List.of(fixture.analysed()))) {
                final Set<String> before = prepared.levelOneFiles();
                final String modelBefore = json(prepared.compile(null).model());
                prepared.extendFocus(List.of(fixture.analysed(), "/no/such/File.txt"));
                prepared.extendFocus(null);
                assertEquals(before, prepared.levelOneFiles());
                assertEquals(modelBefore, json(prepared.compile(null).model()));
            }
        }
    }

    @Test
    public void twoPassesOverTwoRevisionsEqualAFreshUnionOfBoth() throws Exception {
        for (final Fixture fixture : fixtures()) {
            final Map<String, String> base = fixture.repository();
            final Map<String, String> head = fixture.repository();
            head.remove(fixture.unrelated());
            final List<String> first = List.of(fixture.analysed());
            final List<String> more = List.of(fixture.levelOne(), fixture.unrelated());
            final CompileResult[] twoPass;
            try (PreparedAnalysis baseAnalysis = prepareOn(base, fixture.lang(), first);
                 PreparedAnalysis headAnalysis = prepareOn(head, fixture.lang(), first)) {
                final Set<String> union = union(baseAnalysis, headAnalysis);
                baseAnalysis.compile(union);
                headAnalysis.compile(union);
                baseAnalysis.extendFocus(more);
                headAnalysis.extendFocus(more);
                final Set<String> union2 = union(baseAnalysis, headAnalysis);
                twoPass = new CompileResult[] {baseAnalysis.compile(union2), headAnalysis.compile(union2)};
            }
            final List<String> all = new ArrayList<>(first);
            all.addAll(more);
            final CompileResult[] fresh = OneLevelTestSupport.unionCompile(base, head, fixture.lang(), all);
            assertSame(fixture.lang() + " base", fresh[0], twoPass[0]);
            assertSame(fixture.lang() + " head", fresh[1], twoPass[1]);
            assertEquals(twoPass[0].levelOne().levelOneFiles(), twoPass[1].levelOne().levelOneFiles());
        }
    }

    @Test
    public void extendingAClosedAnalysisThrows() throws Exception {
        final Fixture fixture = fixtures().get(0);
        final PreparedAnalysis prepared = prepare(fixture, AnalysisOptions.oneLevel(), List.of(fixture.analysed()));
        prepared.close();
        try {
            prepared.extendFocus(List.of(fixture.levelOne()));
            throw new AssertionError("extending a closed analysis should throw");
        } catch (final IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("closed"));
        }
    }

    @Test
    public void anAnalysisPreparedWithNoFilesCanBeExtended() throws Exception {
        for (final Fixture fixture : fixtures()) {
            final CompileResult extended;
            try (PreparedAnalysis prepared = new ClarpseProject(project(fixture.repository()), fixture.lang(),
                    List.of("/not/a/file/of/this/language.txt"), AnalysisOptions.oneLevel()).prepare()) {
                assertTrue(prepared.levelOneFiles().isEmpty());
                prepared.extendFocus(List.of(fixture.analysed()));
                extended = prepared.compile(null);
            }
            assertSame(fixture.lang().value(), fresh(fixture, AnalysisOptions.oneLevel(),
                    List.of(fixture.analysed())), extended);
        }
    }

    private static CompileResult assertExtendedEqualsFresh(final Fixture fixture, final AnalysisOptions options,
                                                          final List<String> first, final List<String> more)
            throws Exception {
        final CompileResult extended;
        final Set<String> extendedLevelOne;
        try (PreparedAnalysis prepared = prepare(fixture, options, first)) {
            prepared.compile(null);
            prepared.extendFocus(more);
            extendedLevelOne = prepared.levelOneFiles();
            extended = prepared.compile(null);
        }
        final List<String> all = new ArrayList<>(first);
        all.addAll(more);
        final Set<String> freshLevelOne;
        try (PreparedAnalysis prepared = prepare(fixture, options, all)) {
            freshLevelOne = prepared.levelOneFiles();
        }
        assertEquals(fixture.lang() + " level-one set", freshLevelOne, extendedLevelOne);
        assertSame(fixture.lang().value(), fresh(fixture, options, all), extended);
        return extended;
    }

    private static CompileResult fresh(final Fixture fixture, final AnalysisOptions options, final List<String> all)
            throws Exception {
        try (PreparedAnalysis prepared = prepare(fixture, options, all)) {
            return prepared.compile(null);
        }
    }

    private static PreparedAnalysis prepare(final Fixture fixture, final AnalysisOptions options,
                                            final List<String> analysed) throws Exception {
        return new ClarpseProject(project(fixture.repository()), fixture.lang(), analysed, options).prepare();
    }

    private static PreparedAnalysis prepareOn(final Map<String, String> files, final Lang lang,
                                              final List<String> analysed) throws Exception {
        return new ClarpseProject(project(files), lang, analysed, AnalysisOptions.oneLevel()).prepare();
    }

    private static Set<String> union(final PreparedAnalysis left, final PreparedAnalysis right) {
        final Set<String> union = new TreeSet<>(left.levelOneFiles());
        union.addAll(right.levelOneFiles());
        return union;
    }

    /** Asserts two compile results are the same analysis, component by component. */
    private static void assertSame(final String label, final CompileResult expected, final CompileResult actual)
            throws Exception {
        assertEquals(label + " model", json(expected.model()), json(actual.model()));
        assertEquals(label + " reference sets", referenceSets(expected.model()), referenceSets(actual.model()));
        assertEquals(label + " level one", expected.levelOne().levelOneFiles(), actual.levelOne().levelOneFiles());
        assertEquals(label + " held by budget", expected.levelOne().heldByBudget(),
                actual.levelOne().heldByBudget());
        assertEquals(label + " not loaded", expected.levelOne().notLoadedReferences(),
                actual.levelOne().notLoadedReferences());
        assertEquals(label + " failures", failures(expected), failures(actual));
    }

    private static Map<String, String> referenceSets(final OOPSourceCodeModel model) {
        final Map<String, String> sets = new TreeMap<>();
        model.components().forEach(c -> sets.put(c.uniqueName(), "boundary=" + c.isBoundary()
                + " internal=" + targets(c.internalDependencies())
                + " notLoaded=" + targets(c.notLoadedDependencies())
                + " external=" + targets(c.externalDependencies())));
        return sets;
    }

    private static Set<String> targets(final Iterable<ComponentReference> references) {
        final Set<String> targets = new TreeSet<>();
        references.forEach(r -> targets.add(r.invokedComponent()));
        return targets;
    }

    private static Set<String> failures(final CompileResult result) {
        final Set<String> failures = new TreeSet<>();
        for (final CompileFailure failure : result.failures()) {
            failures.add(failure.file().path() + " " + failure.errorCode() + " " + failure.message());
        }
        return failures;
    }

    @Test
    public void everyLanguageHasAFixture() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        assertEquals(4, fixtures().size());
    }
}
