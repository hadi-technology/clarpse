package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.Assume;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class TypeScriptTestUtil {

    private TypeScriptTestUtil() {
    }

    public static Path fixturePath(final String fixtureName) {
        return Paths.get("src/test/resources/typescript", fixtureName).toAbsolutePath();
    }

    public static ProjectFiles loadProject(final String fixtureName) throws Exception {
        return new ProjectFiles(fixturePath(fixtureName).toString());
    }

    public static CompileResult compileFixture(final String fixtureName) throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = loadProject(fixtureName);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
        if (!result.skipped().isEmpty()) {
            Assume.assumeTrue("TypeScript resolver unavailable.", false);
        }
        return result;
    }

    public static String signature(final String name, final String... paramTypes) {
        if (paramTypes == null || paramTypes.length == 0) {
            return name + "()";
        }
        return name + "(" + String.join(", ", paramTypes) + ")";
    }

    public static String uniqueName(final String packagePath, final String moduleName, final String symbolPath) {
        final String pkgPrefix = packagePrefix(packagePath);
        if (pkgPrefix.isEmpty()) {
            return moduleName + "." + symbolPath;
        }
        return pkgPrefix + "." + moduleName + "." + symbolPath;
    }

    public static String packagePrefix(final String packagePath) {
        if (packagePath == null || packagePath.isEmpty()) {
            return "";
        }
        String normalized = packagePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.replace('/', '.');
    }
}
