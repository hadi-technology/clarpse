package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

/** A TypeScript file whose model cannot be built is that file's failure alone. */
public class TypeScriptOneFileModelFailureTest {

    private static final ProjectFile FILE = new ProjectFile("/bad.ts", "export const x = 1;");

    @Test
    public void aRuntimeExceptionIsTheFilesFailure() {
        final Set<CompileFailure> failures = new HashSet<>();
        assertFalse(ClarpseTypeScriptCompiler.assembleIsolated(FILE, failures, () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(1, failures.size());
        final CompileFailure failure = failures.iterator().next();
        assertEquals("/bad.ts", failure.file().path());
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), failure.errorCode());
    }

    @Test
    public void aStackOverflowIsTheFilesFailure() {
        final Set<CompileFailure> failures = new HashSet<>();
        assertFalse(ClarpseTypeScriptCompiler.assembleIsolated(FILE, failures, () -> {
            throw new StackOverflowError();
        }));
        assertEquals(1, failures.size());
    }

    @Test
    public void aModelThatIsBuiltRecordsNoFailure() {
        final Set<CompileFailure> failures = new HashSet<>();
        assertTrue(ClarpseTypeScriptCompiler.assembleIsolated(FILE, failures, () -> { }));
        assertTrue(failures.isEmpty());
    }

    @Test
    public void anOutOfMemoryErrorIsNotCaught() {
        assertThrows(OutOfMemoryError.class, () -> ClarpseTypeScriptCompiler.assembleIsolated(FILE,
                new HashSet<>(), () -> {
                    throw new OutOfMemoryError();
                }));
    }
}
