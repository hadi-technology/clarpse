package com.hadi.test.onelevel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Builds fixture repositories and reads models for the one-level analysis tests.
 */
final class OneLevelTestSupport {

    private OneLevelTestSupport() {
    }

    /** An ordered map of path to content, for building fixtures inline. */
    static Map<String, String> files(final String... pathsAndContents) {
        final Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            files.put(pathsAndContents[i], pathsAndContents[i + 1]);
        }
        return files;
    }

    static ProjectFiles project(final Map<String, String> files) {
        final ProjectFiles projectFiles = new ProjectFiles();
        files.forEach((path, content) -> projectFiles.insertFile(new ProjectFile(path, content)));
        return projectFiles;
    }

    static Component component(final OOPSourceCodeModel model, final String uniqueName) {
        return model.component(uniqueName).orElseThrow(() -> new AssertionError(
                "no component " + uniqueName + " in " + model.components().map(Component::uniqueName)
                        .sorted().collect(Collectors.toList())));
    }

    static Set<String> targets(final Collection<ComponentReference> references) {
        return references.stream().map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Every component-to-target pair of the components declared in the given files. */
    static Set<String> referencePairs(final OOPSourceCodeModel model, final Collection<String> paths) {
        final Set<String> normalized = new HashSet<>();
        paths.forEach(path -> normalized.add(ClarpseCompiler.normalizeForComparison(path)));
        final Set<String> pairs = new TreeSet<>();
        model.components()
                .filter(c -> c.sourceFile() != null
                        && normalized.contains(ClarpseCompiler.normalizeForComparison(c.sourceFile())))
                .forEach(c -> c.references().forEach(r -> pairs.add(c.uniqueName() + " -> " + r.invokedComponent())));
        return pairs;
    }

    /** The components declared in the given files. */
    static Set<String> componentsOf(final OOPSourceCodeModel model, final Collection<String> paths) {
        final Set<String> normalized = new HashSet<>();
        paths.forEach(path -> normalized.add(ClarpseCompiler.normalizeForComparison(path)));
        return model.components()
                .filter(c -> c.sourceFile() != null
                        && normalized.contains(ClarpseCompiler.normalizeForComparison(c.sourceFile())))
                .map(Component::uniqueName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static String json(final OOPSourceCodeModel model) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, String> sorted = new java.util.TreeMap<>();
        model.components().forEach(c -> {
            try {
                sorted.put(c.uniqueName(), mapper.writeValueAsString(c));
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return sorted.toString();
    }
}
