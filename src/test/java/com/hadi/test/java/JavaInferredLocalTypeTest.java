package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A {@code var} local depends on the type its initializer resolves to.
 */
public class JavaInferredLocalTypeTest {

    private static final ProjectFile FOO = new ProjectFile("/v/Foo.java",
            "package v; public class Foo { public void hello() { } }");
    private static final ProjectFile FOO2 = new ProjectFile("/v/Foo2.java", "package v; public class Foo2 { }");
    private static final ProjectFile FOO3 = new ProjectFile("/v/Foo3.java", "package v; public class Foo3 { }");
    private static final ProjectFile A = new ProjectFile("/v/A.java",
            "package v; public class A { public Foo getFoo() { return null; } public Foo2 f2 = null;"
                    + " public static Foo3 mk() { return null; } public Foo[] foos() { return null; }"
                    + " public int count() { return 1; } }");

    private static OOPSourceCodeModel model(final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, Lang.JAVA).result().model();
    }

    private static OOPSourceCodeModel withVarFixtures(final String body) throws Exception {
        return model(FOO, FOO2, FOO3, A, new ProjectFile("/v/C.java",
                "package v; public class C { void m(A a) { " + body + " } }"));
    }

    private static Set<String> referenced(final OOPSourceCodeModel model, final String component) {
        return model.copyOfComponent(component).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    @Test
    public void aVarInitializedFromAMethodCallDependsOnTheReturnType() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("var x = a.getFoo();");

        assertTrue(referenced(model, "v.C.m(A)").contains("v.Foo"));
    }

    @Test
    public void aVarInitializedFromAFieldDependsOnTheFieldType() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("var x = a.f2;");

        assertTrue(referenced(model, "v.C.m(A)").contains("v.Foo2"));
    }

    @Test
    public void aVarInitializedFromAStaticCallDependsOnTheReturnType() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("var x = A.mk();");

        assertTrue(referenced(model, "v.C.m(A)").contains("v.Foo3"));
    }

    @Test
    public void aVarHoldingAnArrayDependsOnTheElementType() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("var xs = a.foos();");

        assertTrue(referenced(model, "v.C.m(A)").contains("v.Foo"));
    }

    @Test
    public void aVarHoldingAPrimitiveRecordsNothingMore() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("var n = a.count();");

        assertEquals(Set.of("v.A"), referenced(model, "v.C.m(A)"));
    }

    @Test
    public void aVarWhoseInitializerCannotBeResolvedRecordsNothing() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("var y = missing.call();");

        assertFalse(referenced(model, "v.C.m(A)").contains("v.Foo"));
    }

    @Test
    public void aDeclaredTypeIsNotReadFromTheInitializer() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures("Object x = a.getFoo();");

        assertFalse(referenced(model, "v.C.m(A)").contains("v.Foo"));
    }

    @Test
    public void aStringOrCommentNamingAVarIsNotALocal() throws Exception {
        final OOPSourceCodeModel model = withVarFixtures(
                "String s = \"var x = a.getFoo();\"; // var y = a.f2;\n /* var z = A.mk(); */");

        final Set<String> references = referenced(model, "v.C.m(A)");
        assertFalse(references.contains("v.Foo"));
        assertFalse(references.contains("v.Foo2"));
        assertFalse(references.contains("v.Foo3"));
    }
}
