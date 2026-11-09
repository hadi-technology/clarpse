package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal data-transfer models shared between the C# parse phase and the
 * assembler phase. These types intentionally keep syntax extraction separate
 * from final component creation so parsing can remain lightweight and parallel.
 */
final class CSharpModel {

    private CSharpModel() {
    }

    static final class ParseOutcome {
        private final int index;
        private final CSharpFileModel fileModel;
        private final com.hadi.clarpse.compiler.CompileFailure failure;

        ParseOutcome(final int index,
                     final CSharpFileModel fileModel,
                     final com.hadi.clarpse.compiler.CompileFailure failure) {
            this.index = index;
            this.fileModel = fileModel;
            this.failure = failure;
        }

        int index() {
            return index;
        }

        CSharpFileModel fileModel() {
            return fileModel;
        }

        com.hadi.clarpse.compiler.CompileFailure failure() {
            return failure;
        }
    }

    static final class CSharpFileModel {
        final ProjectFile sourceFile;
        final String sourceText;
        final String moduleName;
        final List<CSharpUsingModel> usings = new ArrayList<>();
        final List<CSharpTypeModel> types = new ArrayList<>();

        CSharpFileModel(final ProjectFile sourceFile,
                        final String sourceText,
                        final String moduleName) {
            this.sourceFile = sourceFile;
            this.sourceText = sourceText;
            this.moduleName = moduleName;
        }

        /**
         * A copy whose type models, nested ones included, are fresh objects, so assembling it leaves
         * this model as parsed. Assembly merges the parts of a partial type into the first part's
         * model, which is why a file model assembled more than once must be copied each time. Member
         * models are shared: assembly only adds imports to them, which is idempotent.
         */
        CSharpFileModel assemblyCopy() {
            final CSharpFileModel copy = new CSharpFileModel(sourceFile, sourceText, moduleName);
            copy.usings.addAll(usings);
            for (final CSharpTypeModel type : types) {
                copy.types.add(type.assemblyCopy());
            }
            return copy;
        }
    }

    static final class CSharpUsingModel {
        final String alias;
        final String target;
        final boolean aliasImport;
        final boolean globalImport;
        final boolean staticImport;

        CSharpUsingModel(final String alias,
                         final String target,
                         final boolean aliasImport,
                         final boolean globalImport,
                         final boolean staticImport) {
            this.alias = alias;
            this.target = target;
            this.aliasImport = aliasImport;
            this.globalImport = globalImport;
            this.staticImport = staticImport;
        }
    }

    static final class CSharpTypeModel {
        String kind;
        /** The simple name, without type parameters. */
        String name;
        /** The number of type parameters. */
        int arity;
        String namespaceName;
        String moduleName;
        String sourcePath;
        String sourceText;
        String componentName;
        String uniqueName;
        String comment = "";
        String codeFragment;
        boolean partial;
        int startOffset;
        int endOffset;
        /** Hash over the whole declaration, body included, so that implementation edits are visible. */
        int implementationHash;
        List<String> modifiers = new ArrayList<>();
        /** Names of the attributes ({@code [ApiController]}) applied to this type declaration. */
        List<CSharpAttribute> annotations = new ArrayList<>();
        List<String> baseTypes = new ArrayList<>();
        /** Types named by {@code where} constraints, which are not supertypes. */
        List<String> constraintTypes = new ArrayList<>();
        /** Names of the type parameters this type declares. */
        Set<String> typeParameters = new LinkedHashSet<>();
        /** Declared types of the primary-constructor parameters of a class or struct. */
        List<String> primaryParameterTypes = new ArrayList<>();
        List<CSharpMemberModel> members = new ArrayList<>();
        List<CSharpTypeModel> nestedTypes = new ArrayList<>();
        Set<String> imports = new LinkedHashSet<>();
        Map<String, String> usingAliases = new LinkedHashMap<>();

