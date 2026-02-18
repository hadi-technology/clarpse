package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptComponentModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptFileModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptReferenceModel;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptTargetModel;
import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;

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
        if (fileModel == null || fileModel.declarations == null) {
            return;
        }
        for (final TypeScriptComponentModel declaration : fileModel.declarations) {
            insertComponentTree(pkg, moduleName, sourcePath, repoRoot, declaration, srcModel);
        }
    }

    private static void insertComponentTree(final Package pkg,
                                            final String moduleName,
                                            final String sourcePath,
                                            final String repoRoot,
                                            final TypeScriptComponentModel declaration,
                                            final OOPSourceCodeModel srcModel) {
        final Stack<Component> stack = new Stack<>();
        insertComponentTree(pkg, moduleName, sourcePath, repoRoot, declaration, stack, srcModel);
    }

    private static void insertComponentTree(final Package pkg,
                                            final String moduleName,
                                            final String sourcePath,
                                            final String repoRoot,
                                            final TypeScriptComponentModel declaration,
                                            final Stack<Component> stack,
                                            final OOPSourceCodeModel srcModel) {
        final Component component = buildComponent(pkg, moduleName, sourcePath, repoRoot, declaration, stack);
        if (component == null) {
            return;
        }
        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        for (final TypeScriptComponentModel member : declaration.members) {
            insertComponentTree(pkg, moduleName, sourcePath, repoRoot, member, stack, srcModel);
        }
        if (component.componentType().isMethodComponent() && declaration.cyclo > 0) {
            component.setCyclo(declaration.cyclo);
        }
        srcModel.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private static Component buildComponent(final Package pkg,
                                            final String moduleName,
                                            final String sourcePath,
                                            final String repoRoot,
                                            final TypeScriptComponentModel declaration,
                                            final Stack<Component> stack) {
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
        if (codeFragment != null && !codeFragment.isEmpty()) {
            component.setCodeFragment(codeFragment);
            component.setCodeHash(codeFragment.hashCode());
        }
        attachReferences(component, declaration, repoRoot);
        return component;
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
                || componentType == OOPSourceModelConstants.ComponentType.INTERFACE)
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
                                         final String repoRoot) {
        if (declaration.references == null) {
            return;
        }
        for (final TypeScriptReferenceModel reference : declaration.references) {
            final ComponentReference componentReference = buildComponentReference(reference, repoRoot);
            if (componentReference != null
                    && !componentReference.invokedComponent().equals(component.uniqueName())) {
                component.insertCmpRef(componentReference);
            }
        }
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
        if ("implements".equals(reference.kind)) {
            return new TypeImplementationReference(invoked);
        }
        return new SimpleTypeReference(invoked);
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
        if (target == null || target.filePath == null || target.symbolName == null) {
            return null;
        }
        return CompilerSupport.resolveUniqueNameFromTarget(repoRoot, target.filePath, target.symbolName);
    }
}
