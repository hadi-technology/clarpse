package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.ProjectFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The declarations the Kotlin parse phase reads out of a file, before any name is resolved. The
 * assembler turns them into components once every file's declarations are known.
 */
final class KotlinModel {

    private KotlinModel() {
    }

    /** What parsing one file produced: its model, or the failure that took its place. */
    record ParseOutcome(int index, KotlinFileModel fileModel, CompileFailure failure) {
    }

    /** The kinds of type declaration. */
    enum TypeKind {
        CLASS, INTERFACE, OBJECT, ENUM, ANNOTATION
    }

    /** The kinds of member declaration. */
    enum MemberKind {
        PROPERTY, FUNCTION, CONSTRUCTOR, ENUM_ENTRY
    }

    /** One Kotlin file: its package, imports and declarations. */
    static final class KotlinFileModel {
        final ProjectFile sourceFile;
        final String moduleName;
        String packageName = "";
        /** The name given by {@code @file:JvmName}, or null. */
        String jvmName;
        boolean multifileClass;
        final List<KotlinImport> imports = new ArrayList<>();
        final List<KotlinTypeModel> types = new ArrayList<>();
        /** Functions and properties declared at the top level, extension functions included. */
        final List<KotlinMemberModel> topLevelMembers = new ArrayList<>();
        final List<KotlinTypeAlias> typeAliases = new ArrayList<>();

        KotlinFileModel(final ProjectFile sourceFile, final String moduleName) {
            this.sourceFile = sourceFile;
            this.moduleName = moduleName;
        }

        String path() {
            return sourceFile.path();
        }
    }

    /**
     * An import directive.
     *
     * @param target The imported name, or the package of a star import.
     * @param alias  The name given with {@code as}, or null.
     * @param star   Whether the directive imports everything in {@code target}.
     */
    record KotlinImport(String target, String alias, boolean star) {

        /** The simple name the import brings into scope, or null for a star import. */
        String visibleName() {
            if (star) {
                return null;
            }
            if (alias != null) {
                return alias;
            }
            final int dot = target.lastIndexOf('.');
            return target.substring(dot + 1);
        }
    }

    /**
     * A {@code typealias}.
     *
     * @param name   The alias.
     * @param target The type names the aliased type expression is written with, the aliased type
     *               first.
     */
    record KotlinTypeAlias(String name, List<String> target) {
    }

    /**
     * A type named in source.
     *
     * @param name       The name as written, qualifier included and type arguments excluded.
     * @param expression Whether it is named in an expression (a call, a receiver, a class literal)
     *                   rather than in a type position.
     * @param value      Whether it is a name used as a value on its own, such as an object passed as
     *                   an argument, which counts only when it names a repository type.
     * @param call       Whether it is the name of a call, {@code Name(...)}, which a function or a
     *                   property closer in scope may be the target of.
     */
    record TypeUsage(String name, boolean expression, boolean value, boolean call) {

        TypeUsage(final String name, final boolean expression) {
            this(name, expression, false, false);
        }

        TypeUsage(final String name, final boolean expression, final boolean value) {
            this(name, expression, value, false);
        }
    }

    /** One supertype entry. */
    static final class KotlinSuperType {
        final String name;
        final List<String> typeArguments;
        /** Whether the entry calls a constructor, which only a superclass can be. */
        final boolean constructorCall;

        KotlinSuperType(final String name, final List<String> typeArguments, final boolean constructorCall) {
            this.name = name;
            this.typeArguments = typeArguments;
            this.constructorCall = constructorCall;
        }
    }

    /** An annotation applied to a declaration: its type as written, and the arguments written on it. */
    record KotlinAnnotationUse(String name, List<KotlinAnnotationArgument> arguments) {
    }

    /** One argument of an applied annotation; the name is null when it was written without one. */
    record KotlinAnnotationArgument(String name, KotlinAnnotationValue value) {
    }

    /** What kind of expression an annotation argument was written as. */
    enum KotlinAnnotationValueKind {
        STRING, NUMBER, BOOLEAN, CLASS,
        /** A dotted name, which is an enum constant when its qualifier is an enum. */
        QUALIFIED,
        ARRAY,
        /** Any other expression. */
        OTHER
    }

    /**
     * An annotation argument as written: for a literal its value, for a class literal or dotted name
     * its text, for an array its elements.
     */
    record KotlinAnnotationValue(KotlinAnnotationValueKind kind, String text, List<KotlinAnnotationValue> elements) {
    }

    /**
     * An expression whose type a declaration can tell: a name, a call of a bare name, or a member of
     * another such expression reached by {@code .} or {@code ?.}. Any other expression is not read.
     */
    sealed interface KotlinExpr {
    }

