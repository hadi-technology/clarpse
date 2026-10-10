package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * A Kotlin parse task whose thread is already interrupted aborts before parsing, so cancelling a
 * compile drains the tasks that have not started.
 */
public class KotlinParseTaskCancellationTest {

    @Test
    public void cancelledTaskThrowsBeforeParsing() {
        final KotlinParseTask task = new KotlinParseTask(new ProjectFile("/A.kt", "class A"), 0, true);

        Thread.currentThread().interrupt();
        try {
            task.call();
            fail("a cancelled Kotlin parse task should throw");
        } catch (final CancellationException expected) {
            // ok
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void theFileClassNameFollowsTheJvmConvention() {
        assertEquals("UtilsKt", KotlinDeclarationIndex.defaultFileClassSimpleName("utils"));
        assertEquals("String_utilsKt", KotlinDeclarationIndex.defaultFileClassSimpleName("string-utils"));
        assertEquals("MainKt", KotlinDeclarationIndex.defaultFileClassSimpleName("Main"));
    }
}
