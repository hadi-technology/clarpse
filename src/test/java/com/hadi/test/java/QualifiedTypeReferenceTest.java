package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A type written with its qualified name inline, with no import, is the same dependency as the
 * imported simple name. Every form here names {@code com.example.model.Foo} from package
 * {@code com.example.app}; before, most of them produced a reference to {@code com.example.app.Foo}
 * instead -- a type in the current package that does not exist, or, where one of that simple name
 * does exist, the wrong one -- and the real edge was missing.
 */
public class QualifiedTypeReferenceTest {

    private static final String CLIENT = "com.example.app.Client";
    private static final String FOO = "com.example.model.Foo";
    private static final String INVENTED_FOO = "com.example.app.Foo";

    private static OOPSourceCodeModel model(final String clientBody, final ProjectFile... extraFiles)
            throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/model/Foo.java",
                "package com.example.model;\n"
                + "public class Foo {\n"
                + "  public static final int LIMIT = 1;\n"
                + "  public static Foo create() { return new Foo(); }\n"
                + "}\n"));
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/model/Outer.java",
                "package com.example.model;\n"
                + "public class Outer { public static class Inner { public static final int K = 1; } }\n"));
        for (final ProjectFile file : extraFiles) {
            projectFiles.insertFile(file);
        }
        projectFiles.insertFile(new ProjectFile("/src/main/java/com/example/app/Client.java",
                "package com.example.app;\nimport java.util.List;\n" + clientBody));
        return new ClarpseProject(projectFiles, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final OOPSourceCodeModel model) {
        final Component cmp = model.copyOfComponent(CLIENT).orElseThrow(
                () -> new AssertionError("no component " + CLIENT));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    private static void assertReferencesFoo(final String clientBody) throws Exception {
        final Set<String> refs = refs(model(clientBody));
        assertTrue("expected " + FOO + " in " + refs, refs.contains(FOO));
        assertFalse("invented " + INVENTED_FOO + " in " + refs, refs.contains(INVENTED_FOO));
    }

    @Test
    public void objectCreation() throws Exception {
        assertReferencesFoo("public class Client { void m() { Object o = new com.example.model.Foo(); } }\n");
    }

    @Test
    public void objectCreationPassedAsAnArgument() throws Exception {
        assertReferencesFoo(
                "public class Client { void m(List<Object> l) { l.add(new com.example.model.Foo()); } }\n");
    }

    @Test
    public void fieldType() throws Exception {
        assertReferencesFoo("public class Client { private com.example.model.Foo foo; }\n");
    }

    @Test
    public void localVariableType() throws Exception {
        assertReferencesFoo("public class Client { void m() { com.example.model.Foo foo = null; } }\n");
    }

    @Test
    public void parameterType() throws Exception {
        assertReferencesFoo("public class Client { void m(com.example.model.Foo foo) { } }\n");
    }

    @Test
    public void varargsParameterType() throws Exception {
        assertReferencesFoo("public class Client { void m(com.example.model.Foo... foos) { } }\n");
    }

    @Test
    public void returnType() throws Exception {
        assertReferencesFoo("public class Client { com.example.model.Foo m() { return null; } }\n");
    }

    @Test
    public void arrayType() throws Exception {
        assertReferencesFoo("public class Client { private com.example.model.Foo[] foos; }\n");
    }

    @Test
    public void cast() throws Exception {
        assertReferencesFoo("public class Client { Object m(Object o) { return (com.example.model.Foo) o; } }\n");
    }

    @Test
    public void instanceOf() throws Exception {
        assertReferencesFoo(
                "public class Client { boolean m(Object o) { return o instanceof com.example.model.Foo; } }\n");
    }

    @Test
    public void typeArgument() throws Exception {
        assertReferencesFoo("public class Client { private List<com.example.model.Foo> foos; }\n");
    }

    @Test
    public void methodReference() throws Exception {
        assertReferencesFoo("public class Client { Object m() {"
                + " java.util.function.Supplier<Object> s = com.example.model.Foo::create; return s; } }\n");
    }

    @Test
    public void staticMethodCall() throws Exception {
        assertReferencesFoo("public class Client { void m() { com.example.model.Foo.create(); } }\n");
    }

    /** The unqualified form, {@code Foo.LIMIT} with an import, always produced this edge. */
    @Test
    public void staticFieldAccess() throws Exception {
        assertReferencesFoo("public class Client { int m() { return com.example.model.Foo.LIMIT; } }\n");
    }

    @Test
    public void supertypes() throws Exception {
        final OOPSourceCodeModel model = model(
                "public class Client extends com.example.model.Base implements com.example.model.Api { }\n",
                new ProjectFile("/src/main/java/com/example/model/Base.java",
                        "package com.example.model;\npublic class Base { }\n"),
                new ProjectFile("/src/main/java/com/example/model/Api.java",
                        "package com.example.model;\npublic interface Api { }\n"));
        assertTrue(refs(model).contains("com.example.model.Base"));
        assertTrue(refs(model).contains("com.example.model.Api"));
    }

    /**
     * Where the current package does hold a type of the same simple name, resolving the last segment
     * on its own did not invent a type -- it picked a real, wrong one.
     */
    @Test
    public void aSameNamedTypeInTheCurrentPackageIsNotTheOneReferenced() throws Exception {
        final OOPSourceCodeModel model = model("public class Client { private com.example.model.Foo foo; }\n",
                new ProjectFile("/src/main/java/com/example/app/Foo.java",
                        "package com.example.app;\npublic class Foo { }\n"));
        assertEquals(Set.of(FOO), refs(model));
    }

    /**
     * Qualifying a name is how Java code tells apart two types of one simple name, so the qualified
     * one must not be read through the import of the other.
     */
    @Test
    public void aQualifiedNameIsNotReadThroughAnImportOfTheSameSimpleName() throws Exception {
        final OOPSourceCodeModel model = model("import com.example.other.Foo;\n"
                        + "public class Client { private com.example.model.Foo a; private Foo b; }\n",
                new ProjectFile("/src/main/java/com/example/other/Foo.java",
                        "package com.example.other;\npublic class Foo { }\n"));
        assertEquals(Set.of(FOO, "com.example.other.Foo"), refs(model));
    }

    @Test
    public void nestedTypeByFullyQualifiedName() throws Exception {
        final OOPSourceCodeModel model = model("public class Client { private com.example.model.Outer.Inner i; }\n");
        assertEquals(Set.of("com.example.model.Outer.Inner"), refs(model));
    }

    /** The same edge an import of {@code com.example.model.Outer.Inner} gives. */
    @Test
    public void nestedTypeQualifiedByAnImportedType() throws Exception {
        final OOPSourceCodeModel model = model(
                "import com.example.model.Outer;\npublic class Client { private Outer.Inner i; }\n");
        assertEquals(Set.of("com.example.model.Outer.Inner"), refs(model));
    }

    @Test
    public void staticFieldOfANestedTypeByFullyQualifiedName() throws Exception {
        final OOPSourceCodeModel model = model(
                "public class Client { int m() { return com.example.model.Outer.Inner.K; } }\n");
        assertEquals(Set.of("com.example.model.Outer.Inner"), refs(model));
    }

    /**
     * A qualified name outside the parse path is recorded as written -- what an import of it would
     * record -- and never under the current package.
     */
    @Test
    public void anUnresolvableQualifiedTypeIsKeptAsWritten() throws Exception {
        final OOPSourceCodeModel model = model(
                "public class Client { private org.nowhere.Missing a; void m(org.nowhere.Gone g) { } }\n");
        assertEquals(Set.of("org.nowhere.Missing", "org.nowhere.Gone"), refs(model));
    }

    /**
     * In an expression a qualified name outside the parse path cannot be told apart from a chain of
     * field accesses, so it names no type; before, the call became a call on
     * {@code com.example.app.Util}.
     */
    @Test
    public void anUnresolvableQualifiedNameInAnExpressionInventsNothing() throws Exception {
        final OOPSourceCodeModel model = model(
                "public class Client { int m() { org.nowhere.Util.run(); return org.nowhere.Util.LIMIT; } }\n");
        assertEquals(Set.of(), refs(model));
    }

    /**
     * A variable in scope takes precedence over a package of the same name, so {@code com.example.model.Foo}
     * here is a chain of field accesses on the parameter {@code com}, even though a type of exactly that
     * name exists.
     */
    @Test
    public void aVariableShadowingAPackageNameIsNotReadAsATypeName() throws Exception {
        final OOPSourceCodeModel model = model(
                "public class Client { Object m(Holder com) { return com.example.model.Foo; } }\n",
                new ProjectFile("/src/main/java/com/example/app/Holder.java",
                        "package com.example.app;\npublic class Holder { public Object example; }\n"));
        assertFalse(refs(model).contains(FOO));
    }

    @Test
    public void callsThroughVariablesAreNotReadAsTypeNames() throws Exception {
        final OOPSourceCodeModel model = model(
                "public class Client { Client a; void m(Client b) { b.a.run(); b.run(); } void run() { } }\n");
        assertEquals(Set.of(), refs(model));
    }
}
