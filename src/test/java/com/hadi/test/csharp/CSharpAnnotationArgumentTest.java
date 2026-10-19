package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.AnnotationArgument;
import com.hadi.clarpse.reference.AnnotationArgument.Kind;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A C# attribute keeps the literal values of its arguments, in written order, and records an
 * argument that is not a literal as present but unresolved.
 */
public class CSharpAnnotationArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Verb.cs", "namespace Demo; public enum Verb { Get, Post }"),
                new ProjectFile("/Limits.cs", "namespace Demo; public static class Limits { public const string Prefix = \"/api\"; }"),
                new ProjectFile("/Orders.cs", String.join("\n",
                        "namespace Demo;",
                        "[Route(\"api/[controller]\")]",
                        "[Doc(Name = \"orders\", Unique = true, Size = 12, Ratio = -1.5, Verb = Verb.Post,",
                        "     Type = typeof(Orders), Tags = new[] { \"a\", \"b\" }, Joined = \"/a\" + \"/b\", Esc = \"q\\\"t\")]",
                        "[Many(\"a\", \"b\")]",
                        "[Marker, Empty()]",
                        "[Computed(Path = Limits.Prefix, Name = $\"x{1}\", Plain = Prefix, Call = Compute(), Quoted = nameof(Orders))]",
                        "public class Orders {",
                        "    [HttpGet(\"list\", Name = \"all\")]",
                        "    public void List() {}",
                        "}"))).model();
    }

    @Test
    public void aSingleUnnamedArgumentIsNamedValue() {
        final AnnotationArgument route = arguments("Demo.Orders", "Route").get("value");
        assertEquals(Kind.STRING, route.kind());
        assertEquals("api/[controller]", route.value());
    }

    @Test
    public void severalUnnamedArgumentsAreNamedByPosition() {
        final Map<String, AnnotationArgument> many = arguments("Demo.Orders", "Many");
        assertEquals(Arrays.asList("0", "1"), new ArrayList<>(many.keySet()));
        assertEquals("b", many.get("1").value());
    }

    @Test
    public void namedArgumentsKeepTheirKindAndValueInWrittenOrder() {
        final Map<String, AnnotationArgument> doc = arguments("Demo.Orders", "Doc");
        assertEquals(Arrays.asList("Name", "Unique", "Size", "Ratio", "Verb", "Type", "Tags", "Joined", "Esc"),
                new ArrayList<>(doc.keySet()));
        assertEquals("orders", doc.get("Name").value());
        assertEquals(Kind.BOOLEAN, doc.get("Unique").kind());
        assertEquals("true", doc.get("Unique").value());
        assertEquals("12", doc.get("Size").value());
        assertEquals("-1.5", doc.get("Ratio").value());
        assertEquals(Kind.ENUM, doc.get("Verb").kind());
        assertEquals("Verb.Post", doc.get("Verb").value());
        assertEquals(Kind.CLASS, doc.get("Type").kind());
        assertEquals("Demo.Orders", doc.get("Type").value());
        assertEquals(Kind.ARRAY, doc.get("Tags").kind());
        assertEquals("b", doc.get("Tags").elements().get(1).value());
        assertEquals("/a/b", doc.get("Joined").value());
        assertEquals("q\"t", doc.get("Esc").value());
    }

    @Test
    public void anArgumentThatIsNotALiteralIsPresentButUnresolved() {
        final Map<String, AnnotationArgument> computed = arguments("Demo.Orders", "Computed");
        for (final String name : Arrays.asList("Path", "Name", "Plain", "Call", "Quoted")) {
            assertNotNull(name + " should be present", computed.get(name));
            assertEquals(name, Kind.UNRESOLVED, computed.get(name).kind());
        }
    }

    @Test
    public void anAttributeWithoutArgumentsIsReadAsHavingNone() {
        assertTrue(arguments("Demo.Orders", "Marker").isEmpty());
        assertTrue(arguments("Demo.Orders", "Empty").isEmpty());
    }

    @Test
    public void aMemberAttributeKeepsPositionalAndNamedArguments() {
        final Component list = model.components()
                .filter(component -> component.uniqueName().startsWith("Demo.Orders.List"))
                .findFirst().orElseThrow(() -> new AssertionError("no List member"));
        final Map<String, AnnotationArgument> get = annotation(list, "HttpGet").arguments();
        assertEquals("list", get.get("value").value());
        assertEquals("all", get.get("Name").value());
    }

    private static Map<String, AnnotationArgument> arguments(final String owner, final String simpleName) {
        final Component component = model.copyOfComponent(owner)
                .orElseThrow(() -> new AssertionError("no component " + owner));
        final Map<String, AnnotationArgument> arguments = annotation(component, simpleName).arguments();
        assertNotNull("arguments were not read for " + simpleName, arguments);
        return arguments;
    }

    private static AnnotationReference annotation(final Component component, final String simpleName) {
        for (final ComponentReference reference : component.references()) {
            if (reference instanceof AnnotationReference
                    && reference.invokedComponent().endsWith(simpleName)) {
                return (AnnotationReference) reference;
            }
        }
        throw new AssertionError("no [" + simpleName + "] on " + component.uniqueName());
    }
}
