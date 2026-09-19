package com.hadi.test;

import com.hadi.clarpse.compiler.CompilerSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompilerSupportTest {

    /** Holds every directory and file these tests create, and deletes them after each test. */
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void absolutePathOutsideRepoRootIsRebasedUnderRepoRoot() throws Exception {
        Path repoRoot = temp.newFolder("repo").toPath();
        Path hostFile = temp.newFile("host.py").toPath();

        String resolved = CompilerSupport.resolveFileOnDisk(repoRoot.toString(), hostFile.toString());
        Path resolvedPath = Path.of(resolved).toAbsolutePath().normalize();

        assertTrue(resolvedPath.startsWith(repoRoot.toAbsolutePath().normalize()));
        assertFalse(resolvedPath.equals(hostFile.toAbsolutePath().normalize()));
    }

    @Test
    public void absolutePathInsideRepoRootIsPreserved() throws Exception {
        Path repoRoot = temp.newFolder("repo").toPath();
        Path repoFile = repoRoot.resolve("src").resolve("main").resolve("App.ts");
        Files.createDirectories(repoFile.getParent());
        Files.writeString(repoFile, "export const app = true;");

        String resolved = CompilerSupport.resolveFileOnDisk(repoRoot.toString(), repoFile.toString());
        assertEquals(repoFile.toAbsolutePath().normalize().toString(), resolved);
    }

    @Test
    public void slashPrefixedPathResolvesUnderRepoRoot() throws Exception {
        Path repoRoot = temp.newFolder("repo").toPath();
        String resolved = CompilerSupport.resolveFileOnDisk(repoRoot.toString(), "/src/pkg/mod.py");
        String expected = repoRoot.resolve("src").resolve("pkg").resolve("mod.py").normalize().toString();
        assertEquals(expected, resolved);
    }

    @Test
    public void aFileReportedUnderTheRealPathOfALinkedRootIsInsideTheRoot() throws Exception {
        final Path real = temp.newFolder("real").toPath();
        final Path file = Files.createDirectories(real.resolve("src/app")).resolve("dom.ts");
        Files.writeString(file, "export class Foo { }\n");
        final Path link = Files.createSymbolicLink(temp.getRoot().toPath().resolve("link"), real);
        final String reported = file.toRealPath().toString();
        assertEquals("src/app", CompilerSupport.resolvePackagePath(link.toString(), reported));
        assertEquals("src.app.dom.Foo", CompilerSupport.resolveUniqueNameFromTarget(link.toString(), reported, "Foo"));
        assertEquals("src.app.dom", CompilerSupport.resolveModuleUniqueName(link.toString(), reported));
    }

    @Test
    public void aModuleAtTheRootIsNamedByItsModuleNameAlone() throws Exception {
        final Path root = temp.newFolder("root").toPath();
        final Path file = root.resolve("index.ts");
        Files.writeString(file, "export {};\n");
        assertEquals("index", CompilerSupport.resolveModuleUniqueName(root.toString(), file.toString()));
        assertEquals(null, CompilerSupport.resolveModuleUniqueName(root.toString(), null));
    }
}
