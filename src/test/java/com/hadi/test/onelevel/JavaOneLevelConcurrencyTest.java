package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.PreparedAnalysis;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hadi.test.onelevel.OneLevelTestSupport.json;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A one-level Java compile parses on several threads that share the units their solvers read. Run
 * repeatedly over files that all reference one another, so every thread's solver reads units other
 * threads walk and resolve into, it must produce the same model every time and never fail.
 */
public class JavaOneLevelConcurrencyTest {

    private static final int PACKAGES = 4;
    private static final int CLASSES_PER_PACKAGE = 12;
    private static final int RUNS = 20;

    private static String name(final int pkg, final int cls) {
        return "C" + pkg + "x" + cls;
    }

    private static String path(final int pkg, final int cls) {
        return "/src/main/java/p" + pkg + "/" + name(pkg, cls) + ".java";
    }

    /** Every class extends another, holds fields of several, and calls methods and statics on them. */
    private static Map<String, String> repository() {
        final Map<String, String> files = new LinkedHashMap<>();
        for (int pkg = 0; pkg < PACKAGES; pkg++) {
            for (int cls = 0; cls < CLASSES_PER_PACKAGE; cls++) {
                final StringBuilder body = new StringBuilder();
                body.append("package p").append(pkg).append(";\n");
                for (int other = 0; other < PACKAGES; other++) {
                    if (other != pkg) {
                        body.append("import p").append(other).append(".*;\n");
                    }
                }
                final int basePkg = (pkg + 1) % PACKAGES;
                final int baseCls = (cls + 1) % CLASSES_PER_PACKAGE;
                body.append("public class ").append(name(pkg, cls));
                if (cls % 3 != 0) {
                    body.append(" extends ").append(name(basePkg, baseCls));
                }
                body.append(" {\n");
                final List<String> referenced = new ArrayList<>();
                for (int step = 1; step <= 5; step++) {
                    referenced.add(name((pkg + step) % PACKAGES, (cls * 7 + step * 5) % CLASSES_PER_PACKAGE));
                }
                for (int i = 0; i < referenced.size(); i++) {
                    body.append("  ").append(referenced.get(i)).append(" f").append(i).append(";\n");
                }
                body.append("  public ").append(name(pkg, cls)).append(" self() { return this; }\n");
                body.append("  public static ").append(name(pkg, cls)).append(" make() { return null; }\n");
                body.append("  public Object run() {\n");
                for (int i = 0; i < referenced.size(); i++) {
                    body.append("    f").append(i).append(".self().run();\n");
                    body.append("    ").append(referenced.get(i)).append(".make();\n");
                }
                body.append("    return new ").append(referenced.get(0)).append("();\n  }\n}\n");
                files.put(path(pkg, cls), body.toString());
            }
        }
        return files;
    }

    private static List<String> analysed() {
        final List<String> analysed = new ArrayList<>();
        for (int pkg = 0; pkg < PACKAGES; pkg++) {
            for (int cls = 0; cls < CLASSES_PER_PACKAGE; cls += 3) {
                analysed.add(path(pkg, cls));
            }
        }
        return analysed;
    }

    @Test
    public void repeatedParallelCompilesProduceTheSameModel() throws Exception {
        Assume.assumeTrue("needs more than one parser thread", Runtime.getRuntime().availableProcessors() > 1);
        final Map<String, String> repository = repository();
        final List<String> analysed = analysed();
        String expected = null;
        for (int run = 0; run < RUNS; run++) {
            final String actual;
            if (run % 2 == 0) {
                actual = json(new ClarpseProject(project(repository), Lang.JAVA, analysed,
                        AnalysisOptions.oneLevel()).result().model());
            } else {
                try (PreparedAnalysis prepared = new ClarpseProject(project(repository), Lang.JAVA, analysed,
                        AnalysisOptions.oneLevel()).prepare()) {
                    final CompileResult result = prepared.compile(prepared.levelOneFiles());
                    assertTrue(result.failures().toString(), result.failures().isEmpty());
                    actual = json(result.model());
                }
            }
            if (expected == null) {
                expected = actual;
                assertTrue(expected.contains("p1.C1x2"));
            } else {
                assertEquals("run " + run + " differs from run 0", expected, actual);
            }
        }
    }

    /** The analysed files' references, resolved in parallel, equal whole-repository analysis's. */
    @Test
    public void parallelOneLevelReferencesEqualWholeRepositoryAnalysis() throws Exception {
        Assume.assumeTrue(Runtime.getRuntime().availableProcessors() > 1);
        final Map<String, String> repository = repository();
        final List<String> analysed = analysed();
        final CompileResult oneLevel = new ClarpseProject(project(repository), Lang.JAVA, analysed,
                AnalysisOptions.oneLevel()).result();
        assertTrue(oneLevel.levelOne().levelOneFiles().size() > analysed.size());
        assertEquals(OneLevelTestSupport.referencePairs(
                        new ClarpseProject(project(repository), Lang.JAVA).result().model(), analysed),
                OneLevelTestSupport.referencePairs(oneLevel.model(), analysed));
    }
}
