package com.hadi.clarpse.listener;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds the type a call's receiver has when that follows exactly from a type argument written on a
 * variable or field of a parameterised type: the field of a generic type ({@code b.value}), an element of a
 * collection ({@code l.get(0)}) and the parameter of a lambda given to one ({@code l.forEach(o -> ...)}).
 *
 * <p>The variable's type has to be a parameterised type every argument of which is a type itself: a raw type,
 * a wildcard and a type variable give nothing. A field is not trusted where a supertype the parse cannot see
 * could have given the class a field of that name.
 */
final class JavaTypeArgumentReceivers {

    private static final Set<String> ELEMENT_LAMBDA_CALLS = Set.of("forEach", "removeIf");

    private JavaTypeArgumentReceivers() {
    }

    /**
     * The declaration of the type of a field access or an element read of a variable of a written
     * parameterised type.
     *
     * @param scope        The receiver of the call.
     * @param valuesKnown  Whether every value visible at the given expression is known, so that a field of
     *                     that name cannot come from a supertype the parse cannot see.
     * @return The declaration, or null.
     */
    static ResolvedReferenceTypeDeclaration ofValue(final Expression scope, final Predicate<Expression> valuesKnown) {
        final Expression holder;
        if (scope.isFieldAccessExpr()) {
            holder = scope.asFieldAccessExpr().getScope();
        } else if (scope.isMethodCallExpr() && "get".equals(scope.asMethodCallExpr().getNameAsString())
                && scope.asMethodCallExpr().getArguments().size() == 1) {
            holder = scope.asMethodCallExpr().getScope().orElse(null);
        } else {
            return null;
        }
        if (!isTrustedHolder(holder, valuesKnown)) {
            return null;
        }
        return typeDeclaration(scope);
    }

    /**
     * The declaration of the element type of a collection, when the receiver is the one parameter of a
     * lambda given to {@code forEach} or {@code removeIf} of a variable declared with a written element type.
     * The solver does not always type such a parameter.
     *
     * @param scope       The receiver of the call.
     * @param valuesKnown See {@link #ofValue(Expression, Predicate)}.
     * @return The declaration, or null.
     */
    static ResolvedReferenceTypeDeclaration ofLambdaParameter(final Expression scope,
                                                              final Predicate<Expression> valuesKnown) {
        if (!scope.isNameExpr()) {
            return null;
        }
        final NameExpr name = scope.asNameExpr();
        final Optional<LambdaExpr> lambda = name.findAncestor(LambdaExpr.class);
        if (lambda.isEmpty() || lambda.get().getParameters().size() != 1
                || !lambda.get().getParameter(0).getNameAsString().equals(name.getNameAsString())) {
            return null;
        }
        final Optional<Node> parent = lambda.get().getParentNode();
        if (parent.isEmpty() || !(parent.get() instanceof MethodCallExpr)) {
            return null;
        }
        final MethodCallExpr call = (MethodCallExpr) parent.get();
        if (!ELEMENT_LAMBDA_CALLS.contains(call.getNameAsString()) || call.getArguments().size() != 1
                || call.getScope().isEmpty() || !isTrustedHolder(call.getScope().get(), valuesKnown)) {
            return null;
        }
        final ResolvedReferenceType collection = call.getScope().get().calculateResolvedType().asReferenceType();
        if (collection.typeParametersValues().size() != 1 || collection.getAllAncestors().stream()
                .noneMatch(ancestor -> "java.lang.Iterable".equals(ancestor.getQualifiedName()))) {
            return null;
        }
        return collection.typeParametersValues().get(0).asReferenceType().getTypeDeclaration().orElse(null);
    }

    /** Whether the holder is a variable or field of a type written with type arguments that are all types. */
    private static boolean isTrustedHolder(final Expression holder, final Predicate<Expression> valuesKnown) {
        if (holder == null || !holder.isNameExpr()) {
            return false;
        }
        final ResolvedType type = holder.calculateResolvedType();
        if (!type.isReferenceType() || type.asReferenceType().typeParametersValues().isEmpty()
                || !type.asReferenceType().typeParametersValues().stream().allMatch(ResolvedType::isReferenceType)) {
            return false;
        }
        return !holder.asNameExpr().resolve().isField() || valuesKnown.test(holder);
    }

    private static ResolvedReferenceTypeDeclaration typeDeclaration(final Expression value) {
        final ResolvedType type = value.calculateResolvedType();
        if (!type.isReferenceType()) {
            return null;
        }
        return type.asReferenceType().getTypeDeclaration().orElse(null);
    }
}
