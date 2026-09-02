package com.hadi.test;

import static org.junit.Assert.fail;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.java.ParseTask;
import com.hadi.clarpse.compiler.java.ParserContext;
import org.junit.Test;

import java.util.concurrent.CancellationException;

/**
 * Cooperative cancellation of the per-file Java parse task (#178). A task whose thread is already
 * interrupted when it runs must abort before parsing, so a {@code shutdownNow()} on a cancelled
 * compile drains the not-yet-started tasks instead of parsing every remaining file.
 */
public class ParseTaskCancellationTest {

    @Test
    public void cancelledTaskThrowsAndDoesNotBuildAParserContext() {
        // If the task tried to parse, it would pull a ParserContext from this ThreadLocal — the
        // supplier fails the test if that happens, proving the task bailed before any parsing.
        ThreadLocal<ParserContext> context = ThreadLocal.withInitial(() -> {
            throw new AssertionError("a cancelled parse task must not build a parser context");
        });
        ParseTask task = new ParseTask(context, new ProjectFile("a/B.java", "class B {}"), 0);

        Thread.currentThread().interrupt();
        try {
            task.call();
            fail("a cancelled ParseTask should throw");
        } catch (CancellationException expected) {
            // ok
        } finally {
            // Clear the flag so it does not leak into other tests sharing this thread.
            Thread.interrupted();
        }
    }
}
