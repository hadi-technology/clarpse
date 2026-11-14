package com.hadi.clarpse.sourcemodel;

/**
 * How an import directive selects what it brings in.
 *
 * <p>The kind says whether the directive names what it takes or takes everything its target
 * offers, and whether the target's members are static.
 */
public enum ImportKind {

    /** The directive names what it imports: a type, a member, a list of names, or a module. */
    SINGLE,

    /**
     * The directive imports everything its target offers, without naming it: {@code a.b.*},
     * {@code using Ns;}, {@code from m import *}, {@code import * as ns}.
     */
    ON_DEMAND,

    /** The directive names one static member of a type: Java {@code import static a.B.m}, C# {@code using static}. */
    STATIC,

    /** The directive imports every static member of a type: Java {@code import static a.B.*}, C# {@code using static T}. */
    STATIC_ON_DEMAND;

    /**
     * The kind of a directive from what its text says.
     *
     * @param isStatic Whether the directive takes static members.
     * @param onDemand Whether it takes everything its target offers.
     * @return The matching kind.
     */
    public static ImportKind of(final boolean isStatic, final boolean onDemand) {
        if (isStatic && onDemand) {
            return STATIC_ON_DEMAND;
        }
        if (isStatic) {
            return STATIC;
        }
        if (onDemand) {
            return ON_DEMAND;
        }
        return SINGLE;
    }
}
