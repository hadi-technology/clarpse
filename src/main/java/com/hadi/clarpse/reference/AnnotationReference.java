package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hadi.clarpse.sourcemodel.StringPool;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference indicating that a component's declaration carries an applied annotation.
 *
 * <p>An applied annotation ({@code @Service} in Java, {@code [ApiController]} in C#,
 * {@code @dataclass} in Python, {@code @Component} in TypeScript) names a type the same way
 * {@code extends} and {@code implements} do, so it is modelled as its own kind of
 * {@link ComponentReference} -- {@link TypeExtensionReference} and
 * {@link TypeImplementationReference} are the models it mirrors -- rather than as a separate field.
 * The {@link #invokedComponent()} is the annotation type's name: fully qualified where the parser
 * could resolve it, and the simple name written in source otherwise.
 *
 * <p>An annotation carries architectural intent (a bean's stereotype, a controller's role, a
 * field's injection) that no other part of the parsed model expresses. It is deliberately
 * <em>not</em> an ordinary type-usage dependency: a class annotated {@code @Service} does not
 * <em>use</em> {@code Service} the way it uses a collaborator it calls, so a downstream consumer
 * must route this kind to an annotations fact rather than a dependency edge.
 *
 * <p>The annotation's arguments (the {@code "/x"} in {@code @RequestMapping("/x")}) are kept in an
 * optional, ordered map of argument name to {@link AnnotationArgument}. A single unnamed argument is
 * named {@code value}; several unnamed arguments are named by their position, {@code 0}, {@code 1}
 * and so on. A TypeScript decorator called with a single object of plain properties contributes
 * those properties as its arguments. Only literals keep their value; an argument that is present but is not a literal is
 * {@link AnnotationArgument.Kind#UNRESOLVED}. {@link #arguments()} is {@code null} where the
 * language front end did not read arguments, and empty where it read them and there were none, so a
 * consumer can tell an annotation without arguments from one whose arguments are unknown. The
 * arguments take no part in equality: two references to the same annotation type are the same
 * reference.
 */
public final class AnnotationReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 8815092020304050607L;
    @JsonIgnore
    public final String type = "annotation";
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, AnnotationArgument> arguments;

    @Override
    public int priority() {
        return 3;
    }

    public AnnotationReference() {
        super();
    }

    public AnnotationReference(final String invokedComponent) {
        super(invokedComponent);
    }

    /**
     * The annotation's arguments, in the order they were written.
     *
     * @return An unmodifiable map, empty when the annotation was read and has no arguments, or
     *         {@code null} when the language front end did not read arguments.
     */
    public Map<String, AnnotationArgument> arguments() {
        if (arguments == null) {
            return null;
        }
        return Collections.unmodifiableMap(arguments);
    }

    /**
     * Records the annotation's arguments.
     *
     * @param arguments Argument name to value, in written order; empty for an annotation read as
     *                  having none, {@code null} for one whose arguments were not read.
     */
    public void setArguments(final Map<String, AnnotationArgument> arguments) {
        if (arguments == null) {
            this.arguments = null;
        } else {
            this.arguments = new LinkedHashMap<>(arguments);
        }
    }

    /**
     * Decides the enum candidates among the arguments, see {@link AnnotationArgument#enumCandidate}.
     *
     * @param isEnum Whether a unique name is an enum of the repository.
     */
    public void settleEnumCandidates(final java.util.function.Predicate<String> isEnum) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        final Map<String, AnnotationArgument> settled = new LinkedHashMap<>();
        for (final Map.Entry<String, AnnotationArgument> entry : arguments.entrySet()) {
            settled.put(entry.getKey(), entry.getValue().settled(isEnum));
        }
        this.arguments = settled;
    }

    /**
     * Replaces this reference's argument strings with the pool's canonical instances.
     *
     * @param pool The pool to canonicalise through.
     */
    public void pool(final StringPool pool) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        final Map<String, AnnotationArgument> pooled = new LinkedHashMap<>();
        for (final Map.Entry<String, AnnotationArgument> entry : arguments.entrySet()) {
            pooled.put(pool.pooled(entry.getKey()), entry.getValue().pooled(pool));
        }
        this.arguments = pooled;
    }

    @Override
    public Object clone() {
        final AnnotationReference copy = new AnnotationReference(invokedComponent());
        copy.setResolutionKind(resolutionKind());
        copy.arguments = arguments;
        return copy;
    }
}
