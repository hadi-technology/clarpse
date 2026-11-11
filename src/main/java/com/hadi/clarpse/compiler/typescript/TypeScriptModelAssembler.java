package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.StandardLibraries;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptImportDirectiveModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptArgumentModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptComponentModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptFileModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptReferenceModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptTargetModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptValueModel;
import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.AnnotationArgument;
import com.hadi.clarpse.reference.AnnotationArguments;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ConstructionReference;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.ImportDirective;
import com.hadi.clarpse.sourcemodel.ImportKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;

import java.util.Set;
import java.util.Stack;

/**
 * Maps TypeScript daemon models into Clarpse components and references.
 */
final class TypeScriptModelAssembler {

    private TypeScriptModelAssembler() {
    }

    static Package resolvePackage(final String repoRoot, final String filePath) {
        String pkgPath = CompilerSupport.resolvePackagePath(repoRoot, filePath);
        return new Package(pkgPath, pkgPath);
    }

    static void insertFileModel(final Package pkg,
                                final String moduleName,
                                final String sourcePath,
                                final String repoRoot,
                                final TypeScriptFileModel fileModel,
                                final OOPSourceCodeModel srcModel) {
        insertFileModel(pkg, moduleName, sourcePath, repoRoot, fileModel, srcModel, null);
    }

    /**
     * Inserts a file's components, collecting the names of references that resolved into the
     * repository, and those whose type was lost to a file the program did not load.
     *
     * @param inRepositoryNames Collects those names; may be {@code null}.
     */
    static void insertFileModel(final Package pkg,
                                final String moduleName,
                                final String sourcePath,
                                final String repoRoot,
                                final TypeScriptFileModel fileModel,
                                final OOPSourceCodeModel srcModel,
                                final Set<String> inRepositoryNames) {
        if (fileModel == null || fileModel.declarations == null) {
            return;
        }
        final FileScope scope = new FileScope(pkg, moduleName, sourcePath, repoRoot, inRepositoryNames);
        for (final TypeScriptComponentModel declaration : fileModel.declarations) {
            insertComponentTree(scope, declaration, new Stack<>(), srcModel);
        }
        applyImports(repoRoot, sourcePath, fileModel, srcModel);
    }

    /**
     * Attaches the file's imports to what it declares at module level: its types, and its functions,
     * constants and type aliases. Members never carry them: a field or method reporting its file's
     * import list as its own is what made {@code imports()} mean a different thing in each language
     * (issue #156). An import the TypeScript compiler resolved inside the repository is recorded as
     * the component name it refers to, so it lines up with the rest of the model; anything else
     * keeps its specifier verbatim rather than being guessed into a path.
     */
    private static void applyImports(final String repoRoot,
                                     final String sourcePath,
                                     final TypeScriptFileModel fileModel,
                                     final OOPSourceCodeModel srcModel) {
        final java.util.Set<String> importedFiles = new java.util.LinkedHashSet<>();
        if (fileModel.importedFiles != null) {
            fileModel.importedFiles.stream().filter(f -> f != null && !f.isEmpty()).forEach(importedFiles::add);
        }
        final java.util.List<ImportDirective> directives = importDirectives(fileModel);
        if ((fileModel.imports == null || fileModel.imports.isEmpty()) && importedFiles.isEmpty()
                && directives.isEmpty()) {
            return;
        }
        final java.util.Set<String> imports = new java.util.LinkedHashSet<>();
        java.util.List<com.hadi.clarpse.compiler.typescript.model.TypeScriptImportModel> importList =
                java.util.List.of();
        if (fileModel.imports != null) {
            importList = fileModel.imports;
        }
        for (final com.hadi.clarpse.compiler.typescript.model.TypeScriptImportModel imported : importList) {
            if (imported == null) {
                continue;
            }
            String value = null;
            if (imported.filePath != null && imported.symbolName != null) {
                value = CompilerSupport.resolveUniqueNameFromTarget(
                        repoRoot, imported.filePath, imported.symbolName);
            } else if (imported.filePath != null && imported.namespace) {
                value = CompilerSupport.resolveModuleUniqueName(repoRoot, imported.filePath);
            }
            if (value == null || value.isEmpty()) {
                value = imported.module;
            }
            if (value != null && !value.isEmpty()) {
                imports.add(value);
            }
        }
        if (imports.isEmpty() && importedFiles.isEmpty() && directives.isEmpty()) {
            return;
        }
        final java.util.List<Component> declared = srcModel.components()
                .filter(component -> sourcePath.equals(component.sourceFile()))
                .toList();
        // A function or constant of a file that also declares types imports what the file imports
        // just as much as the types do, so it holds them too.
        declared.stream()
                .filter(TypeScriptModelAssembler::carriesFileImports)
                .forEach(component -> {
                    component.setImports(imports);
                    component.setImportedFiles(importedFiles);
                    component.setImportDirectives(directives);
                });
    }

