package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.KotlinDeclaration;
import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.TopLevelFunction;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.CallExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinParameterModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.MemberExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.NameExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.ReceiverCall;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.LiteralExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.ThisExpr;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;
import com.hadi.clarpse.compiler.kotlin.KotlinModelAssembler.Shadow;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Origin;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Resolution;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Scope;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final Set<String> classMembers;
    private final KotlinNameResolver resolver;
    private final KotlinTypeMembers typeMembers;
    private final Set<String> resolving = new HashSet<>();

    KotlinReceiverTyping(final KotlinMemberModel member, final Scope scope, final Shadow shadow,
                         final Set<String> classMembers, final KotlinNameResolver resolver,
                         final KotlinTypeMembers typeMembers) {
        this.member = member;
        this.scope = scope;
        this.shadow = shadow;
        this.classMembers = classMembers;
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
     * Whether a call name is declared by something closer in scope than any import or type: a
     * function or property the class, its readable supertypes or the member declares by that name.
     * A name a supertype the parse cannot read might declare does not count, because the file's own
     * scope binds the name exactly and nothing readable contradicts it.
     */
    static boolean callDeclaredCloser(final KotlinNameResolver resolver, final String name, final Scope scope,
                                      final Shadow closer) {
        return closer.names().contains(name) && resolver.originOf(name, scope) != Origin.ENCLOSING;
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
        if (expression instanceof LiteralExpr literal) {
            return literal.type();
        }
        if (expression instanceof ThisExpr) {
            return enclosingType();
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
            if (local.initializer == null || !resolving.add(name)) {
                return null;
            }
            try {
                return typeOf(local.initializer);
            } finally {
                resolving.remove(name);
            }
        }
        return null;
    }

    private String typeOfRoot(final String name) {
        final String declared = typeOfName(name);
        if (declared != null) {
            return declared;
        }
        if (!member.declarations.containsKey(name) && classMembers.contains(name)) {
            return typeOfProperty(name);
        }
        if (member.declarations.containsKey(name) || shadow.names().contains(name)
                || name.isEmpty() || !Character.isUpperCase(name.charAt(0))
                || KotlinReceiverTyping.callBoundElsewhere(resolver, name, scope, shadow)) {
            return null;
        }
        final Resolution resolved = resolver.resolve(name, scope, true);
        if (resolved == null || resolved.kind() != ResolutionKind.EXACT) {
            return null;
        }
        final KotlinDeclaration declaration = resolver.index().declaration(resolved.name());
        if (declaration == null) {
            return null;
        }
        if (declaration.kind() == TypeKind.OBJECT) {
            return resolved.name();
        }
        KotlinTypeModel companion = null;
        for (final KotlinTypeModel nested : declaration.type().nestedTypes) {
            if (nested.companion) {
                if (companion != null) {
                    return null;
                }
                companion = nested;
            }
        }
        if (companion == null) {
            return null;
        }
        return resolved.name() + "." + companion.name;
    }

    /**
     * The class whose members a bare name, a call with no receiver or {@code this} reaches: the
     * enclosing class, unless the member is an extension or its text holds a lambda, either of which
     * may give those names another receiver.
     */
    private String enclosingType() {
        if (member.receiverType != null || member.hasLambda || scope.enclosingTypes().isEmpty()) {
            return null;
        }
        return scope.enclosingTypes().get(0);
    }

    /** The written type of a property of the enclosing class that no name of the text hides or retypes. */
    private String typeOfProperty(final String name) {
        final String owner = enclosingType();
        if (owner == null || member.narrowed.contains(name) || member.narrowed.contains("this." + name)) {
            return null;
        }
        return typeMembers.memberType(owner, name, false);
    }

    /**
     * The type of the receiver of a call: a parameter or local, a property of the enclosing class
     * or of a readable supertype, {@code this.property}, or {@code this}.
     */
    private String typeOfReceiver(final String receiver) {
        if ("this".equals(receiver)) {
            return enclosingType();
        }
        if (receiver.startsWith("this.")) {
            final String name = receiver.substring("this.".length());
            final String owner = enclosingType();
            if (owner == null || member.narrowed.contains(name) || member.narrowed.contains(receiver)) {
                return null;
            }
            return typeMembers.memberType(owner, name, false);
        }
        final String element = typeFromTypeArgument(receiver);
        if (element != null) {
            return element;
        }
        final String declared = typeOfName(receiver);
        if (declared != null || member.declarations.containsKey(receiver) || !classMembers.contains(receiver)) {
            return declared;
        }
        return typeOfProperty(receiver);
    }

    private static final Set<String> ITERABLE_OF_ONE = Set.of("List", "MutableList", "Set", "MutableSet",
            "Collection", "MutableCollection", "Iterable", "MutableIterable", "Sequence", "ArrayList", "HashSet",
            "LinkedHashSet", "Array");
    private static final Set<String> INDEXED_OF_ONE = Set.of("List", "MutableList", "ArrayList", "Array");
    private static final Set<String> INDEXED_BY_KEY = Set.of("Map", "MutableMap", "HashMap", "LinkedHashMap");

    /**
     * The type a receiver has when it follows exactly from a type argument written on a declaration: the
     * property of a generic type ({@code b.value}), an element of a collection ({@code l[0]}), the variable
     * of a loop over one and the parameter of a lambda given to one of its element functions. The name the
     * type argument is written for has to be one of a parameter or local that its text declares once and
     * no type test or assignment may retype.
     */
    private String typeFromTypeArgument(final String receiver) {
        if (receiver.endsWith("[]")) {
            return elementOf(writtenTypeOfName(receiver.substring(0, receiver.length() - 2)), true);
        }
        final int dot = receiver.indexOf('.');
        if (dot > 0) {
            return typeOfGenericProperty(writtenTypeOfName(receiver.substring(0, dot)), receiver.substring(dot + 1));
        }
        if (!member.declarations.containsKey(receiver) && !"it".equals(receiver)
                || member.declarations.getOrDefault(receiver, 0) > 1 || member.narrowed.contains(receiver)) {
            return null;
        }
        final String loopCollection = member.loopCollections.get(receiver);
        if (loopCollection != null && !loopCollection.isEmpty() && !loopCollection.equals(receiver)) {
            final String written = member.loopTypes.get(receiver);
            if (written != null) {
                return typeMembers.resolveWritten(written, scope);
            }
            return elementOf(writtenTypeOfName(loopCollection), false);
        }
        final String lambdaCollection = member.lambdaCollections.get(receiver);
        if (lambdaCollection != null && !lambdaCollection.isEmpty() && !lambdaCollection.equals(receiver)
                && !classMembers.contains(receiver)) {
            return elementOf(writtenTypeOfName(lambdaCollection), false);
        }
        return null;
    }

    /** The type a parameter or local is written with, when its text declares it once and nothing retypes it. */
    private String writtenTypeOfName(final String name) {
        if (member.declarations.getOrDefault(name, 0) != 1 || member.narrowed.contains(name)) {
            return null;
        }
        for (final KotlinParameterModel parameter : member.parameters) {
            if (name.equals(parameter.name)) {
                return parameter.declaredType;
            }
        }
        for (final KotlinMemberModel local : member.locals) {
            if (name.equals(local.name)) {
                return local.declaredType;
            }
        }
        return null;
    }

    /** The head and the top-level type arguments of a written type; null when it has none. */
    private static List<String> headAndArguments(final String written) {
        if (written == null) {
            return null;
        }
        String text = written.trim();
        if (text.endsWith("?")) {
            text = text.substring(0, text.length() - 1);
        }
        final int open = text.indexOf('<');
        if (open <= 0 || !text.endsWith(">")) {
            return null;
        }
        final List<String> parts = new ArrayList<>();
        parts.add(text.substring(0, open).trim());
        int depth = 0;
        int start = open + 1;
        for (int i = open; i < text.length(); i += 1) {
            final char c = text.charAt(i);
            if (c == '<') {
                depth += 1;
            } else if (c == '>') {
                depth -= 1;
                if (depth == 0 && i != text.length() - 1) {
                    return null;
                }
            } else if (c == ',' && depth == 1) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start, text.length() - 1).trim());
        return parts;
    }

    /** The class a collection's written head names, when it is a collection of the platform and no type of the repository. */
    private String libraryCollection(final String head) {
        final String resolved = typeMembers.resolveWritten(head, scope);
        if (resolved == null || resolver.index().declaration(resolved) != null
                || !(resolved.startsWith("kotlin.") || resolved.startsWith("java.util."))) {
            return null;
        }
        return resolved.substring(resolved.lastIndexOf('.') + 1);
    }

    /** The type an element of a collection written with its type argument has, or null. */
    private String elementOf(final String collection, final boolean indexed) {
        final List<String> parts = headAndArguments(collection);
        if (parts == null) {
            return null;
        }
        final String simple = libraryCollection(parts.get(0));
        if (simple == null) {
            return null;
        }
        if (indexed && INDEXED_BY_KEY.contains(simple) && parts.size() == 3) {
            return typeMembers.resolveWritten(parts.get(2), scope);
        }
        Set<String> single = ITERABLE_OF_ONE;
        if (indexed) {
            single = INDEXED_OF_ONE;
        }
        if (parts.size() == 2 && single.contains(simple)) {
            return typeMembers.resolveWritten(parts.get(1), scope);
        }
        return null;
    }

    /** The type of {@code holder.property} when the property is written with a type parameter the holder gives an argument. */
    private String typeOfGenericProperty(final String holder, final String property) {
        final List<String> parts = headAndArguments(holder);
        if (parts == null || property.indexOf('.') >= 0) {
            return null;
        }
        final String type = typeMembers.resolveWritten(parts.get(0), scope);
        if (type == null || resolver.index().declaration(type) == null) {
            return null;
        }
        final int position = typeMembers.typeParameterPosition(type, property);
        if (position < 0 || position + 1 >= parts.size()
                || resolver.index().declaration(type).type().typeParameters.size() != parts.size() - 1) {
            return null;
        }
        return typeMembers.resolveWritten(parts.get(position + 1), scope);
    }

    private String typeOfCall(final String name) {
        if (!member.declarations.containsKey(name) && classMembers.contains(name)) {
            final String owner = enclosingType();
            if (owner == null) {
                return null;
            }
            return typeMembers.memberType(owner, name, true);
        }
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
        if (resolved == null || resolved.kind() != ResolutionKind.EXACT
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

    /**
     * The file class holding the extension function a call on a typed receiver reaches, or null.
     * It is credited only when the receiver is a single name of a known type; exactly one file class
     * holds the extensions of that name that the file sees; one of them takes the call's arguments
     * and extends the receiver's type, a type the receiver's type inherits from, or any type; and
     * the receiver's type has no member of that name, which needs its whole ancestry readable. A
     * receiver of a type the repository does not declare qualifies only for an extension of that
     * very type.
     *
     * @param call The call.
     * @return The file class's fully qualified name, or null.
     */
    String extensionFileClass(final ReceiverCall call) {
        if (classMembers.contains(call.name())) {
            return null;
        }
        final String receiver = typeOfReceiver(call.receiver());
        if (receiver == null || member.declarations.containsKey(call.name())) {
            return null;
        }
        final List<TopLevelFunction> extensions = new ArrayList<>();
        for (final TopLevelFunction visible : resolver.visibleFunctions(call.name(), scope)) {
            if (visible.function().receiverType != null) {
                extensions.add(visible);
            }
        }
        if (extensions.isEmpty() || (opaqueStarImport() && !importedByName(call.name()))) {
            return null;
        }
        final String fileClass = extensions.get(0).fileClass();
        for (final TopLevelFunction extension : extensions) {
            if (!fileClass.equals(extension.fileClass())) {
                return null;
            }
        }
        for (final TopLevelFunction extension : extensions) {
            if (argumentsFit(extension.function(), call.arguments()) && extends0(extension, receiver, call.name())) {
                return fileClass;
            }
        }
        return null;
    }

    /**
     * The member a call on a receiver of exactly known type uses. The receiver is a parameter, local
     * or property of a written type, or an object or companion named in the source; a call with no
     * receiver, on {@code this}, on a name the member redeclares and on any other receiver adds
     * nothing.
     *
     * <p>A type of the repository owns the member itself unless a readable ancestor declares it, and
     * a name that no readable type declares is not credited while an extension function of that name
     * is visible, since the call may be that. A JDK type owns the member only when the platform has
     * a member of that name on it.
     *
     * @param call The call.
     * @return The reference, or null when the receiver or the owner is not known.
     */
    MemberReference memberReference(final ReceiverCall call) {
        final String receiver = call.receiver();
        if ("this".equals(receiver) || member.declarations.containsKey(call.name())) {
            return null;
        }
        String type = typeOfReceiver(receiver);
        if (type == null && !receiver.isEmpty() && receiver.indexOf('.') < 0
                && Character.isUpperCase(receiver.charAt(0))) {
            type = typeOfRoot(receiver);
        }
        if (type == null || KotlinTypeMembers.isArray(type)) {
            return null;
        }
        final MemberReference reference;
        if (resolver.index().declaration(type) != null) {
            String owner = typeMembers.declaringType(type, call.name());
            if (owner == null) {
                if (hasVisibleExtension(call.name())) {
                    return null;
                }
                owner = type;
            }
            reference = new MemberReference(owner, call.name(), MemberKind.CALL);
        } else {
            final String unit = typeMembers.platformPackage(type, call.name());
            if (unit == null) {
                return null;
            }
            reference = new MemberReference(type, call.name(), MemberKind.CALL, unit);
        }
        reference.setResolutionKind(ResolutionKind.EXACT);
        return reference;
    }

    private boolean hasVisibleExtension(final String name) {
        for (final TopLevelFunction visible : resolver.visibleFunctions(name, scope)) {
            if (visible.function().receiverType != null) {
                return true;
            }
        }
        return opaqueStarImport() && !importedByName(name);
    }

    /**
     * Whether the file star-imports a package that is neither the repository's nor the JDK's, which
     * may bring in an extension of the same name that the call actually reaches.
     */
    private boolean opaqueStarImport() {
        for (final KotlinModel.KotlinImport kotlinImport : scope.file().imports) {
            if (kotlinImport.star() && !kotlinImport.target().startsWith("java.")
                    && !kotlinImport.target().startsWith("javax.")
                    && !resolver.index().declaresPackage(kotlinImport.target())) {
                return true;
            }
        }
        return false;
    }

    private boolean importedByName(final String name) {
        for (final KotlinModel.KotlinImport kotlinImport : scope.file().imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean argumentsFit(final KotlinMemberModel function, final int arguments) {
        int required = 0;
        boolean vararg = false;
        for (final KotlinParameterModel parameter : function.parameters) {
            if (!parameter.optional) {
                required += 1;
            }
            vararg |= parameter.vararg;
        }
        return arguments >= required && (vararg || arguments <= function.parameters.size());
    }

    private boolean extends0(final TopLevelFunction extension, final String receiver, final String name) {
        final String written = KotlinTypeMembers.writtenName(extension.function().receiverType);
        if (written == null) {
            return false;
        }
        final KotlinTypeMembers.MemberSet members = typeMembers.of(receiver);
        if (extension.function().typeParameters.contains(written)) {
            return !extension.function().boundedTypeParameters.contains(written) && members != null
                    && members.complete() && !members.names().contains(name);
        }
        final Set<String> declared = new HashSet<>(extension.function().typeParameters);
        final String extended = typeMembers.resolveWritten(extension.function().receiverType,
                new Scope(extension.file(), List.of(), declared));
        if (extended == null) {
            return false;
        }
        if (members == null) {
            return extended.equals(receiver) && !resolver.index().isType(receiver);
        }
        return members.complete() && !members.names().contains(name)
                && (extended.equals(receiver) || typeMembers.ancestorsOf(receiver).contains(extended));
    }
}
