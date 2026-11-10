package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private String module;
    @JsonInclude(Include.NON_EMPTY)
    private Set<String> imports = new HashSet<>();
    @JsonInclude(Include.NON_EMPTY)
    private Set<String> importedFiles = new LinkedHashSet<>();
    @JsonInclude(Include.NON_EMPTY)
    private Set<String> modifiers = new LinkedHashSet<>();
    private ComponentType type;
    @JsonIgnore
    private Set<ComponentReference> internalReferences = new LinkedHashSet<>();
    @JsonIgnore
    private Set<ComponentReference> externalReferences = new LinkedHashSet<>();
    @JsonIgnore
    private Set<ComponentReference> notLoadedReferences = new LinkedHashSet<>();
    @JsonInclude(Include.NON_DEFAULT)
    private boolean boundary;
    private String componentName;
    private int codeHash;
    private String codeFragment;

    public Component(final Component component) {
        this(component, null);
    }

    /**
     * Copies the given component, canonicalising every string field that repeats through the given
     * pool.
     *
     * <p>Pooling happens here rather than in the parsers because a component is copied on its way
     * into a model anyway, so the canonical instance can be chosen during a copy that was already
     * being made: the cost is one hash lookup per field and no extra allocation. Threading a pool
     * through four parsers would reach the same values later, having allocated them all first.
     *
     * @param component Component to copy.
     * @param pool      Pool to canonicalise repeated strings through, or {@code null} to copy the
     *                  references as they are.
     */
    public Component(final Component component, final StringPool pool) {
        if (pool == null) {
            this.modifiers = new LinkedHashSet<>(component.modifiers);
            this.imports = new LinkedHashSet<>(component.imports);
            this.importedFiles = new LinkedHashSet<>(component.importedFiles());
            this.codeFragment = component.codeFragment;
            this.componentName = component.componentName;
            this.pkg = component.pkg;
            this.value = component.value;
            this.sourceFile = component.sourceFile;
            this.module = component.module;
            this.comment = component.comment;
            this.name = component.name;
            this.children.addAll(component.children);
        } else {
            this.modifiers = pool.pooledSet(component.modifiers);
            this.imports = pool.pooledSet(component.imports);
            this.importedFiles = pool.pooledSet(component.importedFiles);
            this.codeFragment = pool.pooled(component.codeFragment);
            this.componentName = pool.pooled(component.componentName);
            this.pkg = pool.pooled(component.pkg);
            this.value = pool.pooled(component.value);
            this.sourceFile = pool.pooled(component.sourceFile);
            this.module = pool.pooled(component.module);
            this.comment = pool.pooled(component.comment);
            this.name = pool.pooled(component.name);
            for (final String child : component.children) {
                this.children.add(pool.pooled(child));
            }
        }
        this.type = component.type;
        this.codeHash = component.codeHash;
        this.cyclo = component.cyclo;
        this.boundary = component.boundary;
        component.references().forEach(reference -> insertCmpRefCopy(reference, pool));
    }

    public Component() {
    }

    /**
     * This component's child component names, in declaration order.
     *
     * <p>An unmodifiable <b>view</b> of the live list rather than a copy of it. It was a copy, and
     * a consumer comparing two models was making that copy inside a filter predicate -- once per
     * child per component, on models of twelve thousand components each. Every known caller only
     * reads: {@code contains}, {@code size}, iteration. A caller that
     * needs a snapshot across a mutation must now take one, and a caller inserting a child while
     * iterating this view will get a {@link java.util.ConcurrentModificationException} where it
     * previously got a silently stale list -- which is the better of the two failures.
     *
     * @return Unmodifiable view of this component's children.
     */
    public List<String> children() {
        return Collections.unmodifiableList(children);
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
        combined.addAll(notLoadedReferences);
        return combined;
    }

    @JsonProperty("references")
    private void setReferences(final Set<ComponentReference> refs) {
        internalReferences = new LinkedHashSet<>();
        externalReferences = new LinkedHashSet<>();
        notLoadedReferences = new LinkedHashSet<>();
        if (refs != null) {
            refs.forEach(this::insertCmpRef);
        }
    }

    /**
     * The references this component makes to types inside the codebase.
     *
     * <p>An unmodifiable view rather than a copy. Relationship extraction reads both dependency sets
     * three times per component and was paying for six set copies each time.
     *
     * @return Unmodifiable view of the internalReferences.
     */
    public Set<ComponentReference> internalDependencies() {
        return Collections.unmodifiableSet(internalReferences);
    }

    /**
     * The references this component makes to types outside the codebase.
     *
     * <p>An unmodifiable view rather than a copy. Relationship extraction reads both dependency sets
     * three times per component and was paying for six set copies each time.
     *
     * @return Unmodifiable view of the externalReferences.
     */
    public Set<ComponentReference> externalDependencies() {
        return Collections.unmodifiableSet(externalReferences);
    }

    /**
     * The references this component makes to types declared in the repository but not loaded into
     * the model. Always empty outside a one-level analysis.
     *
     * @return Unmodifiable view of the references in the {@link ComponentReference#isNotLoaded()}
     *         state.
     */
    public Set<ComponentReference> notLoadedDependencies() {
        return Collections.unmodifiableSet(notLoadedReferences);
    }

    /**
     * Whether this component was loaded only because a focus file of a one-level analysis
     * references its file.
     *
     * <p>A boundary component's declarations are complete, but its outgoing references are not: a
     * dependency it has on something past the boundary can be missing from the model or appear as
     * a {@link ComponentReference#isNotLoaded()} reference. A consumer must not conclude from a
     * missing edge that a boundary component has no such dependency.
     *
     * @return {@code true} for a component of a level-one file of a one-level analysis.
     */
    public boolean isBoundary() {
        return boundary;
    }

    public void setBoundary(final boolean boundary) {
        this.boundary = boundary;
    }

    public void insertCmpRef(final ComponentReference ref) {
        if (ref.isNotLoaded()) {
            notLoadedReferences.add(ref);
        } else if (ref.isExternal()) {
            externalReferences.add(ref);
        } else {
            internalReferences.add(ref);
        }
        LOGGER.debug("Inserted " + ref + " for " + this);
    }

    /**
     * This component's import statements.
     *
     * @return Unmodifiable view of the imports, in declaration order.
     */
    public Set<String> imports() {
        return Collections.unmodifiableSet(imports);
    }

    /**
     * The project files that the import directives of this component's file name.
     *
     * <p>For TypeScript and Python that is a module file an import resolves to. For Java, Kotlin
     * and C# it is the file or files declaring the single type or member a directive names. A
     * namespace or package wildcard ({@code import a.b.*}, Kotlin {@code import a.b.*}, C#
     * {@code using A.B;}, a namespace alias) names no file and contributes nothing; a wildcard
     * over a type contributes that type's file. A library, framework, JDK or unresolved target,
     * a file outside the project, and the component's own file contribute nothing.
     *
     * <p>Each entry is a path in the form of {@link #sourceFile()}, so it can be compared with the
     * source file of another component directly. The order is the source order of first appearance,
     * without repeats. No path is derived from the text of an import.
     *
     * <ul>
     *   <li><b>TypeScript.</b> The target of every relative import, and of every workspace-package
     *       or path-alias import that resolves to a source file inside the project, whether written
     *       as a static import, {@code export ... from}, {@code import()} or {@code require} with a
     *       literal argument. A target counts whether or not it declares a type. A package resolved
     *       into {@code node_modules} and a specifier that does not resolve contribute
     *       nothing.</li>
     *   <li><b>Python.</b> Each import is resolved from the importing file by the type checker's
     *       import resolution. {@code import a.b.c} gives the file of the module {@code a.b.c}
     *       ({@code a/b/c.py}, or {@code a/b/c/__init__.py} for a package); the {@code __init__.py}
     *       files of the parent packages are not listed. {@code from a.b import c} gives
     *       {@code a/b/c.py} when {@code c} is a submodule, and otherwise the file of
     *       {@code a.b}; {@code from a.b import *} gives the file of {@code a.b}. Relative imports
     *       resolve against the importing file's package, and {@code importlib.import_module},
     *       {@code import_module} and {@code __import__} with a literal argument count as imports,
     *       as do imports inside functions, classes and {@code if TYPE_CHECKING:}. A resolution
     *       that lands on a {@code .pyi} stub records the {@code .py} beside it (or the
     *       {@code __init__.py} beside an {@code __init__.pyi}); a stub with none beside it names
     *       no file. The standard library, bundled stubs and installed packages contribute
     *       nothing.</li>
     *   <li><b>Java.</b> A single-type import gives the file declaring the top-level type its name
     *       falls under, so a nested type leads to the file of the type containing it. A static
     *       import gives the file declaring the type whose member it names, and a static
     *       on-demand import the file declaring that type. An on-demand import over a nested type
     *       gives the file of the top-level type. When no Java file declares the name, the
     *       project's Kotlin files are asked, a Kotlin top-level function going through the file
     *       class it compiles into.</li>
     *   <li><b>Kotlin.</b> A name import gives every file declaring a type, a typealias or a
     *       top-level function or property of that name, so overloads spread over several files
     *       are all listed. A name that matches none is read as a member of a type (an object
     *       member, an enum entry, a nested type) and gives the file declaring the nearest
     *       enclosing type. An alias changes the name a file sees, not the target. A name no
     *       Kotlin file declares is looked up among the project's Java files by the Java rule.</li>
     *   <li><b>C#.</b> A {@code using static} gives every file declaring the type, so all parts of
     *       a partial type are listed, and an alias of a type gives the files declaring it. A
     *       directive written inside a namespace is looked up from the innermost enclosing
     *       namespace outwards, and a {@code global::} name from the root. A global using belongs
     *       to the file that writes it and is not applied to the others. A partial type's
     *       component carries the directives of every part.</li>
     * </ul>
     *
     * <p>A component of a boundary file reports what the resolution of its file gives, exactly as
     * for an analysed file.
     *
     * @return Unmodifiable set of paths, in the order the imports first appear in the file; never
     *         {@code null}.
     */
    public Set<String> importedFiles() {
        if (importedFiles == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(importedFiles);
    }

    public String componentName() {
        return componentName;
    }

    public String module() {
        return module;
    }

    public void setModule(final String module) {
        this.module = module;
    }

    public void setCodeFragment(final String componentDeclarationTypeFragment) {
        codeFragment = componentDeclarationTypeFragment;
    }

    public void setExternalTypeReferences(final Set<ComponentReference> externalReferences) {
        internalReferences = new LinkedHashSet<>();
        this.externalReferences = new LinkedHashSet<>();
        this.notLoadedReferences = new LinkedHashSet<>();
        if (externalReferences != null) {
            externalReferences.forEach(ref -> {
                final ComponentReference copiedRef = cloneReference(ref);
                copiedRef.setExternal(true);
                this.externalReferences.add(copiedRef);
            });
        }
    }

    public void setReferenceClassification(final Set<ComponentReference> internalReferences,
                                           final Set<ComponentReference> externalReferences) {
        setReferenceClassification(internalReferences, externalReferences, null);
    }

    /**
     * Replaces this component's references with copies sorted into the three states.
     *
     * @param internalReferences  references whose target component is in the model
     * @param externalReferences  references to types outside the codebase
     * @param notLoadedReferences references to types declared in the repository but not loaded;
     *                            may be {@code null}
     */
    public void setReferenceClassification(final Set<ComponentReference> internalReferences,
                                           final Set<ComponentReference> externalReferences,
                                           final Set<ComponentReference> notLoadedReferences) {
        this.internalReferences = new LinkedHashSet<>();
        this.externalReferences = new LinkedHashSet<>();
        this.notLoadedReferences = new LinkedHashSet<>();
        if (notLoadedReferences != null) {
            notLoadedReferences.forEach(ref -> {
                final ComponentReference copiedRef = cloneReference(ref);
                copiedRef.setNotLoaded(true);
                this.notLoadedReferences.add(copiedRef);
            });
        }
        if (internalReferences != null) {
            internalReferences.forEach(ref -> {
                final ComponentReference copiedRef = cloneReference(ref);
                copiedRef.setExternal(false);
                copiedRef.setNotLoaded(false);
                this.internalReferences.add(copiedRef);
            });
        }
        if (externalReferences != null) {
            externalReferences.forEach(ref -> {
                final ComponentReference copiedRef = cloneReference(ref);
                copiedRef.setExternal(true);
                this.externalReferences.add(copiedRef);
            });
        }
    }

    public void setImports(final Set<String> currentImports) {
        if (currentImports == null) {
            imports = new LinkedHashSet<>();
        } else {
            imports = new LinkedHashSet<>(currentImports);
        }
    }

    /**
     * Sets the repository files this component's imports resolve to; see {@link #importedFiles()}.
     *
     * @param files The paths, in order; {@code null} is the empty set.
     */
    public void setImportedFiles(final Set<String> files) {
        if (files == null) {
            importedFiles = new LinkedHashSet<>();
        } else {
            importedFiles = new LinkedHashSet<>(files);
        }
    }

    public void setComponentName(final String componentName) {
        this.componentName = componentName;
    }

    /**
     * This component's access and declaration modifiers, lower-cased.
     *
     * @return Unmodifiable view of the modifiers, in declaration order.
     */
    public Set<String> modifiers() {
        return Collections.unmodifiableSet(modifiers);
    }

    /**
     * Records a modifier, storing the vocabulary's own instance of the token rather than a fresh
     * lower-cased copy of the caller's string.
     *
     * <p>There are seven distinct modifier tokens in the whole vocabulary and
     * {@code toLowerCase} was allocating one string per declaration: measured at 7,634 instances
     * carrying 7 values across a base/head pair, a ratio of 1,090 to one and 305KB of duplicate
     * text. Canonicalising to the constant needs no pool to do it.
     *
     * @param modifier Modifier token, in any case.
     * @throws IllegalArgumentException If the token is not a supported modifier.
     */
    public void insertAccessModifier(final String modifier) {
        final OOPSourceModelConstants.AccessModifiers resolved =
                OOPSourceModelConstants.accessModifier(modifier);
        if (resolved == null) {
            throw new IllegalArgumentException(modifier + " is an invalid modifier!");
        }
        modifiers.add(OOPSourceModelConstants.getAccessModifierMap().get(resolved));
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

    /**
     * Inserts every given modifier, enforcing the same vocabulary as
     * {@link #insertAccessModifier(String)} so that {@code modifiers()} only ever holds tokens the
     * model can resolve through {@link OOPSourceModelConstants#accessModifier(String)}.
     *
     * @param list Modifier tokens, in any case.
     * @throws IllegalArgumentException If any token is not a supported modifier.
     */
    public void setAccessModifiers(final List<String> list) {
        for (final String modifier : list) {
            insertAccessModifier(modifier);
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

    private void insertCmpRefCopy(final ComponentReference reference, final StringPool pool) {
        if (reference == null) {
            return;
        }
        final ComponentReference clonedRef = cloneReference(reference);
        clonedRef.setExternal(reference.isExternal());
        clonedRef.setNotLoaded(reference.isNotLoaded());
        if (pool != null && clonedRef instanceof AnnotationReference) {
            ((AnnotationReference) clonedRef).pool(pool);
        }
        this.insertCmpRef(clonedRef);
    }

    private static ComponentReference cloneReference(final ComponentReference reference) {
        try {
            return (ComponentReference) reference.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Failed to clone component reference.", e);
        }
    }
}
