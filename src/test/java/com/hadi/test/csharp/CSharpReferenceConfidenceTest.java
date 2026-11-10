package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CSharpReferenceConfidenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/src/app/Widget.cs", """
                        namespace Demo.App;
                        using Demo.Common;
                        public class Widget {
                          public int Value { get; set; }
                          public Helper helper { get; set; }
                          public Gadget gadget { get; set; }
                          public Dup dup { get; set; }
                          public Missing missing { get; set; }
                          public int Read(int? maybe) { return maybe.Value; }
                          public void Assign(int v) { this.Value = v; }
                        }
                        """),
                new ProjectFile("/src/common/Common.cs", """
                        namespace Demo.Common;
                        public class Helper {}
                        """),
                new ProjectFile("/src/other/Other.cs", """
                        namespace Other.Place;
                        public class Gadget {}
                        """),
                new ProjectFile("/src/dup/One.cs", """
                        namespace One.Place;
                        public class Dup {}
                        """),
                new ProjectFile("/src/dup/Two.cs", """
                        namespace Two.Place;
                        public class Dup {}
                        """)
        ).model();
    }

    /**
     * `maybe.Value` is the `.Value` of a `Nullable<int>`. Binding it to the enclosing type's own
     * `Value` property invents a dependency between two unrelated types.
     */
    @Test
    public void aMemberAccessOnAnUnknownReceiverDoesNotBindToTheEnclosingType() {
        assertFalse("maybe.Value must not bind to Demo.App.Widget.Value",
                invokes(component("Demo.App.Widget.Read(int?)"), "Demo.App.Widget.Value"));
    }

    /** `this` is the one receiver whose type is known, so it still binds. */
    @Test
    public void aMemberAccessThroughThisStillBinds() {
        assertTrue(invokes(component("Demo.App.Widget.Assign(int)"), "Demo.App.Widget.Value"));
    }

    @Test
    public void aTypeReachedThroughAUsingIsExact() {
        assertEquals(ResolutionKind.EXACT,
                kindOf("Demo.App.Widget.helper", "Demo.Common.Helper"));
    }

    @Test
    public void aBuiltinTypeIsExact() {
        assertEquals(ResolutionKind.EXACT, kindOf("Demo.App.Widget.Value", "System.Int32"));
    }

    /** Exactly one type in the repository carries the name, but its namespace is not one the name is visible from. */
    @Test
    public void aSoleShortNameMatchInANamespaceOutOfScopeStaysUnresolved() {
        assertEquals(ResolutionKind.UNRESOLVED, kindOf("Demo.App.Widget.gadget", "Gadget"));
        assertFalse(invokes(component("Demo.App.Widget.gadget"), "Other.Place.Gadget"));
    }

    /**
     * Two types carry the name, but neither namespace is visible from the file, so neither is a
     * candidate: the name is unresolved, not ambiguous. Ambiguity needs several visible candidates.
     */
    @Test
    public void severalTypesOfTheNameNoneOfThemVisibleIsUnresolved() {
        assertEquals(ResolutionKind.UNRESOLVED, kindOf("Demo.App.Widget.dup", "Dup"));
    }

    @Test
    public void aNameNothingDeclaresIsUnresolved() {
        assertEquals(ResolutionKind.UNRESOLVED, kindOf("Demo.App.Widget.missing", "Missing"));
    }

    /** Java reports how it resolved a name too: an implicit {@code java.lang} type is exact. */
    @Test
    public void javaReportsHowItResolvedANameToo() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/app/Client.java",
                "package app;\npublic class Client { private String name; }\n"));
        final OOPSourceCodeModel javaModel = new ClarpseProject(files, Lang.JAVA).result().model();
        final Component field = javaModel.copyOfComponent("app.Client.name").orElseThrow();
        for (final ComponentReference reference : field.references()) {
            assertEquals(ResolutionKind.EXACT, reference.resolutionKind());
        }
    }

    private static ResolutionKind kindOf(final String componentName, final String invoked) {
        for (final ComponentReference reference : component(componentName).references()) {
            if (invoked.equals(reference.invokedComponent())) {
                return reference.resolutionKind();
            }
        }
        throw new AssertionError(componentName + " has no reference to " + invoked
                + ", it has " + component(componentName).references());
    }

    private static boolean invokes(final Component component, final String invoked) {
        for (final ComponentReference reference : component.references()) {
            if (invoked.equals(reference.invokedComponent())) {
                return true;
            }
        }
        return false;
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName).orElseThrow(
                () -> new AssertionError("no component named " + uniqueName));
    }
}
