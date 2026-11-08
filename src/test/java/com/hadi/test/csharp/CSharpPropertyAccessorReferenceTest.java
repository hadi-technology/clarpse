package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A type named inside a property accessor body is a reference of that property and of its type. */
public class CSharpPropertyAccessorReferenceTest {

    private static final String SOURCE = """
            namespace Demo;
            public static class Settings { public static Defaults Defaults { get; } }
            public class Defaults { public string Name { get; set; } }
            public class Gateway { public static int Limit() { return 1; } }
            public class Holder {
              public static string Forced {
                get => Settings.Defaults.Name;
                set { Settings.Defaults.Name = value; }
              }
              public int Computed { get { return Gateway.Limit(); } }
              public string Arrow => Gateway.Limit().ToString();
              public int Plain { get; set; }
            }
            """;

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void getterAndSetterBodiesAreScanned() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Holder.cs", SOURCE)).model();
        assertTrue(references(model, "Demo.Holder.Forced", "Demo.Settings"));
        assertTrue(references(model, "Demo.Holder", "Demo.Settings"));
    }

    @Test
    public void blockBodiedGetterIsScanned() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Holder.cs", SOURCE)).model();
        assertTrue(references(model, "Demo.Holder.Computed", "Demo.Gateway"));
    }

    @Test
    public void expressionBodiedPropertyIsScanned() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Holder.cs", SOURCE)).model();
        assertTrue(references(model, "Demo.Holder.Arrow", "Demo.Gateway"));
    }

    @Test
    public void autoPropertyGainsNoReference() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Holder.cs", SOURCE)).model();
        assertFalse(references(model, "Demo.Holder.Plain", "Demo.Gateway"));
    }
}
