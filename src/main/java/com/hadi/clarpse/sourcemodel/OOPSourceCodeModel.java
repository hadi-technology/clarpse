package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        components.put(cloned.uniqueName(), cloned);
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
        this.components.remove(cmpUniqueName);
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
        String currParentClassName = cmpUniqueName;
        Optional<Component> parent;
        for (parent = this.getComponent(currParentClassName); parent.isPresent()
                && !parent.get().componentType().isBaseComponent(); parent = this.getComponent(currParentClassName)) {
            currParentClassName = parent.get().parentUniqueName();
        }
        if (parent.isPresent()) {
            return parent.get();
        } else {
            throw new IllegalArgumentException("No parent exists for given component: " + cmpUniqueName);
        }
    }
}
