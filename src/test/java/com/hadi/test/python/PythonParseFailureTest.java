package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.python.PythonDaemonException;
import org.junit.Assert;
import org.junit.Test;

public class PythonParseFailureTest {

    private static final String FIXTURE = "parse-failure";

    @Test
    public void testParseFailureIsFileScopedAndCompilationContinues() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);

        String goodName = PythonTestUtil.uniqueName("src", "good", "Good");
        Assert.assertTrue(result.model().containsComponent(goodName));
        Assert.assertEquals(1, result.failures().size());

        CompileFailure failure = result.failures().iterator().next();
        Assert.assertEquals(Integer.valueOf(PythonDaemonException.CODE_PARSE_FAILED), failure.errorCode());
        Assert.assertTrue(failure.file().path().endsWith("/src/bad.py")
                || failure.file().path().endsWith("\\src\\bad.py"));
    }
}