    /** A simple name: a parameter, a local, a property or an object. */
    record NameExpr(String name) implements KotlinExpr {
    }

    /** A call of a bare name, {@code name(...)}: a constructor or a top-level function. */
    record CallExpr(String name) implements KotlinExpr {
    }

    /**
     * A property read or a call of a member on a receiver.
     *
     * @param receiver The expression the member is reached on.
     * @param name     The member's name.
     * @param call     Whether it is called, {@code receiver.name(...)}, rather than read.
     */
    record MemberExpr(KotlinExpr receiver, String name, boolean call) implements KotlinExpr {
    }

    /**
     * A call of a member on a receiver that is a single name, {@code receiver.name(arguments)}.
     *
     * @param receiver  The receiver's name.
     * @param name      The called name.
     * @param arguments How many arguments the call passes, a trailing lambda included.
     */
    record ReceiverCall(String receiver, String name, int arguments) {
    }

    /** A class, interface, object, enum class or annotation class. */
    static final class KotlinTypeModel {
        TypeKind kind;
        String name;
        String comment = "";
        String codeFragment;
        int implementationHash;
        int startOffset;
        boolean companion;
        /** Whether it is a {@code data} class, which generates {@code copy} and the component functions. */
        boolean data;
        final List<String> modifiers = new ArrayList<>();
        final List<KotlinAnnotationUse> annotations = new ArrayList<>();
        final List<KotlinSuperType> superTypes = new ArrayList<>();
        /**
         * Types the declaration names outside its members: type parameter bounds and constraints,
         * the arguments of a superclass constructor call, and initializer blocks.
         */
        final List<TypeUsage> ownUsages = new ArrayList<>();
        /** Names that initializer blocks declare, which are values in the scope of their own text. */
        final Set<String> initializerDeclarations = new LinkedHashSet<>();
        final List<KotlinMemberModel> members = new ArrayList<>();
        final List<KotlinTypeModel> nestedTypes = new ArrayList<>();
        /** Names of this type's type parameters, which shadow types of the same name. */
        final Set<String> typeParameters = new LinkedHashSet<>();
    }

    /** A property, function, constructor or enum entry. */
    static final class KotlinMemberModel {
        MemberKind kind;
        String name;
        String comment = "";
        String codeFragment;
        /** The declared or return type as written, or null when inferred. */
        String declaredType;
        /** The receiver type of an extension, as written, or null. */
        String receiverType;
        int implementationHash;
        int startOffset;
        int cyclo = 1;
        final List<String> modifiers = new ArrayList<>();
        final List<KotlinAnnotationUse> annotations = new ArrayList<>();
        final List<KotlinParameterModel> parameters = new ArrayList<>();
        /** The local variables a function or constructor body declares. */
        final List<KotlinMemberModel> locals = new ArrayList<>();
        /** Every type named by this member, its signature and body included. */
        final List<TypeUsage> typeUsages = new ArrayList<>();
        /** Names this member calls as functions, which may be top-level functions of the repository. */
        final Set<String> calledNames = new LinkedHashSet<>();
        /**
         * The subset of {@link #calledNames} called only inside a lambda, where a receiver the lambda
         * is given may declare a function of the same name.
         */
        final Set<String> calledInLambdas = new LinkedHashSet<>();
        /** Names of this member's own type parameters. */
        final Set<String> typeParameters = new LinkedHashSet<>();
        /** The type parameters of this member that carry a bound, in the list or in a {@code where} clause. */
        final Set<String> boundedTypeParameters = new LinkedHashSet<>();
        /**
         * How many times the member's text declares each name as a value: its parameters, its
         * locals, and the parameters of lambdas, loops and catch clauses and the entries of
         * destructuring declarations anywhere inside it.
         */
        final Map<String, Integer> declarations = new HashMap<>();
        /**
         * Names whose type at some point may differ from the declared one: tested or cast with
         * {@code is} or {@code as}, matched by a {@code when} with {@code is} entries, or assigned.
         */
        final Set<String> narrowed = new LinkedHashSet<>();
        /** For a local declared without a type, the expression that initializes it, when readable. */
        KotlinExpr initializer;
        /** The calls on a receiver that is a single name. */
        final List<ReceiverCall> receiverCalls = new ArrayList<>();
    }

    /** A parameter of a function or constructor. */
    static final class KotlinParameterModel {
        String name;
        String declaredType;
        int implementationHash;
        final List<String> modifiers = new ArrayList<>();
        final List<TypeUsage> typeUsages = new ArrayList<>();
        /** Whether a primary constructor parameter declares a property ({@code val} or {@code var}). */
        boolean property;
        boolean mutable;
        /** Whether a call may leave the parameter out: it has a default value, or takes a vararg. */
        boolean optional;
        boolean vararg;
    }
}
