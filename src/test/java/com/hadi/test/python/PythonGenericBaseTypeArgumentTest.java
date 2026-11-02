package com.hadi.test.python;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Covers a subscripted base class: the subscripted name is extended, and nothing else is. */
public class PythonGenericBaseTypeArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = PythonTestUtil.compileFixture("generic-bases").model();
    }

    private static List<String> targets(final String symbol, final TypeReferences kind) {
        return model.copyOfComponent(PythonTestUtil.uniqueName("src", "shapes", symbol)).orElseThrow()
                .references(kind).stream().map(ComponentReference::invokedComponent).sorted().toList();
    }

    private static String name(final String symbol) {
        return PythonTestUtil.uniqueName("src", "shapes", symbol);
    }

    @Test
    public void onlyTheSubscriptedBaseIsExtended() {
        assertEquals(List.of(name("Base")), targets("Derived", TypeReferences.EXTENSION));
        assertEquals(List.of(name("Base")), targets("Nested", TypeReferences.EXTENSION));
    }

    @Test
    public void aTypeArgumentIsNeverAnExtension() {
        assertEquals(List.of(), targets("Derived", TypeReferences.IMPLEMENTATION));
    }
}
