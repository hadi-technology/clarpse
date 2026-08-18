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
import static org.junit.Assert.assertTrue;

/**
 * The scopes a plain type name can come from, each of which silently produced no edge at all.
 *
 * <p>Every case here was found by auditing parsed models of real repositories against what their
 * sources plainly declare, and each one cost a documented architectural rule its answer: a relation
 * that is absent from the model cannot be checked, so the rule reports "couldn't tell" about
 * something stated in one line of Java.
 */
public class JavaTypeResolutionScopeTest {

    private static OOPSourceCodeModel model(final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final OOPSourceCodeModel model, final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component + " in "
                        + model.components().map(Component::uniqueName).collect(Collectors.toList())));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    /**
     * An on-demand import. The type solver is what resolves these, and it was rooted at the project
     * directory rather than the source root, so it found nothing under a conventional layout: the
     * identical sources produced the edge when flat and no edge under {@code src/main/java}.
     */
    @Test
    public void onDemandImportResolvesUnderAConventionalSourceLayout() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/src/main/java/a/Base.java",
                        "package a;\npublic interface Base { void run(); }\n"),
                new ProjectFile("/src/main/java/b/StarImpl.java",
                        "package b;\nimport a.*;\npublic class StarImpl implements Base { public void run() {} }\n"));

        assertTrue("wildcard-imported supertype should resolve",
                refs(model, "b.StarImpl").contains("a.Base"));
    }

    /** The same file with an explicit import, which never needed the solver and always worked. */
    @Test
    public void explicitImportStillResolves() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/src/main/java/a/Base.java",
                        "package a;\npublic interface Base { void run(); }\n"),
                new ProjectFile("/src/main/java/b/ExactImpl.java",
                        "package b;\nimport a.Base;\npublic class ExactImpl implements Base { public void run() {} }\n"));

        assertTrue(refs(model, "b.ExactImpl").contains("a.Base"));
    }

    /**
     * A nested type implementing the type it is declared inside. The guard against self-references
     * compared against every enclosing component, so this read as a class referencing itself.
     */
    @Test
    public void nestedTypeMayInheritItsEnclosingType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/src/main/java/a/Event.java",
                        "package a;\npublic interface Event {\n  class Created implements Event { }\n}\n"));

        assertTrue("nested class should reference the interface it implements",
                refs(model, "a.Event.Created").contains("a.Event"));
    }

    /**
     * A nested type implementing a sibling nested type, named plainly. Observed on debezium's
     * {@code Field.RangeValidator implements Validator}, where both are declared in one file.
     */
    @Test
    public void nestedTypeMayInheritASiblingNestedType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/src/main/java/a/Field.java",
                        "package a;\npublic class Field {\n"
                        + "  public interface Validator { void validate(); }\n"
                        + "  public static class RangeValidator implements Validator { public void validate() {} }\n"
                        + "}\n"));

        assertTrue("sibling nested supertype should resolve to its qualified name",
                refs(model, "a.Field.RangeValidator").contains("a.Field.Validator"));
    }

    /**
     * The counterpart the widened guard broke: a member is not a reference to the type that declares
     * it. A constructor shares its type's name, and treating that as a reference gives every class
     * a reference to itself.
     */
    @Test
    public void aMemberIsNotAReferenceToItsDeclaringType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/src/main/java/a/Test.java",
                        "package a;\nclass Test { public Test() {} }\n"));

        assertEquals(0, refs(model, "a.Test").size());
        assertEquals(0, refs(model, "a.Test.Test()").size());
    }
}
