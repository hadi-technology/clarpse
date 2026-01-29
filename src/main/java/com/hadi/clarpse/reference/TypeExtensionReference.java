package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

public final class TypeExtensionReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 6641497827060470449L;
    @JsonIgnore
    public final String type = "extension";

    @Override
    public int priority() {
        return 1;
    }

    public TypeExtensionReference() {
        super();
    }

    public TypeExtensionReference(String invokedComponent) {
        super(invokedComponent);
    }

    @Override
    public Object clone() {
        return new TypeExtensionReference(invokedComponent());
    }
}
