package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptMonorepoTest {

    @Test
    public void multipleTsconfigsAreHandled() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("monorepo");
        assertTrue(result.model().containsComponent("packages.a.src.foo.Foo"));
        assertTrue(result.model().containsComponent("packages.b.src.bar.Bar"));
    }
}
