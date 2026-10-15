package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A module's type aliases are components of the module, referencing the types they are written
 * with; a value of the same name keeps the name; and a file declaring no type gives its imports to
 * its module-level declarations.
 */
public class TypeScriptModuleDeclarationsTest {

    private static final String FIXTURE = "module-declarations";
    private static final String PACKAGE_PATH = "src/lib";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void aTypeAliasIsAComponentOfItsModule() {
        Component options = component("api", "Options");
        assertEquals(OOPSourceModelConstants.ComponentType.TYPE_ALIAS, options.componentType());
        assertTrue(options.modifiers().contains("export"));
        assertEquals("api", options.module());
        assertTrue(options.comment().contains("What a client is built from"));
    }

    @Test
    public void anUnexportedTypeAliasIsModelledToo() {
        Component internal = component("api", "Internal");
        assertEquals(OOPSourceModelConstants.ComponentType.TYPE_ALIAS, internal.componentType());
        assertFalse(internal.modifiers().contains("export"));
    }

    @Test
    public void aTypeAliasReferencesTheTypesItIsWrittenWith() {
        assertTrue(simpleRefs(component("api", "Options")).contains(name("store", "Store")));
        assertTrue(simpleRefs(component("api", "Internal")).contains(name("api", "Mode")));
    }

    @Test
    public void aGenericTypeAliasDoesNotReferenceItsOwnTypeParameter() {
        Set<String> refs = simpleRefs(component("api", "Handler"));
        assertTrue(refs.contains(name("store", "Store")));
        assertFalse(refs.contains(name("api", "T")));
        assertEquals("Handler<T>", component("api", "Handler").codeFragment());
    }

    @Test
    public void aTypeAliasOfLiteralsReferencesNoComponent() {
        assertTrue(simpleRefs(component("api", "Mode")).stream().noneMatch(ref -> ref.startsWith("src.lib.")));
    }

    @Test
    public void aBindingHoldingAFunctionStaysAModuleField() {
        assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD,
                component("api", "createClient").componentType());
        assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD,
                component("api", "serve").componentType());
    }

    @Test
    public void aValueAndATypeAliasOfOneNameKeepTheValue() {
        assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD,
                component("kinds", "Kind").componentType());
        assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION,
                component("kinds", "describe").componentType());
    }

    @Test
    public void aFunctionDeclarationReferencesTheTypesItConstructs() {
        Component buildStore = component("api", "buildStore");
        assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION, buildStore.componentType());
        assertTrue(simpleRefs(buildStore).contains(name("store", "Store")));
    }

    @Test
    public void aPlainConstantKeepsItsShape() {
        Component limit = component("api", "LIMIT");
        assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, limit.componentType());
        assertTrue(simpleRefs(limit).stream().noneMatch(ref -> ref.startsWith("src.lib.")));
    }

    @Test
    public void aFileDeclaringNoTypeGivesItsImportsToItsModuleLevelDeclarations() {
        for (String symbol : new String[] {"label", "DEFAULT_LABEL", "Labeller"}) {
            Component declared = model.copyOfComponent(
                    TypeScriptTestUtil.uniqueName("src/util", "format", symbol)).orElseThrow();
            assertTrue(symbol + " imports " + declared.imports(), declared.imports().contains("path"));
            assertTrue(symbol + " imports " + declared.imports(),
                    declared.imports().contains(name("store", "Store")));
        }
    }

    @Test
    public void aFileDeclaringATypeKeepsItsImportsOnTheType() {
        assertTrue(component("client", "Client").imports().contains(name("store", "Store")));
        assertTrue(component("client", "connect").imports().isEmpty());
    }

    private static Component component(final String module, final String symbol) {
        return model.copyOfComponent(name(module, symbol)).orElseThrow(
                () -> new AssertionError("no component " + name(module, symbol)));
    }

    private static Set<String> simpleRefs(final Component component) {
        return component.references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .map(ComponentReference::invokedComponent)
                .collect(Collectors.toSet());
    }

    private static String name(final String module, final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, module, symbolPath);
    }
}
