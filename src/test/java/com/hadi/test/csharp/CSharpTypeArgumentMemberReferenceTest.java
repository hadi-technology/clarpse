package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A call on a receiver whose type follows exactly from a written type argument is a reference to the
 * member on that type: the field of a generic type, the variable of a loop over a collection and an
 * element of one. A type variable, an unresolved element type and a raw collection give nothing.
 */
public class CSharpTypeArgumentMemberReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(new ProjectFile("/A.cs", """
                using System.Collections.Generic;
                namespace App {
                  public class Order { public int Total() { return 1; } }
                  public class Box<T> { public T Value; }
                  public class A {
                    public int FieldOfGeneric(Box<Order> b) { return b.Value.Total(); }
                    public int ForeachVar(List<Order> l) { var r = 0; foreach (var o in l) { r += o.Total(); } return r; }
                    public int ForeachTyped(List<Order> l) { var r = 0; foreach (Order o in l) { r += o.Total(); } return r; }
                    public void LambdaParam(List<Order> l) { l.ForEach(o => o.Total()); }
                    public int Indexed(List<Order> l) { return l[0].Total(); }
                    public int TypeVariable<T>(Box<T> b) { return b.Value.Total(); }
                    public int Unresolved(Box<Missing> b) { return b.Value.Total(); }
                    public int UnresolvedList(List<Missing> l) { return l[0].Total(); }
                    public int Raw(Box b) { return b.Value.Total(); }
                  }
                }
                """)).model();
    }

    private static List<MemberReference> totals(final String method) {
        return model.components().filter(c -> c.uniqueName().startsWith("App.A." + method + "("))
                .filter(c -> c.componentType().isMethodComponent())
                .flatMap(c -> c.references().stream())
                .filter(MemberReference.class::isInstance).map(MemberReference.class::cast)
                .filter(r -> r.member().equals("Total")).collect(Collectors.toList());
    }

    private static void assertTotalOnOrder(final String method) {
        final List<MemberReference> found = totals(method);
        assertEquals(method + " " + found, 1, found.size());
        assertEquals("App.Order", found.get(0).ownerType());
        assertEquals(ResolutionKind.EXACT, found.get(0).resolutionKind());
    }

    @Test
    public void aFieldOfAGenericTypeWhoseArgumentIsWritten() {
        assertTotalOnOrder("FieldOfGeneric");
    }

    @Test
    public void aLoopVariableWithItsElementTypeWritten() {
        assertTotalOnOrder("ForeachTyped");
    }

    @Test
    public void aLoopVariableWithItsElementTypeInferred() {
        assertTotalOnOrder("ForeachVar");
    }

    @Test
    public void aLambdaParameterOfACollectionOfAWrittenType() {
        assertTotalOnOrder("LambdaParam");
    }

    @Test
    public void anElementOfACollectionOfAWrittenType() {
        assertTotalOnOrder("Indexed");
    }

    @Test
    public void aTypeVariableAnUnresolvedElementAndARawTypeGiveNothing() {
        for (final String method : List.of("TypeVariable", "Unresolved", "UnresolvedList", "Raw")) {
            assertTrue(method + " " + totals(method), totals(method).isEmpty());
        }
    }
}
