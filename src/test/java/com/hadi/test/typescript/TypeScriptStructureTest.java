package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptStructureTest {

    @Test
    public void classMembersAndCycloAreModeled() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("simple");

        Component widget = result.model().getComponent("src.structs.Widget.Widget").orElseThrow();
        assertTrue(widget.comment().contains("Widget doc"));
        assertTrue(widget.modifiers().contains("export"));

        Component field = result.model().getComponent("src.structs.Widget.Widget.id").orElseThrow();
        assertTrue(field.comment().contains("field doc"));
        assertTrue(field.modifiers().contains("readonly"));

        Component method = result.model().getComponent("src.structs.Widget.Widget.compute(boolean)").orElseThrow();
        assertTrue(method.comment().contains("method doc"));
        assertEquals(4, method.cyclo());
        assertTrue(method.children().contains("src.structs.Widget.Widget.compute(boolean).flag"));
    }
}
