package com.hadi.test.python;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.python.PythonDaemon;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.Assume;
import org.junit.Test;

/**
 * {@link PythonDaemon#forceStop()} must kill the daemon process — that is what unblocks a thread
 * parked in a daemon read when a parse is cancelled (#180). Node-gated, like the other daemon tests.
 */
public class PythonDaemonForceStopTest {

    @Test
    public void forceStopKillsTheDaemonProcess() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        try (PythonDaemon daemon = new PythonDaemon()) {
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
