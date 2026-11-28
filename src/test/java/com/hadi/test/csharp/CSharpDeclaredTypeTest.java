package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.test.DeclaredTypes;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The declared type of a C# method, property, field and parameter is the one named type it is written with. */
public class CSharpDeclaredTypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Repo.cs", """
                        using System.Collections.Generic;
                        using Lib;
                        namespace A {
                          public class Repo<T> {
                            public List<Order> Orders() { return null; }
                            public Order Find(string id) { return null; }
                            public void Clear() {}
                            public Order Current { get; }
                            public Order Field;
                            public Order? Maybe() { return null; }
                            public Order[] All() { return null; }
                            public T Element() { return default; }
                            public (Order, int) Pair() { return default; }
                            public int Count() { return 0; }
                            public Shown Remote() { return null; }
                            public ref Order Ref() { throw null; }
                          }
                          public class Order {}
                        }
                        """),
                new ProjectFile("/Shown.cs", "namespace Lib { public class Shown {} }\n")).model();
    }

    @Test
    public void aMethodAPropertyAndAFieldAreDeclaredWithTheirType() {
        assertEquals("A.Order", DeclaredTypes.of(model, "A.Repo", "Find"));
        assertEquals("A.Order", DeclaredTypes.of(model, "A.Repo", "Current"));
        assertEquals("A.Order", DeclaredTypes.of(model, "A.Repo", "Field"));
        assertEquals("A.Order", DeclaredTypes.of(model, "A.Repo", "Maybe"));
        assertEquals("A.Order", DeclaredTypes.of(model, "A.Repo", "Ref"));
    }

    @Test
    public void aGenericTypeIsErasedAndATypeOfAnotherFileIsInternal() {
        assertEquals("List", DeclaredTypes.of(model, "A.Repo", "Orders"));
        assertTrue(DeclaredTypes.member(model, "A.Repo", "Orders").declaredType().isExternal());
        assertEquals("Lib.Shown", DeclaredTypes.of(model, "A.Repo", "Remote"));
        assertFalse(DeclaredTypes.member(model, "A.Repo", "Remote").declaredType().isExternal());
    }

    @Test
    public void voidArrayTupleAndTypeParameterAreNull() {
        assertNull(DeclaredTypes.of(model, "A.Repo", "Clear"));
        assertNull(DeclaredTypes.of(model, "A.Repo", "All"));
        assertNull(DeclaredTypes.of(model, "A.Repo", "Pair"));
        assertNull(DeclaredTypes.of(model, "A.Repo", "Element"));
    }

    @Test
    public void aParameterIsDeclaredWithItsWrittenType() {
        assertEquals("System.String", DeclaredTypes.parameter(model, "A.Repo.Find(", "id").invokedComponent());
    }

    @Test
    public void theDeclaredTypeIsOneOfTheReferences() {
        DeclaredTypes.assertContainedInReferences(model);
    }
}
