package com.hadi.test;

import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Compiling a Java project must not leave anything behind in JavaParser's static state.
 *
 * <p>{@code JavaParserFacade} keeps a {@code private static final Map<TypeSolver, JavaParserFacade>
 * instances}, populated by {@code JavaParserFacade.get(typeSolver)} during symbol resolution and
 * never pruned. Clarpse builds a fresh {@code CombinedTypeSolver} per thread per compile, so every
 * compile inserts up to {@code parallelism} entries that nothing ever removes. Each entry strongly
 * retains its solver, each solver retains the {@code JavaParserTypeSolver} caches of parsed
 * {@code CompilationUnit}s, and each compilation unit retains a whole AST. In a JVM that compiles
 * one project and exits this is invisible; in a long-lived process that compiles many, it is an
 * unbounded leak of every AST the process has ever parsed.
 *
 * <p>The leak is measured here rather than inferred, because what makes it hard to observe in
 * production is that it does not announce itself. The retaining caches hold their values softly,
 * and a JVM must clear every soft reference before it is allowed to throw {@code OutOfMemoryError}
 * — so instead of failing, the process settles into near-continuous full GC, collecting and
 * re-resolving forever. Heap-dump-on-OOM and exit-on-OOM never fire, because no OOM is ever thrown.
 *
 * <p>The assertions are on the map's size, not on heap usage: a heap-based test would have to
 * distinguish a leak from ordinary garbage awaiting collection, which is precisely the ambiguity
 * that let this survive.
 */
public class JavaParserFacadeLeakTest {

    /** A Java project of non-trivial size, so several parser threads and solvers are in play. */
    private static final String JAVA_PROJECT_ZIP = "/clarpse.zip";

    @Before
    public void clearFacadeState() {
        JavaParserFacade.clearInstances();
    }

    /**
     * Reads the private static registry directly. Reflection is the point of the test: the field is
     * the leak, and any assertion that did not look at it would be measuring a proxy.
     */
    private static int facadeInstanceCount() throws Exception {
        final Field field = JavaParserFacade.class.getDeclaredField("instances");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    private static void compileJavaProject() throws Exception {
        final ProjectFiles projectFiles =
                new ProjectFiles(JavaParserFacadeLeakTest.class.getResourceAsStream(JAVA_PROJECT_ZIP));
        new ClarpseProject(projectFiles, Lang.JAVA).result();
    }

    /**
     * Guards the other two tests. They assert a count of zero, which is also what a broken accessor
     * or a renamed field would report — so without this, they would pass for the wrong reason and
     * keep passing forever. This pins the mechanism itself: registering a solver grows the map, and
     * clearing empties it.
     */
    @Test
    public void theRegistryThisTestReadsIsTheOneResolutionPopulates() throws Exception {
        assertEquals("clearInstances() should leave an empty registry", 0, facadeInstanceCount());

        JavaParserFacade.get(new ReflectionTypeSolver());
        assertTrue("JavaParserFacade.get() should register the solver; if this fails the field name "
                        + "or the registry's behaviour has changed and the leak assertions below "
                        + "are vacuous",
                facadeInstanceCount() > 0);

        JavaParserFacade.clearInstances();
        assertEquals(0, facadeInstanceCount());
    }

    @Test
    public void oneCompileLeavesNoFacadeInstancesBehind() throws Exception {
        compileJavaProject();

        assertEquals("compiling a project must not leave entries in JavaParserFacade.instances",
                0, facadeInstanceCount());
    }

    @Test
    public void repeatedCompilesDoNotAccumulateFacadeInstances() throws Exception {
        compileJavaProject();
        final int afterFirstCompile = facadeInstanceCount();

        for (int i = 0; i < 3; i++) {
            compileJavaProject();
        }
        final int afterFourthCompile = facadeInstanceCount();

        assertEquals("JavaParserFacade.instances grew across compiles: " + afterFirstCompile
                        + " -> " + afterFourthCompile + "; each retained entry pins a "
                        + "CombinedTypeSolver and every AST it parsed",
                afterFirstCompile, afterFourthCompile);
        assertEquals("expected a clean registry after each compile", 0, afterFourthCompile);
    }
}
