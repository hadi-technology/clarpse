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
        List<String> baseTypes = new ArrayList<>();
        List<CSharpMemberModel> members = new ArrayList<>();
        List<CSharpTypeModel> nestedTypes = new ArrayList<>();
        Set<String> imports = new LinkedHashSet<>();
        Map<String, String> usingAliases = new LinkedHashMap<>();
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
        String comment = "";
        /** Hash over the whole parameter declaration, its modifiers and default value included. */
        int implementationHash;
        List<String> modifiers = new ArrayList<>();
    }
}
