package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** An array or a pointer is not its element type: it is read as a type nothing is known about. */
public class CSharpArrayReceiverTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        using System.Collections.Generic;
                        namespace App {
                          public class Order { }
                          public class Path { public Segment Length; }
                          public class Segment { }
                          public static class OrderExt { public static void ToDto(this Order o) { } }
                          public static class OrdersExt { public static void ToDto(this IEnumerable<Order> o) { } }
                        }
                        """),
                new ProjectFile("/Uses.cs", """
                        namespace App {
                          public class Uses
                          {
                            public void Check(Order[] orders) { orders.ToDto(); }
                            public void Jagged(Order[][] orders) { orders.ToDto(); }
                            public void Grid(Order[,] orders) { orders.ToDto(); }
                            public unsafe void Pointer(Order* orders) { orders.ToDto(); }
                            public void Single(Order order) { order.ToDto(); }
                            public void Length(Path[] paths) { var n = paths.Length; }
                            public void LengthOfOne(Path path) { var n = path.Length; }
                          }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void singleOrderIsCreditedToItsExtensionClass() {
        assertTrue(references("App.Uses.Single(Order)", "App.OrderExt"));
    }

    @Test
    public void arrayOfOrdersIsNotAnOrder() {
        assertFalse(references("App.Uses.Check(Order[])", "App.OrderExt"));
        assertFalse(references("App.Uses.Jagged(Order[][])", "App.OrderExt"));
        assertFalse(references("App.Uses.Grid(Order[,])", "App.OrderExt"));
    }

    @Test
    public void pointerToOrderIsNotAnOrder() {
        assertFalse(references("App.Uses.Pointer(Order*)", "App.OrderExt"));
    }

    @Test
    public void memberOfAnElementTypeIsNotReadOnAnArray() {
        assertTrue(references("App.Uses.LengthOfOne(Path)", "App.Segment"));
        assertFalse(references("App.Uses.Length(Path[])", "App.Segment"));
    }
}
