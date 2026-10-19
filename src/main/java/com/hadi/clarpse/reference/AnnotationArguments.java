package com.hadi.clarpse.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the arguments written on one applied annotation, in written order, and names them the
 * way {@link AnnotationReference#arguments()} documents.
 *
 * <p>A named argument keeps its name. A single unnamed argument is named {@code value}; several
 * unnamed arguments are named by their position among the unnamed ones. When a later argument
 * repeats an earlier name, the earlier one is kept.
 */
public final class AnnotationArguments {

    /** The name given to a single unnamed argument. */
    public static final String VALUE = "value";

    private final Map<String, AnnotationArgument> named = new LinkedHashMap<>();
    private final List<Object[]> order = new ArrayList<>();
    private final List<AnnotationArgument> unnamed = new ArrayList<>();

    /**
     * Adds an argument written with a name.
     *
     * @param name     The argument's name.
     * @param argument Its value.
     * @return This collector.
     */
    public AnnotationArguments named(final String name, final AnnotationArgument argument) {
        order.add(new Object[] {name, argument});
        return this;
    }

    /**
     * Adds an argument written without a name.
     *
     * @param argument Its value.
     * @return This collector.
     */
    public AnnotationArguments unnamed(final AnnotationArgument argument) {
        order.add(new Object[] {null, argument});
        unnamed.add(argument);
        return this;
    }

    /**
     * The collected arguments.
     *
     * @return Argument name to value in written order; empty when none were written.
     */
    public Map<String, AnnotationArgument> build() {
        named.clear();
        int position = 0;
        for (final Object[] entry : order) {
            final String name;
            if (entry[0] != null) {
                name = (String) entry[0];
            } else if (unnamed.size() == 1) {
                name = VALUE;
            } else {
                name = String.valueOf(position);
                position++;
            }
            named.putIfAbsent(name, (AnnotationArgument) entry[1]);
        }
        return new LinkedHashMap<>(named);
    }
}
