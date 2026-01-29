package com.hadi.test;

import static junit.framework.TestCase.assertEquals;

import org.junit.Test;

import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;

public class OOPSourceCodeModelTest {

    @Test
    public void codeModelCopyTest() {
        OOPSourceCodeModel original = new OOPSourceCodeModel();
        Component component = new Component();
        component.setComponentName("Test");
        original.insertComponent(component);
        OOPSourceCodeModel copy = original.copy();
        assertEquals(true, copy.size() == original.size());
        assertEquals(1, copy.size());
    }

    @Test
    public void codeModelTrueCopyTest() {
        OOPSourceCodeModel original = new OOPSourceCodeModel();
        Component component = new Component();
        component.setComponentName("Test");
        original.insertComponent(component);
        OOPSourceCodeModel copy = original.copy();
        copy.removeComponent(component.uniqueName());
        assertEquals(false, copy.size() == original.size());
        assertEquals(1, original.size());
        assertEquals(0, copy.size());
    }

    @Test
    public void parentBaseCmpTest() {
        OOPSourceCodeModel codeModel = new OOPSourceCodeModel();
        Component testComponent = new Component();
        testComponent.setComponentName("Test");
        testComponent.insertChildComponent("ChildA");
        testComponent.setComponentType(ComponentType.CLASS);

        Component componentA = new Component();
        componentA.setComponentName("Test.ChildA");
        componentA.insertChildComponent("ChildB");
        componentA.setComponentType(ComponentType.METHOD);

        Component componentB = new Component();
        componentB.setComponentName("Test.ChildA.ChildB");
        componentB.setComponentType(ComponentType.LOCAL);

        codeModel.insertComponent(testComponent);
        codeModel.insertComponent(componentA);
        codeModel.insertComponent(componentB);

        assertEquals(testComponent, codeModel.parentBaseCmp(componentB.uniqueName()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parentBaseCmpTestTThrows() {
        OOPSourceCodeModel childCmp = new OOPSourceCodeModel();
        Component component = new Component();
        component.setComponentName("Test");
        childCmp.parentBaseCmp("Test");
    }
}
