package com.hadi.clarpse.compiler;

import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared compiler utilities used across language implementations.
 */
public final class CompilerSupport {

    private CompilerSupport() {
    }

    public static void classifyReferences(final OOPSourceCodeModel srcModel) {
        classifyReferences(srcModel, null);
    }

    /**
     * Sorts every reference in the model into internal, external or not loaded.
     *
     * <p>A reference is internal when the model has a component by its name. Otherwise it is not
     * loaded when {@code declaredInRepository} says its target is declared in the repository, and
     * external when it is not. This is the only place a reference is put in the not-loaded state.
     *
     * @param srcModel             The model whose references to classify.
     * @param declaredInRepository Whether a reference's target is declared in the repository, or
     *                             {@code null} outside a one-level compile, when no reference is left
     *                             not loaded.
     */
    public static void classifyReferences(final OOPSourceCodeModel srcModel,
                                          final Predicate<ComponentReference> declaredInRepository) {
        srcModel.components().forEach(component -> {
            final Set<ComponentReference> internalReferences = new LinkedHashSet<>();
            final Set<ComponentReference> externalReferences = new LinkedHashSet<>();
            final Set<ComponentReference> notLoadedReferences = new LinkedHashSet<>();
            component.references().forEach(componentReference -> {
                final String target = componentReference.invokedComponent();
                if (srcModel.containsComponent(target)) {
                    internalReferences.add(componentReference);
                } else if (declaredInRepository != null && declaredInRepository.test(componentReference)) {
                    notLoadedReferences.add(componentReference);
                } else {
                    externalReferences.add(componentReference);
                }
            });
            component.setReferenceClassification(internalReferences, externalReferences,
                    notLoadedReferences);
        });
    }

    /**
     * Marks as boundary every component declared in one of the given files. This is the only place
     * a component is marked boundary.
     *
     * @param srcModel      The model of a one-level compile.
     * @param levelOneFiles The level-one files the compile modelled.
     */
    public static void markBoundary(final OOPSourceCodeModel srcModel,
                                    final Collection<String> levelOneFiles) {
        final Set<String> boundaryFiles = new HashSet<>();
        for (final String path : levelOneFiles) {
            boundaryFiles.add(ClarpseCompiler.normalizeForComparison(path));
        }
        srcModel.components().forEach(component -> {
            final String sourceFile = component.sourceFile();
            if (sourceFile != null
                    && boundaryFiles.contains(ClarpseCompiler.normalizeForComparison(sourceFile))) {
                component.setBoundary(true);
            }
        });
    }

    /**
     * The report of a one-level compile whose model is final.
     *
     * @param srcModel             The classified model.
     * @param selection            The level-one selection the compile modelled.
     * @param loadedBeyondLevelOne Files read beyond level one to resolve the analysed files.
     * @return The report.
     */
    public static LevelOneReport levelOneReport(final OOPSourceCodeModel srcModel,
                                                final LevelOneSelection selection,
                                                final Collection<String> loadedBeyondLevelOne) {
        final int notLoaded = srcModel.components()
                .mapToInt(component -> component.notLoadedDependencies().size())
                .sum();
        List<String> beyond = List.of();
        if (loadedBeyondLevelOne != null) {
            beyond = List.copyOf(loadedBeyondLevelOne);
        }
        return new LevelOneReport(selection.modelledPaths(), selection.heldByBudget(), beyond, notLoaded);
    }

    public static void classifyClassCyclo(final OOPSourceCodeModel srcModel,
                                          final Collection<OOPSourceModelConstants.ComponentType> types) {
        srcModel.components().forEach(component -> {
            if (types.contains(component.componentType())) {
                component.setCyclo(ParseUtil.calculateClassCyclo(component, srcModel));
            }
        });
    }

