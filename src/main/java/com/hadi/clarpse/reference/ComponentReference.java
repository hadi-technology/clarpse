package com.hadi.clarpse.reference;

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
        invokedComponent = invocationComponentName;
    }

    public ComponentReference(final ComponentReference invocation) {
        invokedComponent = invocation.invokedComponent();
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
