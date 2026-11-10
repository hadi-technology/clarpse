package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A {@code var} local has the type of a {@code new} expression only when the whole initializer is
 * that one expression; a {@code new} somewhere inside a longer initializer says nothing of the type.
 */
public class CSharpVarLocalNewInitializerTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        namespace App {
                          public class CustomerBuilder { }
                          public class Order { }
                          public class OrderBuilder
                          {
                            public CustomerBuilder Customer;
                            public OrderBuilder WithId(int id) { return this; }
                            public Order Build() { return null; }
                          }
                          public class GetX { public CustomerBuilder Customer; }
                          public class Mediator { public Order Send(GetX x) { return null; } }
                        }
                        """),
                new ProjectFile("/Uses.cs", """
                        namespace App {
                          public class Plain { public void M() { var b = new OrderBuilder(); var c = b.Customer; } }
                          public class Initialized { public void M() { var b = new OrderBuilder { Customer = null }; var c = b.Customer; } }
                          public class Chained { public void M() { var order = new OrderBuilder().WithId(1).Build(); var c = order.Customer; } }
                          public class Wrapped { public void M(Mediator m) { var r = m.Send(new GetX()); var c = r.Customer; } }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void plainNewInitializerTypesTheLocal() {
        assertTrue(references("App.Plain.M()", "App.CustomerBuilder"));
    }

    @Test
    public void newWithAnObjectInitializerTypesTheLocal() {
        assertTrue(references("App.Initialized.M()", "App.CustomerBuilder"));
    }

    @Test
    public void newOnlyAtTheStartOfAChainDoesNotTypeTheLocal() {
        assertFalse(references("App.Chained.M()", "App.CustomerBuilder"));
    }

    @Test
    public void newAsAnArgumentDoesNotTypeTheLocal() {
        assertFalse(references("App.Wrapped.M(Mediator)", "App.CustomerBuilder"));
    }
}
