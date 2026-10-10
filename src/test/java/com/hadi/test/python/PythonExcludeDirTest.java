package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class PythonExcludeDirTest {

    private static final String FIXTURE = "large-deps";
    private static OOPSourceCodeModel model;
    private static Collection<CompileFailure> failures;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        failures = result.failures();
    }

    @Test
    public void testExcludedVenvFilesNotParsed() {
        String appName = PythonTestUtil.uniqueName("src", "app", "App");
        Assert.assertTrue(model.containsComponent(appName));
        String ignoredName = PythonTestUtil.uniqueName(".venv/lib/site-packages", "ignored", "Ignored");
        Assert.assertFalse(model.containsComponent(ignoredName));
    }

    /** The excluded file is reported as not analysed, and is the only failure. */
    @Test
    public void testExcludedVenvFileIsRecordedAsExcluded() {
        Assert.assertEquals(failures.toString(), 1, failures.size());
        CompileFailure failure = failures.iterator().next();
        Assert.assertEquals(Integer.valueOf(FailureCode.FILE_EXCLUDED), failure.errorCode());
        Assert.assertTrue(failure.file().path(),
                failure.file().path().endsWith(".venv/lib/site-packages/ignored.py"));
    }
}
