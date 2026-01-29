package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Representation of the individual code level components (classes,
 * methodComponents, fieldComponents, etc..) that are used to create a code
 * base.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class Component implements Serializable {

    private static final Logger LOGGER = LogManager.getLogger(Component.class);
    private static final long serialVersionUID = 1L;
    @JsonInclude(Include.NON_EMPTY)
    private final List<String> children = new ArrayList<>();
    private String value;
    private Package pkg;
    private int cyclo;
    private String name;
    private String comment = "";
    private String sourceFile;
    @JsonInclude(Include.NON_EMPTY)
    private Set<String> imports = new HashSet<>();
    @JsonInclude(Include.NON_EMPTY)
    private Set<String> modifiers = new LinkedHashSet<>();
    private ComponentType type;
    @JsonIgnore
    private Set<ComponentReference> internalReferences = new LinkedHashSet<>();
    @JsonIgnore
    private Set<ComponentReference> externalReferences = new LinkedHashSet<>();
    private String componentName;
    private int codeHash;
    private String codeFragment;

    public Component(final Component component) throws Exception {
        modifiers = component.modifiers();
        type = component.componentType();
        codeFragment = component.codeFragment();
        imports = component.imports();
        componentName = component.componentName();
        pkg = component.pkg();
        value = component.value();
        sourceFile = component.sourceFile();
        comment = component.comment();
        codeHash = component.codeHash();
        cyclo = component.cyclo();
        name = component.name();
        children.addAll(component.children);
        for (final ComponentReference ref : component.references()) {
            final ComponentReference clonedRef = (ComponentReference) ref.clone();
            clonedRef.setExternal(ref.isExternal());
            this.insertCmpRef(clonedRef);
        }
    }

    public Component() {
    }

    public List<String> children() {
        return children;
    }

    public String uniqueName() {
        if (this.pkg != null && !this.pkg.ellipsisSeparatedPkgPath().isEmpty()) {
            return this.pkg.ellipsisSeparatedPkgPath() + "." + componentName;
        } else if (this.pkg != null && !this.pkg.name().isEmpty()) {
            return this.pkg.name() + "." + componentName;
        } else {
            return this.componentName;
        }
    }

    public int cyclo() {
        return cyclo;
    }

    public void setCyclo(final int cyclo) {
        this.cyclo = cyclo;
        LOGGER.debug("Set cyclo of " + cyclo + " for " + this + ".");
    }

    public String name() {
        return name;
    }

    public void insertChildComponent(final String childComponentName) {
        children.add(childComponentName);
    }

    public void addImports(final String importStmt) {
        imports.add(importStmt);
    }

    public String codeFragment() {
        return codeFragment;
    }

    @JsonProperty("references")
    public Set<ComponentReference> references() {
        final Set<ComponentReference> combined = new LinkedHashSet<>(internalReferences);
        combined.addAll(externalReferences);
        return combined;
    }

    @JsonProperty("references")
    private void setReferences(final Set<ComponentReference> refs) {
        internalReferences = new LinkedHashSet<>();
        externalReferences = new LinkedHashSet<>();
        if (refs != null) {
            refs.forEach(this::insertCmpRef);
        }
    }

    public Set<ComponentReference> internalDependencies() {
        return internalReferences;
    }

    public Set<ComponentReference> externalDependencies() {
        return externalReferences;
    }

    public void insertCmpRef(final ComponentReference ref) {
        if (ref.isExternal()) {
            externalReferences.add(ref);
        } else {
            internalReferences.add(ref);
        }
        LOGGER.debug("Inserted " + ref + " for " + this);
    }

    public Set<String> imports() {
        return imports;
    }

    public String componentName() {
        return componentName;
    }

    public void setCodeFragment(final String componentDeclarationTypeFragment) {
        codeFragment = componentDeclarationTypeFragment;
    }

    public void setExternalTypeReferences(final Set<ComponentReference> externalReferences) {
        internalReferences = new LinkedHashSet<>();
        this.externalReferences = new LinkedHashSet<>(externalReferences);
        this.externalReferences.forEach(ref -> ref.setExternal(true));
    }

    public void setReferenceClassification(final Set<ComponentReference> internalReferences,
                                           final Set<ComponentReference> externalReferences) {
        internalReferences.forEach(ref -> ref.setExternal(false));
        externalReferences.forEach(ref -> ref.setExternal(true));
        this.internalReferences = internalReferences;
        this.externalReferences = externalReferences;
    }

    public void setImports(final Set<String> currentImports) {
        imports = currentImports;
    }

    public void setComponentName(final String componentName) {
        this.componentName = componentName;
    }

    public Set<String> modifiers() {
        return modifiers;
    }

    public void insertAccessModifier(final String modifier) {
        if (OOPSourceModelConstants.getJavaAccessModifierMap().containsValue(modifier.toLowerCase())) {
            modifiers.add(modifier.toLowerCase());
        } else {
            throw new IllegalArgumentException(modifier + " is an invalid modifier!");
        }
    }

    public ComponentType componentType() {
        return type;
    }

    public void setComponentType(final ComponentType componentType) {
        type = componentType;
    }

    public Package pkg() {
        return this.pkg;
    }

    public void setPkg(final Package pkg) {
        this.pkg = pkg;
    }

    @Override
    public String toString() {
        return this.uniqueName();
    }
    public String value() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String comment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public String parentUniqueName() {
        final int lastOpeningBracket = uniqueName().indexOf("(");
        if (lastOpeningBracket == -1 || !type.isMethodComponent()) {
            if (uniqueName().contains(".")) {
                final int lastPeriod = uniqueName().lastIndexOf(".");
                return uniqueName().substring(0, lastPeriod);
            } else {
                throw new IllegalArgumentException("Cannot get parent of component: " + uniqueName());
            }
        } else {
            final String methodComponentName = uniqueName().substring(0, lastOpeningBracket);
            final int lastPeriod = methodComponentName.lastIndexOf(".");
            return methodComponentName.substring(0, lastPeriod);
        }
    }

    public void insertCmpRefs(final Collection<ComponentReference> cmpRefs) {
        for (final ComponentReference typeRef : cmpRefs) {
            insertCmpRef(typeRef);
        }
    }

    public List<ComponentReference> references(final TypeReferences type) {
        final List<ComponentReference> tmpReferences = new ArrayList<>();
        for (final ComponentReference compReference : references()) {
            if (type.getMatchingClass().isAssignableFrom(compReference.getClass())) {
                tmpReferences.add(compReference);
            }
        }
        return tmpReferences;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String sourceFile() {
        return sourceFile;
    }

    public void setSourceFilePath(final String sourceFilePath) {
        sourceFile = sourceFilePath;
    }

    public void setAccessModifiers(final List<String> list) {
        for (final String modifier : list) {
            modifiers.add(modifier.toLowerCase());
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return (((Component) o).uniqueName().equals(uniqueName()));
    }

    @Override
    public int hashCode() {
        return uniqueName().hashCode();
    }

    public int codeHash() {
        return codeHash;
    }

    public void setCodeHash(int codeHash) {
        this.codeHash = codeHash;
    }
}
