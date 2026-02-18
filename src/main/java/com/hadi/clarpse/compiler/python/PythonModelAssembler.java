package com.hadi.clarpse.compiler.python;

import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.python.model.PythonClassModel;
import com.hadi.clarpse.compiler.python.model.PythonFieldModel;
import com.hadi.clarpse.compiler.python.model.PythonFileModel;
import com.hadi.clarpse.compiler.python.model.PythonMethodModel;
import com.hadi.clarpse.compiler.python.model.PythonParamModel;
import com.hadi.clarpse.compiler.python.model.PythonTypeRefModel;
import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;

import java.util.Set;
import java.util.Stack;

/**
 * Maps Python daemon models into Clarpse components and references.
 */
final class PythonModelAssembler {

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".venv",
            "venv",
            "__pycache__",
            ".tox",
            "build",
            "dist",
            "node_modules",
            ".mypy_cache",
            ".pytest_cache"
    );

    private PythonModelAssembler() {
    }

    static boolean shouldSkipPath(final String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        String normalized = filePath.replace('\\', '/');
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (EXCLUDED_DIRS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    static Package resolvePackage(final PythonFileModel fileModel) {
        if (fileModel == null || fileModel.packageName == null || fileModel.packageName.isEmpty()) {
            return new Package("", "");
        }
        String pkgPath = fileModel.packageName.replace('.', '/');
        if (pkgPath.startsWith("/")) {
            pkgPath = pkgPath.substring(1);
        }
        return new Package(pkgPath, pkgPath);
    }

    static void insertFileModel(final Package pkg,
                                final String moduleName,
                                final String sourcePath,
                                final PythonFileModel fileModel,
                                final OOPSourceCodeModel srcModel) {
        if (fileModel == null) {
            return;
        }
        final Stack<Component> stack = new Stack<>();
        if (fileModel.moduleFields != null) {
            for (final PythonFieldModel moduleField : fileModel.moduleFields) {
                final Component fieldComponent = buildModuleFieldComponent(pkg, moduleName, sourcePath,
                        fileModel.packageName, moduleField);
                insertComponent(fieldComponent, stack, srcModel, null);
            }
        }
        if (fileModel.functions != null) {
            for (final PythonMethodModel function : fileModel.functions) {
                final Component functionComponent = buildFunctionComponent(pkg, moduleName, sourcePath,
                        fileModel.packageName, function);
                insertComponent(functionComponent, stack, srcModel, () -> {
                    if (function.params != null) {
                        for (final PythonParamModel param : function.params) {
                            final Component paramComponent = buildFunctionParamComponent(pkg, moduleName, sourcePath,
                                    fileModel.packageName, function, param);
                            insertComponent(paramComponent, stack, srcModel, null);
                        }
                    }
                });
            }
        }
        if (fileModel.classes != null) {
            for (final PythonClassModel classModel : fileModel.classes) {
                insertClass(pkg, moduleName, sourcePath, fileModel.packageName, classModel, stack, srcModel);
            }
        }
    }

    private static void insertClass(final Package pkg,
                                    final String moduleName,
                                    final String sourcePath,
                                    final String packageName,
                                    final PythonClassModel classModel,
                                    final Stack<Component> stack,
                                    final OOPSourceCodeModel srcModel) {
        final Component classComponent = buildClassComponent(pkg, moduleName, sourcePath, packageName, classModel);
        insertComponent(classComponent, stack, srcModel, () -> {
            if (classModel.fields != null) {
                for (final PythonFieldModel field : classModel.fields) {
                    final Component fieldComponent = buildFieldComponent(pkg, moduleName, sourcePath,
                            packageName, classModel, field);
                    insertComponent(fieldComponent, stack, srcModel, null);
                }
            }
            if (classModel.methods != null) {
                for (final PythonMethodModel method : classModel.methods) {
                    final Component methodComponent = buildMethodComponent(pkg, moduleName, sourcePath,
                            packageName, classModel, method);
                    insertComponent(methodComponent, stack, srcModel, () -> {
                        if (method.params != null) {
                            for (final PythonParamModel param : method.params) {
                                final Component paramComponent = buildParamComponent(pkg, moduleName, sourcePath,
                                        packageName, classModel, method, param);
                                insertComponent(paramComponent, stack, srcModel, null);
                            }
                        }
                    });
                }
            }
        });
    }

    private static void insertComponent(final Component component,
                                        final Stack<Component> stack,
                                        final OOPSourceCodeModel srcModel,
                                        final Runnable childrenBuilder) {
        if (component == null) {
            return;
        }
        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        if (childrenBuilder != null) {
            childrenBuilder.run();
        }
        srcModel.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private static Component buildClassComponent(final Package pkg,
                                                 final String moduleName,
                                                 final String sourcePath,
                                                 final String packageName,
                                                 final PythonClassModel classModel) {
        if (classModel == null || classModel.className == null || classModel.className.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        component.setComponentType(OOPSourceModelConstants.ComponentType.CLASS);
        component.setName(classModel.className);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, classModel.uniqueName));
        component.setSourceFilePath(sourcePath);
        if (classModel.bases != null) {
            for (final PythonTypeRefModel base : classModel.bases) {
                final ComponentReference ref = buildReference(base, true);
                if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
                    component.insertCmpRef(ref);
                }
            }
        }
        return component;
    }

    private static Component buildFieldComponent(final Package pkg,
                                                 final String moduleName,
                                                 final String sourcePath,
                                                 final String packageName,
                                                 final PythonClassModel classModel,
                                                 final PythonFieldModel field) {
        if (field == null || field.name == null || field.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        component.setComponentType(OOPSourceModelConstants.ComponentType.FIELD);
        component.setName(field.name);
        final String fieldUniqueName = CompilerSupport.uniqueNameForMember(classModel.uniqueName, field.name);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, fieldUniqueName));
        component.setSourceFilePath(sourcePath);
        if (field.rawType != null && !field.rawType.isEmpty()) {
            component.setCodeFragment(field.name + " : " + field.rawType);
        }
        final PythonTypeRefModel typeRef = new PythonTypeRefModel();
        typeRef.raw = field.rawType;
        typeRef.targetUniqueName = field.targetUniqueName;
        typeRef.externalLabel = field.externalLabel;
        final ComponentReference ref = buildReference(typeRef, false);
        if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
            component.insertCmpRef(ref);
        }
        return component;
    }

    private static Component buildModuleFieldComponent(final Package pkg,
                                                       final String moduleName,
                                                       final String sourcePath,
                                                       final String packageName,
                                                       final PythonFieldModel field) {
        if (field == null || field.name == null || field.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        component.setComponentType(OOPSourceModelConstants.ComponentType.MODULE_FIELD);
        component.setName(field.name);
        final String base;
        if (packageName == null || packageName.isEmpty()) {
            base = moduleName;
        } else {
            base = packageName + "." + moduleName;
        }
        final String fieldUniqueName = CompilerSupport.uniqueNameForMember(base, field.name);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, fieldUniqueName));
        component.setSourceFilePath(sourcePath);
        if (field.rawType != null && !field.rawType.isEmpty()) {
            component.setCodeFragment(field.name + " : " + field.rawType);
        }
        final PythonTypeRefModel typeRef = new PythonTypeRefModel();
        typeRef.raw = field.rawType;
        typeRef.targetUniqueName = field.targetUniqueName;
        typeRef.externalLabel = field.externalLabel;
        final ComponentReference ref = buildReference(typeRef, false);
        if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
            component.insertCmpRef(ref);
        }
        return component;
    }

    private static Component buildMethodComponent(final Package pkg,
                                                  final String moduleName,
                                                  final String sourcePath,
                                                  final String packageName,
                                                  final PythonClassModel classModel,
                                                  final PythonMethodModel method) {
        if (method == null || method.name == null || method.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        final OOPSourceModelConstants.ComponentType methodType;
        if (isConstructor(method)) {
            methodType = OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
        } else {
            methodType = OOPSourceModelConstants.ComponentType.METHOD;
        }
        component.setComponentType(methodType);
        component.setName(method.name);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, method.uniqueName));
        component.setSourceFilePath(sourcePath);
        if (method.signature != null && !method.signature.isEmpty()) {
            component.setCodeFragment(method.signature);
        }
        if (method.returnType != null) {
            final ComponentReference ref = buildReference(method.returnType, false);
            if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
                component.insertCmpRef(ref);
            }
        }
        return component;
    }

    private static Component buildFunctionComponent(final Package pkg,
                                                    final String moduleName,
                                                    final String sourcePath,
                                                    final String packageName,
                                                    final PythonMethodModel function) {
        if (function == null || function.name == null || function.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        component.setComponentType(OOPSourceModelConstants.ComponentType.FUNCTION);
        component.setName(function.name);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, function.uniqueName));
        component.setSourceFilePath(sourcePath);
        if (function.signature != null && !function.signature.isEmpty()) {
            component.setCodeFragment(function.signature);
        }
        if (function.returnType != null) {
            final ComponentReference ref = buildReference(function.returnType, false);
            if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
                component.insertCmpRef(ref);
            }
        }
        return component;
    }

    private static Component buildParamComponent(final Package pkg,
                                                 final String moduleName,
                                                 final String sourcePath,
                                                 final String packageName,
                                                 final PythonClassModel classModel,
                                                 final PythonMethodModel method,
                                                 final PythonParamModel param) {
        if (param == null || param.name == null || param.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        final OOPSourceModelConstants.ComponentType paramType;
        if (isConstructor(method)) {
            paramType = OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT;
        } else {
            paramType = OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
        }
        component.setComponentType(paramType);
        component.setName(param.name);
        final String paramUniqueName = CompilerSupport.uniqueNameForMember(method.uniqueName, param.name);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, paramUniqueName));
        component.setSourceFilePath(sourcePath);
        if (param.rawType != null && !param.rawType.isEmpty()) {
            component.setCodeFragment(param.rawType);
        }
        final PythonTypeRefModel typeRef = new PythonTypeRefModel();
        typeRef.raw = param.rawType;
        typeRef.targetUniqueName = param.targetUniqueName;
        typeRef.externalLabel = param.externalLabel;
        final ComponentReference ref = buildReference(typeRef, false);
        if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
            component.insertCmpRef(ref);
        }
        return component;
    }

    private static Component buildFunctionParamComponent(final Package pkg,
                                                         final String moduleName,
                                                         final String sourcePath,
                                                         final String packageName,
                                                         final PythonMethodModel function,
                                                         final PythonParamModel param) {
        if (param == null || param.name == null || param.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
        component.setModule(moduleName);
        component.setComponentType(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT);
        component.setName(param.name);
        final String paramUniqueName = CompilerSupport.uniqueNameForMember(function.uniqueName, param.name);
        component.setComponentName(CompilerSupport.componentNameFromUniqueName(packageName, paramUniqueName));
        component.setSourceFilePath(sourcePath);
        if (param.rawType != null && !param.rawType.isEmpty()) {
            component.setCodeFragment(param.rawType);
        }
        final PythonTypeRefModel typeRef = new PythonTypeRefModel();
        typeRef.raw = param.rawType;
        typeRef.targetUniqueName = param.targetUniqueName;
        typeRef.externalLabel = param.externalLabel;
        final ComponentReference ref = buildReference(typeRef, false);
        if (ref != null && !ref.invokedComponent().equals(component.uniqueName())) {
            component.insertCmpRef(ref);
        }
        return component;
    }

    private static boolean isConstructor(final PythonMethodModel method) {
        return method != null && "__init__".equals(method.name);
    }

    private static ComponentReference buildReference(final PythonTypeRefModel ref,
                                                     final boolean isExtends) {
        if (ref == null) {
            return null;
        }
        String invoked = null;
        if (ref.targetUniqueName != null && !ref.targetUniqueName.isEmpty()) {
            invoked = ref.targetUniqueName;
        } else if (ref.externalLabel != null && !ref.externalLabel.isEmpty()) {
            invoked = ref.externalLabel;
        } else if (ref.raw != null && !ref.raw.isEmpty()) {
            invoked = ref.raw;
        }
        if (invoked == null || invoked.isEmpty()) {
            return null;
        }
        if (isExtends) {
            return new TypeExtensionReference(invoked);
        }
        return new SimpleTypeReference(invoked);
    }
}
