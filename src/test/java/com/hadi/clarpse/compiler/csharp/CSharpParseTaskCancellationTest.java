package com.hadi.clarpse.compiler.csharp;

import static org.junit.Assert.fail;

import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.concurrent.CancellationException;

/**
 * Cooperative cancellation of the in-process C# parse task (#180) — the C# counterpart of the Java
 * {@code ParseTask} test. A task whose thread is already interrupted must abort before parsing, so a
 * {@code shutdownNow()} on a cancelled compile drains not-yet-started tasks.
 *
 * <p>In the C# package so it can see the package-private {@link CSharpParseTask}. No parser is
 * invoked: the task throws before it would reach {@code CSharpFileParser}.
 */
public class CSharpParseTaskCancellationTest {

    @Test
    public void cancelledTaskThrowsBeforeParsing() {
        final CSharpParseTask task = new CSharpParseTask(new ProjectFile("A.cs", "class A {}"), 0);

        Thread.currentThread().interrupt();
        try {
            task.call();
            fail("a cancelled C# parse task should throw");
        } catch (final CancellationException expected) {
            // ok
        } finally {
            Thread.interrupted(); // clear the flag so it does not leak to other tests
        }
    }
}
