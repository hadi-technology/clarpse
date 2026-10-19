package com.hadi.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.AnnotationArgument;
import com.hadi.clarpse.reference.AnnotationArgument.Kind;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.StringPool;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A Java annotation keeps the literal values of its arguments, in written order, and records an
 * argument that is not a literal as present but unresolved.
 */
public class AnnotationArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/app/Verb.java", String.join("\n",
                "package app;",
                "public enum Verb { GET, POST }")));
        files.insertFile(new ProjectFile("/app/Limits.java", String.join("\n",
                "package app;",
                "public class Limits { public static final String PREFIX = \"/api\"; }")));
        files.insertFile(new ProjectFile("/app/Route.java", String.join("\n",
                "package app;",
                "public @interface Route { String value() default \"\"; }")));
        files.insertFile(new ProjectFile("/app/Orders.java", String.join("\n",
                "package app;",
                "@Route(\"/orders\")",
                "@Mapping(name = \"orders\", unique = true, size = 12, ratio = -1.5, verb = Verb.POST)",
                "@Tags({\"a\", \"b\"})",
                "@Marker",
                "@Empty()",
                "@Types(value = Orders.class, others = {String.class, Verb.class})",
                "@Computed(path = Limits.PREFIX, joined = \"/x\" + \"/y\", sum = 1 + 2, call = name())",
                "@Nested(inner = @Marker)",
                "public class Orders {",
                "    @Mapping(name = \"id\")",
                "    private String id;",
                "    @Limit(5)",
                "    public void run() {}",
                "    static String name() { return \"n\"; }",
                "}")));
        files.insertFile(new ProjectFile("/app/Twice.java", String.join("\n",
                "package app;",
                "@Mapping(name = \"shared-name\")",
                "public class Twice {}")));
        files.insertFile(new ProjectFile("/app/Again.java", String.join("\n",
                "package app;",
                "@Mapping(name = \"shared-name\")",
                "public class Again {}")));
        final CompileResult result = new ClarpseProject(files, Lang.JAVA).result();
        assertTrue("fixture failed to compile: " + result.failures(), result.failures().isEmpty());
        model = result.model();
    }

    @Test
    public void aSingleUnnamedArgumentIsNamedValue() {
        final AnnotationArgument value = arguments("app.Orders", "Route").get("value");
        assertEquals(Kind.STRING, value.kind());
        assertEquals("/orders", value.value());
    }

    @Test
    public void namedLiteralArgumentsKeepTheirKindAndValueInWrittenOrder() {
        final Map<String, AnnotationArgument> mapping = arguments("app.Orders", "Mapping");
        assertEquals(Arrays.asList("name", "unique", "size", "ratio", "verb"),
                new ArrayList<>(mapping.keySet()));
        assertEquals("orders", mapping.get("name").value());
        assertEquals(Kind.BOOLEAN, mapping.get("unique").kind());
        assertEquals("true", mapping.get("unique").value());
        assertEquals(Kind.NUMBER, mapping.get("size").kind());
        assertEquals("12", mapping.get("size").value());
        assertEquals("-1.5", mapping.get("ratio").value());
        assertEquals(Kind.ENUM, mapping.get("verb").kind());
        assertEquals("Verb.POST", mapping.get("verb").value());
    }

    @Test
    public void anArrayKeepsItsElementsInOrder() {
        final AnnotationArgument tags = arguments("app.Orders", "Tags").get("value");
        assertEquals(Kind.ARRAY, tags.kind());
        assertEquals(2, tags.elements().size());
        assertEquals("a", tags.elements().get(0).value());
        assertEquals("b", tags.elements().get(1).value());
    }

    @Test
    public void classLiteralsKeepTheTypeName() {
        final Map<String, AnnotationArgument> types = arguments("app.Orders", "Types");
        assertEquals(Kind.CLASS, types.get("value").kind());
        assertEquals("app.Orders", types.get("value").value());
        assertEquals("java.lang.String", types.get("others").elements().get(0).value());
        assertEquals("app.Verb", types.get("others").elements().get(1).value());
    }

    @Test
    public void aStringConcatenationOfLiteralsIsOneString() {
        assertEquals("/x/y", arguments("app.Orders", "Computed").get("joined").value());
    }

    @Test
    public void anArgumentThatIsNotALiteralIsPresentButUnresolved() {
        final Map<String, AnnotationArgument> computed = arguments("app.Orders", "Computed");
        for (final String name : Arrays.asList("path", "sum", "call")) {
            assertNotNull(name + " should be present", computed.get(name));
            assertEquals(name, Kind.UNRESOLVED, computed.get(name).kind());
            assertFalse(computed.get(name).isResolved());
            assertEquals(null, computed.get(name).value());
        }
        assertEquals(Kind.UNRESOLVED, arguments("app.Orders", "Nested").get("inner").kind());
    }

    @Test
    public void anAnnotationWithoutArgumentsIsReadAsHavingNone() {
        assertTrue(arguments("app.Orders", "Marker").isEmpty());
        assertTrue(arguments("app.Orders", "Empty").isEmpty());
    }

    @Test
    public void memberAndFieldAnnotationsKeepTheirArguments() {
        assertEquals("id", arguments("app.Orders.id", "Mapping").get("name").value());
        assertEquals("5", arguments("app.Orders.run()", "Limit").get("value").value());
    }

    @Test
    public void repeatedValuesAreSharedAcrossComponentsOfOneModel() {
        final String first = arguments("app.Twice", "Mapping").get("name").value();
        final String second = arguments("app.Again", "Mapping").get("name").value();
        assertEquals("shared-name", first);
        assertSame(first, second);
    }

    @Test
    public void aPoolCanonicalisesAnArgumentAcrossReferences() {
        final StringPool pool = new StringPool();
        final Component one = new Component(component("app.Twice"), pool);
        final Component other = new Component(component("app.Again"), pool);
        assertSame(annotation(one, "Mapping").arguments().get("name").value(),
                annotation(other, "Mapping").arguments().get("name").value());
    }

    @Test
    public void argumentsSurviveSerialisationAndAreAbsentWhenNotRead() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final AnnotationReference read = annotation(component("app.Orders"), "Route");
        final String json = mapper.writeValueAsString(read);
        assertTrue(json, json.contains("\"arguments\""));
        final AnnotationReference restored = (AnnotationReference) mapper.readValue(json,
                ComponentReference.class);
        assertEquals("/orders", restored.arguments().get("value").value());
        final String empty = mapper.writeValueAsString(annotation(component("app.Orders"), "Marker"));
        assertTrue(empty, empty.contains("\"arguments\":{}"));
        final String unread = mapper.writeValueAsString(new AnnotationReference("app.Route"));
        assertFalse(unread, unread.contains("arguments"));
        assertEquals(null, new AnnotationReference("app.Route").arguments());
    }

    private static Map<String, AnnotationArgument> arguments(final String owner, final String simpleName) {
        final Map<String, AnnotationArgument> arguments = annotation(component(owner), simpleName).arguments();
        assertNotNull("arguments were not read for " + simpleName, arguments);
        return arguments;
    }

    private static AnnotationReference annotation(final Component component, final String simpleName) {
        for (final ComponentReference reference : component.references()) {
            if (reference instanceof AnnotationReference
                    && (reference.invokedComponent().equals(simpleName)
                    || reference.invokedComponent().endsWith("." + simpleName))) {
                return (AnnotationReference) reference;
            }
        }
        throw new AssertionError("no @" + simpleName + " on " + component.uniqueName());
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName).orElseThrow(
                () -> new AssertionError("no component named " + uniqueName));
    }
}
