package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemon;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TypeScriptDaemonTest {

    @Test
    public void daemonInitRepoOnFixture() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path repoRoot = Paths.get("src/test/resources/typescript/simple").toAbsolutePath();
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            TypeScriptDaemon.InitResult result = daemon.initRepo(repoRoot.toString());
            assertTrue(result.configCount() > 0);
        } catch (TypeScriptDaemonException e) {
            if (e.code() == TypeScriptDaemonException.CODE_TYPESCRIPT_NOT_FOUND) {
                Assume.assumeTrue("TypeScript not available for daemon test.", false);
            }
            throw e;
        }
    }
}
