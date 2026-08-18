package com.hadi.test.sourcemodel;

import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.Package;
import com.hadi.clarpse.sourcemodel.StringPool;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins that a shared pool makes two models share their text, and that sharing text changes nothing a
 * caller can observe about the models.
 *
 * <p>Identity assertions are the point here: {@code assertEquals} on a string would pass whether or
 * not the pool did anything at all, so every assertion that matters below is {@code assertSame}.
 */
public class StringPoolTest {

    private static Component classComponent(final String name, final String pkg) {
        Component cls = new Component();
        cls.setComponentType(ComponentType.CLASS);
        cls.setComponentName(name);
        cls.setName(name);
        cls.setPkg(new Package(pkg, pkg));
        cls.setModule("core");
        cls.setComment("A comment repeated across revisions.");
        cls.setCodeFragment("class " + name);
        cls.setSourceFilePath("/" + pkg + "/" + name + ".java");
        cls.insertChildComponent(pkg + "." + name + ".field");
        cls.insertAccessModifier("public");
        Set<String> imports = new LinkedHashSet<>();
        imports.add("java.util.List");
        cls.setImports(imports);
        return cls;
    }

    /** Fresh strings with equal contents, as two separate parses of the same file would produce. */
    private static Component freshlyBuilt(final String name, final String pkg) {
        return classComponent(new String(name.toCharArray()), new String(pkg.toCharArray()));
    }

    @Test
    public void twoModelsSharingAPoolShareTheirStrings() {
        StringPool pool = new StringPool();
        OOPSourceCodeModel base = new OOPSourceCodeModel(pool);
        OOPSourceCodeModel head = new OOPSourceCodeModel(pool);

        base.insertComponent(freshlyBuilt("Widget", "app"));
        head.insertComponent(freshlyBuilt("Widget", "app"));

        Component fromBase = base.liveComponent("app.Widget").orElseThrow();
        Component fromHead = head.liveComponent("app.Widget").orElseThrow();

        assertSame("componentName", fromBase.componentName(), fromHead.componentName());
        assertSame("name", fromBase.name(), fromHead.name());
        assertSame("module", fromBase.module(), fromHead.module());
        assertSame("comment", fromBase.comment(), fromHead.comment());
        assertSame("codeFragment", fromBase.codeFragment(), fromHead.codeFragment());
        assertSame("sourceFile", fromBase.sourceFile(), fromHead.sourceFile());
        assertSame("pkg", fromBase.pkg(), fromHead.pkg());
        assertSame("children element",
                fromBase.children().get(0), fromHead.children().get(0));
        assertSame("imports element",
                fromBase.imports().iterator().next(), fromHead.imports().iterator().next());
    }

    @Test
    public void twoModelsWithSeparatePoolsDoNotShare() {
        OOPSourceCodeModel base = new OOPSourceCodeModel();
        OOPSourceCodeModel head = new OOPSourceCodeModel();

        base.insertComponent(freshlyBuilt("Widget", "app"));
        head.insertComponent(freshlyBuilt("Widget", "app"));

        assertEquals(base.liveComponent("app.Widget").orElseThrow().name(),
                head.liveComponent("app.Widget").orElseThrow().name());
        assertTrue("a model given no pool still shares within itself",
                base.stringPool().size() > 0);
    }

    @Test
    public void poolingLeavesEveryValueUnchanged() {
        StringPool pool = new StringPool();
        OOPSourceCodeModel pooled = new OOPSourceCodeModel(pool);
        OOPSourceCodeModel plain = new OOPSourceCodeModel();

        pooled.insertComponent(classComponent("Widget", "app"));
        plain.insertComponent(classComponent("Widget", "app"));

        Component a = pooled.liveComponent("app.Widget").orElseThrow();
        Component b = plain.liveComponent("app.Widget").orElseThrow();
        assertEquals(b.componentName(), a.componentName());
        assertEquals(b.uniqueName(), a.uniqueName());
        assertEquals(b.name(), a.name());
        assertEquals(b.module(), a.module());
        assertEquals(b.comment(), a.comment());
        assertEquals(b.codeFragment(), a.codeFragment());
        assertEquals(b.sourceFile(), a.sourceFile());
        assertEquals(b.pkg(), a.pkg());
        assertEquals(b.children(), a.children());
        assertEquals(b.imports(), a.imports());
        assertEquals(b.modifiers(), a.modifiers());
    }

    @Test
    public void copyCarriesThePoolRatherThanStartingAFreshOne() {
        StringPool pool = new StringPool();
        OOPSourceCodeModel base = new OOPSourceCodeModel(pool);
        base.insertComponent(freshlyBuilt("Widget", "app"));

        OOPSourceCodeModel copy = base.copy();
        assertSame("a copy shares its source's pool", pool, copy.stringPool());
        assertSame("so a copied component shares the original's text",
                base.liveComponent("app.Widget").orElseThrow().name(),
                copy.liveComponent("app.Widget").orElseThrow().name());
    }

    @Test
    public void pooledIsNullSafeAndLeavesEmptyStringsAlone() {
        StringPool pool = new StringPool();
        assertNull(pool.pooled((String) null));
        assertNull(pool.pooled((Package) null));
        assertEquals("", pool.pooled(""));
        assertTrue(pool.pooledSet(null).isEmpty());
    }

    @Test
    public void pooledSetKeepsDeclarationOrder() {
        StringPool pool = new StringPool();
        Set<String> in = new LinkedHashSet<>(List.of("zebra", "apple", "mango"));
        assertEquals(List.copyOf(in), List.copyOf(pool.pooledSet(in)));
    }

    @Test
    public void aModelRefusesANullPool() {
        try {
            new OOPSourceCodeModel((StringPool) null);
            throw new AssertionError("expected a rejected null pool");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void modifiersAreStoredAsTheVocabularysOwnInstance() {
        Component cmp = new Component();
        cmp.insertAccessModifier("PUBLIC");
        String canonical = OOPSourceModelConstants.getAccessModifierMap()
                .get(OOPSourceModelConstants.AccessModifiers.PUBLIC);
        assertSame("a modifier must not be a fresh lower-cased copy",
                canonical, cmp.modifiers().iterator().next());
    }

    @Test
    public void anInvalidModifierIsStillRefused() {
        Component cmp = new Component();
        try {
            cmp.insertAccessModifier("notamodifier");
            throw new AssertionError("expected an invalid modifier to be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("notamodifier"));
        }
    }
}
