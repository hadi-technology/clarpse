package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

public class TypeImplementationReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 7807962152246261233L;
    @JsonIgnore
    public final String type = "implementation";

    @Override
    public int priority() {
        return 1;
    }

    public TypeImplementationReference() {
        super();
    }

    public TypeImplementationReference(String invokedComponent) {
        super(invokedComponent);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        super.clone();
        return new TypeImplementationReference(invokedComponent());
    }
}
