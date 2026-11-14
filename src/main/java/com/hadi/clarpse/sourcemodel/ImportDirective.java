package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One import directive of a source file: how the file imports, and what the directive binds.
 *
 * <p>{@link Component#imports()} holds the names a file imports and nothing about the manner. A
 * directive adds the manner: whether the target is written relative to the importing file, whether
 * the directive names what it takes or takes everything, which local names it binds, whether it
 * imports a module as a whole, and whether it runs when the code runs rather than when the file
 * loads. Directives are read from the file's own text and are not resolved against the model.
 *
 * <p><b>The target</b> is what the directive names, as written, without a trailing wildcard:
 * the type or member for {@code import java.util.List}, the package for {@code import java.util.*},
 * the module specifier for {@code import {a} from "./m"} and {@code from .m import a} (a Python
 * relative module keeps its leading dots), the namespace for {@code using System.Text;}.
 *
 * <p><b>Names and binds.</b> {@link #names()} are the members taken from the target, as the target
 * exports them ({@code default} for a TypeScript default import); {@link #binds()} are the local
 * names those members are bound to, which differ from {@link #names()} where the directive renames
 * ({@code from m import a as b}, {@code import {a as b}}, Kotlin {@code import p.A as B}). Both are
 * parallel lists, in written order, and both are empty for a directive that takes no member by name:
 * a whole-module import, a wildcard, and a static wildcard. Java and Kotlin name a member by writing
 * it into the target, so for them {@code names} holds that last segment. An {@link #alias()} is the
 * local name given to the target as a whole ({@code import numpy as np}, {@code import * as ns},
 * {@code using Io = System.IO}).
 *
 * <p><b>Module versus names.</b> {@link #importsModule()} is true when the directive binds or loads a
 * module as a whole: Python {@code import a.b}, TypeScript {@code import * as ns}, a side-effect import,
 * {@code import x = require()}, {@code require()} and {@code import()}. It is false where a directive
 * takes names ({@code from a import b}, {@code import {b} from}), and always false in Java, Kotlin and C#,
 * which import types, members and scopes but have no module value; their whole-scope forms are
 * {@link ImportKind#ON_DEMAND}.
 *
 * <p><b>Dynamic.</b> {@link #isDynamic()} is true for an import that runs when its code runs:
 * TypeScript {@code import()} and a {@code require()} written inside a function, and in Python an
 * import statement inside a function body, or a call of {@code importlib.import_module} or
 * {@code __import__} with a literal argument. Java, Kotlin and C# declare imports statically, so it
 * is always false for them.
 *
 * <p><b>Type-only.</b> {@link #isTypeOnly()} is true for a directive that exists for the type checker:
 * TypeScript {@code import type}, {@code export type ... from}, and an {@code import} whose every named
 * binding is written {@code type} (and that takes no default or namespace); a Python import inside an
 * {@code if TYPE_CHECKING:} (or {@code typing.TYPE_CHECKING}) block. It is always false for Java, Kotlin
 * and C#.
 *
 * <p>Equality covers every field.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class ImportDirective implements Serializable {

    private static final long serialVersionUID = 1L;
    private String target = "";
    private ImportKind kind = ImportKind.SINGLE;
    @JsonInclude(Include.NON_DEFAULT)
    private boolean relative;
    @JsonInclude(Include.NON_EMPTY)
    private List<String> names = new ArrayList<>();
    @JsonInclude(Include.NON_EMPTY)
    private List<String> binds = new ArrayList<>();
    @JsonInclude(Include.NON_NULL)
    private String alias;
    @JsonInclude(Include.NON_DEFAULT)
    private boolean importsModule;
    @JsonInclude(Include.NON_DEFAULT)
    private boolean dynamic;
    @JsonInclude(Include.NON_DEFAULT)
    private boolean typeOnly;
    private StandardLibraryStatus standardLibrary = StandardLibraryStatus.UNKNOWN;

    /** For deserialisation. */
    public ImportDirective() {
    }

    /**
     * Describes one directive.
     *
     * @param target         What the directive names, as written, without a trailing wildcard.
     * @param kind           How it selects what it brings in.
     * @param relative       Whether the target is written relative to the importing file.
     * @param names          The members taken from the target by name, as the target exports them.
     * @param binds          The local names bound to those members, parallel to {@code names}.
     * @param alias          The local name given to the target as a whole, or {@code null}.
     * @param standardLibrary Whether the target is the language's standard library.
     */
    public ImportDirective(final String target, final ImportKind kind, final boolean relative,
                           final List<String> names, final List<String> binds, final String alias,
                           final StandardLibraryStatus standardLibrary) {
        if (target != null) {
            this.target = target.intern();
        }
        if (kind != null) {
            this.kind = kind;
        }
        this.relative = relative;
        if (names != null) {
            this.names = new ArrayList<>(names);
        }
        if (binds != null) {
            this.binds = new ArrayList<>(binds);
        }
        if (alias != null) {
            this.alias = alias.intern();
        }
        if (standardLibrary != null) {
            this.standardLibrary = standardLibrary;
        }
    }

    /**
     * Sets how the directive loads, when it does more than name members.
     *
     * @param loadsModule Whether the directive binds or loads a module as a whole.
     * @param runsLater   Whether the import runs when its code runs rather than when the file loads.
     * @return This directive.
     */
    public ImportDirective loading(final boolean loadsModule, final boolean runsLater) {
        this.importsModule = loadsModule;
        this.dynamic = runsLater;
        return this;
    }

    /**
     * Sets whether the directive brings in names for the type checker only.
     *
     * @param onlyTypes Whether nothing the directive binds exists when the code runs.
     * @return This directive.
     */
    public ImportDirective typeOnly(final boolean onlyTypes) {
        this.typeOnly = onlyTypes;
        return this;
    }

    /**
     * What the directive names, as written, without a trailing wildcard.
     *
     * @return The target; a Python relative module keeps its leading dots.
     */
    public String target() {
        return target;
    }

    /**
     * How the directive selects what it brings in.
     *
     * @return The kind.
     */
    public ImportKind kind() {
        return kind;
    }

    /**
     * Whether the target is written relative to the importing file (a TypeScript specifier starting
     * with {@code .}, a Python module with leading dots). Java, Kotlin and C# have no relative
     * imports, so it is always false for them.
     *
     * @return {@code true} for a relative target, {@code false} for an absolute one.
     */
    public boolean isRelative() {
        return relative;
    }

    /**
     * The members the directive takes from its target by name, as the target exports them.
     *
     * @return An unmodifiable list, empty when no member is taken by name.
     */
    public List<String> names() {
        return Collections.unmodifiableList(names);
    }

    /**
     * The local names the directive binds to the members it takes, parallel to {@link #names()}.
     *
     * @return An unmodifiable list, empty when no member is taken by name.
     */
    public List<String> binds() {
        return Collections.unmodifiableList(binds);
    }

    /**
     * The local name given to the target as a whole.
     *
     * @return The alias, or {@code null} when the directive gives the target no name.
     */
    public String alias() {
        return alias;
    }

    /**
     * Whether the directive binds or loads a module as a whole rather than taking names from it.
     *
     * @return {@code true} for a module import; always {@code false} in Java, Kotlin and C#.
     */
    public boolean importsModule() {
        return importsModule;
    }

    /**
     * Whether the import runs when its code runs rather than when the file loads.
     *
     * @return {@code true} for a dynamic import; always {@code false} in Java, Kotlin and C#.
     */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * Whether the directive brings in names for the type checker only, so that nothing it binds
     * exists when the code runs: TypeScript {@code import type}, {@code export type ... from}, an
     * {@code import} whose every named binding is written {@code type}, and a Python import inside
     * an {@code if TYPE_CHECKING:} block. Always {@code false} in Java, Kotlin and C#, whose imports
     * are not erased by the language.
     *
     * @return {@code true} for a type-only import.
     */
    public boolean isTypeOnly() {
        return typeOnly;
    }

    /**
     * Whether the target belongs to the language's standard library.
     *
     * @return The status; {@link StandardLibraryStatus#UNKNOWN} where the language cannot say.
     */
    public StandardLibraryStatus standardLibrary() {
        return standardLibrary;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ImportDirective)) {
            return false;
        }
        final ImportDirective other = (ImportDirective) obj;
        return relative == other.relative && importsModule == other.importsModule && dynamic == other.dynamic
                && typeOnly == other.typeOnly
                && kind == other.kind && standardLibrary == other.standardLibrary
                && target.equals(other.target) && names.equals(other.names) && binds.equals(other.binds)
                && Objects.equals(alias, other.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, kind, relative, names, binds, alias, importsModule, dynamic, typeOnly, standardLibrary);
    }

    @Override
    public String toString() {
        return "ImportDirective[" + kind + " " + target + " names=" + names + " binds=" + binds + "]";
    }
}
