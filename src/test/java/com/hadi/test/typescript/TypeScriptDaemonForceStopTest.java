package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemon;
import org.junit.Assume;
import org.junit.Test;

/**
 * {@link TypeScriptDaemon#forceStop()} must kill the daemon process — that is what unblocks the
 * analysis thread when a TypeScript parse is cancelled (#180). Node-gated, like the other daemon
 * tests.
 */
public class TypeScriptDaemonForceStopTest {

    @Test
    public void forceStopKillsTheDaemonProcess() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            assertTrue("daemon should be alive after start()", daemon.isProcessAlive());

            daemon.forceStop();

            final long deadline = System.currentTimeMillis() + 5000;
            while (daemon.isProcessAlive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertFalse("forceStop() should kill the daemon process", daemon.isProcessAlive());
        }
    }
}
