package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.BeforeClass;
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
 * One-level analysis of Python: level one is the set of modules declaring what the analysed file's
 * references name.
 */
public class PythonOneLevelTest {

    private static final String A = "/app/a.py";
    private static final String B = "/lib/b.py";
    private static final String SHAPE = "/lib/shape.py";
    private static final String C = "/deep/c.py";
    private static final String REMOTE = "/other/remote.py";
    private static final String PKG_INIT = "/pkg/__init__.py";
    private static final String RESPONSES = "/pkg/responses.py";
    private static final String TEXT = "/util/text.py";

    private static CompileResult oneLevel;
    private static OOPSourceCodeModel whole;

    static Map<String, String> repository() {
        return files(
                "/app/__init__.py", "",
                A, "from lib.b import B\n"
                        + "from lib.shape import Shape\n"
                        + "import other.remote\n"
                        + "from pkg import Redirect\n"
                        + "\n"
                        + "class A(B):\n"
                        + "    shape: Shape\n"
                        + "\n"
                        + "    def go(self) -> Redirect:\n"
                        + "        remote = other.remote.Remote()\n"
                        + "        return Redirect()\n"
                        + "\n"
                        + "    def title(self, s):\n"
                        + "        from util.text import capfirst\n"
                        + "        return capfirst(s)\n",
                "/lib/__init__.py", "",
                B, "from deep.c import C\n\nclass B:\n    c: C\n",
                SHAPE, "class Shape:\n    pass\n",
                "/deep/__init__.py", "",
                C, "class C:\n    pass\n",
                "/other/__init__.py", "",
                REMOTE, "class Remote:\n    pass\n",
                PKG_INIT, "from .responses import Redirect\n",
                RESPONSES, "class Redirect:\n    pass\n",
                "/util/__init__.py", "",
                TEXT, "def capfirst(x):\n    return x\n",
                "/app/unrelated.py", "class Unrelated:\n    pass\n");
    }

    @BeforeClass
    public static void compile() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        oneLevel = new ClarpseProject(project(repository()), Lang.PYTHON, List.of(A),
                AnalysisOptions.oneLevel()).result();
        whole = new ClarpseProject(project(repository()), Lang.PYTHON).result().model();
    }

    @Test
    public void levelOneIsTheModulesTheAnalysedFileResolvesInto() {
        assertEquals(new TreeSet<>(Set.of(B, SHAPE, REMOTE, RESPONSES, TEXT)),
                new TreeSet<>(oneLevel.levelOne().levelOneFiles()));
    }

    @Test
    public void levelOneComponentsAreBoundaryAndNothingBeyondIsModelled() {
        final OOPSourceCodeModel model = oneLevel.model();
        assertFalse(component(model, "app.a.A").isBoundary());
        assertTrue(component(model, "lib.b.B").isBoundary());
        assertTrue(component(model, "lib.shape.Shape").isBoundary());
        assertTrue(component(model, "other.remote.Remote").isBoundary());
        assertFalse(model.containsComponent("deep.c.C"));
        assertFalse(model.containsComponent("app.unrelated.Unrelated"));
    }

    @Test
    public void boundaryReferencesPastLevelOneAreNotLoadedNeverExternal() {
        final Component b = component(oneLevel.model(), "lib.b.B");
        assertTrue(targets(b.notLoadedDependencies()).toString(),
                targets(b.notLoadedDependencies()).contains("deep.c.C"));
        assertFalse(targets(b.externalDependencies()).contains("deep.c.C"));
    }

    @Test
    public void analysedReferencesEqualThoseOfWholeRepositoryAnalysis() {
        assertEquals(referencePairs(whole, List.of(A)), referencePairs(oneLevel.model(), List.of(A)));
        assertEquals(componentsOf(whole, List.of(B)), componentsOf(oneLevel.model(), List.of(B)));
    }

    /**
     * A name re-exported by a package's {@code __init__.py} resolves to the module declaring it, so
     * level one reaches {@code pkg/responses.py}, not the package module, and the reference is
     * internal.
     */
    @Test
    public void reexportedNamesReachTheDeclaringModule() {
        assertTrue(oneLevel.levelOne().levelOneFiles().contains(RESPONSES));
        assertFalse(oneLevel.levelOne().levelOneFiles().contains(PKG_INIT));
        final Set<String> internal = new TreeSet<>();
        oneLevel.model().components()
                .filter(c -> c.uniqueName().startsWith("app.a.A"))
                .forEach(c -> internal.addAll(targets(c.internalDependencies())));
        assertTrue(internal.toString(), internal.contains("pkg.responses.Redirect"));
    }

    /** A module-level function imported inside a function body is level one and its call is an edge. */
    @Test
    public void functionsImportedInsideBodiesAreLevelOne() {
        assertTrue(oneLevel.levelOne().levelOneFiles().contains(TEXT));
        final Set<String> internal = new TreeSet<>();
        oneLevel.model().components()
                .filter(c -> c.uniqueName().startsWith("app.a.A.title"))
                .forEach(c -> internal.addAll(targets(c.internalDependencies())));
        assertTrue(internal.toString(), internal.stream().anyMatch(name -> name.startsWith("util.text.capfirst")));
    }

    @Test
    public void budgetHoldsLevelOneFilesBack() throws Exception {
        final CompileResult held = new ClarpseProject(project(repository()), Lang.PYTHON, List.of(A),
                AnalysisOptions.oneLevel().withLevelOneBudget(1)).result();
        assertEquals(1, held.levelOne().levelOneFiles().size());
        assertEquals(4, held.levelOne().heldByBudget().size());
    }

    @Test
    public void levelOneFilesCanBeDiscoveredWithoutModelling() throws Exception {
        assertEquals(new TreeSet<>(Set.of(B, SHAPE, REMOTE, RESPONSES, TEXT)),
                new ClarpseProject(project(repository()), Lang.PYTHON, List.of(A), AnalysisOptions.oneLevel())
                        .levelOneFiles());
    }

    @Test
    public void defaultModeIsUnchangedByTheOptionsApi() throws Exception {
        final OOPSourceCodeModel plain = new ClarpseProject(project(repository()), Lang.PYTHON, List.of(A))
                .result().model();
        final OOPSourceCodeModel full = new ClarpseProject(project(repository()), Lang.PYTHON, List.of(A),
                AnalysisOptions.full()).result().model();
        assertEquals(json(plain), json(full));
    }
}
