package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.SkipReason;
import com.hadi.clarpse.compiler.SkippedFile;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;

/**
 * TypeScript compiler stub that enforces the Node-backed failure contract.
 */
public class ClarpseTypeScriptCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseTypeScriptCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles) throws CompileException {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<ProjectFile> compileFailures = new HashSet<>();
        final Set<SkippedFile> skippedFiles = new HashSet<>();
        final List<ProjectFile> tsFiles = new ArrayList<>(projectFiles.files(Lang.TYPESCRIPT));

        if (tsFiles.isEmpty()) {
            return new CompileResult(srcModel, compileFailures, skippedFiles);
        }

        if (!NodeRuntime.isNodeAvailable()) {
            tsFiles.forEach(file -> skippedFiles.add(new SkippedFile(file, SkipReason.NODE_NOT_FOUND)));
            LOGGER.warn("Node.js not found. Skipping " + tsFiles.size() + " TypeScript files.");
            return new CompileResult(srcModel, compileFailures, skippedFiles);
        }

        String persistDir = null;
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            persistDir = projectFiles.projectDir();
            daemon.start();
            daemon.initRepo(persistDir);
            for (final ProjectFile file : tsFiles) {
                final String diskPath = resolveFileOnDisk(persistDir, file.path());
                final TypeScriptFileModel fileModel;
                try {
                    fileModel = daemon.getFileModel(diskPath);
                } catch (final TypeScriptDaemonException e) {
                    skippedFiles.add(new SkippedFile(file, SkipReason.RESOLVER_START_FAILED));
                    LOGGER.warn("TypeScript resolver failed for file " + file.path() + ".", e);
                    continue;
                }
                final Package pkg = resolvePackage(persistDir, diskPath);
                final String moduleName = moduleNameForFile(diskPath);
                for (final TypeScriptComponentModel declaration : fileModel.declarations) {
                    insertComponentTree(pkg, moduleName, file.path(), persistDir, declaration, srcModel);
                }
            }
            classifyClassCyclo(srcModel);
            classifyReferences(srcModel);
        } catch (final TypeScriptDaemonException e) {
            tsFiles.forEach(file -> skippedFiles.add(new SkippedFile(file, SkipReason.RESOLVER_START_FAILED)));
            LOGGER.warn("TypeScript resolver failed to start. Skipping " + tsFiles.size() + " TypeScript files.", e);
        } finally {
            if (persistDir != null && !persistDir.isEmpty() && projectFiles.isTempProjectDir()) {
                FileUtils.deleteQuietly(new File(persistDir));
            }
        }
        return new CompileResult(srcModel, compileFailures, skippedFiles);
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
        final OOPSourceModelConstants.ComponentType componentType =
                mapComponentType(declaration.kind, stack.isEmpty() ? null : stack.peek());
        if (componentType == null || declaration.name == null || declaration.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(pkg);
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
        if (componentType.isMethodComponent() && declaration.signature != null && !declaration.signature.isEmpty()) {
            identifier = declaration.signature;
        }
        if (!stack.isEmpty()) {
            return stack.peek().componentName() + "." + identifier;
        }
        return moduleName + "." + identifier;
    }

    private static String buildCodeFragment(final TypeScriptComponentModel declaration,
                                            final OOPSourceModelConstants.ComponentType componentType) {
        if (componentType.isMethodComponent()) {
            String fragment = declaration.signature != null ? declaration.signature : declaration.name;
            if (declaration.returnType != null && !declaration.returnType.isEmpty()
                    && !"void".equals(declaration.returnType)) {
                fragment += " : " + declaration.returnType;
            }
            return fragment;
        }
        if (componentType == OOPSourceModelConstants.ComponentType.FIELD
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
                return OOPSourceModelConstants.ComponentType.METHOD;
            case "method":
                return OOPSourceModelConstants.ComponentType.METHOD;
            case "constructor":
                return OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
            case "field":
                return OOPSourceModelConstants.ComponentType.FIELD;
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

    private static void classifyClassCyclo(final OOPSourceCodeModel srcModel) {
        srcModel.components().forEach(component -> {
            if (component.componentType() == OOPSourceModelConstants.ComponentType.CLASS
                    || component.componentType() == OOPSourceModelConstants.ComponentType.ENUM) {
                component.setCyclo(ParseUtil.calculateClassCyclo(component, srcModel));
            }
        });
    }

    private static void attachReferences(final Component component,
                                         final TypeScriptComponentModel declaration,
                                         final String repoRoot) {
        if (declaration.references == null) {
            return;
        }
        for (final TypeScriptReferenceModel reference : declaration.references) {
            final ComponentReference componentReference = buildComponentReference(reference, repoRoot);
            if (componentReference != null) {
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
        final String pkgPath = resolvePackagePath(repoRoot, target.filePath);
        final String moduleName = moduleNameForFile(target.filePath);
        if (pkgPath.isEmpty()) {
            return moduleName + "." + target.symbolName;
        }
        return pkgPath.replace('/', '.')
                + "." + moduleName + "." + target.symbolName;
    }

    private static String resolvePackagePath(final String repoRoot, final String filePath) {
        if (filePath == null) {
            return "";
        }
        Path file = Paths.get(filePath).toAbsolutePath();
        Path parent = file.getParent();
        if (parent == null) {
            return "";
        }
        String pkgPath;
        try {
            Path root = Paths.get(repoRoot).toAbsolutePath();
            pkgPath = root.relativize(parent).toString();
        } catch (final Exception e) {
            pkgPath = parent.toString();
        }
        pkgPath = pkgPath.replace(File.separatorChar, '/');
        if (pkgPath.startsWith("/")) {
            pkgPath = pkgPath.substring(1);
        }
        return pkgPath;
    }

    private static void classifyReferences(final OOPSourceCodeModel srcModel) {
        srcModel.components().forEach(component -> {
            final Set<ComponentReference> internalReferences = new HashSet<>();
            final Set<ComponentReference> externalReferences = new HashSet<>();
            component.references().forEach(componentReference -> {
                final boolean isInternal = srcModel.containsComponent(componentReference.invokedComponent());
                componentReference.setExternal(!isInternal);
                if (isInternal) {
                    internalReferences.add(componentReference);
                } else {
                    externalReferences.add(componentReference);
                }
            });
            component.setReferenceClassification(internalReferences, externalReferences);
        });
    }

    private static String resolveFileOnDisk(final String repoRoot, final String originalPath) {
        if (originalPath == null || originalPath.isEmpty()) {
            return originalPath;
        }
        if (repoRoot != null && !repoRoot.isEmpty() && originalPath.startsWith(repoRoot)) {
            return originalPath;
        }
        if (originalPath.startsWith(File.separator)) {
            return repoRoot + originalPath;
        }
        return repoRoot + File.separator + originalPath;
    }

    private static Package resolvePackage(final String repoRoot, final String filePath) {
        if (filePath == null) {
            return new Package("", "");
        }
        Path file = Paths.get(filePath).toAbsolutePath();
        Path parent = file.getParent();
        if (parent == null) {
            return new Package("", "");
        }
        String pkgPath;
        try {
            Path root = Paths.get(repoRoot).toAbsolutePath();
            Path relative = root.relativize(parent);
            pkgPath = relative.toString();
        } catch (final Exception e) {
            pkgPath = parent.toString();
        }
        pkgPath = pkgPath.replace(File.separatorChar, '/');
        if (pkgPath.startsWith("/")) {
            pkgPath = pkgPath.substring(1);
        }
        return new Package(pkgPath, pkgPath);
    }

    private static String moduleNameForFile(final String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        String fileName = Paths.get(filePath).getFileName().toString();
        int extIndex = fileName.lastIndexOf('.');
        if (extIndex > 0) {
            fileName = fileName.substring(0, extIndex);
        }
        return fileName;
    }
}