    private static java.util.List<ImportDirective> importDirectives(final TypeScriptFileModel fileModel) {
        final java.util.List<ImportDirective> directives = new java.util.ArrayList<>();
        if (fileModel.importDirectives == null) {
            return directives;
        }
        for (final TypeScriptImportDirectiveModel raw : fileModel.importDirectives) {
            if (raw == null || raw.target == null || raw.target.isEmpty()) {
                continue;
            }
            ImportKind kind = ImportKind.SINGLE;
            if ("on_demand".equals(raw.kind)) {
                kind = ImportKind.ON_DEMAND;
            }
            directives.add(new ImportDirective(raw.target, kind, raw.relative, raw.names, raw.binds, raw.alias,
                    StandardLibraries.ofImport(Lang.TYPESCRIPT, raw.target, raw.relative, raw.inProject)).loading(raw.importsModule, raw.dynamic));
        }
        return directives;
    }

    private static boolean carriesFileImports(final Component component) {
        return component.componentType().isBaseComponent() || isModuleLevelDeclaration(component);
    }

    private static boolean isModuleLevelDeclaration(final Component component) {
        final OOPSourceModelConstants.ComponentType type = component.componentType();
        return type == OOPSourceModelConstants.ComponentType.FUNCTION
                || type == OOPSourceModelConstants.ComponentType.MODULE_FIELD
                || type == OOPSourceModelConstants.ComponentType.TYPE_ALIAS
                || type == OOPSourceModelConstants.ComponentType.NAMESPACE;
    }

    /**
     * What every component of one file shares: where it is declared, and the collector of names
     * its references resolved to inside the repository.
     */
    private record FileScope(Package pkg, String moduleName, String sourcePath, String repoRoot,
                             Set<String> inRepositoryNames) {
    }

