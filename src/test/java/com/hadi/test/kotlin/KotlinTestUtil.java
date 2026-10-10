package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class KotlinTestUtil {

    private KotlinTestUtil() {
    }

    public static CompileResult compileInline(final ProjectFile... files) throws Exception {
        return new ClarpseProject(projectOf(files), Lang.KOTLIN).result();
    }

    public static ProjectFiles projectOf(final ProjectFile... files) {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return projectFiles;
    }

    public static Component component(final OOPSourceCodeModel model, final String uniqueName) {
        return model.copyOfComponent(uniqueName).orElseThrow(() -> new AssertionError(
                "no component " + uniqueName + " in " + new TreeSet<>(model.components()
                        .map(Component::uniqueName).collect(Collectors.toSet()))));
    }

    public static Set<String> targets(final Component component) {
        return component.references().stream().map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static Set<String> internalTargets(final Component component) {
        return component.internalDependencies().stream().map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static ComponentReference reference(final Component component, final String target) {
        return component.references().stream().filter(ref -> ref.invokedComponent().equals(target))
                .findFirst().orElseThrow(() -> new AssertionError(component.uniqueName()
                        + " has no reference to " + target + "; it has " + targets(component)));
    }
}
