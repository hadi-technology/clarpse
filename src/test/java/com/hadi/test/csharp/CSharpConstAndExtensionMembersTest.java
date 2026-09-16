package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpConstAndExtensionMembersTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Members.cs", """
                        namespace Demo;
                        public class Tenant {
                          public const string DefaultTenantId = "";
                          private const int Limit = 10;
                          public static readonly string Other = "x";
                        }
                        public class Widget {
                          public string Label() { return ""; }
                        }
                        public static class WidgetExtensions {
                          public static void Reset(this Widget w) {}
                          public static string Slug(this string s) { return s; }
                        }
                        """)
        ).model();
    }

    @Test
    public void constFieldIsAMemberOfItsType() {
        final Component constant = model.copyOfComponent("Demo.Tenant.DefaultTenantId").orElseThrow();
        assertEquals(ComponentType.FIELD, constant.componentType());
        assertTrue(constant.modifiers().contains("const"));
        assertTrue(constant.modifiers().contains("public"));
        assertTrue(model.copyOfComponent("Demo.Tenant").orElseThrow().children()
                .contains("Demo.Tenant.DefaultTenantId"));
    }

    @Test
    public void privateConstFieldIsAlsoAMember() {
        final Component constant = model.copyOfComponent("Demo.Tenant.Limit").orElseThrow();
        assertEquals(ComponentType.FIELD, constant.componentType());
        assertTrue(constant.modifiers().contains("private"));
    }

    @Test
    public void nonConstFieldsAreUnaffected() {
        assertTrue(model.containsComponent("Demo.Tenant.Other"));
    }

    /**
     * Callers write `widget.Reset()`, so the extended type has to be able to find the method. It
     * stays on the static class that declares it as well.
     */
    @Test
    public void anExtensionMethodIsReachableFromTheTypeItExtends() {
        assertTrue(model.containsComponent("Demo.WidgetExtensions.Reset(Widget)"));
        assertTrue(invokes(model.copyOfComponent("Demo.Widget").orElseThrow(),
                "Demo.WidgetExtensions.Reset(Widget)"));
    }

    /** An extension on a framework type resolves to nothing this model holds, and is left alone. */
    @Test
    public void anExtensionOnANonRepositoryTypeLinksNothing() {
        assertTrue(model.containsComponent("Demo.WidgetExtensions.Slug(string)"));
        model.components().forEach(component -> assertTrue(
                "nothing should claim to declare an extension on a framework type",
                !component.uniqueName().startsWith("System.")));
    }

    private static boolean invokes(final Component component, final String invoked) {
        for (final ComponentReference reference : component.references()) {
            if (invoked.equals(reference.invokedComponent())) {
                return true;
            }
        }
        return false;
    }
}
