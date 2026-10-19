package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hadi.clarpse.sourcemodel.StringPool;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The value of one argument of an applied annotation, kept only when the source wrote it as a
 * literal.
 *
 * <p>A literal is a string, a number, a boolean, an enum constant, a class literal, or an array of
 * these. Anything else -- a computed expression, a constant declared elsewhere, a nested
 * annotation -- is recorded as {@link Kind#UNRESOLVED}: the argument is present, and its value is
 * not known. A value is never guessed.
 *
 * <p>Instances are immutable, so a reference and its copies share them.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AnnotationArgument implements Serializable {

    private static final long serialVersionUID = 6140723982201110301L;

    /**
     * What an argument's value is.
     */
    public enum Kind {
        /** A string; {@link #value()} is its content without quotes. */
        STRING,
        /** A number; {@link #value()} is its text as written, without a type suffix. */
        NUMBER,
        /** A boolean; {@link #value()} is {@code true} or {@code false}. */
        BOOLEAN,
        /** An enum constant; {@link #value()} is its name as written, qualified or not. */
        ENUM,
        /** A class literal; {@link #value()} is the type name. */
        CLASS,
        /** An array; {@link #elements()} holds its elements in order. */
        ARRAY,
        /** An argument that is present but is not a literal; it has no value. */
        UNRESOLVED
    }

    private Kind kind;
    private String value;
    private List<AnnotationArgument> elements;
    @JsonIgnore
    private transient String enumText;
    @JsonIgnore
    private transient String enumOwner;

    private AnnotationArgument() {
    }

    private AnnotationArgument(final Kind kind, final String value,
                               final List<AnnotationArgument> elements) {
        this.kind = kind;
        this.value = value;
        this.elements = elements;
    }

    /**
     * A literal of the given scalar kind.
     *
     * @param kind  Any kind other than {@link Kind#ARRAY} and {@link Kind#UNRESOLVED}.
     * @param value The literal's value.
     * @return The argument.
     */
    public static AnnotationArgument literal(final Kind kind, final String value) {
        if (kind == Kind.ARRAY || kind == Kind.UNRESOLVED || value == null) {
            throw new IllegalArgumentException("Not a scalar literal: " + kind);
        }
        return new AnnotationArgument(kind, value, null);
    }

    /**
     * An array of arguments.
     *
     * @param elements The array's elements, in order.
     * @return The argument.
     */
    public static AnnotationArgument array(final List<AnnotationArgument> elements) {
        return new AnnotationArgument(Kind.ARRAY, null,
                Collections.unmodifiableList(new ArrayList<>(elements)));
    }

    /**
     * An argument that is present but not a literal.
     *
     * @return The argument.
     */
    public static AnnotationArgument unresolved() {
        return new AnnotationArgument(Kind.UNRESOLVED, null, null);
    }

    /**
     * A dotted name that is an enum constant if its qualifier names an enum, which a front end that
     * parses one file at a time cannot tell until the whole repository is known.
     *
     * <p>The argument is {@link Kind#UNRESOLVED} until {@link AnnotationReference#settleEnumCandidates}
     * decides it, and stays so if it is never settled.
     *
     * @param text  The name as written, qualifier included.
     * @param owner The unique name of the type the qualifier resolved to.
     * @return The undecided argument.
     */
    public static AnnotationArgument enumCandidate(final String text, final String owner) {
        final AnnotationArgument argument = unresolved();
        argument.enumText = text;
        argument.enumOwner = owner;
        return argument;
    }

    /**
     * This argument with its enum candidates decided; arrays are settled element by element.
     *
     * @param isEnum Whether a unique name is an enum of the repository.
     * @return This argument when nothing in it was a candidate, otherwise the settled copy.
     */
    AnnotationArgument settled(final Predicate<String> isEnum) {
        if (kind == Kind.ARRAY) {
            boolean changed = false;
            final List<AnnotationArgument> settledElements = new ArrayList<>();
            for (final AnnotationArgument element : elements()) {
                final AnnotationArgument settled = element.settled(isEnum);
                changed |= settled != element;
                settledElements.add(settled);
            }
            if (changed) {
                return array(settledElements);
            }
            return this;
        }
        if (enumOwner != null && isEnum.test(enumOwner)) {
            return literal(Kind.ENUM, enumText);
        }
        return this;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * @return The literal's value, or {@code null} for an array or an unresolved argument.
     */
    public String value() {
        return value;
    }

    /**
     * @return An array's elements, in order; empty for any other kind.
     */
    public List<AnnotationArgument> elements() {
        if (elements == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(elements);
    }

    public boolean isResolved() {
        return kind != Kind.UNRESOLVED;
    }

    /**
     * A copy whose strings are the pool's canonical instances.
     *
     * @param pool The pool to canonicalise through.
     * @return This argument itself when it holds nothing to share, otherwise the pooled copy.
     */
    AnnotationArgument pooled(final StringPool pool) {
        if (kind == Kind.UNRESOLVED) {
            return this;
        }
        if (kind == Kind.ARRAY) {
            final List<AnnotationArgument> pooledElements = new ArrayList<>();
            for (final AnnotationArgument element : elements()) {
                pooledElements.add(element.pooled(pool));
            }
            return new AnnotationArgument(kind, null, Collections.unmodifiableList(pooledElements));
        }
        return new AnnotationArgument(kind, pool.pooled(value), null);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof AnnotationArgument)) {
            return false;
        }
        final AnnotationArgument other = (AnnotationArgument) obj;
        return kind == other.kind && Objects.equals(value, other.value)
                && elements().equals(other.elements());
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, elements());
    }

    @Override
    public String toString() {
        if (kind == Kind.ARRAY) {
            return elements().toString();
        }
        if (kind == Kind.UNRESOLVED) {
            return "<unresolved>";
        }
        return kind + ":" + value;
    }
}
