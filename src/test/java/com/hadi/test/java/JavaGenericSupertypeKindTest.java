package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Covers the type arguments of {@code extends} and {@code implements} clauses: only the named
 * supertype is extended or implemented, and each argument is an ordinary reference.
 */
public class JavaGenericSupertypeKindTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/com/Shapes.java", """
                package com;
                class Base<T> { }
                interface Handler<T> { }
                class Arg { }
                class Inner { }
                class Wrapper<T> { }
                class Nested extends Base<Wrapper<Inner>> { }
                class Both extends Base<Arg> implements Handler<Inner> { }
                interface Child extends Handler<Arg> { }
                """));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static List<String> targets(final String component, final TypeReferences kind) {
        return model.copyOfComponent(component).orElseThrow().references(kind).stream()
                .map(ComponentReference::invokedComponent).sorted().toList();
    }

    @Test
    public void nestedTypeArgumentsAreAllPlainReferences() {
        assertEquals(List.of("com.Base"), targets("com.Nested", TypeReferences.EXTENSION));
        assertEquals(List.of("com.Inner", "com.Wrapper"), targets("com.Nested", TypeReferences.SIMPLE));
    }

    @Test
    public void bothClausesKeepTheirArgumentsPlain() {
        assertEquals(List.of("com.Base"), targets("com.Both", TypeReferences.EXTENSION));
        assertEquals(List.of("com.Handler"), targets("com.Both", TypeReferences.IMPLEMENTATION));
        assertEquals(List.of("com.Arg", "com.Inner"), targets("com.Both", TypeReferences.SIMPLE));
    }

    @Test
    public void interfaceExtendingAGenericInterfaceKeepsItsArgumentPlain() {
        assertEquals(List.of("com.Handler"), targets("com.Child", TypeReferences.EXTENSION));
        assertEquals(List.of("com.Arg"), targets("com.Child", TypeReferences.SIMPLE));
    }
}
