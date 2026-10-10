package com.hadi.clarpse.compiler.kotlin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A Kotlin file's package is read from its header without parsing it, and a header the reader is
 * not sure of gives no package rather than a wrong one.
 */
public class KotlinPackageHeaderTest {

    @Test
    public void aPlainHeaderGivesItsPackage() {
        assertEquals("com.acme.orders", KotlinPackageHeader.of("package com.acme.orders\n\nclass Order\n"));
    }

    @Test
    public void aFileWithoutAPackageDirectiveIsInTheRootPackage() {
        assertEquals("", KotlinPackageHeader.of("import java.util.List\n\nclass Order\n"));
        assertEquals("", KotlinPackageHeader.of(""));
        assertEquals("", KotlinPackageHeader.of("@Suppress(\"unused\")\nclass Order\n"));
    }

    @Test
    public void commentsAShebangAndAByteOrderMarkComeBeforeThePackage() {
        assertEquals("a.b", KotlinPackageHeader.of("﻿#!/usr/bin/env kotlin\n// line\n/* block /* nested */ */\n"
                + "/** doc */ package a.b\n"));
    }

    @Test
    public void fileAnnotationsComeBeforeThePackage() {
        assertEquals("okhttp3.internal", KotlinPackageHeader.of(
                "@file:JvmName(\"Internal\")\n@file:Suppress(\"a\", \"b)\")\npackage okhttp3.internal\n"));
        assertEquals("a", KotlinPackageHeader.of("@file:[JvmName(\"X\") JvmMultifileClass]\npackage a\n"));
        assertEquals("a", KotlinPackageHeader.of("@file : kotlin.jvm.JvmName(\"X\")\npackage a\n"));
        assertEquals("a", KotlinPackageHeader.of("@file:OptIn(Foo::class, Bar::class) // why\npackage a\n"));
    }

    @Test
    public void aBacktickedOrSpacedNameIsReadAsTheParserReadsIt() {
        assertEquals("a.in.c", KotlinPackageHeader.of("package a.`in`.c\n"));
        assertEquals("a.b.c", KotlinPackageHeader.of("package a .b\n  .c\nimport x.Y\n"));
        assertEquals("a.b", KotlinPackageHeader.of("package a.b; class C\n"));
    }

    @Test
    public void aWordThatOnlyStartsWithPackageIsNotTheDirective() {
        assertEquals("", KotlinPackageHeader.of("packageName()\n"));
    }

    @Test
    public void aHeaderItCannotReadWithCertaintyGivesNoPackage() {
        assertNull(KotlinPackageHeader.of("package {{packageName}}\n"));
        assertNull(KotlinPackageHeader.of("/* never closed\npackage a\n"));
        assertNull(KotlinPackageHeader.of("package a /* why */ .b\n"));
        assertNull(KotlinPackageHeader.of("package a.\n// later\nb\n"));
        assertNull(KotlinPackageHeader.of("@file:JvmName(\"${x}\")\npackage a\n"));
        assertNull(KotlinPackageHeader.of("@file:Ann<T>\npackage a\n"));
        assertNull(KotlinPackageHeader.of("@file:JvmName(\"X\"\npackage a\n"));
        assertNull(KotlinPackageHeader.of(null));
    }
}
