package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /**
     * Transient and Json-ignored: it is an allocation strategy, not model content, and a serialized
     * model must not carry one. A model restored without a pool gets a fresh one on first use.
     */
    @JsonIgnore
    private transient StringPool stringPool;

    public OOPSourceCodeModel() {
        this(new StringPool());
    }

    /**
     * Builds a model that canonicalises the repeated strings of every component inserted into it
     * through the given pool.
     *
     * <p>Pass the <b>same</b> pool to two models to share their text. That is the case this exists
     * for: a differential analysis holds a base and a head model at once, naming almost entirely the
     * same packages, types and members, and 11% of the pair's footprint was measured to be the same
     * text held twice. See {@link StringPool} for the per-field figures and for why the pool is not
     * static.
     *
     * @param stringPool Pool to canonicalise through. Must not be {@code null}; a model given no
     *                   pool gets one of its own, which shares within itself but not with any other
     *                   model.
     */
    public OOPSourceCodeModel(final StringPool stringPool) {
        if (stringPool == null) {
            throw new IllegalArgumentException("A string pool is required.");
        }
        this.stringPool = stringPool;
    }

    public OOPSourceCodeModel(Map<String, Component> components) {
        this(new StringPool());
        insertComponents(components);
    }

    /**
     * The pool this model canonicalises its components' strings through.
     *
     * @return This model's pool, never {@code null}.
     */
    public StringPool stringPool() {
        StringPool pool = this.stringPool;
        if (pool == null) {
            pool = new StringPool();
            this.stringPool = pool;
        }
        return pool;
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
        final Component cloned = new Component(component, stringPool());
        components.put(cloned.uniqueName(), cloned);
    }

    public boolean containsComponent(final String componentName) {
        return getComponents().containsKey(componentName);
    }

    /**
     * A component of this model, as an isolated copy the caller may mutate freely.
     *
     * <p>The copy is deep: the imports, the children and every reference are duplicated. Callers rely
     * on that isolation, so this contract is unchanged -- but on a read-only path it is expensive
     * enough to dominate an analysis, and {@link #component(String)} is the accessor to reach for
     * there.
     *
     * @param componentName Unique name of the component.
     * @return A fresh copy of the component, or empty if this model has no such component.
     */
    public Optional<Component> copyOfComponent(final String componentName) {
        final Component component = this.getComponents().get(componentName);
        if (component == null) {
            return Optional.empty();
        }
        return Optional.of(new Component(component));
    }

    /**
     * A component of this model <b>as it is held</b>, without copying it.
     *
     * <p>For callers that only read, or that intend to modify the model through the component they
     * are handed. Mutating the returned component mutates this model, and iterating one of its
     * collections while inserting into it will throw, so a caller that needs isolation wants
     * {@link #copyOfComponent(String)} instead -- which is what the {@code copyOf} on that name is
     * there to tell you at the call site.
     *
     * <p>It exists because {@link #copyOfComponent(String)} deep-copies on every read, and the read
     * paths are enormous: relationship extraction resolves every reference in the model through it
     * and walks a parent chain per member, repeatedly, when two models are compared. Measured on a
     * pair of 11,750-component models, moving those paths onto this accessor cut one comparison from
     * 619MB of allocation to a fraction of it. It is allocation churn rather than live size that
     * dominates there, which is why a consumer can exhaust a budget many times its configured heap
     * without ever seeing an {@code OutOfMemoryError}.
     *
     * @param componentName Unique name of the component.
     * @return The model's own instance of the component, or empty if this model has no such
     *         component.
     */
    public Optional<Component> component(final String componentName) {
        return Optional.ofNullable(this.getComponents().get(componentName));
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

    /**
     * A copy of this model whose components are copies, sharing this model's string pool.
     *
     * <p>Sharing the pool is what keeps the copy from re-allocating every name it already holds an
     * instance of.
     *
     * @return An independent model holding equal components.
     */
    public OOPSourceCodeModel copy() {
        final OOPSourceCodeModel copy = new OOPSourceCodeModel(this.stringPool());
        copy.insertComponents(this.getComponents());
        return copy;
    }

    /**
     * Fetches the current component's parent base component if it exists. This may
     * not be the component's direct parent.
     */
    public Component copyOfParentBaseComponent(String cmpUniqueName) throws IllegalArgumentException {
        return new Component(parentBaseComponent(cmpUniqueName));
    }

    /**
     * The same walk as {@link #copyOfParentBaseComponent(String)}, returning this model's own instance rather
     * than a copy, and copying nothing on the way up.
     *
     * <p>The walk itself was the cost, not just its result: every step called
     * {@link #copyOfComponent(String)} and so deep-copied a component in order to read one field off it
     * and throw it away. Relationship extraction walks this chain once per member component and once
     * per resolved reference, so on a model of twelve thousand components it was tens of thousands of
     * discarded copies per extraction, three extractions per analysis.
     *
     * <p>Mutating the result mutates the model. See {@link #component(String)}.
     *
     * @param cmpUniqueName Unique name of the component to walk up from.
     * @return This model's own instance of the nearest enclosing base component.
     * @throws IllegalArgumentException If no base component encloses the given name.
     */
    public Component parentBaseComponent(final String cmpUniqueName) throws IllegalArgumentException {
        String currParentClassName = cmpUniqueName;
        Optional<Component> parent;
        for (parent = this.component(currParentClassName); parent.isPresent()
                && !parent.get().componentType().isBaseComponent();
             parent = this.component(currParentClassName)) {
            currParentClassName = parent.get().parentUniqueName();
        }
        if (parent.isPresent()) {
            return parent.get();
        } else {
            throw new IllegalArgumentException("No parent exists for given component: " + cmpUniqueName);
        }
    }
}
