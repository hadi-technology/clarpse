package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.test.DeclaredTypes;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The declared type of a Java method, field and parameter is the one named type it is written with. */
public class JavaDeclaredTypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/a/Repo.java", """
                package a;
                import java.util.List;
                import java.util.Map;
                import b.Elsewhere;
                public class Repo<E> {
                  public List<Order> orders() { return null; }
                  public Order find(String id) { return null; }
                  public void clear() {}
                  public <T extends Order> T pick() { return null; }
                  public Order[] all() { return null; }
                  public E element() { return null; }
                  public int count() { return 0; }
                  public Map.Entry<String, Order> entry() { return null; }
                  public Order held;
                  public Order[] heldMany;
                  public Elsewhere other() { return null; }
                  public void take(Order o, int n, Order... rest) {}
                }
                class Order { public Customer owner() { return null; } }
                class Customer {}
                """));
        files.insertFile(new ProjectFile("/b/Elsewhere.java", "package b;\npublic class Elsewhere {}\n"));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @Test
    public void aMethodReturnsOneNamedType() {
        assertEquals("a.Order", DeclaredTypes.of(model, "a.Repo", "find"));
        assertEquals("a.Customer", DeclaredTypes.of(model, "a.Order", "owner"));
    }

    @Test
    public void aGenericTypeIsErased() {
        assertEquals("java.util.List", DeclaredTypes.of(model, "a.Repo", "orders"));
    }

    @Test
    public void nothingTypeVariableArrayOrPrimitiveIsNull() {
        assertNull(DeclaredTypes.of(model, "a.Repo", "clear"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "pick"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "all"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "element"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "count"));
        assertNull(DeclaredTypes.of(model, "a.Repo", "heldMany"));
    }

    @Test
    public void aFieldIsDeclaredWithItsWrittenType() {
        assertEquals("a.Order", DeclaredTypes.of(model, "a.Repo", "held"));
    }

    @Test
    public void aParameterIsDeclaredWithItsWrittenType() {
        assertEquals("a.Order", DeclaredTypes.parameter(model, "a.Repo.take(", "o").invokedComponent());
        assertNull(DeclaredTypes.parameter(model, "a.Repo.take(", "n"));
        assertNull(DeclaredTypes.parameter(model, "a.Repo.take(", "rest"));
    }

    @Test
    public void aTypeOfAnotherFileOfTheProjectIsInternalAndALibraryTypeIsExternal() {
        assertEquals("b.Elsewhere", DeclaredTypes.of(model, "a.Repo", "other"));
        assertTrue(!DeclaredTypes.member(model, "a.Repo", "other").declaredType().isExternal());
        assertTrue(DeclaredTypes.member(model, "a.Repo", "orders").declaredType().isExternal());
    }

    @Test
    public void theDeclaredTypeIsOneOfTheReferences() {
        DeclaredTypes.assertContainedInReferences(model);
    }
}
