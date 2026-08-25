package com.hadi.clarpse;

/**
 * Reduces a written type expression to the name of the type it refers to.
 *
 * <p>Source spells a type as an expression -- {@code DTO<HttpRequest>}, {@code Foo<Bar>[]},
 * {@code Bar...} -- but a model has one component per type, named {@code DTO}, {@code Foo},
 * {@code Bar}. A reference recorded under the written form matches no component, so the edge is
 * present in the model, well formed, and joined to nothing. Nothing downstream can detect that:
 * the model does not claim the type has no incoming references, it simply has none to offer.
 *
 * <p>The reduction is erasure plus the array and varargs suffixes. It is deliberately textual --
 * it runs on names that a language front end has already resolved, and there is no type system
 * here to consult.
 */
public final class TypeNames {

    private TypeNames() {
    }

    /**
     * The name of the type a written type expression refers to.
     *
     * <p>Type arguments are written in angle brackets in Java, C# and TypeScript and in square
     * brackets in Python, and an array is square brackets with nothing in them, so the rule is one
     * rule: the name ends where the first bracket of either kind opens. {@code DTO<HttpRequest>},
     * {@code Map<String, List<Foo>>}, {@code Foo<?>}, {@code Foo<? extends Bar>},
     * {@code Dict[str, int]}, {@code Bar[][]} and {@code Foo<Bar>...} name {@code DTO},
     * {@code Map}, {@code Foo}, {@code Foo}, {@code Dict}, {@code Bar} and {@code Foo}.
     *
     * <p>A name with nothing to strip comes back identical, which is the common case and the one
     * that must stay exact: a raw {@code Foo} is already the name of a type, and reducing it
     * further would be inventing. So is a name that opens with a bracket, which is not a
     * parameterised type at all -- truncating there would leave nothing, and a reference to
     * nothing is worse than a reference to a name that matches nothing.
     *
     * <p>The type arguments are dropped rather than recorded, because a name is a name. Where they
     * carry a dependency of their own -- {@code HttpRequest} in {@code DTO<HttpRequest>} is a real
     * one -- it is for the caller to record as a reference in its own right; that is a different
     * edge from the one being named here, and naming this one after both would describe neither.
     *
     * @param typeName a written type expression, or null
     * @return the erased type name; the input unchanged when there is nothing to erase
     */
    public static String erasure(final String typeName) {
        if (typeName == null) {
            return null;
        }
        String result = typeName;
        final int arguments = firstBracket(result);
        if (arguments > 0) {
            result = result.substring(0, arguments);
        }
        result = result.trim();
        while (result.endsWith("...")) {
            result = result.substring(0, result.length() - 3).trim();
        }
        if (result.isEmpty()) {
            return typeName;
        }
        return result;
    }

    private static int firstBracket(final String typeName) {
        final int angle = typeName.indexOf('<');
        final int square = typeName.indexOf('[');
        if (angle < 0) {
            return square;
        }
        if (square < 0) {
            return angle;
        }
        return Math.min(angle, square);
    }
}
