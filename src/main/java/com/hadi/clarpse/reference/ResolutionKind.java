package com.hadi.clarpse.reference;

/**
 * How a {@link ComponentReference} came to name the type it names.
 *
 * <p>Resolving a written name to a type is not one thing. An import or a qualified name says
 * exactly which type is meant; a bare short name that happens to match one type in the repository
 * is a good guess; a short name matching several is a coin toss. All three produced the same
 * reference, so every relationship looked equally certain and a consumer had no way to decide which
 * ones were safe to rely on.
 *
 * <p>{@link #UNSPECIFIED} is the honest default. A front end that has not been taught to report how
 * it resolved a name says so, rather than claiming a confidence nothing measured.
 */
public enum ResolutionKind {

    /** An import, a using alias, a qualified name, an enclosing scope, or a compiler symbol. */
    EXACT,

    /** Exactly one type in the repository carries this short name. */
    UNIQUE_SIMPLE_NAME,

    /** Several types carry this short name and nothing in scope chose between them. */
    AMBIGUOUS,

    /** Nothing in scope names this type; the name is kept as written. */
    UNRESOLVED,

    /** The front end did not report how it resolved this reference. */
    UNSPECIFIED
}