    private static void insertComponentTree(final FileScope scope,
                                            final TypeScriptComponentModel declaration,
                                            final Stack<Component> stack,
                                            final OOPSourceCodeModel srcModel) {
        final Component component = buildComponent(scope, declaration, stack);
        if (component == null) {
            return;
        }
        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        for (final TypeScriptComponentModel member : declaration.members) {
            insertComponentTree(scope, member, stack, srcModel);
        }
        if (component.componentType().isMethodComponent() && declaration.cyclo > 0) {
            component.setCyclo(declaration.cyclo);
        }
        srcModel.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private static Component buildComponent(final FileScope scope,
                                            final TypeScriptComponentModel declaration,
                                            final Stack<Component> stack) {
        final Package pkg = scope.pkg();
        final String moduleName = scope.moduleName();
        final String sourcePath = scope.sourcePath();
        Component parent = null;
        if (!stack.isEmpty()) {
            parent = stack.peek();
        }
        final OOPSourceModelConstants.ComponentType componentType =
                mapComponentType(declaration.kind, parent);
        if (componentType == null || declaration.name == null || declaration.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        component.setComponentType(componentType);
        component.setName(declaration.name);
        component.setComponentName(generateComponentName(moduleName, declaration, componentType, stack));
        component.setSourceFilePath(sourcePath);
        if (declaration.jsDoc != null && !declaration.jsDoc.isEmpty()) {
            component.setComment(declaration.jsDoc);
        }
        if (!declaration.modifiers.isEmpty()) {
            component.setAccessModifiers(declaration.modifiers);
        }
        final String codeFragment = buildCodeFragment(declaration, componentType);
        applyCodeSignature(component, declaration, codeFragment);
        attachReferences(component, declaration, scope.repoRoot(), scope.inRepositoryNames());
        return component;
    }

    private static void applyCodeSignature(final Component component,
                                           final TypeScriptComponentModel declaration,
                                           final String codeFragment) {
        if (codeFragment != null && !codeFragment.isEmpty()) {
            component.setCodeFragment(codeFragment);
        }
        int hash = 0;
        if (declaration != null) {
            hash = declaration.implementationHash;
        }
        if (hash == 0 && codeFragment != null && !codeFragment.isEmpty()) {
            hash = codeFragment.hashCode();
        }
        if (hash == 0) {
            // Every component gets a non-zero hash, so that a zero unambiguously means "never computed".
            hash = component.componentName().hashCode();
        }
        if (hash == 0) {
            hash = 1;
        }
        component.setCodeHash(hash);
    }

    private static String generateComponentName(final String moduleName,
                                                final TypeScriptComponentModel declaration,
                                                final OOPSourceModelConstants.ComponentType componentType,
                                                final Stack<Component> stack) {
        String identifier = declaration.name;
        if (componentType.isMethodComponent()) {
            if ("function".equals(declaration.kind) && stack.isEmpty()) {
                identifier = declaration.name;
            } else if (declaration.signature != null && !declaration.signature.isEmpty()) {
                identifier = declaration.signature;
            }
        }
        if (!stack.isEmpty()) {
            return stack.peek().componentName() + "." + identifier;
        }
        return moduleName + "." + identifier;
    }

    private static String buildCodeFragment(final TypeScriptComponentModel declaration,
                                            final OOPSourceModelConstants.ComponentType componentType) {
        if (componentType.isMethodComponent()) {
            String fragment = declaration.signature;
            if (fragment == null || fragment.isEmpty()) {
                fragment = declaration.name;
            }
            if (declaration.returnType != null && !declaration.returnType.isEmpty()
                    && !"void".equals(declaration.returnType)) {
                fragment += " : " + declaration.returnType;
            }
            return fragment;
        }
        if (componentType == OOPSourceModelConstants.ComponentType.FIELD
                || componentType == OOPSourceModelConstants.ComponentType.MODULE_FIELD
                || componentType == OOPSourceModelConstants.ComponentType.LOCAL) {
            if (declaration.type == null || declaration.type.isEmpty()) {
                return null;
            }
            return declaration.name + " : " + declaration.type;
        }
        if (componentType == OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT
                || componentType == OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT) {
            return declaration.type;
        }
        if ((componentType == OOPSourceModelConstants.ComponentType.CLASS
                || componentType == OOPSourceModelConstants.ComponentType.INTERFACE
                || componentType == OOPSourceModelConstants.ComponentType.TYPE_ALIAS)
                && declaration.signature != null) {
            return declaration.signature;
        }
        return null;
    }

    private static OOPSourceModelConstants.ComponentType mapComponentType(final String kind,
                                                                          final Component parent) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case "class":
                return OOPSourceModelConstants.ComponentType.CLASS;
            case "interface":
                return OOPSourceModelConstants.ComponentType.INTERFACE;
            case "enum":
                return OOPSourceModelConstants.ComponentType.ENUM;
            case "function":
                return OOPSourceModelConstants.ComponentType.FUNCTION;
            case "method":
                return OOPSourceModelConstants.ComponentType.METHOD;
            case "constructor":
                return OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
            case "field":
                return OOPSourceModelConstants.ComponentType.FIELD;
            case "moduleField":
                return OOPSourceModelConstants.ComponentType.MODULE_FIELD;
            case "namespace":
                return OOPSourceModelConstants.ComponentType.NAMESPACE;
            case "typeAlias":
                return OOPSourceModelConstants.ComponentType.TYPE_ALIAS;
            case "enumMember":
                return OOPSourceModelConstants.ComponentType.ENUM_CONSTANT;
            case "parameter":
                if (parent != null && parent.componentType() == OOPSourceModelConstants.ComponentType.CONSTRUCTOR) {
                    return OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT;
                }
                return OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
            case "local":
                return OOPSourceModelConstants.ComponentType.LOCAL;
            default:
                return null;
        }
    }

    private static void attachReferences(final Component component,
                                         final TypeScriptComponentModel declaration,
                                         final String repoRoot,
                                         final Set<String> inRepositoryNames) {
        if (declaration.references == null) {
            return;
        }
        for (final TypeScriptReferenceModel reference : declaration.references) {
            final ComponentReference componentReference = buildComponentReference(reference, repoRoot);
            if (componentReference != null
                    && !componentReference.invokedComponent().equals(component.uniqueName())) {
                componentReference.setResolutionKind(resolutionOf(reference));
                if (inRepositoryNames != null && ((!reference.external && reference.target != null)
                        || reference.lost)) {
                    inRepositoryNames.add(componentReference.invokedComponent());
                }
                component.insertCmpRef(componentReference);
            }
        }
    }

