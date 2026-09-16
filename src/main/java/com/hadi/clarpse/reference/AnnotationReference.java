package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

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
 * must route this kind to an annotations fact rather than a dependency edge. The annotation's
 * member arguments (for example the {@code "/x"} in {@code @RequestMapping("/x")}) are not
 * retained -- only the type name.
 */
public final class AnnotationReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 8815092020304050607L;
    @JsonIgnore
    public final String type = "annotation";

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

    @Override
    public Object clone() {
        final AnnotationReference copy = new AnnotationReference(invokedComponent());
        copy.setResolutionKind(resolutionKind());
        return copy;
    }
}
