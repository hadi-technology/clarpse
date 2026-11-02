package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Covers the type arguments of a supertype list: only the named supertype is extended or
 * implemented, and each argument is an ordinary reference.
 */
public class KotlinGenericSupertypeKindTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = KotlinTestUtil.compileInline(new ProjectFile("/src/Shapes.kt", String.join("\n",
                "package app",
                "open class Base<T>",
                "interface Handler<T>",
                "class Arg",
                "class Inner",
                "class Wrapper<T>",
                "class Nested : Base<Wrapper<Inner>>()",
                "class Both : Base<Arg>(), Handler<Inner>",
                ""))).model();
    }

    private static List<String> targets(final String component, final TypeReferences kind) {
        return model.copyOfComponent(component).orElseThrow().references(kind).stream()
                .map(ComponentReference::invokedComponent).sorted().toList();
    }

    @Test
    public void nestedTypeArgumentsAreAllPlainReferences() {
        assertEquals(List.of("app.Base"), targets("app.Nested", TypeReferences.EXTENSION));
        assertEquals(List.of("app.Inner", "app.Wrapper"), targets("app.Nested", TypeReferences.SIMPLE));
    }

    @Test
    public void bothKindsKeepTheirArgumentsPlain() {
        assertEquals(List.of("app.Base"), targets("app.Both", TypeReferences.EXTENSION));
        assertEquals(List.of("app.Handler"), targets("app.Both", TypeReferences.IMPLEMENTATION));
        assertEquals(List.of("app.Arg", "app.Inner"), targets("app.Both", TypeReferences.SIMPLE));
    }
}
