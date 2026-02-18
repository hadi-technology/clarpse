package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import org.junit.Assert;
import org.junit.Test;

public class PythonParallelismOverrideTest {

    private static final String PARALLELISM_PROP = "clarpse.python.parallelism";
    private static final String FIXTURE = "component-types";

    @Test
    public void testSerialOverrideParsesSuccessfully() throws Exception {
        CompileResult result = compileWithOverride("1");
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testParallelOverrideParsesSuccessfully() throws Exception {
        CompileResult result = compileWithOverride("2");
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testInvalidOverrideFallsBackAndParsesSuccessfully() throws Exception {
        CompileResult result = compileWithOverride("invalid");
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static CompileResult compileWithOverride(final String value) throws Exception {
        final String previous = System.getProperty(PARALLELISM_PROP);
        try {
            System.setProperty(PARALLELISM_PROP, value);
            return PythonTestUtil.compileFixture(FIXTURE);
        } finally {
            if (previous == null) {
                System.clearProperty(PARALLELISM_PROP);
            } else {
                System.setProperty(PARALLELISM_PROP, previous);
            }
        }
    }
}
