package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import java.util.Optional;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * A heritage name is a library global only when the checker resolves it to a declaration of a
 * default library or an {@code @types} file. A program that has no library, or a name nothing
 * declares, keeps the name as written and marks it unresolved; no list of names decides it.
 */
public class TypeScriptLibraryGlobalTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Optional<ComponentReference> base(final CompileResult result, final String component,
                                                     final String name) {
        return result.model().component(component).orElseThrow().references().stream()
                .filter(r -> r instanceof TypeExtensionReference && r.invokedComponent().equals(name)).findFirst();
    }

    /** A file no tsconfig names is read without a library. */
    private static CompileResult noConfig(final String source) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/a.ts", source));
        return new ClarpseProject(files, Lang.TYPESCRIPT).result();
    }

    @Test
    public void aGlobalTheCheckerResolvesToTheLibraryIsExact() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/a.ts", "export class Lookup extends Map<string, number> {}\n");
        assertEquals(ResolutionKind.EXACT, base(result, "src.a.Lookup", "Map").orElseThrow().resolutionKind());
    }

    @Test
    public void aGlobalInAProgramWithoutALibraryIsKeptAsWrittenAndUnresolved() throws Exception {
        final CompileResult result = noConfig("export class Failure extends Error {}\n");
        assertEquals(ResolutionKind.UNRESOLVED, base(result, "src.a.Failure", "Error").orElseThrow().resolutionKind());
    }

    @Test
    public void aNameNothingDeclaresIsNotTakenForALibraryGlobalButIsKeptAsWritten() throws Exception {
        final CompileResult result = noConfig("export class Mine extends NotAGlobal {}\n");
        assertEquals(ResolutionKind.UNRESOLVED, base(result, "src.a.Mine", "NotAGlobal").orElseThrow().resolutionKind());
    }

    @Test
    public void aNameTheRepositoryDeclaresIsTheRepositoryType() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/a.ts", "export class Map {}\nexport class Mine extends Map {}\n");
        final Component mine = result.model().component("src.a.Mine").orElseThrow();
        assertTrue(mine.references().toString(), base(result, "src.a.Mine", "src.a.Map").isPresent());
        assertFalse(mine.references().toString(), base(result, "src.a.Mine", "Map").isPresent());
    }
}
