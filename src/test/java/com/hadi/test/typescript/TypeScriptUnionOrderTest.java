package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The text of a union type the checker renders does not depend on what else the program holds or
 * which files were modelled first: the checker orders union members by the order it created their
 * types in, so the text is put in an order of its own.
 */
public class TypeScriptUnionOrderTest {

    private static final String CONFIG = "{ \"compilerOptions\": { \"target\": \"es2020\", \"strict\": true },"
            + " \"include\": [\"src/**/*.ts\"] }";
    private static final String A = "/src/a.ts";
    private static final String B = "/src/b.ts";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static ProjectFiles repository() {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json", CONFIG));
        // The literal "debug" is first met in b.ts; in a.ts alone "verbose" is met first.
        files.insertFile(new ProjectFile(B, "export const level: \"debug\" = \"debug\";\n"
                + "export const other: \"error\" = \"error\";\n"));
        files.insertFile(new ProjectFile(A, "export class Logger {\n"
                + "  log(level: \"verbose\" | \"debug\", extra: Array<\"verbose\" | \"error\" | \"debug\">): void {}\n"
                + "}\n"
                + "export function pick(mode: \"verbose\" | \"debug\" | \"error\" | undefined): void {}\n"));
        return files;
    }

    private static Set<String> namesIn(final List<String> analysed) throws Exception {
        final CompileResult result = new ClarpseProject(repository(), Lang.TYPESCRIPT, analysed,
                AnalysisOptions.oneLevel()).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model().components()
                .filter(c -> A.equals("/" + c.sourceFile()) || A.equals(c.sourceFile()))
                .map(Component::uniqueName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void theNamesOfAFileDoNotDependOnWhichOtherFilesAreModelledFirst() throws Exception {
        final Set<String> alone = namesIn(List.of(A));
        final Set<String> afterAnother = namesIn(List.of(B, A));
        assertTrue("the file should declare components: " + alone, !alone.isEmpty());
        assertEquals("names of " + A + " when it is analysed alone and after " + B, alone, afterAnother);
    }
}
