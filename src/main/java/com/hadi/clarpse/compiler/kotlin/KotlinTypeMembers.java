package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.KotlinDeclaration;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinParameterModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinSuperType;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Resolution;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Scope;
import com.hadi.clarpse.reference.ResolutionKind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The names a Kotlin type declares members by, itself and through the supertypes the repository
 * declares in Kotlin, and whether that list is the whole story.
 *
 * <p>A set is complete only when every supertype, at every level, is a Kotlin type the repository
 * declares exactly once. A supertype the parse cannot read -- a library type, a Java type -- may
 * declare, and so pass down, any name, so a name missing from an incomplete set is not known to be
 * absent. A supertype the JDK declares is read from the platform, so it does not make the set
 * incomplete. The members every class has ({@code equals}, {@code hashCode}, {@code toString}) and those
 * an enum or data class generates are always part of the set.
 */
final class KotlinTypeMembers {

    /**
     * The member names of a type.
     *
     * @param names    Every name the type, its companion object and its readable ancestors declare.
     * @param complete Whether no supertype is unreadable.
     */
    record MemberSet(Set<String> names, boolean complete) {
    }

    private static final int MAX_DATA_COMPONENTS = 32;
    private static final Set<String> ANY_MEMBERS = Set.of("equals", "hashCode", "toString");
    private static final Set<String> ENUM_MEMBERS = Set.of("name", "ordinal", "values", "valueOf", "entries",
            "compareTo");

    private static final Map<String, Optional<Set<String>>> PLATFORM_MEMBERS = new ConcurrentHashMap<>();

    private final KotlinNameResolver resolver;

