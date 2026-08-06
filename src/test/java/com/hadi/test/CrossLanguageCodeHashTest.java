package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code codeHash} is the model's change-detection primitive: two parses of the same source must agree,
 * an implementation edit must not, and no component may be left without a hash. Asserted for every
 * supported language, since the four compilers derive it independently.
 */
public class CrossLanguageCodeHashTest {

    private static final String JAVA_V1 = "package app;\npublic class Svc {\n  public int size() { return 0; }\n}\n";
    private static final String JAVA_V2 = "package app;\npublic class Svc {\n  public int size() { return 42; }\n}\n";
    private static final String CSHARP_V1 =
            "namespace App {\n  public class Svc {\n    public int Size() { return 0; }\n  }\n}\n";
    private static final String CSHARP_V2 =
            "namespace App {\n  public class Svc {\n    public int Size() { return 42; }\n  }\n}\n";

    @Test
    public void javaBodyEditChangesTheHash() throws Exception {
        assertBodyEditIsVisible(
                hashes(inMemory("/app/Svc.java", JAVA_V1), Lang.JAVA),
                hashes(inMemory("/app/Svc.java", JAVA_V2), Lang.JAVA),
                "METHOD app.Svc.size()", "CLASS app.Svc");
    }

    @Test
    public void csharpBodyEditChangesTheHash() throws Exception {
        assertBodyEditIsVisible(
                hashes(inMemory("/Svc.cs", CSHARP_V1), Lang.CSHARP),
                hashes(inMemory("/Svc.cs", CSHARP_V2), Lang.CSHARP),
                "METHOD App.Svc.Size()", "CLASS App.Svc");
    }

    @Test
    public void typescriptBodyEditChangesTheHash() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        assertBodyEditIsVisible(
                hashes(fixture("typescript", "code-hash-v1"), Lang.TYPESCRIPT),
                hashes(fixture("typescript", "code-hash-v2"), Lang.TYPESCRIPT),
                "METHOD src.svc.Svc.size()", "CLASS src.svc.Svc");
    }

    @Test
    public void pythonBodyEditChangesTheHash() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        assertBodyEditIsVisible(
                hashes(fixture("python", "code-hash-v1"), Lang.PYTHON),
                hashes(fixture("python", "code-hash-v2"), Lang.PYTHON),
                "METHOD src.svc.Svc.size() : int", "CLASS src.svc.Svc");
    }

    @Test
    public void everyComponentCarriesAHash() throws Exception {
        assertEveryComponentIsHashed(compile(inMemory("/app/Svc.java", JAVA_V1), Lang.JAVA));
        assertEveryComponentIsHashed(compile(inMemory("/Svc.cs", CSHARP_V1), Lang.CSHARP));
        if (NodeRuntime.isNodeAvailable()) {
            assertEveryComponentIsHashed(compile(fixture("typescript", "code-hash-v1"), Lang.TYPESCRIPT));
            assertEveryComponentIsHashed(compile(fixture("python", "code-hash-v1"), Lang.PYTHON));
        }
    }

    @Test
    public void hashesAreStableAcrossParses() throws Exception {
        assertEquals(hashes(inMemory("/app/Svc.java", JAVA_V1), Lang.JAVA),
                hashes(inMemory("/app/Svc.java", JAVA_V1), Lang.JAVA));
        assertEquals(hashes(inMemory("/Svc.cs", CSHARP_V1), Lang.CSHARP),
                hashes(inMemory("/Svc.cs", CSHARP_V1), Lang.CSHARP));
        if (NodeRuntime.isNodeAvailable()) {
            assertEquals(hashes(fixture("typescript", "code-hash-v1"), Lang.TYPESCRIPT),
                    hashes(fixture("typescript", "code-hash-v1"), Lang.TYPESCRIPT));
            assertEquals(hashes(fixture("python", "code-hash-v1"), Lang.PYTHON),
                    hashes(fixture("python", "code-hash-v1"), Lang.PYTHON));
        }
    }

    private static void assertBodyEditIsVisible(final Map<String, Integer> before,
                                                final Map<String, Integer> after,
                                                final String... changedKeys) {
        assertEquals("the same components should be modelled either side of a body edit",
                before.keySet(), after.keySet());
        for (final String key : changedKeys) {
            assertTrue("expected to model " + key + ", modelled " + before.keySet(), before.containsKey(key));
            assertNotEquals("a changed method body should change " + key + "'s code hash",
                    before.get(key), after.get(key));
        }
    }

    private static void assertEveryComponentIsHashed(final OOPSourceCodeModel model) {
        final List<String> unhashed = model.components()
                .filter(component -> component.codeHash() == 0)
                .map(Component::uniqueName)
                .toList();
        assertTrue("components left without a code hash: " + unhashed, unhashed.isEmpty());
    }

    private static Map<String, Integer> hashes(final ProjectFiles files, final Lang lang) throws Exception {
        final Map<String, Integer> hashes = new TreeMap<>();
        compile(files, lang).components().forEach(
                component -> hashes.put(component.componentType() + " " + component.uniqueName(),
                        component.codeHash()));
        return hashes;
    }

    private static OOPSourceCodeModel compile(final ProjectFiles files, final Lang lang) throws Exception {
        final CompileResult result = new ClarpseProject(files, lang).result();
        assertTrue("fixture failed to compile: " + result.failures(), result.failures().isEmpty());
        return result.model();
    }

    private static ProjectFiles inMemory(final String path, final String content) {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile(path, content));
        return files;
    }

    private static ProjectFiles fixture(final String language, final String fixtureName) throws Exception {
        return new ProjectFiles(
                Paths.get("src/test/resources", language, fixtureName).toAbsolutePath().toString());
    }
}
