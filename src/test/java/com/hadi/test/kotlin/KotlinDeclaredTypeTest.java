package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.test.DeclaredTypes;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** The declared type of a Kotlin function, property and parameter is the one named type it is written with. */
public class KotlinDeclaredTypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/a/Repo.kt", String.join("\n",
                        "package a",
                        "import b.Shown",
                        "class Repo<T> {",
                        "  fun orders(): List<Order> = listOf()",
                        "  fun find(id: String): Order? = null",
                        "  fun clear() {}",
                        "  fun explicit(): Unit {}",
                        "  val current: Order? = null",
                        "  fun element(): T? = null",
                        "  fun function(): (Int) -> Order = { Order() }",
                        "  fun remote(): Shown = Shown()",
                        "  fun inferred() = Order()",
                        "}",
                        "class Order")),
                new ProjectFile("/b/Shown.kt", "package b\nclass Shown\n")).model();
    }

    @Test
    public void aNullableMarkerDoesNotChangeTheType() {
        assertEquals("a.Order", DeclaredTypes.of(model, "a.Repo", "find"));
        assertEquals("a.Order", DeclaredTypes.of(model, "a.Repo", "current"));
    }

    @Test
    public void aGenericTypeIsErasedAndATypeOfAnotherFileIsFound() {
        assertNotNull(DeclaredTypes.of(model, "a.Repo", "orders"));
        assertEquals("b.Shown", DeclaredTypes.of(model, "a.Repo", "remote"));
    }

    @Test
    public void unitNoTypeFunctionTypesAndTypeParametersAreNull() {
        assertNull(DeclaredTypes.of(model, "a.Repo", "clear"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "explicit"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "inferred"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "function"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "element"));
    }

    @Test
    public void theDeclaredTypeIsOneOfTheReferences() {
        DeclaredTypes.assertContainedInReferences(model);
    }
}
