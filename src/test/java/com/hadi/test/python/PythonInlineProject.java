package com.hadi.test.python;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.Assume;

import java.util.Set;
import java.util.TreeSet;

/** Compiles Python sources written inline in a test and reads back what the model holds. */
final class PythonInlineProject {

    /** A repository module declaring one class per name the tests refer to. */
    static final String MODELS = "class Foo:\n    LIMIT = 1\nclass Base:\n    pass\nclass Cfg:\n    pass\n"
            + "class Bar:\n    pass\nclass Baz:\n    pass\nclass Qux:\n    pass\nclass Corge:\n    pass\n"
            + "class Grault:\n    pass\n";

    private final OOPSourceCodeModel model;

    private PythonInlineProject(final OOPSourceCodeModel model) {
        this.model = model;
    }

    /**
     * Compiles the given path and source pairs, together with the {@code pkg} package and its
     * {@code pkg.models} module.
     */
    static PythonInlineProject compile(final String... pathsAndSources) throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/pkg/__init__.py", ""));
        files.insertFile(new ProjectFile("/pkg/models.py", MODELS));
        for (int i = 0; i < pathsAndSources.length; i += 2) {
            files.insertFile(new ProjectFile(pathsAndSources[i], pathsAndSources[i + 1]));
        }
        final CompileResult result = new ClarpseProject(files, Lang.PYTHON).result();
        Assert.assertTrue(result.failures().toString(), result.failures().isEmpty());
        return new PythonInlineProject(result.model());
    }

    static String lines(final String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /** The component whose unique name is the given one or starts with it followed by a signature. */
    Component component(final String uniqueName) {
        return model.components()
                .filter(cmp -> cmp.uniqueName().equals(uniqueName) || cmp.uniqueName().startsWith(uniqueName + "("))
                .filter(cmp -> cmp.componentType() != ComponentType.METHOD_PARAMETER_COMPONENT
                        && cmp.componentType() != ComponentType.LOCAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component " + uniqueName));
    }

    /** What a parameter of the given function references. */
    Set<String> parameterReferences(final String function, final String parameter) {
        final Set<String> names = new TreeSet<>();
        model.components()
                .filter(cmp -> cmp.componentType() == ComponentType.METHOD_PARAMETER_COMPONENT
                        && cmp.uniqueName().startsWith(function + "(") && parameter.equals(cmp.name()))
                .flatMap(cmp -> cmp.references().stream())
                .forEach(ref -> names.add(ref.invokedComponent()));
        return names;
    }

    /** What the component references, internal targets by unique name and external ones by label. */
    Set<String> references(final String uniqueName) {
        final Set<String> names = new TreeSet<>();
        for (final ComponentReference ref : component(uniqueName).references()) {
            names.add(ref.isExternal() ? ref.invokedComponent() + "[ext]" : ref.invokedComponent());
        }
        return names;
    }

    OOPSourceCodeModel model() {
        return model;
    }
}
