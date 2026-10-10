package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A type and its namesake of another arity in one scope ({@code Converter} and
 * {@code Converter<T>}) are two components. A generic type carries its arity in its unique name, in
 * CLR metadata form ({@code Acme.Converter`1}), only when its name is shared with a type of another
 * arity; a reference binds to the type whose arity matches the type arguments written with it.
 */
public class CSharpArityOverloadedTypesTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Converter.cs", """
                        namespace Acme;

                        public abstract class Converter
                        {
                            internal abstract void WriteObject(object value);
                        }

                        public abstract class Converter<T> : Converter
                        {
                            public virtual void Write(T value) { }
                        }
                        """),
                new ProjectFile("/Converter2.cs", """
                        namespace Acme;

                        public abstract class Converter<TIn, TOut> : Converter<TIn>
                        {
                            public abstract TOut Convert(TIn value);
                        }
                        """),
                new ProjectFile("/Repo.cs", """
                        namespace Acme;

                        public class Repo<T> { }
                        """),
                new ProjectFile("/Comparers.cs", """
                        namespace Acme;

                        public interface IComparer { int Compare(object a, object b); }
                        public interface IComparer<T> { int Compare(T a, T b); }

                        public class LooseComparer : IComparer
                        {
                            public int Compare(object a, object b) { return 0; }
                        }

                        public class OrderComparer : IComparer<Order>
                        {
                            public int Compare(Order a, Order b) { return 0; }
                        }

                        public class Order { }
                        """),
                new ProjectFile("/Users.cs", """
                        using System;
                        namespace Acme.Users;

                        public class User
                        {
                            public Converter Plain { get; set; }
                            public Converter<int> Generic { get; set; }
                            public Converter<int, string> Pair { get; set; }
                            public Repo<int> Store { get; set; }
                            public void Build()
                            {
                                var made = new Converter<string>();
                            }
                        }
                        """),
                new ProjectFile("/Box.cs", """
                        namespace Acme;

                        public class Box { public int Width; }
                        public partial class Box<T> { public T First; }
                        """),
                new ProjectFile("/Box.Part.cs", """
                        namespace Acme;

                        public partial class Box<T> { public T Second; }
                        """),
                new ProjectFile("/Bag.cs", """
                        namespace Acme;

                        public partial class Bag<T> { public T First; }
                        """),
                new ProjectFile("/Bag.Part.cs", """
                        namespace Acme;

                        public partial class Bag<T> { public T Second; }
                        """),
                new ProjectFile("/Tree.cs", """
                        namespace Acme;

                        public class Tree
                        {
                            public class Node { }
                            public class Node<T> { public T Value; }
                            public Node Root;
                            public Node<int> Leaf;
                        }

                        public class Tree<T>
                        {
                            public class Node { }
                        }
                        """),
                new ProjectFile("/Tags.cs", """
                        using System;
                        namespace Acme;

                        public class Tag : Attribute { }
                        public class Tag<T> : Attribute { }

                        [Tag]
                        public class Tagged { }
                        """)
        ).model();
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName)
                .orElseThrow(() -> new AssertionError("no component " + uniqueName + " in "
                        + model.components().map(Component::uniqueName).sorted().collect(Collectors.toList())));
    }

    private static Set<String> targets(final String uniqueName) {
        return component(uniqueName).references().stream()
                .map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void aTypeAndItsGenericNamesakeAreTwoComponentsWithTheirOwnMembers() {
        assertEquals(List.of("Acme.Converter.WriteObject(object)"), component("Acme.Converter").children());
        assertEquals(List.of("Acme.Converter`1.Write(T)"), component("Acme.Converter`1").children());
        assertEquals(List.of("Acme.Converter`2.Convert(TIn)"), component("Acme.Converter`2").children());
        assertEquals("Converter", component("Acme.Converter`1").name());
    }

    @Test
    public void theGenericTypeExtendsItsNonGenericNamesake() {
        assertTrue(component("Acme.Converter`1").references(TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("Acme.Converter")));
        assertTrue(component("Acme.Converter`2").references(TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("Acme.Converter`1")));
    }

    @Test
    public void aReferenceWithNoTypeArgumentsBindsToTheNonGenericType() {
        assertEquals(Set.of("Acme.Converter"), targets("Acme.Users.User.Plain"));
    }

    @Test
    public void aReferenceWithOneTypeArgumentBindsToTheTypeOfArityOne() {
        assertTrue(targets("Acme.Users.User.Generic").contains("Acme.Converter`1"));
        assertFalse(targets("Acme.Users.User.Generic").contains("Acme.Converter"));
        assertTrue(targets("Acme.Users.User.Build()").contains("Acme.Converter`1"));
    }

    @Test
    public void aReferenceWithTwoTypeArgumentsBindsToTheTypeOfArityTwo() {
        assertTrue(targets("Acme.Users.User.Pair").contains("Acme.Converter`2"));
        assertFalse(targets("Acme.Users.User.Pair").contains("Acme.Converter`1"));
    }

    @Test
    public void aGenericTypeWhoseNameIsNotOverloadedKeepsItsErasedName() {
        assertTrue(model.containsComponent("Acme.Repo"));
        assertFalse(model.containsComponent("Acme.Repo`1"));
        assertTrue(targets("Acme.Users.User.Store").contains("Acme.Repo"));
    }

    @Test
    public void interfacesOverloadedByArityAreImplementedSeparately() {
        assertTrue(component("Acme.LooseComparer").references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Acme.IComparer")));
        assertTrue(component("Acme.OrderComparer").references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Acme.IComparer`1")));
        assertFalse(component("Acme.OrderComparer").references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Acme.IComparer")));
    }

    @Test
    public void thePartsOfAPartialGenericTypeStillMerge() {
        assertEquals(List.of("Acme.Box`1.First", "Acme.Box`1.Second"),
                component("Acme.Box`1").children().stream().sorted().collect(Collectors.toList()));
        assertEquals(List.of("Acme.Box.Width"), component("Acme.Box").children());
        assertEquals(List.of("Acme.Bag.First", "Acme.Bag.Second"),
                component("Acme.Bag").children().stream().sorted().collect(Collectors.toList()));
    }

    @Test
    public void nestedTypesOverloadedByArityAreTwoComponents() {
        assertTrue(model.containsComponent("Acme.Tree.Node"));
        assertEquals(List.of("Acme.Tree.Node`1.Value"), component("Acme.Tree.Node`1").children());
        assertEquals(Set.of("Acme.Tree.Node"), targets("Acme.Tree.Root"));
        assertTrue(targets("Acme.Tree.Leaf").contains("Acme.Tree.Node`1"));
    }

    @Test
    public void typesNestedInATypeAndItsGenericNamesakeAreTwoComponents() {
        assertTrue(model.containsComponent("Acme.Tree`1"));
        assertTrue(model.containsComponent("Acme.Tree`1.Node"));
        assertEquals(List.of("Acme.Tree`1.Node"), component("Acme.Tree`1").children());
    }

    /**
     * An attribute's type arguments are not read, so the reference has no known arity and binds to
     * the type with the fewest type parameters: the non-generic one.
     */
    @Test
    public void aReferenceOfUnknownArityBindsToTheNonGenericType() {
        assertTrue(targets("Acme.Tagged").contains("Acme.Tag"));
        assertFalse(targets("Acme.Tagged").contains("Acme.Tag`1"));
    }

    /**
     * A one-level compile finds, through the declaration index, the file declaring the type of the
     * arity referenced, and names the boundary component as the full compile does.
     */
    @Test
    public void aOneLevelCompileLoadsTheFileDeclaringTheArityReferenced() throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/Plain.cs", "namespace Acme;\npublic class Converter { }\n"));
        projectFiles.insertFile(new ProjectFile("/Generic.cs",
                "namespace Acme;\npublic class Converter<T> { public T Value; }\n"));
        projectFiles.insertFile(new ProjectFile("/User.cs",
                "namespace Acme;\npublic class User { public Converter<int> Generic; }\n"));
        final CompileResult result = new ClarpseProject(projectFiles, Lang.CSHARP, List.of("/User.cs"),
                AnalysisOptions.oneLevel()).result();

        assertEquals(Set.of("/Generic.cs"), new TreeSet<>(result.levelOne().levelOneFiles()));
        assertTrue(result.model().copyOfComponent("Acme.Converter`1").orElseThrow().isBoundary());
        assertTrue(result.model().copyOfComponent("Acme.User.Generic").orElseThrow().references().stream()
                .anyMatch(r -> r.invokedComponent().equals("Acme.Converter`1")));
    }
}
