package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The types an annotation on a class names in its arguments -- a class literal, an annotation
 * created inside an array -- are references of the class, as they are for the same annotation on a
 * member.
 */
public class KotlinClassAnnotationArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Types.kt", String.join("\n",
                        "package app",
                        "annotation class Anno(val value: kotlin.reflect.KClass<*>)",
                        "annotation class Storage(val value: String)",
                        "annotation class Other",
                        "annotation class State(val storages: Array<Storage> = [], val other: Other = Other())",
                        "annotation class Plain",
                        "class Foo",
                        "class Bar")),
                new ProjectFile("/app/Use.kt", String.join("\n",
                        "package app",
                        "@Anno(Foo::class)",
                        "class Literal",
                        "@State(storages = [Storage(\"x\")], other = Other())",
                        "class Nested",
                        "@Plain",
                        "class Bare",
                        "class Outer {",
                        "  @Anno(Bar::class)",
                        "  class Inner",
                        "  @Anno(Bar::class)",
                        "  fun member() {}",
                        "}")))
                .model();
    }

    @Test
    public void aClassLiteralInAClassAnnotationIsAReference() {
        assertTrue(targets(component(model, "app.Literal")).contains("app.Foo"));
    }

    @Test
    public void anAnnotationBuiltInsideAClassAnnotationIsAReference() {
        assertTrue(targets(component(model, "app.Nested")).containsAll(java.util.Set.of("app.Storage", "app.Other")));
    }

    @Test
    public void aNestedClassKeepsItsOwnAnnotationArgumentsAndTheOuterGetsThem() {
        assertTrue(targets(component(model, "app.Outer.Inner")).contains("app.Bar"));
        assertTrue(targets(component(model, "app.Outer")).contains("app.Bar"));
        assertTrue(targets(component(model, "app.Outer.member()")).contains("app.Bar"));
    }

    @Test
    public void anAnnotationWithoutArgumentsAddsNoTypeReferenceBeyondTheAnnotation() {
        assertEquals(java.util.Set.of("app.Plain"), targets(component(model, "app.Bare")));
    }
}
