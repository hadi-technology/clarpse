package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.KotlinDeclaration;
import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.TopLevelFunction;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.CallExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinParameterModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.MemberExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.NameExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;
import com.hadi.clarpse.compiler.kotlin.KotlinModelAssembler.Shadow;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Origin;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Resolution;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Scope;
import com.hadi.clarpse.reference.ResolutionKind;

import java.util.List;

/**
 * What the declarations of one member say about the types of the names and expressions it uses,
 * and nothing else: the type of a parameter or local that is written or made by a plain
 * constructor call, and the type of a call or property reached from such a receiver, an object or
 * a top-level function through the types the repository declares.
 *
 * <p>A type is known only when it can be stated without guessing. A name the member's text declares
 * more than once, or may give another type with a type test, a cast or an assignment, is unknown;
 * so is anything shadowed by a member of the class or one an unreadable supertype may pass down, an
 * array, a generic or a type parameter, a member whose overloads disagree, and a chain through any
 * of these. Return types are only the ones a declaration writes; none is inferred.
 */
final class KotlinReceiverTyping {

    private final KotlinMemberModel member;
    private final Scope scope;
    private final Shadow shadow;
    private final KotlinNameResolver resolver;
    private final KotlinTypeMembers typeMembers;

    KotlinReceiverTyping(final KotlinMemberModel member, final Scope scope, final Shadow shadow,
                         final KotlinNameResolver resolver,
                         final KotlinTypeMembers typeMembers) {
        this.member = member;
        this.scope = scope;
        this.shadow = shadow;
        this.resolver = resolver;
        this.typeMembers = typeMembers;
    }

    /**
     * Whether a call name binds to something closer in scope than the type or import it would
     * otherwise be read as: a function or property the class, its readable supertypes or the member
     * declares by that name, or, when a supertype the parse cannot read may pass the name down, any
     * name an import supplies.
     */
    static boolean callBoundElsewhere(final KotlinNameResolver resolver, final String name, final Scope scope,
                                      final Shadow closer) {
        final Origin origin = resolver.originOf(name, scope);
        if (origin == Origin.ENCLOSING) {
            return false;
        }
        if (closer.names().contains(name)) {
            return true;
        }
        return closer.ancestryUnknown() && (origin == Origin.IMPORT || origin == Origin.STAR);
    }

    /**
     * The type of an expression a local is initialized with.
     *
     * @param expression The expression.
     * @return The type's fully qualified name, or null when it is not known.
     */
    String typeOf(final KotlinExpr expression) {
        if (expression instanceof NameExpr name) {
            return typeOfRoot(name.name());
        }
        if (expression instanceof CallExpr call) {
            return typeOfCall(call.name());
        }
        if (expression instanceof MemberExpr access) {
            final String receiver = typeOf(access.receiver());
            if (receiver == null) {
                return null;
            }
            return typeMembers.memberType(receiver, access.name(), access.call());
        }
        return null;
    }

    /**
     * The type of a parameter or local, when its text declares it once and writes its type or
     * constructs it plainly.
     *
     * @param name The name.
     * @return The type's fully qualified name, or null when it is not known.
     */
    String typeOfName(final String name) {
        if (member.declarations.getOrDefault(name, 0) != 1 || member.narrowed.contains(name)) {
            return null;
        }
        for (final KotlinParameterModel parameter : member.parameters) {
            if (name.equals(parameter.name)) {
                return typeMembers.resolveWritten(parameter.declaredType, scope);
            }
        }
        for (final KotlinMemberModel local : member.locals) {
            if (!name.equals(local.name)) {
                continue;
            }
            if (local.declaredType != null) {
                return typeMembers.resolveWritten(local.declaredType, scope);
            }
            if (local.initializer instanceof CallExpr call) {
                return constructedType(call.name());
            }
            return null;
        }
        return null;
    }

    private String typeOfRoot(final String name) {
        final String declared = typeOfName(name);
        if (declared != null) {
            return declared;
        }
        if (member.declarations.containsKey(name) || shadow.names().contains(name)
                || name.isEmpty() || !Character.isUpperCase(name.charAt(0))
                || KotlinReceiverTyping.callBoundElsewhere(resolver, name, scope, shadow)) {
            return null;
        }
        final Resolution resolved = resolver.resolve(name, scope, true);
        if (resolved == null || resolved.kind() == ResolutionKind.UNRESOLVED) {
            return null;
        }
        final KotlinDeclaration declaration = resolver.index().declaration(resolved.name());
        if (declaration == null || declaration.kind() != TypeKind.OBJECT) {
            return null;
        }
        return resolved.name();
    }

    private String typeOfCall(final String name) {
        final String constructed = constructedType(name);
        if (constructed != null) {
            return constructed;
        }
        return topLevelFunctionType(name);
    }

    /** The class a bare call constructs, when the name is a class nothing closer shadows. */
    private String constructedType(final String name) {
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))
                || callBoundElsewhere(resolver, name, scope, shadow)
                || !resolver.visibleFunctions(name, scope).isEmpty()) {
            return null;
        }
        final Resolution resolved = resolver.resolve(name, scope, true);
        if (resolved == null || resolved.kind() == ResolutionKind.UNRESOLVED
                || KotlinTypeMembers.isArray(resolved.name())) {
            return null;
        }
        final KotlinDeclaration declaration = resolver.index().declaration(resolved.name());
        if (declaration != null && declaration.kind() != TypeKind.CLASS) {
            return null;
        }
        return resolved.name();
    }

    /** The type a top-level function of the repository writes as its return type, when every one visible agrees. */
    private String topLevelFunctionType(final String name) {
        if (shadow.names().contains(name) || shadow.ancestryUnknown() || member.receiverType != null) {
            return null;
        }
        String found = null;
        for (final TopLevelFunction candidate : resolver.visibleFunctions(name, scope)) {
            if (candidate.function().receiverType != null) {
                continue;
            }
            final String written = candidate.function().declaredType;
            final Scope declaring = new Scope(candidate.file(), List.of(), candidate.function().typeParameters);
            final String resolved = typeMembers.resolveWritten(written, declaring);
            if (resolved == null || (found != null && !found.equals(resolved))) {
                return null;
            }
            found = resolved;
        }
        return found;
    }
}
