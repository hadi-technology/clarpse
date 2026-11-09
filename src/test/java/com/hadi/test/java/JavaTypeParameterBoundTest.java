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
 * The bounds of a class, interface or record type parameter are dependencies of the declaration.
 */
public class JavaTypeParameterBoundTest {

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
    public void aClassTypeParameterBoundIsADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/Cfg2.java", "package g; public interface Cfg2 { }"),
                new ProjectFile("/g/OnClass.java", "package g; public class OnClass<T extends Cfg2> { }"));

        assertTrue(referenced(model, "g.OnClass").contains("g.Cfg2"));
        assertFalse(referenced(model, "g.OnClass").contains("g.T"));
    }

    @Test
    public void anInterfaceTypeParameterWithSeveralBoundsRecordsEachOfThem() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/Cfg3.java", "package g; public interface Cfg3 { }"),
                new ProjectFile("/g/OnIface.java",
                        "package g; public interface OnIface<T extends Cfg3 & Comparable<T>> { }"));

        assertTrue(referenced(model, "g.OnIface").contains("g.Cfg3"));
        assertTrue(referenced(model, "g.OnIface").contains("java.lang.Comparable"));
        assertFalse(referenced(model, "g.OnIface").contains("g.T"));
    }

    @Test
    public void aRecordTypeParameterBoundIsADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/g/Cfg4.java", "package g; public interface Cfg4 { }"),
                new ProjectFile("/g/R.java", "package g; public record R<T extends Cfg4>(T t) { }"));

        assertTrue(referenced(model, "g.R").contains("g.Cfg4"));
    }
}
