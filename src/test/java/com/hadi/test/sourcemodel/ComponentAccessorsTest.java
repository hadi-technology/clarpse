package com.hadi.test.sourcemodel;

import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.Package;
import org.junit.Test;

import java.util.ConcurrentModificationException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pins the difference between the copying and the non-copying component accessors, in both
 * directions: that {@code copyOfComponent} still isolates its caller, and that {@code component}
 * deliberately does not.
 *
 * <p>The isolation half matters more than the speed half. A caller that mutates what
 * {@code copyOfComponent} hands it must go on being unable to reach the model, or this optimisation
 * becomes a class of mutation bug that no test would catch.
 */
public class ComponentAccessorsTest {

    private static OOPSourceCodeModel modelWithOneClass() {
        OOPSourceCodeModel model = new OOPSourceCodeModel();
        Component cls = new Component();
        cls.setComponentType(ComponentType.CLASS);
        cls.setComponentName("Widget");
        cls.setName("Widget");
        cls.setPkg(new Package("app", "app"));
        cls.insertChildComponent("app.Widget.field");
        model.insertComponent(cls);

        Component field = new Component();
        field.setComponentType(ComponentType.FIELD);
        field.setComponentName("Widget.field");
        field.setName("field");
        field.setPkg(new Package("app", "app"));
        model.insertComponent(field);
        return model;
    }

    @Test
    public void copyOfComponentStillHandsBackAnIsolatedCopy() {
        OOPSourceCodeModel model = modelWithOneClass();

        Component first = model.copyOfComponent("app.Widget").orElseThrow();
        Component second = model.copyOfComponent("app.Widget").orElseThrow();
        assertNotSame("copyOfComponent must not hand out the model's own instance", first, second);

        first.insertChildComponent("app.Widget.mutated");
        assertFalse("mutating a copy must not reach the model",
                model.copyOfComponent("app.Widget").orElseThrow().children()
                        .contains("app.Widget.mutated"));
    }

    @Test
    public void componentHandsBackTheModelsOwnInstance() {
        OOPSourceCodeModel model = modelWithOneClass();

        Component first = model.component("app.Widget").orElseThrow();
        Component second = model.component("app.Widget").orElseThrow();
        assertSame("component() must not copy", first, second);

        first.insertChildComponent("app.Widget.added");
        assertTrue("mutating a live component must reach the model",
                model.copyOfComponent("app.Widget").orElseThrow().children()
                        .contains("app.Widget.added"));
    }

    @Test
    public void componentIsEmptyForAnAbsentName() {
        assertTrue(modelWithOneClass().component("app.Missing").isEmpty());
        assertTrue(modelWithOneClass().copyOfComponent("app.Missing").isEmpty());
    }

    @Test
    public void parentBaseComponentFindsTheSameComponentAsTheCopyingWalk() {
        OOPSourceCodeModel model = modelWithOneClass();

        Component live = model.parentBaseComponent("app.Widget.field");
        Component copied = model.copyOfParentBaseComponent("app.Widget.field");

        assertEquals("app.Widget", live.uniqueName());
        assertEquals(copied.uniqueName(), live.uniqueName());
        assertSame("the live walk returns the model's own instance",
                model.component("app.Widget").orElseThrow(), live);
        assertNotSame("the copying walk still copies",
                model.component("app.Widget").orElseThrow(), copied);
    }

    @Test
    public void copyOfParentBaseComponentStillThrowsForAnUnrootedName() {
        OOPSourceCodeModel model = modelWithOneClass();
        assertThrows(IllegalArgumentException.class, () -> model.copyOfParentBaseComponent("nothing.Here"));
        assertThrows(IllegalArgumentException.class, () -> model.parentBaseComponent("nothing.Here"));
    }

    @Test
    public void childrenIsAViewAndRefusesMutationThroughIt() {
        OOPSourceCodeModel model = modelWithOneClass();
        Component live = model.component("app.Widget").orElseThrow();

        List<String> view = live.children();
        assertThrows(UnsupportedOperationException.class, () -> view.add("app.Widget.sneaked"));

        live.insertChildComponent("app.Widget.second");
        assertTrue("the view reflects the component it came from",
                view.contains("app.Widget.second"));
    }

    @Test
    public void iteratingChildrenWhileInsertingFailsLoudly() {
        OOPSourceCodeModel model = modelWithOneClass();
        Component live = model.component("app.Widget").orElseThrow();

        assertThrows(ConcurrentModificationException.class, () -> {
            for (String child : live.children()) {
                live.insertChildComponent(child + ".copy");
            }
        });
    }

    @Test
    public void dependencyAndModifierAccessorsAreUnmodifiableViews() {
        OOPSourceCodeModel model = modelWithOneClass();
        Component live = model.component("app.Widget").orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> live.imports().add("java.util.List"));
        assertThrows(UnsupportedOperationException.class, () -> live.modifiers().add("public"));
        assertThrows(UnsupportedOperationException.class,
                () -> live.internalDependencies().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> live.externalDependencies().clear());
    }
}
