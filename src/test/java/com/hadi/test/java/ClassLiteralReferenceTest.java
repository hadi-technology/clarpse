package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A class literal is a use of the type it names. {@code store.save(record, Foo.class)} can be the
 * only place a class mentions {@code Foo}, and it produced no reference at all -- imported,
 * same-package or qualified -- so the dependency was absent from the model.
 */
public class ClassLiteralReferenceTest {

    private static final String CLIENT = "com.example.app.Client";
    private static final String FOO = "com.example.model.Foo";
    private static final String BAR = "com.example.model.Bar";

    private static OOPSourceCodeModel model(final String clientSource) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/model/Foo.java",
                "package com.example.model;\npublic class Foo { }\n"));
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/model/Bar.java",
                "package com.example.model;\npublic class Bar { }\n"));
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/model/Outer.java",
                "package com.example.model;\npublic class Outer { public static class Inner { } }\n"));
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/model/Uses.java",
                "package com.example.model;\npublic @interface Uses { Class<?>[] value(); }\n"));
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/app/Sibling.java",
                "package com.example.app;\npublic class Sibling { }\n"));
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/app/Client.java",
                "package com.example.app;\n" + clientSource));
        return new ClarpseProject(projectFiles, Lang.JAVA).result().model();
    }

    private static Component client(final OOPSourceCodeModel model) {
        return model.copyOfComponent(CLIENT).orElseThrow(() -> new AssertionError("no component " + CLIENT));
    }

    private static Set<String> names(final Iterable<ComponentReference> refs) {
        final Set<String> names = new java.util.HashSet<>();
        refs.forEach(ref -> names.add(ref.invokedComponent()));
        return names;
    }

    private static Set<String> internal(final String clientSource) throws Exception {
        return names(client(model(clientSource)).internalDependencies());
    }

    private static void assertInternal(final String clientSource, final String... targets) throws Exception {
        final Set<String> internal = internal(clientSource);
        for (final String target : targets) {
            assertTrue("expected " + target + " in " + internal, internal.contains(target));
        }
    }

    @Test
    public void importedTypePassedAsAnArgument() throws Exception {
        assertInternal("import com.example.model.Foo;\n"
                + "public class Client { Object store; Object query;\n"
                + "  void m() { save(query, store, Foo.class); }\n"
                + "  void save(Object a, Object b, Class<?> c) { } }\n", FOO);
    }

    @Test
    public void importedTypeAssignedToAField() throws Exception {
        assertInternal("import com.example.model.Foo;\n"
                + "public class Client { private final Class<?> type = Foo.class; }\n", FOO);
    }

    @Test
    public void samePackageType() throws Exception {
        assertInternal("public class Client { Object m() { return Sibling.class; } }\n", "com.example.app.Sibling");
    }

    @Test
    public void fullyQualifiedType() throws Exception {
        assertInternal("public class Client { Object m() { return com.example.model.Foo.class; } }\n", FOO);
    }

    @Test
    public void nestedTypeQualifiedByAnImportedType() throws Exception {
        assertInternal("import com.example.model.Outer;\n"
                + "public class Client { Object m() { return Outer.Inner.class; } }\n", "com.example.model.Outer.Inner");
    }

    @Test
    public void nestedTypeByFullyQualifiedName() throws Exception {
        assertInternal("public class Client { Object m() { return com.example.model.Outer.Inner.class; } }\n",
                "com.example.model.Outer.Inner");
    }

    @Test
    public void arrayType() throws Exception {
        assertInternal("import com.example.model.Foo;\n"
                + "public class Client { Object m() { return Foo[].class; } }\n", FOO);
    }

    @Test
    public void literalUsedAsACallReceiver() throws Exception {
        assertInternal("import com.example.model.Foo;\n"
                + "public class Client { String m() { return Foo.class.getName(); } }\n", FOO);
    }

    @Test
    public void annotationOnAMethod() throws Exception {
        assertInternal("import com.example.model.Foo;\nimport com.example.model.Uses;\n"
                + "public class Client { @Uses(Foo.class) void m() { } }\n", FOO);
    }

    @Test
    public void annotationOnAType() throws Exception {
        assertInternal("import com.example.model.Foo;\nimport com.example.model.Bar;\nimport com.example.model.Uses;\n"
                + "@Uses({Foo.class, Bar.class})\npublic class Client { }\n", FOO, BAR);
    }

    @Test
    public void annotationOnARecord() throws Exception {
        final OOPSourceCodeModel model = model("import com.example.model.Foo;\nimport com.example.model.Uses;\n"
                + "@Uses(value = Foo.class)\npublic record Client(int id) { }\n");
        assertTrue(names(client(model).internalDependencies()).contains(FOO));
    }

    @Test
    public void annotationOnAField() throws Exception {
        assertInternal("import com.example.model.Foo;\nimport com.example.model.Uses;\n"
                + "public class Client { @Uses(value = {Foo.class}) private Object f; }\n", FOO);
    }

    /** Already a type use through its type argument; the literal is not needed for this one. */
    @Test
    public void wildcardBoundOfAClassType() throws Exception {
        assertInternal("import com.example.model.Foo;\nimport java.util.List;\n"
                + "public class Client { private List<Class<? extends Foo>> types; }\n", FOO);
    }

    @Test
    public void primitiveAndVoidLiteralsNameNothing() throws Exception {
        final Component client = client(model(
                "public class Client { Object[] m() { return new Object[] { int.class, void.class, int[].class }; } }\n"));
        assertEquals(Set.of("java.lang.Object"), names(client.references()));
    }

    /**
     * A literal of a type outside the parse path is recorded as the same external name any other use of
     * it would be, and nothing internal is invented for it.
     */
    @Test
    public void anUnresolvableTypeIsNotAnInternalReference() throws Exception {
        final Component client = client(model(
                "public class Client { Object m() { return org.nowhere.Gone.class; } }\n"));
        assertEquals(Set.of(), names(client.internalDependencies()));
        assertTrue(names(client.externalDependencies()).contains("org.nowhere.Gone"));
        assertTrue(names(client.references()).stream().noneMatch(name -> name.startsWith("com.example.app.")));
    }

    @Test
    public void aTypeIsNotAReferenceToItself() throws Exception {
        assertEquals(Set.of(), internal("public class Client { Object m() { return Client.class; } }\n")
                .stream().filter(name -> name.equals(CLIENT)).collect(Collectors.toSet()));
    }
}
