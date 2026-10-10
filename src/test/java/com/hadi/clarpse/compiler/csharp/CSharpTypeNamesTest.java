package com.hadi.clarpse.compiler.csharp;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Unique names carry a generic type's arity only where a type of another arity shares its name in
 * the same scope, and type-argument lists are counted only where the text is one.
 */
public class CSharpTypeNamesTest {

    @Test
    public void onlyANameOverloadedByArityCarriesTheArity() {
        final Map<String, String> unique = CSharpTypeNames.uniqueNames(List.of(
                "Acme.Converter", "Acme.Converter`1", "Acme.Repo`1", "Acme.Pair`1", "Acme.Pair`2",
                "Acme.Converter`1.Node", "Acme.Converter.Node", "Acme.Repo`1.Node`1", "Global`1"));
        assertEquals("Acme.Converter", unique.get("Acme.Converter"));
        assertEquals("Acme.Converter`1", unique.get("Acme.Converter`1"));
        assertEquals("Acme.Repo", unique.get("Acme.Repo`1"));
        assertEquals("Acme.Pair`1", unique.get("Acme.Pair`1"));
        assertEquals("Acme.Pair`2", unique.get("Acme.Pair`2"));
        assertEquals("Acme.Converter`1.Node", unique.get("Acme.Converter`1.Node"));
        assertEquals("Acme.Converter.Node", unique.get("Acme.Converter.Node"));
        assertEquals("Acme.Repo.Node", unique.get("Acme.Repo`1.Node`1"));
        assertEquals("Global", unique.get("Global`1"));
    }

    @Test
    public void theLookupNameDropsOnlyTheLastSegmentsArity() {
        assertEquals("Acme.Converter", CSharpTypeNames.lookupName("Acme.Converter`1"));
        assertEquals("Acme.Converter`1.Node", CSharpTypeNames.lookupName("Acme.Converter`1.Node"));
        assertEquals("Acme.Repo", CSharpTypeNames.lookupName("Acme.Repo"));
    }

    @Test
    public void typeArgumentListsAreCountedAtTheirTopLevel() {
        assertEquals(2, CSharpTypeNames.declaredArity("Map<TKey, TValue>"));
        assertEquals(0, CSharpTypeNames.declaredArity("Map"));
        assertEquals(2, CSharpTypeNames.typeArgumentCount("Dictionary<string, List<(int, string)>>", 10));
        assertEquals(1, CSharpTypeNames.typeArgumentCount("Foo<global::A.B[]?>", 3));
        assertEquals(1, CSharpTypeNames.typeArgumentCount("Foo<>", 3));
        assertEquals(3, CSharpTypeNames.typeArgumentCount("Foo<,,>", 3));
    }

    @Test
    public void textThatIsNotATypeArgumentListHasNoArity() {
        assertEquals(CSharpTypeNames.UNKNOWN_ARITY, CSharpTypeNames.typeArgumentCount("Count < Max && x > 1", 6));
        assertEquals(CSharpTypeNames.UNKNOWN_ARITY, CSharpTypeNames.typeArgumentCount("Count < Max)", 6));
        assertEquals(CSharpTypeNames.UNKNOWN_ARITY, CSharpTypeNames.typeArgumentCount("Foo<int", 3));
    }
}
