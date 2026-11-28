package com.hadi.test.kotlin;

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
 * member on that type: the property of a generic type, the variable of a loop over a collection, the
 * implicit parameter of a lambda given to one and an element of one. A type parameter, an unresolved
 * element type and a collection of anything else give nothing.
 */
public class KotlinTypeArgumentMemberReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = KotlinTestUtil.compileInline(new ProjectFile("/app/A.kt", String.join("\n",
                "package app",
                "class Order { fun total() = 1 }",
                "class Box<T>(val value: T)",
                "class A {",
                "  fun fieldOfGeneric(b: Box<Order>): Int = b.value.total()",
                "  fun foreachVar(l: List<Order>): Int { var r = 0; for (o in l) { r += o.total() }; return r }",
                "  fun foreachTyped(l: List<Order>): Int { var r = 0; for (o: Order in l) { r += o.total() }; return r }",
                "  fun lambdaParam(l: List<Order>) { l.forEach { it.total() } }",
                "  fun lambdaNamed(l: List<Order>) { l.forEach { o -> o.total() } }",
                "  fun indexed(l: List<Order>): Int = l[0].total()",
                "  fun <T> typeVariable(b: Box<T>): Int = b.value.total()",
                "  fun unresolved(l: List<Missing>): Int { var r = 0; for (o in l) { r += o.total() }; return r }",
                "  fun unresolvedIndexed(l: List<Missing>): Int = l[0].total()",
                "}"))).model();
    }

    private static List<MemberReference> totals(final String function) {
        return model.components().filter(c -> c.uniqueName().startsWith("app.A." + function + "("))
                .filter(c -> c.componentType().isMethodComponent())
                .flatMap(c -> c.references().stream())
                .filter(MemberReference.class::isInstance).map(MemberReference.class::cast)
                .filter(r -> r.member().equals("total")).collect(Collectors.toList());
    }

    private static void assertTotalOnOrder(final String function) {
        final List<MemberReference> found = totals(function);
        assertEquals(function + " " + found, 1, found.size());
        assertEquals("app.Order", found.get(0).ownerType());
        assertEquals(ResolutionKind.EXACT, found.get(0).resolutionKind());
    }

    @Test
    public void aPropertyOfAGenericTypeWhoseArgumentIsWritten() {
        assertTotalOnOrder("fieldOfGeneric");
    }

    @Test
    public void aLoopVariable() {
        assertTotalOnOrder("foreachVar");
        assertTotalOnOrder("foreachTyped");
    }

    @Test
    public void aLambdaParameter() {
        assertTotalOnOrder("lambdaParam");
        assertTotalOnOrder("lambdaNamed");
    }

    @Test
    public void anElementOfACollection() {
        assertTotalOnOrder("indexed");
    }

    @Test
    public void aTypeParameterAndAnUnresolvedElementGiveNothing() {
        for (final String function : List.of("typeVariable", "unresolved", "unresolvedIndexed")) {
            assertTrue(function + " " + totals(function), totals(function).isEmpty());
        }
    }
}
