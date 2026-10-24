package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * A file's imports are found wherever they are written: in conditional and exception blocks,
 * context managers and loops, and in function and method bodies, at any depth.
 */
public class PythonNestedImportTest {

    private static final String MODULE = "pkg.blocks";
    private static Set<String> imports;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture("nested-imports");
        final OOPSourceCodeModel model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
        imports = new HashSet<>(model.copyOfComponent(MODULE + ".C").orElseThrow().imports());
    }

    private static void assertImported(final String name) {
        Assert.assertTrue(name + " missing from " + imports, imports.contains(name));
    }

    @Test
    public void topLevelImportIsStillFound() {
        assertImported("os");
        assertImported("typing.TYPE_CHECKING");
    }

    @Test
    public void importInIfIsFound() {
        assertImported("requests");
    }

    @Test
    public void importInTryIsFound() {
        assertImported("fcntl");
    }

    @Test
    public void importUnderTypeCheckingIsFound() {
        assertImported("pkg.helpers.Helper");
    }

    @Test
    public void importInWithForWhileAndLoopElseIsFound() {
        assertImported("csv");
        assertImported("glob");
        assertImported("shlex");
        assertImported("heapq");
    }

    @Test
    public void importInFunctionBodyIsFound() {
        assertImported("json");
        assertImported("os.path");
    }

    @Test
    public void importInMethodBodyIsFound() {
        assertImported("urllib.request");
    }

    @Test
    public void importInClassBodyIsFound() {
        assertImported("bisect");
    }

    @Test
    public void importTwoLevelsDeepInAFunctionIsFound() {
        assertImported("pkg.deep.Deep");
    }

    @Test
    public void relativeImportInAFunctionIsResolved() {
        assertImported("pkg.sibling");
    }
}
