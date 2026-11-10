package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A C# file whose parse throws is that file's failure alone: it is reported as a parse failure, it
 * contributes no components, and the other files are modelled as usual.
 */
public class CSharpOneFileFailureTest {

    private static CompileResult compileWithBadFile(final RuntimeException runtime, final StackOverflowError overflow)
            throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/Bad.cs", "namespace Bad { public class Broken { } }"));
        files.insertFile(new ProjectFile("/Good.cs", "namespace Good { public class Fine { public int N() { return 1; } } }"));
        return new ClarpseCSharpCompiler((file, index, implicitNamespaces) -> {
            if (file.path().equals("/Bad.cs")) {
                if (runtime != null) {
                    throw runtime;
                }
                throw overflow;
            }
            return CSharpFileParser.parseFile(file, index, implicitNamespaces);
        }).compile(files, List.of("/Bad.cs", "/Good.cs"));
    }

    private static void assertBadFileFailedAlone(final CompileResult result) {
        assertTrue(result.model().components().anyMatch(c -> c.uniqueName().equals("Good.Fine")));
        assertTrue(result.model().components().anyMatch(c -> c.uniqueName().startsWith("Good.Fine.N")));
        assertTrue(result.model().components().noneMatch(c -> c.uniqueName().startsWith("Bad.")));
        assertEquals(1, result.failures().size());
        final var failure = result.failures().iterator().next();
        assertEquals("/Bad.cs", failure.file().path());
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), failure.errorCode());
    }

    @Test
    public void aFileThatThrowsFailsAloneAndTheOtherFileIsModelled() throws Exception {
        assertBadFileFailedAlone(compileWithBadFile(new IllegalStateException("boom"), null));
    }

    @Test
    public void aFileThatOverflowsTheStackFailsAloneAndTheOtherFileIsModelled() throws Exception {
        assertBadFileFailedAlone(compileWithBadFile(null, new StackOverflowError()));
    }
}
