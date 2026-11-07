package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Covers the type arguments of a base-list entry: only the base type is extended or implemented,
 * and each argument is an ordinary reference.
 */
public class CSharpGenericBaseTypeArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(new ProjectFile("/Bases.cs", """
                namespace Demo;
                public class Base<T> {}
                public class Plain {}
                public class Arg {}
                public class Inner {}
                public class Wrapper<T> {}
                public interface IHandler<T> {}
                public interface IMarker {}
                public class Derived : Base<Arg> {}
                public class Nested : Base<Wrapper<Inner>> {}
                public class Impl : Plain, IHandler<Arg>, IMarker {}
                public interface IChild : IHandler<Arg> {}
                public record Rec : Base<Arg>;
                """)).model();
    }

    private static List<String> targets(final String component, final TypeReferences kind) {
        return model.copyOfComponent(component).get().references(kind).stream()
                .map(ComponentReference::invokedComponent).sorted().toList();
    }

    @Test
    public void typeArgumentOfABaseClassIsAPlainReference() {
        assertEquals(List.of("Demo.Base"), targets("Demo.Derived", TypeReferences.EXTENSION));
        assertEquals(List.of("Demo.Arg"), targets("Demo.Derived", TypeReferences.SIMPLE));
        assertEquals(List.of(), targets("Demo.Derived", TypeReferences.IMPLEMENTATION));
    }

    @Test
    public void nestedTypeArgumentsAreAllPlainReferences() {
        assertEquals(List.of("Demo.Base"), targets("Demo.Nested", TypeReferences.EXTENSION));
        assertEquals(List.of("Demo.Inner", "Demo.Wrapper"), targets("Demo.Nested", TypeReferences.SIMPLE));
    }

    @Test
    public void typeArgumentOfABaseInterfaceIsAPlainReference() {
        assertEquals(List.of("Demo.Plain"), targets("Demo.Impl", TypeReferences.EXTENSION));
        assertEquals(List.of("Demo.IHandler", "Demo.IMarker"), targets("Demo.Impl", TypeReferences.IMPLEMENTATION));
        assertEquals(List.of("Demo.Arg"), targets("Demo.Impl", TypeReferences.SIMPLE));
    }

    @Test
    public void interfaceInheritingAGenericInterfaceKeepsItsArgumentPlain() {
        assertEquals(List.of("Demo.IHandler"), targets("Demo.IChild", TypeReferences.EXTENSION));
        assertEquals(List.of(), targets("Demo.IChild", TypeReferences.IMPLEMENTATION));
        assertEquals(List.of("Demo.Arg"), targets("Demo.IChild", TypeReferences.SIMPLE));
    }

    @Test
    public void recordBaseTypeArgumentIsAPlainReference() {
        assertEquals(List.of("Demo.Base"), targets("Demo.Rec", TypeReferences.EXTENSION));
        assertEquals(List.of("Demo.Arg"), targets("Demo.Rec", TypeReferences.SIMPLE));
    }
}