        /** A copy with its own collections and its own copies of its nested types. */
        CSharpTypeModel assemblyCopy() {
            final CSharpTypeModel copy = new CSharpTypeModel();
            copy.kind = kind;
            copy.name = name;
            copy.arity = arity;
            copy.namespaceName = namespaceName;
            copy.moduleName = moduleName;
            copy.sourcePath = sourcePath;
            copy.sourceText = sourceText;
            copy.componentName = componentName;
            copy.uniqueName = uniqueName;
            copy.comment = comment;
            copy.codeFragment = codeFragment;
            copy.partial = partial;
            copy.startOffset = startOffset;
            copy.endOffset = endOffset;
            copy.implementationHash = implementationHash;
            copy.modifiers = new ArrayList<>(modifiers);
            copy.annotations = new ArrayList<>(annotations);
            copy.baseTypes = new ArrayList<>(baseTypes);
            copy.constraintTypes = new ArrayList<>(constraintTypes);
            copy.typeParameters = new LinkedHashSet<>(typeParameters);
            copy.primaryParameterTypes = new ArrayList<>(primaryParameterTypes);
            copy.members = new ArrayList<>(members);
            copy.nestedTypes = new ArrayList<>();
            for (final CSharpTypeModel nested : nestedTypes) {
                copy.nestedTypes.add(nested.assemblyCopy());
            }
            copy.imports = new LinkedHashSet<>(imports);
            copy.usingAliases = new LinkedHashMap<>(usingAliases);
            return copy;
        }
    }

    /** An attribute applied to a declaration: its name as written and its argument list's text, or null. */
    record CSharpAttribute(String name, String arguments) {
    }

    static final class CSharpMemberModel {
        String kind;
        String name;
        String declaredType;
        String returnType;
        String codeFragment;
        String comment = "";
        String sourcePath;
        String sourceText;
        String moduleName;
        String ownerTypeUniqueName;
        int startOffset;
        int endOffset;
        int cyclo;
        /** Hash over the whole declaration, body included, so that implementation edits are visible. */
        int implementationHash;
        List<String> modifiers = new ArrayList<>();
        /** Names of the attributes ({@code [HttpGet]}) applied to this member declaration. */
        List<CSharpAttribute> annotations = new ArrayList<>();
        List<CSharpParameterModel> parameters = new ArrayList<>();
        List<CSharpMemberModel> locals = new ArrayList<>();
        /** Names of the type parameters a generic method declares. */
        Set<String> typeParameters = new LinkedHashSet<>();
        List<String> simpleTypeUsages = new ArrayList<>();
        /**
         * Dotted names written before a member access in the member's body or initializer; each
         * denotes a type, or a type followed by members.
         */
        List<String> staticReceivers = new ArrayList<>();
        /** The operands of {@code typeof} in the member's body or initializer; each is a type as written. */
        List<String> typeofOperands = new ArrayList<>();
        List<String> memberUsages = new ArrayList<>();
        /**
         * The calls of the form {@code receiver.Name(...)} in the member's body or initializer, each of
         * which may be a call of an extension method.
         */
        Set<ExtensionCall> extensionCalls = new LinkedHashSet<>();
        /**
         * The variables that lambdas and anonymous methods in the member declare, by name, with the
         * type each is written with; a null type is an implicitly typed parameter.
         */
        Map<String, List<String>> lambdaVariables = new LinkedHashMap<>();
        Set<String> imports = new LinkedHashSet<>();
        /** The using aliases of the file that declares this member, which a partial type's other parts lack. */
        Map<String, String> usingAliases = new LinkedHashMap<>();
        boolean inferredType;
        /**
         * For a member declared in a C# 14 {@code extension(T x)} block that has no receiver parameter of
         * its own -- a property, a field, a static member -- the type the block extends, as written.
         */
        String extendedType;
        /**
         * For a {@code var} local initialised from a member access -- {@code a.Member},
         * {@code a?.Method()}, {@code a.B.C} -- the accessed names in order, a name that is called
         * ending in {@code ()}. The first is the receiver; empty for any other local.
         */
        List<String> initializerAccess = new ArrayList<>();
    }

    /**
     * A call {@code receiver.Name(...)}. The receiver is the dotted chain of names written before the
     * call, or null when what precedes it is not such a chain (a call result, an indexer, a literal).
     * The argument count is -1 when it could not be read.
     */
    record ExtensionCall(String receiver, String name, int argumentCount) {
    }

    static final class CSharpParameterModel {
        String name;
        /** True when the parameter has a default value. */
        boolean optional;
        /** True for a {@code params} array. */
        boolean paramsArray;
        String declaredType;
        /** True for the {@code this} parameter of an extension method, which names the type it extends. */
        boolean extensionReceiver;
        String comment = "";
        /** Hash over the whole parameter declaration, its modifiers and default value included. */
        int implementationHash;
        List<String> modifiers = new ArrayList<>();
    }
}
