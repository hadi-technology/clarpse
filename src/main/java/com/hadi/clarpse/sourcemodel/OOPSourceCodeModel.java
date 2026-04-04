package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A language independent representation of a codebase that reveals its
 * structural buildup.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OOPSourceCodeModel implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LogManager.getLogger(OOPSourceCodeModel.class);
    private final Map<String, Component> components = new HashMap<>();
    // NEW: file → component names index for efficient file-based operations
    private final Map<String, Set<String>> componentsByFile = new HashMap<>();

    public OOPSourceCodeModel() {
    }

    public OOPSourceCodeModel(Map<String, Component> components) {
        insertComponents(components);
    }

    private Map<String, Component> getComponents() {
        return components;
    }

    public void merge(final OOPSourceCodeModel sourceModel) {
        insertComponents(sourceModel.getComponents());
    }

    public int size() {
        return components.size();
    }

    public void insertComponent(final Component component) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Inserted component {}.", component);
        }
        final Component cloned = new Component(component);
        String uniqueName = cloned.uniqueName();
        components.put(uniqueName, cloned);

        // Update file-to-component index
        String sourceFile = cloned.sourceFile();
        if (sourceFile != null) {
            componentsByFile.computeIfAbsent(sourceFile, k -> new HashSet<>())
                .add(uniqueName);
        }
    }

    public boolean containsComponent(final String componentName) {
        return getComponents().containsKey(componentName);
    }

    public Optional<Component> getComponent(final String componentName) {
        final Component component = this.getComponents().get(componentName);
        if (component == null) {
            return Optional.empty();
        }
        return Optional.of(new Component(component));
    }

    /**
     * Returns the component directly without copying.
     *
     * @apiNote The returned Component MUST NOT be modified. This method exists
     *          for performance-critical read-only paths (e.g., relationship
     *          extraction). For mutable access, use {@link #getComponent(String)}.
     */
    public Optional<Component> getComponentDirect(final String componentName) {
        return Optional.ofNullable(this.components.get(componentName));
    }

    public void insertComponents(final Map<String, Component> newCmps) {
        if (newCmps == null) {
            return;
        }
        for (final Map.Entry<String, Component> entry : newCmps.entrySet()) {
            if (entry.getValue() != null) {
                insertComponent(entry.getValue());
            }
        }
    }

    public void removeComponent(String cmpUniqueName) {
        Component removed = this.components.remove(cmpUniqueName);
        if (removed != null && removed.sourceFile() != null) {
            Set<String> fileSet = componentsByFile.get(removed.sourceFile());
            if (fileSet != null) {
                fileSet.remove(cmpUniqueName);
                if (fileSet.isEmpty()) {
                    componentsByFile.remove(removed.sourceFile());
                }
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removed component {}.", cmpUniqueName);
        }
    }

    public Stream<Component> components() {
        return components.values().stream();
    }

    public OOPSourceCodeModel copy() {
        return new OOPSourceCodeModel(this.getComponents());
    }

    /**
     * Fetches the current component's parent base component if it exists. This may
     * not be the component's direct parent.
     */
    public Component parentBaseCmp(String cmpUniqueName) throws IllegalArgumentException {
        String currName = cmpUniqueName;
        Optional<Component> parent;
        for (parent = this.getComponentDirect(currName);
             parent.isPresent() && !parent.get().componentType().isBaseComponent();
             parent = this.getComponentDirect(currName)) {
            currName = parent.get().parentUniqueName();
        }
        if (parent.isPresent()) {
            return parent.get();
        }
        throw new IllegalArgumentException("No parent exists for given component: " + cmpUniqueName);
    }

    /**
     * Removes all components belonging to the given source file.
     * Components are removed children-first (longest qualified names first)
     * to prevent orphan child references.
     *
     * @return the set of component unique names that were removed
     */
    public Set<String> removeComponentsForFile(String sourceFile) {
        Set<String> names = componentsByFile.getOrDefault(sourceFile, Collections.emptySet());
        if (names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> removed = new HashSet<>(names);
        // Remove in order (children before parents)
        names.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .forEach(components::remove);
        componentsByFile.remove(sourceFile);
        return removed;
    }

    /**
     * Returns the set of source files that have components in this model.
     */
    public Set<String> sourceFiles() {
        return Collections.unmodifiableSet(componentsByFile.keySet());
    }

    /**
     * Returns component names belonging to the given source file.
     */
    public Set<String> getComponentNamesForFile(String sourceFile) {
        return Collections.unmodifiableSet(
            componentsByFile.getOrDefault(sourceFile, Collections.emptySet()));
    }
}
