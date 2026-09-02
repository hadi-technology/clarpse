package com.hadi.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.InterruptWatchdog;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * The watchdog that unblocks a daemon read when the owning thread is interrupted (#180). No node
 * needed — this tests the cancellation trigger in isolation.
 */
public class InterruptWatchdogTest {

    @Test
    public void firesActionWhenOwnerIsInterrupted() throws Exception {
        final AtomicBoolean fired = new AtomicBoolean(false);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch fireLatch = new CountDownLatch(1);
        final CountDownLatch ownerReady = new CountDownLatch(1);
        // Park in a way that leaves the interrupt flag SET after interrupt (unlike Thread.sleep,
        // which clears it on InterruptedException). This mirrors a blocking readLine() that ignores
        // the interrupt entirely — the exact case the watchdog exists for.
        final Thread owner = new Thread(() -> {
            ownerReady.countDown();
            while (!stop.get()) {
                LockSupport.parkNanos(20_000_000L);
            }
        }, "watchdog-owner");
        owner.start();
        assertTrue(ownerReady.await(2, TimeUnit.SECONDS));

        try (InterruptWatchdog watchdog = new InterruptWatchdog(owner, () -> {
            fired.set(true);
            fireLatch.countDown();
        })) {
            owner.interrupt();
            assertTrue("watchdog must run the action once the owner is interrupted",
                    fireLatch.await(3, TimeUnit.SECONDS));
            assertTrue(fired.get());
        } finally {
            stop.set(true);
            owner.interrupt();
            owner.join(2000);
        }
    }

    @Test
    public void doesNotFireWhileOwnerIsNotInterrupted() throws Exception {
        final AtomicBoolean fired = new AtomicBoolean(false);
        final Thread owner = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException ignored) {
                // fine
            }
        }, "watchdog-owner-quiet");
        owner.start();
        try (InterruptWatchdog watchdog = new InterruptWatchdog(owner, () -> fired.set(true))) {
            Thread.sleep(500);
            assertFalse("watchdog must not fire for an uninterrupted owner", fired.get());
        } finally {
            owner.join(2000);
        }
    }
}
