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
        String name;
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
        List<String> annotations = new ArrayList<>();
        List<String> baseTypes = new ArrayList<>();
        List<CSharpMemberModel> members = new ArrayList<>();
        List<CSharpTypeModel> nestedTypes = new ArrayList<>();
        Set<String> imports = new LinkedHashSet<>();
        Map<String, String> usingAliases = new LinkedHashMap<>();

        /** A copy with its own collections and its own copies of its nested types. */
        CSharpTypeModel assemblyCopy() {
            final CSharpTypeModel copy = new CSharpTypeModel();
            copy.kind = kind;
            copy.name = name;
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
        List<String> annotations = new ArrayList<>();
        List<CSharpParameterModel> parameters = new ArrayList<>();
        List<CSharpMemberModel> locals = new ArrayList<>();
        List<String> simpleTypeUsages = new ArrayList<>();
        List<String> memberUsages = new ArrayList<>();
        Set<String> imports = new LinkedHashSet<>();
        boolean inferredType;
    }

    static final class CSharpParameterModel {
        String name;
        String declaredType;
        /** True for the {@code this} parameter of an extension method, which names the type it extends. */
        boolean extensionReceiver;
        String comment = "";
        /** Hash over the whole parameter declaration, its modifiers and default value included. */
        int implementationHash;
        List<String> modifiers = new ArrayList<>();
    }
}
