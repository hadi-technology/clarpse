package com.hadi.test;

import com.hadi.clarpse.TypeNames;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The reduction from a written type expression to the name of the type it refers to.
 */
public class TypeNamesTest {

    @Test
    public void typeArgumentsAreErased() {
        assertEquals("DTO", TypeNames.erasure("DTO<HttpRequest>"));
        assertEquals("com.example.model.DTO", TypeNames.erasure("com.example.model.DTO<HttpRequest>"));
    }

    @Test
    public void nestedTypeArgumentsEraseToTheOutermostType() {
        assertEquals("Map", TypeNames.erasure("Map<String, List<Foo>>"));
        assertEquals("java.util.Map", TypeNames.erasure("java.util.Map<String, java.util.List<Foo>>"));
    }

    @Test
    public void wildcardsAreErasedLikeAnyOtherArgument() {
        assertEquals("Foo", TypeNames.erasure("Foo<?>"));
        assertEquals("Foo", TypeNames.erasure("Foo<? extends Bar>"));
        assertEquals("Foo", TypeNames.erasure("Foo<? super Bar>"));
    }

    @Test
    public void arraysAndVarargsAreErased() {
        assertEquals("Bar", TypeNames.erasure("Bar[]"));
        assertEquals("Bar", TypeNames.erasure("Bar[][]"));
        assertEquals("Foo", TypeNames.erasure("Foo<Bar>[]"));
        assertEquals("Bar", TypeNames.erasure("Bar..."));
        assertEquals("Foo", TypeNames.erasure("Foo<Bar>..."));
    }

    @Test
    public void pythonSubscriptsAreErased() {
        assertEquals("Dict", TypeNames.erasure("Dict[str, int]"));
        assertEquals("dto.Foo", TypeNames.erasure("dto.Foo[dto.Bar]"));
    }

    /**
     * The near miss. Erasing too eagerly passes every test that asks whether the type arguments are
     * gone, so what has to be pinned is the name that had none: a raw type is already a type name
     * and must come back byte for byte.
     */
    @Test
    public void aNameWithNothingToEraseIsUnchanged() {
        assertEquals("Foo", TypeNames.erasure("Foo"));
        assertEquals("com.example.model.DTO", TypeNames.erasure("com.example.model.DTO"));
        assertEquals("java.lang.String", TypeNames.erasure("java.lang.String"));
        assertEquals("Foo_Bar$Baz", TypeNames.erasure("Foo_Bar$Baz"));
    }

    /**
     * A name that opens with a bracket is not a parameterised type, and truncating it would leave
     * the empty string -- a reference to nothing, which is worse than a reference that matches
     * nothing.
     */
    @Test
    public void aNameThatIsAllBracketsIsLeftAlone() {
        assertEquals("<init>", TypeNames.erasure("<init>"));
        assertEquals("[]", TypeNames.erasure("[]"));
        assertEquals("...", TypeNames.erasure("..."));
        assertEquals("", TypeNames.erasure(""));
    }

    @Test
    public void nullIsNotAName() {
        assertNull(TypeNames.erasure(null));
    }

    @Test
    public void erasureIsIdempotent() {
        assertEquals(TypeNames.erasure("Foo<Bar>[]"), TypeNames.erasure(TypeNames.erasure("Foo<Bar>[]")));
    }
}
