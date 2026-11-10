package com.hadi.clarpse.compiler.python;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** A Python file whose model cannot be built is that file's failure alone. */
public class PythonOneFileModelFailureTest {

    private static final ProjectFile FILE = new ProjectFile("/bad.py", "x = 1");

    @Test
    public void aRuntimeExceptionIsTheFilesFailure() {
        final CompileFailure failure = ClarpsePythonCompiler.assembleIsolated(FILE, () -> {
            throw new IllegalStateException("boom");
        });
        assertSame(FILE, failure.file());
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), failure.errorCode());
        assertEquals("boom", failure.message());
    }

    @Test
    public void aStackOverflowIsTheFilesFailure() {
        final CompileFailure failure = ClarpsePythonCompiler.assembleIsolated(FILE, () -> {
            throw new StackOverflowError();
        });
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), failure.errorCode());
    }

    @Test
    public void aModelThatIsBuiltHasNoFailure() {
        assertNull(ClarpsePythonCompiler.assembleIsolated(FILE, () -> { }));
    }

    @Test
    public void anOutOfMemoryErrorIsNotCaught() {
        assertThrows(OutOfMemoryError.class, () -> ClarpsePythonCompiler.assembleIsolated(FILE, () -> {
            throw new OutOfMemoryError();
        }));
    }
}
