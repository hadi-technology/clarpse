package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptModelTest {

    @Test
    public void topLevelDeclarationsAreModeled() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("simple");
        assertTrue(result.model().containsComponent("src.index.Greeter"));
        assertTrue(result.model().containsComponent("src.utils.date.format"));
    }

    @Test
    public void moduleNamingAvoidsCollisions() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("simple");
        assertTrue(result.model().containsComponent("src.models.user.User"));
        assertTrue(result.model().containsComponent("src.models.user2.User"));
    }
}
