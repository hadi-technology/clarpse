package com.hadi.test;

import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.AccessModifiers;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@code Component}'s two modifier mutators must agree on what is legal, and the vocabulary they enforce
 * has to cover every language the compilers support - not just Java.
 */
public class ComponentModifierContractTest {

    @Test
    public void bothMutatorsEnforceTheSameVocabulary() {
        final Component component = new Component();
        assertThrows(IllegalArgumentException.class, () -> component.insertAccessModifier("wat"));
        assertThrows(IllegalArgumentException.class, () -> component.setAccessModifiers(List.of("wat")));
    }

    @Test
    public void nonJavaModifiersAreLegal() {
        final Component component = new Component();
        component.setAccessModifiers(List.of("partial", "internal", "readonly", "override"));
        component.insertAccessModifier("export");
        component.insertAccessModifier("ASYNC");
        assertTrue(component.modifiers().contains("partial"));
        assertTrue(component.modifiers().contains("export"));
        assertTrue("modifiers are normalised to lower case", component.modifiers().contains("async"));
    }

    @Test
    public void modernJavaModifiersAreLegal() {
        final Component component = new Component();
        component.setAccessModifiers(List.of("sealed", "non-sealed", "default", "transitive"));
        assertTrue(component.modifiers().contains("non-sealed"));
    }

    @Test
    public void everyModifierResolvesBackToItsEnum() {
        for (final String modifier : OOPSourceModelConstants.getAccessModifierMap().values()) {
            assertNotNull("modifier " + modifier + " should resolve to an enum constant",
                    OOPSourceModelConstants.accessModifier(modifier));
        }
        assertEquals(AccessModifiers.PUBLIC, OOPSourceModelConstants.accessModifier("PUBLIC"));
        assertEquals("+", OOPSourceModelConstants.accessModifier("public").getUMLClassDigramSymbol());
        assertNull(OOPSourceModelConstants.accessModifier("wat"));
        assertNull(OOPSourceModelConstants.accessModifier(null));
    }

    @Test
    public void theVocabularyIsASupersetOfJava() {
        assertTrue(OOPSourceModelConstants.getAccessModifierMap().entrySet()
                .containsAll(OOPSourceModelConstants.getJavaAccessModifierMap().entrySet()));
    }
}