    /** The resolution kind of a daemon reference: a repository target is exact, an external one as the daemon says. */
    private static ResolutionKind resolutionOf(final TypeScriptReferenceModel reference) {
        if (reference.lost) {
            return ResolutionKind.UNSPECIFIED;
        }
        if ("exact".equals(reference.resolution) || (!reference.external && reference.target != null)) {
            return ResolutionKind.EXACT;
        }
        if ("unresolved".equals(reference.resolution)) {
            return ResolutionKind.UNRESOLVED;
        }
        if ("ambiguous".equals(reference.resolution)) {
            return ResolutionKind.AMBIGUOUS;
        }
        return ResolutionKind.UNSPECIFIED;
    }

    private static ComponentReference buildComponentReference(final TypeScriptReferenceModel reference,
                                                              final String repoRoot) {
        final String invoked = resolveInvokedComponent(reference, repoRoot);
        if (invoked == null || invoked.isEmpty()) {
            return null;
        }
        if ("extends".equals(reference.kind)) {
            return new TypeExtensionReference(invoked);
        }
        if ("construction".equals(reference.kind)) {
            return new ConstructionReference(invoked);
        }
        if ("implements".equals(reference.kind)) {
            return new TypeImplementationReference(invoked);
        }
        if ("annotation".equals(reference.kind)) {
            final AnnotationReference annotation = new AnnotationReference(invoked);
            if (reference.arguments != null) {
                annotation.setArguments(decoratorArguments(reference.arguments, repoRoot));
            }
            return annotation;
        }
        if (reference.member != null) {
            return new MemberReference(invoked, reference.member, MemberKind.CALL, reference.externalUnit);
        }
        return new SimpleTypeReference(invoked);
    }

    private static java.util.Map<String, AnnotationArgument> decoratorArguments(
            final java.util.List<TypeScriptArgumentModel> arguments, final String repoRoot) {
        final AnnotationArguments collected = new AnnotationArguments();
        for (final TypeScriptArgumentModel argument : arguments) {
            final AnnotationArgument value = decoratorValue(argument.value, repoRoot);
            if (argument.name == null) {
                collected.unnamed(value);
            } else {
                collected.named(argument.name, value);
            }
        }
        return collected.build();
    }

    private static AnnotationArgument decoratorValue(final TypeScriptValueModel value, final String repoRoot) {
        if (value == null || value.kind == null) {
            return AnnotationArgument.unresolved();
        }
        switch (value.kind) {
            case "string":
                return AnnotationArgument.literal(AnnotationArgument.Kind.STRING, value.text);
            case "number":
                return AnnotationArgument.literal(AnnotationArgument.Kind.NUMBER, value.text);
            case "boolean":
                return AnnotationArgument.literal(AnnotationArgument.Kind.BOOLEAN, value.text);
            case "enum":
                return AnnotationArgument.literal(AnnotationArgument.Kind.ENUM, value.text);
            case "class":
                return decoratorClass(value, repoRoot);
            case "array":
                return decoratorArray(value, repoRoot);
            default:
                return AnnotationArgument.unresolved();
        }
    }

    private static AnnotationArgument decoratorClass(final TypeScriptValueModel value, final String repoRoot) {
        final String resolved = resolveUniqueName(repoRoot, value.target);
        if (resolved == null) {
            return AnnotationArgument.literal(AnnotationArgument.Kind.CLASS, value.text);
        }
        return AnnotationArgument.literal(AnnotationArgument.Kind.CLASS, resolved);
    }

    private static AnnotationArgument decoratorArray(final TypeScriptValueModel value, final String repoRoot) {
        final java.util.List<AnnotationArgument> elements = new java.util.ArrayList<>();
        for (final TypeScriptValueModel element : value.elements) {
            elements.add(decoratorValue(element, repoRoot));
        }
        return AnnotationArgument.array(elements);
    }

    private static String resolveInvokedComponent(final TypeScriptReferenceModel reference,
                                                  final String repoRoot) {
        if (reference == null) {
            return null;
        }
        if (!reference.external && reference.target != null) {
            return resolveUniqueName(repoRoot, reference.target);
        }
        return reference.displayName;
    }

    private static String resolveUniqueName(final String repoRoot, final TypeScriptTargetModel target) {
        if (target == null || target.filePath == null) {
            return null;
        }
        if (target.symbolName == null) {
            return CompilerSupport.resolveModuleUniqueName(repoRoot, target.filePath);
        }
        return CompilerSupport.resolveUniqueNameFromTarget(repoRoot, target.filePath, target.symbolName);
    }
}