    public static boolean isAbsolutePath(final String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            return true;
        }
        return path.length() > 2
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && (path.charAt(2) == '/' || path.charAt(2) == '\\');
    }

    public static String resolveFileOnDisk(final String repoRoot, final String originalPath) {
        if (originalPath == null || originalPath.isEmpty()) {
            return originalPath;
        }
        if (repoRoot == null || repoRoot.isEmpty()) {
            return originalPath;
        }
        final String normalizedPath = originalPath.replace('\\', File.separatorChar);
        final java.nio.file.Path repoPath = Paths.get(repoRoot).toAbsolutePath().normalize();

        if (isAbsolutePath(normalizedPath)) {
            final java.nio.file.Path absolutePath = Paths.get(normalizedPath).toAbsolutePath().normalize();
            if (absolutePath.startsWith(repoPath)) {
                return absolutePath.toString();
            }
            // Never trust absolute paths outside repoRoot. Rebase them under the persisted repo directory.
            return rebaseToRepoRoot(repoPath, normalizedPath);
        }
        return rebaseToRepoRoot(repoPath, normalizedPath);
    }

    private static String rebaseToRepoRoot(final java.nio.file.Path repoPath, final String path) {
        String relative = path;
        if (relative.length() > 2
                && Character.isLetter(relative.charAt(0))
                && relative.charAt(1) == ':') {
            relative = relative.substring(2);
        }
        relative = stripLeadingFileSeparators(relative);
        return repoPath.resolve(relative).normalize().toString();
    }

    private static String stripLeadingFileSeparators(final String path) {
        String result = path;
        while (result.startsWith("/") || result.startsWith("\\")) {
            result = result.substring(1);
        }
        return result;
    }

    public static String moduleNameForFile(final String filePath) {
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

    public static String normalizeSlashes(final String path) {
        if (path == null) {
            return null;
        }
        return path.replace(File.separatorChar, '/');
    }

    public static String stripLeadingSlashes(final String path) {
        if (path == null) {
            return null;
        }
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    public static String componentNameFromUniqueName(final String packageName, final String uniqueName) {
        if (uniqueName == null) {
            return "";
        }
        if (packageName == null || packageName.isEmpty()) {
            return uniqueName;
        }
        final String prefix = packageName + ".";
        if (uniqueName.startsWith(prefix)) {
            return uniqueName.substring(prefix.length());
        }
        return uniqueName;
    }

    public static String uniqueNameForMember(final String ownerUniqueName, final String memberName) {
        if (ownerUniqueName == null || ownerUniqueName.isEmpty()) {
            if (memberName == null) {
                return "";
            }
            return memberName;
        }
        if (memberName == null || memberName.isEmpty()) {
            return ownerUniqueName;
        }
        return ownerUniqueName + "." + memberName;
    }

    public static String resolvePackagePath(final String repoRoot, final String filePath) {
        if (filePath == null) {
            return "";
        }
        java.nio.file.Path file = Paths.get(filePath).toAbsolutePath().normalize();
        java.nio.file.Path parent = file.getParent();
        if (parent == null) {
            return "";
        }
        String pkgPath;
        try {
            final java.nio.file.Path root = Paths.get(repoRoot).toAbsolutePath().normalize();
            pkgPath = root.relativize(parent).toString();
            if (pkgPath.startsWith("..")) {
                // A resolver may report the file under the root's real path, where the root is
                // reached through a symbolic link; relative to that, the file is inside the root.
                pkgPath = root.toRealPath().relativize(parent.toRealPath()).toString();
            }
        } catch (final Exception e) {
            pkgPath = parent.toString();
        }
        pkgPath = normalizeSlashes(pkgPath);
        return stripLeadingSlashes(pkgPath);
    }

    /**
     * The unique name of a whole module, a source file referenced as a namespace rather than
     * through one of its declarations: its package path and module name, as the unique names of the
     * declarations in it start.
     *
     * @param repoRoot       The repository root the file is read under.
     * @param targetFilePath The module's file.
     * @return The name, or {@code null} when there is no file.
     */
    public static String resolveModuleUniqueName(final String repoRoot, final String targetFilePath) {
        if (targetFilePath == null) {
            return null;
        }
        final String pkgPath = resolvePackagePath(repoRoot, targetFilePath);
        final String moduleName = moduleNameForFile(targetFilePath);
        if (pkgPath.isEmpty()) {
            return moduleName;
        }
        return pkgPath.replace('/', '.') + "." + moduleName;
    }

    public static String resolveUniqueNameFromTarget(final String repoRoot,
                                                     final String targetFilePath,
                                                     final String symbolName) {
        if (targetFilePath == null || symbolName == null) {
            return null;
        }
        final String pkgPath = resolvePackagePath(repoRoot, targetFilePath);
        final String moduleName = moduleNameForFile(targetFilePath);
        if (pkgPath.isEmpty()) {
            return moduleName + "." + symbolName;
        }
        return pkgPath.replace('/', '.') + "." + moduleName + "." + symbolName;
    }
}
