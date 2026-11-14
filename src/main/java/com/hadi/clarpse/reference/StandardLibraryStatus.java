package com.hadi.clarpse.reference;

/**
 * Whether a reference or an import directive names something that belongs to its language's
 * standard library.
 *
 * <p>The answer has three values so that "not recognised as standard" is never confused with "known
 * not to be standard". {@link #NOT_STANDARD} is claimed only where the language settles it: the
 * target is declared in the codebase, or its name is outside every namespace the language reserves
 * for its platform. {@link #UNKNOWN} is the answer whenever the name alone cannot say, such as a bare
 * name that no import qualified.
 */
public enum StandardLibraryStatus {

    /** The target is part of the language's standard library. */
    STANDARD,

    /** The target is known not to be part of the language's standard library. */
    NOT_STANDARD,

    /** The model cannot say. This is the value of a target no rule of the language classifies. */
    UNKNOWN
}
