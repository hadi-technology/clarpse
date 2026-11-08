package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A type parameter, and the constraint written after {@code where}, are never supertypes of the type
 * that declares them.
 */
public class CSharpTypeParameterSupertypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(new ProjectFile("/Types.cs", """
                namespace Demo;
                public abstract class UserBase { }
                public interface IAudited<T> { }
                public abstract class User<TUser> : UserBase, IAudited<TUser> where TUser : User<TUser> { }
                public class Shape { }
                public class Base { }
                public class Constraint { }
                public class Sub<T> : Base where T : Constraint, new() { }
                public interface ITransport<TSelf> where TSelf : ITransport<TSelf> { }
                public class Gen<A, B> { }
                public class Graph<TShape> : Gen<TShape, int> where TShape : Shape { }
                """)).model();
    }

    private static List<String> targets(final String type, final TypeReferences kind) {
        return model.copyOfComponent(type).orElseThrow().references(kind).stream()
                .map(ComponentReference::invokedComponent).sorted().collect(Collectors.toList());
    }

    private static List<String> allTargets(final String type) {
        return model.copyOfComponent(type).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).sorted().collect(Collectors.toList());
    }

    @Test
    public void onlyTheBaseListNamesSupertypes() {
        assertEquals(List.of("Demo.UserBase"), targets("Demo.User", TypeReferences.EXTENSION));
        assertEquals(List.of("Demo.IAudited"), targets("Demo.User", TypeReferences.IMPLEMENTATION));
        assertEquals(List.of("Demo.Base"), targets("Demo.Sub", TypeReferences.EXTENSION));
        assertEquals(List.of("Demo.Gen"), targets("Demo.Graph", TypeReferences.EXTENSION));
    }

    @Test
    public void interfaceWithOnlyAConstraintHasNoSupertypes() {
        assertEquals(List.of(), targets("Demo.ITransport", TypeReferences.EXTENSION));
        assertEquals(List.of(), targets("Demo.ITransport", TypeReferences.IMPLEMENTATION));
    }

    @Test
    public void typeParametersAreNotReferences() {
        for (final String type : List.of("Demo.User", "Demo.Sub", "Demo.ITransport", "Demo.Graph")) {
            assertTrue(type + " " + allTargets(type),
                    allTargets(type).stream().noneMatch(t -> t.startsWith("T") && !t.contains(".")));
        }
    }

    @Test
    public void aConstraintIsAPlainReference() {
        assertTrue(targets("Demo.Sub", TypeReferences.SIMPLE).contains("Demo.Constraint"));
        assertTrue(targets("Demo.Graph", TypeReferences.SIMPLE).contains("Demo.Shape"));
    }

    @Test
    public void typeParametersOfTheTypeAndOfItsMethodsAreNotReferencesOfItsMembers() throws Exception {
        final OOPSourceCodeModel generic = CSharpTestUtil.compileInline(new ProjectFile("/G.cs", """
                namespace Demo;
                public class Holder<TItem> {
                  public TItem Value;
                  public TItem Get() { return Value; }
                  public TOut Map<TOut>(TItem item) { TOut result = default; return result; }
                }
                """)).model();
        for (final String member : List.of("Demo.Holder.Value", "Demo.Holder.Get()", "Demo.Holder.Map<TOut>(TItem)")) {
            assertEquals(member, List.of(), generic.copyOfComponent(member).orElseThrow().references().stream()
                    .map(ComponentReference::invokedComponent).sorted().collect(Collectors.toList()));
        }
    }
}
