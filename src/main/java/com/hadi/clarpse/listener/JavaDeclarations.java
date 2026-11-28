package com.hadi.clarpse.listener;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.TypeParameter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads declaration facts a Java syntax node states on its own: the accessors and the canonical constructor
 * a record declares, the visibility modifiers of a declaration and the type parameters in scope at a node.
 */
final class JavaDeclarations {

    private JavaDeclarations() {
    }

    /**
     * True if the record body declares the accessor for the named component itself.
     */
    static boolean declaresAccessor(final RecordDeclaration ctx, final String accessorName) {
        for (final Node member : ctx.getMembers()) {
            if (!(member instanceof MethodDeclaration)) {
                continue;
            }
            final MethodDeclaration method = (MethodDeclaration) member;
            if (method.getParameters().isEmpty() && method.getNameAsString().equals(accessorName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the record body declares the canonical constructor in full, in which case the regular
     * constructor visitor models it.
     */
    static boolean declaresCanonicalConstructor(final RecordDeclaration ctx) {
        final List<String> recordComponentTypes = new ArrayList<>();
        for (final Parameter recordComponent : ctx.getParameters()) {
            recordComponentTypes.add(recordComponent.getType().asString());
        }
        for (final Node member : ctx.getMembers()) {
            if (!(member instanceof ConstructorDeclaration)) {
                continue;
            }
            final List<String> ctorParamTypes = new ArrayList<>();
            for (final Parameter param : ((ConstructorDeclaration) member).getParameters()) {
                ctorParamTypes.add(param.getType().asString());
            }
            if (ctorParamTypes.equals(recordComponentTypes)) {
                return true;
            }
        }
        return false;
    }

    static List<String> visibilityModifiers(final NodeList<Modifier> modifiers) {
        final List<String> visibility = new ArrayList<>();
        for (final Modifier modifier : modifiers) {
            final String keyword = modifier.toString().toLowerCase(Locale.ROOT).trim();
            if (keyword.equals("public") || keyword.equals("protected") || keyword.equals("private")) {
                visibility.add(keyword);
            }
        }
        return visibility;
    }

    /**
     * The type variables a node may legally mention: its own, and those of every type that
     * encloses it.
     */
    static Set<String> typeParameterNamesInScope(final Node node) {
        final Set<String> names = new HashSet<>();
        Node current = node;
        while (current != null) {
            if (current instanceof NodeWithTypeParameters) {
                for (final TypeParameter typeParameter : ((NodeWithTypeParameters<?>) current).getTypeParameters()) {
                    names.add(typeParameter.getNameAsString());
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return names;
    }

    static String typeParametersCodeFragment(final NodeList<? extends Type> typeParameters) {
        final StringBuilder fragment = new StringBuilder("<");
        for (final Type typeParam : typeParameters) {
            fragment.append(typeParam.asString()).append(", ");
        }
        while (fragment.toString().endsWith(", ") || fragment.toString().endsWith(",")) {
            fragment.setLength(fragment.length() - 1);
        }
        return fragment + ">";
    }
}
