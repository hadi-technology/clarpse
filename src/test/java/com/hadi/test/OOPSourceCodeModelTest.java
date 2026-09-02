package com.hadi.test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;

import java.util.concurrent.CancellationException;

public class OOPSourceCodeModelTest {

    /**
     * Cooperative cancellation (#178): merging a large model is the CPU wedge where a caller
     * enforcing a deadline otherwise sits past its interrupt. An interrupted merge must abort rather
     * than run to completion, and it must not leave a partial model behind.
     */
    @Test
    public void mergeAbortsWhenTheCallingThreadIsInterrupted() {
        OOPSourceCodeModel target = new OOPSourceCodeModel();
        OOPSourceCodeModel incoming = new OOPSourceCodeModel();
        Component component = new Component();
        component.setComponentName("Test");
        incoming.insertComponent(component);

        Thread.currentThread().interrupt();
        try {
            target.merge(incoming);
            fail("merge should abort when the calling thread is interrupted");
        } catch (CancellationException expected) {
            // The upfront check aborts before the first component is inserted.
            assertEquals(0, target.size());
        } finally {
            // Clear the flag so it does not leak into other tests sharing this thread.
            Thread.interrupted();
        }
    }

    /** The near-miss: an uninterrupted merge is unaffected and completes normally. */
    @Test
    public void mergeSucceedsWhenTheThreadIsNotInterrupted() {
        Thread.interrupted(); // guard against a stray flag from another test on this thread
        OOPSourceCodeModel target = new OOPSourceCodeModel();
        OOPSourceCodeModel incoming = new OOPSourceCodeModel();
        Component component = new Component();
        component.setComponentName("Test");
        incoming.insertComponent(component);

        target.merge(incoming);

        assertEquals(1, target.size());
    }

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

        assertEquals(testComponent, codeModel.copyOfParentBaseComponent(componentB.uniqueName()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parentBaseCmpTestTThrows() {
        OOPSourceCodeModel childCmp = new OOPSourceCodeModel();
        Component component = new Component();
        component.setComponentName("Test");
        childCmp.copyOfParentBaseComponent("Test");
    }
}
