package com.hadi.clarpse.reference;

/**
 * How a {@link ComponentReference} came to name its target. The values mean the same in every
 * language.
 *
 * <ul>
 *   <li>{@link #EXACT}: the language's own scoping rules leave exactly one visible declaration
 *       under the name (an import, an alias, a qualified name, an enclosing scope, the file's own
 *       package or namespace, or a compiler symbol). The target is that declaration.</li>
 *   <li>{@link #AMBIGUOUS}: several declarations are visible under the name and the source does
 *       not choose between them. Nothing is bound; the target is the name as written.</li>
 *   <li>{@link #UNRESOLVED}: no declaration is visible under the name. The target is the name as
 *       written, and it may still be a repository type that was not visible from the file.</li>
 *   <li>{@link #UNSPECIFIED}: only for a kind of reference where the language genuinely cannot say
 *       how the target was arrived at.</li>
 * </ul>
 *
 * <p>"Visible" is the language's own scope. A type that exists somewhere in the repository but
 * cannot be named from the file is not visible, so it makes a reference neither {@code EXACT} nor
 * {@code AMBIGUOUS}.
 *
 * <p>A type that a one-level analysis did not load is not {@code UNRESOLVED}; it is reported by
 * {@link ComponentReference#isNotLoaded()}.
 */
public enum ResolutionKind {

    /** Exactly one declaration is visible under the name; the target is that declaration. */
    EXACT,

    /**
     * No front end produces this value any more. A short-name match outside the scoping rules is
     * not a resolution; such a name is {@link #UNRESOLVED}. Do not branch on it.
     *
     * @deprecated never produced; kept so that code compiled against it still links.
     */
    @Deprecated
    UNIQUE_SIMPLE_NAME,

    /** Several declarations are visible under the name and nothing chose between them; the name is kept as written. */
    AMBIGUOUS,

    /** No declaration is visible under the name; the name is kept as written. */
    UNRESOLVED,

    /** The language cannot say how this kind of reference was resolved. */
    UNSPECIFIED
}
