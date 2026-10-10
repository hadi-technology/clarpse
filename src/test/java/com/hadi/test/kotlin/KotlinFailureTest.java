package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A Kotlin file that cannot be parsed is reported as a failure of that file, and the other files
 * are modelled all the same.
 */
public class KotlinFailureTest {

    @Test
    public void aTruncatedFileIsAFailureAndTheRestIsModelled() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Broken.kt", "package app\nclass Broken {\n    fun run() {\n"),
                new ProjectFile("/app/Fine.kt", "package app\nclass Fine\n"));

        final List<CompileFailure> failures = List.copyOf(result.failures());
        assertEquals(1, failures.size());
        assertEquals("/app/Broken.kt", failures.get(0).file().path());
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), failures.get(0).errorCode());
        assertTrue(result.model().containsComponent("app.Fine"));
        assertFalse(result.model().containsComponent("app.Broken"));
    }

    @Test
    public void aLeadingByteOrderMarkIsIgnored() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Marked.kt", "﻿package app\n\nclass Marked\n"));

        assertTrue(result.failures().isEmpty());
        assertTrue(result.model().containsComponent("app.Marked"));
    }

    @Test
    public void bracesInStringsAndCommentsDoNotUnbalanceAFile() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(new ProjectFile("/app/Text.kt", String.join("\n",
                "package app",
                "// a stray { in a comment",
                "class Text {",
                "    val open = \"{\"",
                "    val template = \"${open} }\"",
                "    val raw = \"\"\"",
                "        ) ]",
                "    \"\"\"",
                "}")));

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(result.model().containsComponent("app.Text.raw"));
    }

    @Test
    public void aProjectWithoutKotlinFilesCompilesToAnEmptyModel() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Main.java", "package app; public class Main { }"));

        assertEquals(0, result.model().size());
        assertTrue(result.failures().isEmpty());
    }
}
