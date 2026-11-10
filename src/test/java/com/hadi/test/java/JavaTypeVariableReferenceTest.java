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
 * A type variable names no type, and never reaches a real type of the same name.
 */
public class JavaTypeVariableReferenceTest {

    private static OOPSourceCodeModel model(final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, Lang.JAVA).result().model();
    }

    private static Set<String> referenced(final OOPSourceCodeModel model, final String component) {
        return model.copyOfComponent(component).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    @Test
    public void aTypeVariableOfAClassIsNotAType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/Box.java", "package g; import java.util.List;"
                        + " public class Box<T> { T held; List<T> all; T get(T in) { T local = in; return local; } }"));

        assertFalse(referenced(model, "g.Box").contains("g.T"));
        assertFalse(referenced(model, "g.Box.held").contains("g.T"));
        assertFalse(referenced(model, "g.Box.all").contains("g.T"));
        assertFalse(referenced(model, "g.Box.get(T)").contains("g.T"));
        assertFalse(referenced(model, "g.Box.get(T).in").contains("g.T"));
        assertFalse(referenced(model, "g.Box.get(T).local").contains("g.T"));
    }

    @Test
    public void aTypeVariableOfAMethodOrConstructorIsNotAType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/Cfg.java", "package g; public interface Cfg { }"),
                new ProjectFile("/g/M.java", "package g; public class M {"
                        + " <T extends Cfg> T pick(T[] in) throws RuntimeException { return in[0]; }"
                        + " <E> M(E e) { } }"));

        assertTrue(referenced(model, "g.M.pick(T[])").contains("g.Cfg"));
        assertFalse(referenced(model, "g.M").contains("g.T"));
        assertFalse(referenced(model, "g.M.pick(T[])").contains("g.T"));
        assertFalse(referenced(model, "g.M.M(E)").contains("g.E"));
        assertFalse(referenced(model, "g.M.M(E).e").contains("g.E"));
    }

    @Test
    public void aTypeVariableDoesNotReachARealTypeOfTheSameName() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/T.java", "package g; public class T { }"),
                new ProjectFile("/g/Box.java", "package g; public class Box<T> { T held; T get(T in) { return in; } }"),
                new ProjectFile("/g/Plain.java", "package g; public class Plain { T held; }"));

        assertFalse(referenced(model, "g.Box").contains("g.T"));
        assertFalse(referenced(model, "g.Box.held").contains("g.T"));
        assertFalse(referenced(model, "g.Box.get(T)").contains("g.T"));
        assertTrue("a real T used where no type variable is in scope is still a dependency",
                referenced(model, "g.Plain").contains("g.T"));
    }

    @Test
    public void aTypeVariableDoesNotLeakToAFollowingClass() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/T.java", "package g; public class T { }"),
                new ProjectFile("/g/Two.java", "package g; class Box<T> { T held; } public class Two { T held; }"));

        assertTrue(referenced(model, "g.Two").contains("g.T"));
    }
}
