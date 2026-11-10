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
            if (resolved == null || resolved.kind() == ResolutionKind.UNRESOLVED) {
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
