package com.hadi.test;

import com.hadi.clarpse.compiler.CompilerSupport;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompilerSupportTest {

    @Test
    public void absolutePathOutsideRepoRootIsRebasedUnderRepoRoot() throws Exception {
        Path repoRoot = Files.createTempDirectory("clarpse-repo");
        Path hostFile = Files.createTempFile("clarpse-host", ".py");

        String resolved = CompilerSupport.resolveFileOnDisk(repoRoot.toString(), hostFile.toString());
        Path resolvedPath = Path.of(resolved).toAbsolutePath().normalize();

        assertTrue(resolvedPath.startsWith(repoRoot.toAbsolutePath().normalize()));
        assertFalse(resolvedPath.equals(hostFile.toAbsolutePath().normalize()));
    }

    @Test
    public void absolutePathInsideRepoRootIsPreserved() throws Exception {
        Path repoRoot = Files.createTempDirectory("clarpse-repo");
        Path repoFile = repoRoot.resolve("src").resolve("main").resolve("App.ts");
        Files.createDirectories(repoFile.getParent());
        Files.writeString(repoFile, "export const app = true;");

        String resolved = CompilerSupport.resolveFileOnDisk(repoRoot.toString(), repoFile.toString());
        assertEquals(repoFile.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    public void slashPrefixedPathResolvesUnderRepoRoot() throws Exception {
        Path repoRoot = Files.createTempDirectory("clarpse-repo");
        String resolved = CompilerSupport.resolveFileOnDisk(repoRoot.toString(), "/src/pkg/mod.py");
        String expected = repoRoot.resolve("src").resolve("pkg").resolve("mod.py").normalize().toString();
        assertEquals(expected, resolved);
    }
}
