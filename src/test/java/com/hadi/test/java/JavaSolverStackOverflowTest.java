package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * A type the symbol solver cannot work out, because working it out exhausts the stack, adds nothing
 * and leaves the rest of the file parsed: it is not a failure of the file.
 */
public class JavaSolverStackOverflowTest {

    private static CompileResult compile(final String source) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/v/C.java", source));
        return new ClarpseProject(files, Lang.JAVA).result();
    }

    /** Deep enough that typing the sum overflows the solver's stack, shallower than the tree walk's own limit. */
    private static final int TERMS = 3000;

    @Test
    public void aVarInitializerTheSolverCannotTypeLeavesTheFileParsed() throws Exception {
        final StringBuilder sum = new StringBuilder("1");
        for (int i = 0; i < TERMS; i++) {
            sum.append(" + 1");
        }
        final CompileResult result = compile(
                "package v; public class C { void m() { var x = " + sum + "; } void other(D d) { } } class D { }");

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(result.model().copyOfComponent("v.C.other(D)").isPresent());
    }
}
