package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
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
 * A call on a receiver whose type follows exactly from a written type argument -- the field of a
 * generic type, the variable of a loop over a collection, the parameter of a lambda given to one, an
 * element of one -- is a reference to the member on that type. A wildcard, a raw type, a type
 * variable and an element type that cannot be resolved give nothing.
 */
public class JavaTypeArgumentMemberReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/main/java/app/A.java", """
                package app;
                import java.util.List;
                public class A {
                  static class Order { int total() { return 1; } }
                  static class Box<T> { T value; }
                  int fieldOfGeneric(Box<Order> b) { return b.value.total(); }
                  int foreachVar(List<Order> l) { int r = 0; for (Order o : l) { r += o.total(); } return r; }
                  int foreachVarInferred(List<Order> l) { int r = 0; for (var o : l) { r += o.total(); } return r; }
                  void lambdaParam(List<Order> l) { l.forEach(o -> o.total()); }
                  int indexed(List<Order> l) { return l.get(0).total(); }
                  int raw(Box b) { return b.hashCode(); }
                  int wildcard(Box<? extends Order> b) { return b.value.total(); }
                  <T extends Order> int typeVariable(Box<T> b) { return b.value.total(); }
                  int unresolved(Box<Missing> b) { return b.value.total(); }
                  int unresolvedList(List<Missing> l) { return l.get(0).total(); }
                  int wildcardList(List<? extends Order> l) { return l.get(0).total(); }
                }
                """));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static List<MemberReference> members(final String method) {
        return model.components().filter(c -> c.uniqueName().startsWith("app.A." + method + "("))
                .filter(c -> c.componentType().isMethodComponent())
                .flatMap(c -> c.references().stream())
                .filter(MemberReference.class::isInstance).map(MemberReference.class::cast)
                .collect(Collectors.toList());
    }

    private static void assertCallsTotalOnOrder(final String method) {
        final List<MemberReference> found = members(method).stream().filter(r -> r.member().equals("total"))
                .collect(Collectors.toList());
        assertEquals(method + " " + found, 1, found.size());
        assertEquals("app.A.Order", found.get(0).ownerType());
        assertEquals(ResolutionKind.EXACT, found.get(0).resolutionKind());
    }

    private static void assertNoTotal(final String method) {
        assertTrue(method + " " + members(method),
                members(method).stream().noneMatch(r -> r.member().equals("total")));
    }

    @Test
    public void aFieldOfAGenericTypeWhoseArgumentIsWritten() {
        assertCallsTotalOnOrder("fieldOfGeneric");
    }

    @Test
    public void aLoopVariableWithItsElementTypeWrittenOrInferred() {
        assertCallsTotalOnOrder("foreachVar");
        assertCallsTotalOnOrder("foreachVarInferred");
    }

    @Test
    public void aLambdaParameterOfACollectionOfAWrittenType() {
        assertCallsTotalOnOrder("lambdaParam");
    }

    @Test
    public void anElementOfACollectionOfAWrittenType() {
        assertCallsTotalOnOrder("indexed");
    }

    @Test
    public void aWildcardATypeVariableAndAnUnresolvedElementGiveNothing() {
        assertNoTotal("wildcard");
        assertNoTotal("typeVariable");
        assertNoTotal("unresolved");
        assertNoTotal("unresolvedList");
        assertNoTotal("wildcardList");
    }

    @Test
    public void aRawTypeGivesNothing() {
        assertNoTotal("raw");
    }
}
