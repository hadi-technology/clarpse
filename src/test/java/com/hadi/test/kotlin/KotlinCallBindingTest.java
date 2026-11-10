package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A class whose supertype the parse cannot read is no reason to doubt a call the file's own scope
 * binds exactly: an explicit import, a star import or the same package, of a repository or a library
 * symbol, a class or a top-level function. Only a member or local the class declares by that name
 * binds the call closer and records nothing.
 */
public class KotlinCallBindingTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/src/main/java/jlib/Foo.java",
                        "package jlib;\npublic class Foo { public Foo(String s) { } }\n"),
                new ProjectFile("/src/main/kotlin/klib/Bar.kt", String.join("\n",
                        "package klib",
                        "class Bar(val s: String)",
                        "fun foo(x: String) {}")),
                new ProjectFile("/src/main/kotlin/app/A.kt", String.join("\n",
                        "package app",
                        "import jlib.*",
                        "import klib.Bar",
                        "import klib.foo",
                        "import ext.Baz",
                        "import com.intellij.ui.components.JBLabel",
                        "class Star : Library() { fun f(x: String) { Foo(x) } }",
                        "class Named : Library() { val b = Bar(\"x\") }",
                        "class Own : Library() { fun Foo(x: String) {}; fun f(x: String) { Foo(x) } }",
                        "class Function : Library() { fun f(x: String) { foo(x) } }",
                        "class OwnFunction : Library() { fun foo() {}; fun f(x: String) { foo(x) } }",
                        "class Lib : Library() { val l = JBLabel(\"x\") }",
                        "class Unknown : Library() { fun f(x: String) { Qux(x) } }",
                        "class External : Library() { fun f(x: String) { Baz(x) } }",
                        "class Field : Library() { val star = Foo(\"x\") }")))
                .model();
    }

    private static boolean references(final String name, final String target) {
        return targets(component(model, name)).contains(target);
    }

    @Test
    public void aJavaClassUnderAStarImportIsConstructedDespiteAnUnreadableBase() {
        assertTrue(references("app.Star.f(String)", "jlib.Foo"));
        assertTrue(references("app.Field.star", "jlib.Foo"));
    }

    @Test
    public void aKotlinClassImportedByNameIsConstructedDespiteAnUnreadableBase() {
        assertTrue(references("app.Named.b", "klib.Bar"));
    }

    @Test
    public void aMemberTheClassDeclaresBindsTheCall() {
        assertFalse(references("app.Own.f(String)", "jlib.Foo"));
    }

    @Test
    public void anImportedTopLevelFunctionIsCreditedDespiteAnUnreadableBase() {
        assertTrue(references("app.Function.f(String)", "klib.BarKt"));
        assertFalse(references("app.OwnFunction.f(String)", "klib.BarKt"));
    }

    @Test
    public void anExplicitlyImportedLibraryClassIsConstructedDespiteAnUnreadableBase() {
        assertTrue(references("app.Lib.l", "com.intellij.ui.components.JBLabel"));
        assertTrue(references("app.External.f(String)", "ext.Baz"));
    }

    @Test
    public void anUnknownNameIsNotRecorded() {
        assertFalse(targets(component(model, "app.Unknown.f(String)")).stream().anyMatch(t -> t.endsWith("Qux")));
    }
}
