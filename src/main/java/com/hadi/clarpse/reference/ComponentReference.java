package com.hadi.clarpse.reference;

import com.hadi.clarpse.TypeNames;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * Represents a reference to another component in the code base.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = SimpleTypeReference.class, name = "simple"),
        @Type(value = TypeExtensionReference.class, name = "extension"),
        @Type(value = TypeImplementationReference.class, name = "implementation")})
public abstract class ComponentReference implements Serializable, Cloneable {

    private static final long serialVersionUID = -242718695900611890L;
    private String invokedComponent = "";
    private boolean external = false;

    public ComponentReference(final String invocationComponentName) {
        invokedComponent = pooled(named(invocationComponentName));
    }

    public ComponentReference(final ComponentReference invocation) {
        invokedComponent = pooled(named(invocation.invokedComponent()));
    }

    /**
     * A reference names a type, so what is stored is a type name and not the type expression the
     * source happened to write.
     *
     * <p>Every language front end here resolves a written type and hands the result on unchanged,
     * so {@code implements DTO<HttpRequest>} arrives as {@code DTO<HttpRequest>} and
     * {@code Bar[] xs} as {@code Bar[]}. The model holds one component per type -- {@code DTO},
     * {@code Bar} -- and matches references to it by name, so such a reference joins to nothing:
     * a type with twenty-six implementers reports no incoming references at all, and
     * {@code Foo<Bar>} and {@code Foo<Baz>} count as two referenced types where the source has
     * one.
     * Both directions are silent, because a model missing an edge looks exactly like a model whose
     * code has no such edge.
     *
     * <p>Normalising here rather than at each recording site is deliberate: this is the one place
     * every reference in every language passes through, and the invariant wanted -- a reference
     * names a type -- is a property of references, not of any one parser. Front ends still need to
     * erase before they <em>resolve</em>, since a name carrying its type arguments matches no
     * import either; this cannot do that for them, only keep the stored name honest.
     */
    private static String named(final String invocationComponentName) {
        return TypeNames.erasure(invocationComponentName);
    }

    /**
     * One instance per distinct name, rather than one per reference.
     *
     * <p>A reference holds a fully-qualified name, and the same type is referenced from everywhere
     * that uses it, so the identical string is allocated once per mention. Measured on a real model:
     * 2,565 reference strings, 750 distinct -- <b>68% of the bytes are duplicates</b>, and
     * references are about 70% of a component's footprint. This is the largest single reduction
     * available in the parsed model, and it costs nothing semantically: the strings are immutable
     * and compared by value everywhere.
     *
     * <p>It matters because striff holds <em>two</em> models at once, base and head, for the whole
     * of a differential analysis. One medium repository was measured at ~4GB of live heap, which is
     * enough to put the JVM into a garbage-collection spiral it never leaves -- 95% GC overhead, an
     * analysis that never completes, and a pod that serves nothing while reporting healthy.
     *
     * <p><b>Deliberately {@link String#intern()} and not a pool of our own.</b> A static
     * {@code Map} here would be the shape of the bug fixed in 10.1.2, where
     * {@code JavaParserFacade} kept a static registry that nothing pruned and each entry pinned a
     * whole AST. The JVM's pool lives in the heap and its entries are collectable once unreachable,
     * so it cannot retain a compile's strings after the compile. A per-compile pool would also
     * work, but it would need threading through every parser and clearing on every exit path, and
     * an intern pool that is not cleared is a leak wearing an optimisation's clothes.
     */
    private static String pooled(final String name) {
        if (name == null) {
            return null;
        }
        return name.intern();
    }

    public abstract int priority();

    public ComponentReference() {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + invokedComponent + "]";
    }

    public String invokedComponent() {
        return invokedComponent;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(final boolean external) {
        this.external = external;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ComponentReference ref = (ComponentReference) obj;
        return this.invokedComponent.equals(ref.invokedComponent);
    }

    @Override
    public int hashCode() {
        return this.invokedComponent().hashCode() + getClass().hashCode();
    }

}
