package com.hadi.clarpse.compiler;

import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared compiler utilities used across language implementations.
 */
public final class CompilerSupport {

    private CompilerSupport() {
    }

    public static void classifyReferences(final OOPSourceCodeModel srcModel) {
        srcModel.components().forEach(component -> {
            final Set<ComponentReference> internalReferences = new LinkedHashSet<>();
            final Set<ComponentReference> externalReferences = new LinkedHashSet<>();
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
            java.nio.file.Path root = Paths.get(repoRoot).toAbsolutePath().normalize();
            pkgPath = root.relativize(parent).toString();
        } catch (final Exception e) {
            pkgPath = parent.toString();
        }
        pkgPath = normalizeSlashes(pkgPath);
        return stripLeadingSlashes(pkgPath);
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
