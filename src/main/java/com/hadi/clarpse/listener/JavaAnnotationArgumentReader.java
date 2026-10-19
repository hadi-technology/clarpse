package com.hadi.clarpse.listener;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedEnumConstantDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.hadi.clarpse.reference.AnnotationArgument;
import com.hadi.clarpse.reference.AnnotationArgument.Kind;
import com.hadi.clarpse.reference.AnnotationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads the arguments written on a Java annotation into {@link AnnotationArgument}s.
 *
 * <p>A string, number, character, boolean or class literal, an array of these, and a reference to a
 * constant of an enum the type solver can find are kept. A concatenation of string literals is kept
 * as the one string it writes. Anything else is recorded as present and unresolved: a reference to
 * a constant that is not an enum constant, a method call, arithmetic, a nested annotation.
 */
final class JavaAnnotationArgumentReader {

    private final TypeSolver typeSolver;
    private final Function<String, String> typeNames;

    /**
     * @param typeSolver Finds the enum a constant reference names.
     * @param typeNames  Resolves a name written in a type position to its qualified name, or {@code null}.
     */
    JavaAnnotationArgumentReader(final TypeSolver typeSolver, final Function<String, String> typeNames) {
        this.typeSolver = typeSolver;
        this.typeNames = typeNames;
    }

    /**
     * The arguments of an annotation.
     *
     * @param annotation An applied annotation.
     * @return Argument name to value in written order; empty for a marker annotation.
     */
    Map<String, AnnotationArgument> read(final AnnotationExpr annotation) {
        final AnnotationArguments arguments = new AnnotationArguments();
        if (annotation instanceof SingleMemberAnnotationExpr) {
            arguments.unnamed(argument(((SingleMemberAnnotationExpr) annotation).getMemberValue()));
        } else if (annotation instanceof NormalAnnotationExpr) {
            for (final MemberValuePair pair : ((NormalAnnotationExpr) annotation).getPairs()) {
                arguments.named(pair.getNameAsString(), argument(pair.getValue()));
            }
        }
        return arguments.build();
    }

    private AnnotationArgument argument(final Expression expression) {
        final AnnotationArgument literal = literal(expression);
        if (literal != null) {
            return literal;
        }
        return AnnotationArgument.unresolved();
    }

    private AnnotationArgument literal(final Expression expression) {
        if (expression instanceof EnclosedExpr) {
            return literal(((EnclosedExpr) expression).getInner());
        }
        if (expression instanceof StringLiteralExpr) {
            return AnnotationArgument.literal(Kind.STRING, ((StringLiteralExpr) expression).asString());
        }
        if (expression instanceof TextBlockLiteralExpr) {
            return AnnotationArgument.literal(Kind.STRING, ((TextBlockLiteralExpr) expression).asString());
        }
        if (expression instanceof CharLiteralExpr) {
            return AnnotationArgument.literal(Kind.STRING, ((CharLiteralExpr) expression).asChar() + "");
        }
        if (expression instanceof BooleanLiteralExpr) {
            return AnnotationArgument.literal(Kind.BOOLEAN,
                    String.valueOf(((BooleanLiteralExpr) expression).getValue()));
        }
        if (expression instanceof IntegerLiteralExpr) {
            return AnnotationArgument.literal(Kind.NUMBER, ((IntegerLiteralExpr) expression).getValue());
        }
        if (expression instanceof LongLiteralExpr) {
            return AnnotationArgument.literal(Kind.NUMBER, ((LongLiteralExpr) expression).getValue());
        }
        if (expression instanceof DoubleLiteralExpr) {
            return AnnotationArgument.literal(Kind.NUMBER, ((DoubleLiteralExpr) expression).getValue());
        }
        if (expression instanceof UnaryExpr) {
            return signedNumber((UnaryExpr) expression);
        }
        if (expression instanceof ClassExpr) {
            return classLiteral((ClassExpr) expression);
        }
        if (expression instanceof ArrayInitializerExpr) {
            final List<AnnotationArgument> elements = new ArrayList<>();
            for (final Expression element : ((ArrayInitializerExpr) expression).getValues()) {
                elements.add(argument(element));
            }
            return AnnotationArgument.array(elements);
        }
        if (expression instanceof BinaryExpr) {
            return concatenation((BinaryExpr) expression);
        }
        if (expression instanceof FieldAccessExpr) {
            return enumConstant((FieldAccessExpr) expression);
        }
        return null;
    }

    private AnnotationArgument signedNumber(final UnaryExpr unary) {
        final AnnotationArgument operand = literal(unary.getExpression());
        if (operand == null || operand.kind() != Kind.NUMBER) {
            return null;
        }
        if (unary.getOperator() == UnaryExpr.Operator.MINUS) {
            return AnnotationArgument.literal(Kind.NUMBER, "-" + operand.value());
        }
        if (unary.getOperator() == UnaryExpr.Operator.PLUS) {
            return operand;
        }
        return null;
    }

    private AnnotationArgument classLiteral(final ClassExpr classExpr) {
        final String written = classExpr.getType().asString();
        String resolved = null;
        try {
            resolved = typeNames.apply(written);
        } catch (final RuntimeException ignored) {
            // An unresolvable class literal still names the type as written.
            resolved = null;
        }
        if (resolved == null || resolved.isEmpty()) {
            resolved = written;
        }
        return AnnotationArgument.literal(Kind.CLASS, resolved);
    }

    private AnnotationArgument concatenation(final BinaryExpr binary) {
        if (binary.getOperator() != BinaryExpr.Operator.PLUS) {
            return null;
        }
        final AnnotationArgument left = literal(binary.getLeft());
        final AnnotationArgument right = literal(binary.getRight());
        if (left == null || right == null
                || left.kind() != Kind.STRING || right.kind() != Kind.STRING) {
            return null;
        }
        return AnnotationArgument.literal(Kind.STRING, left.value() + right.value());
    }

    private AnnotationArgument enumConstant(final FieldAccessExpr access) {
        final String owner = access.getScope().toString();
        final String qualified;
        try {
            qualified = typeNames.apply(owner);
        } catch (final RuntimeException e) {
            return null;
        }
        if (qualified == null || qualified.isEmpty()) {
            return null;
        }
        try {
            final SymbolReference<ResolvedReferenceTypeDeclaration> solved =
                    typeSolver.tryToSolveType(qualified);
            if (!solved.isSolved() || !solved.getCorrespondingDeclaration().isEnum()) {
                return null;
            }
            for (final ResolvedEnumConstantDeclaration constant
                    : solved.getCorrespondingDeclaration().asEnum().getEnumConstants()) {
                if (constant.getName().equals(access.getNameAsString())) {
                    return AnnotationArgument.literal(Kind.ENUM, access.toString());
                }
            }
        } catch (final RuntimeException ignored) {
            // An enum the solver cannot read leaves the constant unresolved.
            return null;
        }
        return null;
    }
}
