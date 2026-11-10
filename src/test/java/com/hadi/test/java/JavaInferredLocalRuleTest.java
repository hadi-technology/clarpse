package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A {@code var} local records what the same type written out would: the type and its type arguments. */
public class JavaInferredLocalRuleTest {

    private static OOPSourceCodeModel model(final String body) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/v/Foo.java", "package v; public class Foo { }"));
        files.insertFile(new ProjectFile("/v/Bar.java", "package v; public class Bar { }"));
        files.insertFile(new ProjectFile("/v/A.java", "package v; import java.util.List; import java.util.Map;"
                + " public class A { public List<Foo> list() { return null; }"
                + " public Map<String, List<Bar>> map() { return null; } }"));
        files.insertFile(new ProjectFile("/v/C.java", "package v; import java.util.List; import java.util.Map;"
                + " public class C { void m(A a) { " + body + " } }"));
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> localReferences(final OOPSourceCodeModel model, final String local) {
        return model.components()
                .filter(c -> c.componentType() == ComponentType.LOCAL && c.name().equals(local))
                .flatMap(c -> c.references().stream()).map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void aVarRecordsTheTypeArgumentsItsWrittenTypeWould() throws Exception {
        final OOPSourceCodeModel model = model("var inferred = a.list(); List<Foo> written = a.list();");
        assertTrue(localReferences(model, "inferred").toString(), localReferences(model, "inferred").contains("v.Foo"));
        assertEquals(localReferences(model, "written"), localReferences(model, "inferred"));
    }

    @Test
    public void aVarRecordsNestedTypeArguments() throws Exception {
        final OOPSourceCodeModel model = model("var inferred = a.map(); Map<String, List<Bar>> written = a.map();");
        assertTrue(localReferences(model, "inferred").toString(), localReferences(model, "inferred").contains("v.Bar"));
        assertEquals(localReferences(model, "written"), localReferences(model, "inferred"));
    }
}
