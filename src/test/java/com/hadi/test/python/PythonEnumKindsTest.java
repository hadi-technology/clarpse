package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * An enum and a class were the same kind in a Python model, so a consumer reasoning over
 * {@code componentType()} could not tell them apart - not because the codebase is uniform, but
 * because the parser could not see the distinction.
 */
public class PythonEnumKindsTest {

    private static final String FIXTURE = "enum-kinds";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "kinds";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void anEnumSubclassIsAnEnum() {
        Assert.assertEquals(ComponentType.ENUM, component("Mode").componentType());
    }

    /** The base may be written `enum.IntEnum` as readily as a plain imported `Enum`. */
    @Test
    public void aQualifiedEnumBaseIsRecognised() {
        Assert.assertEquals(ComponentType.ENUM, component("Level").componentType());
    }

    @Test
    public void enumMembersAreEnumConstants() {
        Assert.assertEquals(ComponentType.ENUM_CONSTANT, component("Mode.FAST").componentType());
        Assert.assertEquals(ComponentType.ENUM_CONSTANT, component("Mode.SLOW").componentType());
        Assert.assertEquals(ComponentType.ENUM_CONSTANT, component("Level.LOW").componentType());
        Assert.assertTrue(component("Mode").children().contains(name("Mode.FAST")));
    }

    /** An ordinary class is untouched: its class-level assignment is still a field. */
    @Test
    public void anOrdinaryClassIsStillAClass() {
        Assert.assertEquals(ComponentType.CLASS, component("Plain").componentType());
        Assert.assertEquals(ComponentType.FIELD, component("Plain.kind").componentType());
    }

    @Test
    public void thePythonModelNowDistinguishesEnumKinds() {
        final Set<ComponentType> kinds = model.components()
                .map(Component::componentType)
                .collect(Collectors.toSet());
        Assert.assertTrue(kinds.contains(ComponentType.ENUM));
        Assert.assertTrue(kinds.contains(ComponentType.ENUM_CONSTANT));
        Assert.assertTrue(kinds.contains(ComponentType.CLASS));
    }

    private static Component component(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow(
                () -> new AssertionError("no component named " + name(symbolPath)));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
