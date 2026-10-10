package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
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
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static com.hadi.test.onelevel.OneLevelTestSupport.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One-level analysis of a Kotlin repository: the analysed file in full, the Kotlin files declaring
 * what it references as boundary, and every other repository type, Kotlin or Java, a reference
 * that is not loaded rather than an external one.
 */
public class KotlinOneLevelTest {

    private static final String A = "/app/A.kt";
    private static final String B = "/lib/B.kt";
    private static final String SHAPE = "/lib/Shape.kt";
    private static final String OUTER = "/lib/Outer.kt";
    private static final String UTIL = "/lib/util.kt";
    private static final String C = "/deep/C.kt";
    private static final String UNRELATED = "/app/Unrelated.kt";
    private static final String JAVA_THING = "/java/lib/JavaThing.java";

    private static CompileResult oneLevel;
    private static OOPSourceCodeModel whole;

    static Map<String, String> repository() {
        return files(
                A, String.join("\n",
                        "package app",
                        "import lib.B",
                        "import lib.Shape",
                        "import lib.Outer",
                        "import lib.helper",
                        "class A : B() {",
                        "    val shape: Shape? = null",
                        "    val inner: Outer.Inner? = null",
                        "    val thing: lib.JavaThing? = null",
                        "    fun go(): Int = helper()",
                        "}"),
                B, "package lib\nimport deep.C\nopen class B { val c: C? = null }\n",
                SHAPE, "package lib\nclass Shape\n",
                OUTER, "package lib\nclass Outer { class Inner }\n",
                UTIL, "package lib\nfun helper(): Int = 1\n",
                C, "package deep\nclass C\n",
                UNRELATED, "package app\nclass Unrelated\n",
                JAVA_THING, "package lib;\npublic class JavaThing { }\n");
    }

    @BeforeClass
    public static void compile() throws Exception {
        oneLevel = new ClarpseProject(project(repository()), Lang.KOTLIN, List.of(A),
                AnalysisOptions.oneLevel()).result();
        whole = new ClarpseProject(project(repository()), Lang.KOTLIN).result().model();
    }

    @Test
    public void levelOneIsTheKotlinFilesDeclaringWhatTheAnalysedFileReferences() {
        assertEquals(new TreeSet<>(Set.of(B, SHAPE, OUTER, UTIL)),
                new TreeSet<>(oneLevel.levelOne().levelOneFiles()));
        assertTrue(oneLevel.failures().isEmpty());
    }

    @Test
    public void theAnalysedFileIsModelledInFullAndLevelOneIsBoundary() {
        assertFalse(component(oneLevel.model(), "app.A").isBoundary());
        assertTrue(component(oneLevel.model(), "lib.B").isBoundary());
        assertTrue(component(oneLevel.model(), "lib.Outer.Inner").isBoundary());
        assertTrue(component(oneLevel.model(), "lib.UtilKt").isBoundary());
        assertFalse(oneLevel.model().containsComponent("deep.C"));
        assertFalse(oneLevel.model().containsComponent("app.Unrelated"));
    }

    @Test
    public void theAnalysedFilesReferencesResolveAsInAWholeCompile() {
        assertEquals(targets(component(whole, "app.A").references()),
                targets(component(oneLevel.model(), "app.A").references()));
        assertTrue(targets(component(oneLevel.model(), "app.A").internalDependencies())
                .containsAll(Set.of("lib.B", "lib.Shape", "lib.Outer.Inner", "lib.UtilKt")));
    }

    @Test
    public void aRepositoryTypeOutsideTheModelIsNotLoadedWhateverItsLanguage() {
        final Component a = component(oneLevel.model(), "app.A");
        assertTrue(targets(a.notLoadedDependencies()).contains("lib.JavaThing"));
        assertTrue(targets(component(oneLevel.model(), "lib.B").notLoadedDependencies()).contains("deep.C"));
        assertTrue(targets(a.externalDependencies()).contains("kotlin.Int"));
        assertFalse(targets(a.externalDependencies()).contains("lib.JavaThing"));
    }
}
