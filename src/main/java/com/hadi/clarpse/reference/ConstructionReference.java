package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * Reference indicating that a component constructs an instance of a type: Java and C#
 * {@code new T(...)}, TypeScript {@code new T(...)}, a Kotlin or Python call of a class.
 *
 * <p>It is the finer companion of the {@link SimpleTypeReference} to the same type, never a
 * replacement for it: where the simple reference says "this component uses {@code Order}", this one
 * says "this component creates an {@code Order}". A consumer that wants only the type-level graph may
 * ignore this kind; it then sees the graph it would see without it.
 *
 * <p>The target is the constructed type, written and classified exactly as a type reference to it
 * is ({@link #isExternal()}, {@link #isNotLoaded()}, {@link #resolutionKind()}). Type arguments are
 * not part of the target.
 *
 * <p><b>When it is recorded.</b> Only when the construction is explicit in the source and the called
 * name is known to be a type. A creation that names no type is not recorded: an anonymous class body
 * ({@code new Runnable() { ... }} constructs an anonymous subtype), an array creation, a C# target-typed
 * {@code new()}, and a call whose callee the parse cannot tell from a function. An unrecorded construction
 * is therefore unknown, not absent.
 *
 * <p>Equality is the class and the target, so a component that constructs one type several times
 * holds one reference.
 */
public final class ConstructionReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 5192843617245580134L;
    @JsonIgnore
    public final String type = "construction";

    public ConstructionReference() {
        super();
    }

    /**
     * A construction of a type.
     *
     * @param constructedType The constructed type's name, as a type reference to it would write it.
     */
    public ConstructionReference(final String constructedType) {
        super(constructedType);
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public Object clone() {
        final ConstructionReference copy = new ConstructionReference(invokedComponent());
        copy.setResolutionKind(resolutionKind());
        return copy;
    }
}
