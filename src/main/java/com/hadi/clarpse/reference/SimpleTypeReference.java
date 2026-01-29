package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

public class SimpleTypeReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 7304258760520469246L;
    @JsonIgnore
    public final String type = "simple";

    public SimpleTypeReference(final String invocationComponentName) {
        super(invocationComponentName);
    }


    @Override
    public int priority() {
        return 2;
    }

    public SimpleTypeReference() {
        super();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        super.clone();
        return new SimpleTypeReference(invokedComponent());
    }
}
