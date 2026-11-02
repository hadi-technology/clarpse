package com.hadi.test.typescript;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Covers the type arguments of an {@code extends} or {@code implements} clause: only the named
 * type is extended or implemented, and each argument is an ordinary reference.
 */
public class TypeScriptGenericHeritageTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = TypeScriptTestUtil.compileFixture("generic-heritage").model();
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName("src", "shapes", symbolPath);
    }

    private static List<String> targets(final String symbolPath, final TypeReferences kind) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow().references(kind).stream()
                .map(ComponentReference::invokedComponent).sorted().toList();
    }

    @Test
    public void typeArgumentOfAnExtendedClassIsAPlainReference() {
        assertEquals(List.of(name("Base")), targets("Derived", TypeReferences.EXTENSION));
        assertEquals(List.of(name("Arg")), targets("Derived", TypeReferences.SIMPLE));
        assertEquals(List.of(), targets("Derived", TypeReferences.IMPLEMENTATION));
    }

    @Test
    public void nestedTypeArgumentsAreAllPlainReferences() {
        assertEquals(List.of(name("Base")), targets("Nested", TypeReferences.EXTENSION));
        assertEquals(List.of(name("Inner"), name("Wrapper")), targets("Nested", TypeReferences.SIMPLE));
    }

    @Test
    public void typeArgumentOfAnImplementedInterfaceIsAPlainReference() {
        assertEquals(List.of(name("Handler")), targets("Impl", TypeReferences.IMPLEMENTATION));
        assertEquals(List.of(), targets("Impl", TypeReferences.EXTENSION));
        assertEquals(true, targets("Impl", TypeReferences.SIMPLE).contains(name("Arg")));
    }

    @Test
    public void interfaceExtendingAGenericInterfaceKeepsItsArgumentPlain() {
        assertEquals(List.of(name("Handler")), targets("Child", TypeReferences.EXTENSION));
        assertEquals(List.of(name("Arg")), targets("Child", TypeReferences.SIMPLE));
    }

    @Test
    public void classWithBothClausesSplitsThemCorrectly() {
        assertEquals(List.of(name("Base")), targets("Mixed", TypeReferences.EXTENSION));
        assertEquals(List.of(name("Handler")), targets("Mixed", TypeReferences.IMPLEMENTATION));
        assertEquals(true, targets("Mixed", TypeReferences.SIMPLE).contains(name("Arg")));
    }
}