    KotlinTypeMembers(final KotlinNameResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * The member names of a type declared in the file the scope belongs to.
     *
     * @param type  The type.
     * @param scope Where the type is declared, its own name included among the enclosing types.
     * @return Its member set.
     */
    MemberSet of(final KotlinTypeModel type, final Scope scope) {
        final Set<String> names = new HashSet<>();
        final boolean complete = collect(type, scope, names, new HashSet<>());
        return new MemberSet(names, complete);
    }

    /**
     * The member names of a repository type.
     *
     * @param uniqueName The type's fully qualified name.
     * @return Its member set, or null when no single Kotlin declaration answers for the name.
     */
    MemberSet of(final String uniqueName) {
        final KotlinDeclaration declaration = resolver.index().declaration(uniqueName);
        if (declaration == null) {
            return null;
        }
        return of(declaration.type(), scopeOf(declaration));
    }

    static Scope scopeOf(final KotlinDeclaration declaration) {
        return new Scope(declaration.file(), declaration.enclosing(), Set.of());
    }

    private boolean collect(final KotlinTypeModel type, final Scope scope, final Set<String> names,
                            final Set<KotlinTypeModel> visiting) {
        if (!visiting.add(type)) {
            return false;
        }
        names.addAll(ANY_MEMBERS);
        names.addAll(ownNames(type, visiting.size() == 1));
        if (type.kind == TypeKind.ENUM) {
            names.addAll(ENUM_MEMBERS);
        }
        if (type.data) {
            names.add("copy");
            for (int i = 1; i <= MAX_DATA_COMPONENTS; i += 1) {
                names.add("component" + i);
            }
        }
        boolean complete = true;
        for (final KotlinSuperType superType : type.superTypes) {
            final Resolution resolved = resolver.resolve(superType.name, scope, false);
            if (resolved == null || resolved.kind() != ResolutionKind.EXACT) {
                complete = false;
                continue;
            }
            final KotlinDeclaration declaration = resolver.index().declaration(resolved.name());
            if (declaration == null) {
                final Set<String> platform = platformMembers(resolved.name());
                if (platform == null) {
                    complete = false;
                } else {
                    names.addAll(platform);
                }
                continue;
            }
            complete &= collect(declaration.type(), scopeOf(declaration), names, visiting);
        }
        visiting.remove(type);
        return complete;
    }

    /**
     * The declared type of a member of a repository type, reached on a receiver of that type: the
     * return type a function writes, or the type a property writes. Only a type whose whole ancestry
     * the parse can read answers, and only a member every declaration of that name in it types the
     * same way; an overload with another return type, a member without a written type, an array,
     * and a type a type parameter stands for all answer nothing.
     *
     * @param owner The receiver's type, fully qualified.
     * @param name  The member's name.
     * @param call  Whether the member is called rather than read.
     * @return The member's type, fully qualified, or null.
     */
    String memberType(final String owner, final String name, final boolean call) {
        final KotlinDeclaration root = resolver.index().declaration(owner);
        if (root == null) {
            return null;
        }
        final List<KotlinDeclaration> chain = new ArrayList<>();
        final Set<String> platform = new HashSet<>();
        if (!readChain(root, chain, platform, new HashSet<>()) || platform.contains(name)) {
            return null;
        }
        String found = null;
        for (final KotlinDeclaration declaration : chain) {
            for (final KotlinMemberModel member : declaration.type().members) {
                if (member.receiverType != null) {
                    continue;
                }
                final Optional<String> written = writtenBy(member, name, call);
                if (written == null) {
                    continue;
                }
                final String resolved = written.map(text -> resolveWritten(text, declaration, member)).orElse(null);
                if (resolved == null || (found != null && !found.equals(resolved))) {
                    return null;
                }
                found = resolved;
            }
        }
        return found;
    }

    /**
     * What a member writes as the type of the name asked for: null when it is not that member, empty
     * when it is but writes no type (or is something the name cannot be read as), else the written type.
     */
    private static Optional<String> writtenBy(final KotlinMemberModel member, final String name,
                                              final boolean call) {
        if (member.kind == KotlinModel.MemberKind.CONSTRUCTOR) {
            for (final KotlinParameterModel parameter : member.parameters) {
                if (!call && parameter.property && name.equals(parameter.name)) {
                    return Optional.ofNullable(parameter.declaredType);
                }
            }
            return null;
        }
        if (!name.equals(member.name)) {
            return null;
        }
        if (call && member.kind == KotlinModel.MemberKind.FUNCTION) {
            return Optional.ofNullable(member.declaredType);
        }
        if (!call && member.kind == KotlinModel.MemberKind.PROPERTY) {
            return Optional.ofNullable(member.declaredType);
        }
        if (member.kind == KotlinModel.MemberKind.FUNCTION) {
            return null;
        }
        // A property that may be invoked, or an enum entry, read the other way: not a typed member.
        return Optional.empty();
    }

    private String resolveWritten(final String text, final KotlinDeclaration declaration,
                                  final KotlinMemberModel member) {
        final Set<String> typeParameters = new HashSet<>(member.typeParameters);
        for (final String enclosing : declaration.enclosing()) {
            final KotlinDeclaration outer = resolver.index().declaration(enclosing);
            if (outer != null) {
                typeParameters.addAll(outer.type().typeParameters);
            }
        }
        return resolveWritten(text, new Scope(declaration.file(), declaration.enclosing(), typeParameters));
    }

    /**
     * The type a written type names, when it is one plain name: not a generic, a function type, an
     * array, a type parameter or a name nothing declares. A trailing {@code ?} is dropped.
     *
     * @param text  The type as written.
     * @param scope Where it is written.
     * @return The fully qualified name, or null.
     */
    String resolveWritten(final String text, final Scope scope) {
        final String written = writtenName(text);
        if (written == null) {
            return null;
        }
        final Resolution resolved = resolver.resolve(written, scope, false);
        if (resolved == null || resolved.kind() != ResolutionKind.EXACT || isArray(resolved.name())) {
            return null;
        }
        return resolved.name();
    }

    /** The plain name a written type is, or null for anything but a possibly nullable dotted name. */
    static String writtenName(final String text) {
        if (text == null) {
            return null;
        }
        String written = text.trim();
        if (written.endsWith("?")) {
            written = written.substring(0, written.length() - 1);
        }
        if (written.isEmpty()) {
            return null;
        }
        for (int i = 0; i < written.length(); i += 1) {
            final char ch = written.charAt(i);
            if (!Character.isJavaIdentifierPart(ch) && ch != '.' && ch != '`') {
                return null;
            }
        }
        return written;
    }

    /** Whether a fully qualified name is a Kotlin array type, which has no members of its element type. */
    static boolean isArray(final String qualifiedName) {
        return qualifiedName.startsWith("kotlin.") && qualifiedName.endsWith("Array");
    }

    /**
     * Every Kotlin type the repository declares among a type's ancestors, itself included.
     *
     * @param uniqueName The type's fully qualified name.
     * @return Their fully qualified names; empty when no single Kotlin declaration answers for it.
     */
    Set<String> ancestorsOf(final String uniqueName) {
        final KotlinDeclaration root = resolver.index().declaration(uniqueName);
        final Set<String> names = new HashSet<>();
        if (root != null) {
            final List<KotlinDeclaration> chain = new ArrayList<>();
            readChain(root, chain, new HashSet<>(), new HashSet<>());
            chain.forEach(declaration -> names.add(declaration.uniqueName()));
        }
        return names;
    }

    /**
     * The repository type, among a type and its readable ancestors, that declares a member of the
     * name: the type itself first, then its ancestors in the order they were read.
     *
     * @param uniqueName The receiver's type, fully qualified.
     * @param name       The member's name.
     * @return The declaring type's fully qualified name, or null when no readable type declares it.
     */
    String declaringType(final String uniqueName, final String name) {
        final KotlinDeclaration root = resolver.index().declaration(uniqueName);
        if (root == null) {
            return null;
        }
        final List<KotlinDeclaration> chain = new ArrayList<>();
        readChain(root, chain, new HashSet<>(), new HashSet<>());
        for (final KotlinDeclaration declaration : chain) {
            if (ownNames(declaration.type(), false).contains(name)) {
                return declaration.uniqueName();
            }
        }
        return null;
    }

    /**
     * The package of a type the JDK declares, when the platform has a public or protected method or
     * field of the name on it: a call of that name on the type is then a call of a member, which wins
     * over any extension function of the same name.
     *
     * @param qualifiedName The type's fully qualified name, as the source resolves it.
     * @param name          The member's name.
     * @return The type's package, or null for a type the platform does not declare, a {@code kotlin}
     *         type (whose members are not those of its JDK counterpart), or a name it has no member of.
     */
    String platformPackage(final String qualifiedName, final String name) {
        if (!qualifiedName.startsWith("java.") && !qualifiedName.startsWith("javax.")) {
            return null;
        }
        final Set<String> members = platformMembers(qualifiedName);
        if (members == null || !members.contains(name)) {
            return null;
        }
        String candidate = qualifiedName;
        while (true) {
            try {
                return Class.forName(candidate, false, ClassLoader.getPlatformClassLoader()).getPackageName();
            } catch (final ClassNotFoundException | LinkageError unreadable) {
                final int cut = candidate.lastIndexOf('.');
                if (cut < 0) {
                    return null;
                }
                candidate = candidate.substring(0, cut) + "$" + candidate.substring(cut + 1);
            }
        }
    }

    /**
     * Collects a declaration and the repository declarations of its ancestors, and the member names
     * of the JDK ancestors; returns whether every supertype, at every level, was one of the two.
     */
    private boolean readChain(final KotlinDeclaration declaration, final List<KotlinDeclaration> chain,
                              final Set<String> platform, final Set<String> visited) {
        if (!visited.add(declaration.uniqueName())) {
            return true;
        }
        chain.add(declaration);
        boolean complete = true;
        final Scope scope = scopeOf(declaration);
        for (final KotlinSuperType superType : declaration.type().superTypes) {
            final Resolution resolved = resolver.resolve(superType.name, scope, false);
            if (resolved == null || resolved.kind() != ResolutionKind.EXACT) {
                complete = false;
                continue;
            }
            final KotlinDeclaration parent = resolver.index().declaration(resolved.name());
            if (parent != null) {
                complete &= readChain(parent, chain, platform, visited);
                continue;
            }
            final Set<String> names = platformMembers(resolved.name());
            if (names == null) {
                complete = false;
            } else {
                platform.addAll(names);
            }
        }
        return complete;
    }

    /**
     * The member names of a type the JDK declares, read from the running platform the way the Java
     * side's type solver reads it: a {@code java} or {@code javax} type, or a {@code kotlin} type
     * that is the JDK type of the same simple name. The repository's own types are not asked.
     *
     * @param qualifiedName The supertype's fully qualified name.
     * @return Every public or protected method and field name of it and its ancestors, or null when
     *         the platform does not declare it.
     */
    private Set<String> platformMembers(final String qualifiedName) {
        if (resolver.index().isType(qualifiedName)) {
            return null;
        }
        String jdkName = qualifiedName;
        if (qualifiedName.startsWith("kotlin.") && qualifiedName.indexOf('.', "kotlin.".length()) < 0) {
            jdkName = "java.lang." + qualifiedName.substring("kotlin.".length());
        } else if (!qualifiedName.startsWith("java.") && !qualifiedName.startsWith("javax.")) {
            return null;
        }
        return PLATFORM_MEMBERS.computeIfAbsent(jdkName, KotlinTypeMembers::reflectMembers).orElse(null);
    }

    private static Optional<Set<String>> reflectMembers(final String className) {
        try {
            final Class<?> type = Class.forName(className, false, ClassLoader.getPlatformClassLoader());
            if (!Modifier.isPublic(type.getModifiers())) {
                return Optional.empty();
            }
            final Set<String> names = new HashSet<>();
            for (final Method method : type.getMethods()) {
                names.add(method.getName());
            }
            for (final Field field : type.getFields()) {
                names.add(field.getName());
            }
            for (Class<?> level = type; level != null; level = level.getSuperclass()) {
                for (final Method method : level.getDeclaredMethods()) {
                    if (Modifier.isProtected(method.getModifiers())) {
                        names.add(method.getName());
                    }
                }
                for (final Field field : level.getDeclaredFields()) {
                    if (Modifier.isProtected(field.getModifiers())) {
                        names.add(field.getName());
                    }
                }
            }
            return Optional.of(names);
        } catch (final ClassNotFoundException | LinkageError | SecurityException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * The names a type, and its companion object, declare members by.
     *
     * @param type         The type.
     * @param constructors Whether its constructors count, which they do in the type's own body but
     *                     are not passed down to a subtype.
     * @return The names.
     */
    static Set<String> ownNames(final KotlinTypeModel type, final boolean constructors) {
        final Set<String> names = new HashSet<>();
        final List<KotlinMemberModel> members = new ArrayList<>(type.members);
        for (final KotlinTypeModel nested : type.nestedTypes) {
            if (nested.companion) {
                members.addAll(nested.members);
            }
        }
        for (final KotlinMemberModel member : members) {
            if (constructors || member.kind != KotlinModel.MemberKind.CONSTRUCTOR) {
                names.add(member.name);
            }
            for (final KotlinParameterModel parameter : member.parameters) {
                if (parameter.property) {
                    names.add(parameter.name);
                }
            }
        }
        return names;
    }
}
