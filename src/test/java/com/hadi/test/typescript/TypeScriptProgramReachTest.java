package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a one-level program holds is what the compiler would see for that config: the planned files
 * the config owns and those they reach, and the files named by {@code /// <reference path>},
 * planned or not, which are held for resolution and not modelled. A global script of another
 * config is never held, so each config binds a global name to its own declaration.
 */
public class TypeScriptProgramReachTest {

    private static final String CONFIG = "{ \"compilerOptions\": { \"target\": \"es2020\", \"strict\": true },"
            + " \"include\": [\"src/**/*.ts\"] }";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static CompileResult compile(final ProjectFiles files, final List<String> analysed) throws Exception {
        final CompileResult result = new ClarpseProject(files, Lang.TYPESCRIPT, analysed,
                AnalysisOptions.oneLevel()).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result;
    }

    private static Set<String> targets(final Component component) {
        return component.references().stream().map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void aFileNamedByAReferencePathIsHeldEvenWhenItIsNotPlanned() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json", CONFIG));
        files.insertFile(new ProjectFile("/src/types.ts", "interface Marker { id: number }\n"));
        files.insertFile(new ProjectFile("/src/refs.ts", "/// <reference path=\"./types.ts\" />\n"
                + "class Holder {\n  marker: Marker | undefined;\n}\n"));
        final CompileResult result = compile(files, List.of("/src/refs.ts"));

        final Component marker = result.model().component("src.refs.Holder.marker").orElseThrow();
        assertTrue("the field's type should resolve to the referenced file's declaration but refers to "
                        + targets(marker), targets(marker).contains("src.types.Marker"));
        assertFalse("the referenced file is held, not modelled",
                result.model().containsComponent("src.types.Marker"));
    }

    @Test
    public void referencePathsAreFollowedThroughFilesThatAreNotPlanned() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json", CONFIG));
        files.insertFile(new ProjectFile("/src/deep.ts", "interface Deep { id: number }\n"));
        files.insertFile(new ProjectFile("/src/middle.ts", "/// <reference path=\"./deep.ts\" />\n"
                + "interface Middle { id: number }\n"));
        files.insertFile(new ProjectFile("/src/refs.ts", "/// <reference path=\"./middle.ts\" />\n"
                + "class Holder {\n  deep: Deep | undefined;\n  middle: Middle | undefined;\n}\n"));
        final CompileResult result = compile(files, List.of("/src/refs.ts"));

        assertTrue(targets(result.model().component("src.refs.Holder.deep").orElseThrow()).toString(),
                targets(result.model().component("src.refs.Holder.deep").orElseThrow())
                        .contains("src.deep.Deep"));
        assertTrue(targets(result.model().component("src.refs.Holder.middle").orElseThrow()).toString(),
                targets(result.model().component("src.refs.Holder.middle").orElseThrow())
                        .contains("src.middle.Middle"));
    }

    @Test
    public void eachConfigBindsAGlobalNameToItsOwnIdenticalCopy() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        for (final String dir : List.of("a", "b")) {
            files.insertFile(new ProjectFile("/" + dir + "/tsconfig.json", CONFIG));
            files.insertFile(new ProjectFile("/" + dir + "/src/model.ts", "interface Cat { name: string }\n"));
            files.insertFile(new ProjectFile("/" + dir + "/src/use.ts",
                    "class Use_" + dir + " {\n  cat: Cat | undefined;\n}\n"));
        }
        final CompileResult result = compile(files,
                List.of("/a/src/model.ts", "/a/src/use.ts", "/b/src/model.ts", "/b/src/use.ts"));

        final Set<String> fromA = targets(result.model().component("a.src.use.Use_a.cat").orElseThrow());
        final Set<String> fromB = targets(result.model().component("b.src.use.Use_b.cat").orElseThrow());
        assertTrue(fromA.toString(), fromA.contains("a.src.model.Cat") && !fromA.contains("b.src.model.Cat"));
        assertTrue(fromB.toString(), fromB.contains("b.src.model.Cat") && !fromB.contains("a.src.model.Cat"));
    }
}
