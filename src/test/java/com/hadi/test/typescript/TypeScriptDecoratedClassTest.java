package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Decorated classes whose base is a call into a package that is not installed
 * (`class A extends Mixin(Base)`), with constructor parameter properties and decorated parameters.
 * The class's own members are all read; the base the package supplies is named by a reference to
 * that package's symbol, and the file is not reported as partly read.
 */
public class TypeScriptDecoratedClassTest {

    private static final String PACKAGE_PATH = "src";
    private static CompileResult result;
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        result = TypeScriptTestUtil.compileFixture("decorated-classes");
        model = result.model();
    }

    @Test
    public void aBaseCalledIntoAnUninstalledPackageIsNotReportedAsUnresolved() {
        assertTrue("unexpected failures: " + result.failures(), result.failures().isEmpty());
    }

    @Test
    public void theCalledBaseIsNamedByTheSymbolOfItsPackage() {
        assertExtends("OrbitJwtStrategy", "@orbit/passport:OrbitStrategy");
        assertExtends("OrbitJwtGuard", "@orbit/passport:OrbitGuardFactory");
    }

    @Test
    public void aTypeNamedInTheBaseCallsArgumentsIsReferenced() {
        assertTrue(references("OrbitJwtStrategy").contains("orbit-jwt:Strategy"));
    }

    @Test
    public void constructorParameterPropertiesAreFieldsOfTheClass() {
        final Component strategy = component("OrbitJwtStrategy");
        assertTrue(strategy.children().contains(name("OrbitJwtStrategy.store")));
        assertTrue(strategy.children().contains(name("OrbitJwtStrategy.token")));
        assertEquals(ComponentType.FIELD, component("OrbitJwtStrategy.store").componentType());
        assertTrue(references("OrbitJwtStrategy").contains(name("store.OrbitStore")));
    }

    @Test
    public void membersOfDecoratedClassesAreKept() {
        assertEquals(ComponentType.METHOD, component("OrbitJwtStrategy.validate(string)").componentType());
        assertEquals(ComponentType.METHOD, component("OrbitJwtGuard.allow()").componentType());
        assertEquals(ComponentType.METHOD, component("OrbitController.list()").componentType());
    }

    @Test
    public void decoratorsAreAnnotationReferencesOnTheClass() {
        assertTrue(references("OrbitController").stream().anyMatch(r -> r.endsWith("Controller")));
        assertTrue(references("OrbitModule").stream().anyMatch(r -> r.endsWith("Module")));
    }

    private static void assertExtends(final String symbol, final String target) {
        assertTrue(symbol + " should extend " + target + " but has " + references(symbol),
                component(symbol).references().stream()
                        .anyMatch(r -> r instanceof TypeExtensionReference && r.invokedComponent().equals(target)));
    }

    private static java.util.Set<String> references(final String symbol) {
        return component(symbol).references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    private static Component component(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow(
                () -> new AssertionError("no component named " + name(symbolPath)));
    }

    private static String name(final String symbolPath) {
        final String module = symbolPath.startsWith("store.") ? "store" : "orbit";
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, module, symbolPath.replaceFirst("^store\\.", ""));
    }
}
