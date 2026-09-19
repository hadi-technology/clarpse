package com.hadi.test.typescript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * TypeScript names never carry the directory the sources were written to for a compile: a whole
 * module referenced through a namespace import, `typeof import(...)` or a module-typed value is
 * named by its place in the repository, and type text is written relative to the repository root.
 * So the same sources give the same model wherever and however often they are compiled.
 */
public class TypeScriptPortableNamesTest {

    private static final String A = "/src/app/a.ts";

    private static Map<String, String> repository() {
        final Map<String, String> files = new LinkedHashMap<>();
        files.put("/tsconfig.json", "{ \"compilerOptions\": { \"target\": \"es2020\", \"module\": \"commonjs\","
                + " \"strict\": true, \"baseUrl\": \".\", \"paths\": { \"@lib/*\": [\"src/lib/*\"] } },"
                + " \"include\": [\"src/**/*.ts\"] }");
        files.put(A, "import * as dom from './dom';\n"
                + "import * as mod from '../pkg/mod';\n"
                + "import * as aliased from '@lib/aliased';\n"
                + "import { ns } from './reexport';\n"
                + "import * as extns from 'extlib';\n"
                + "export class A {\n"
                + "  typed: dom.Foo | undefined;\n"
                + "  other: mod.Bar | undefined;\n"
                + "  viaAlias: aliased.Baz | undefined;\n"
                + "  viaNamespaceExport: ns.Foo | undefined;\n"
                + "  whole = dom;\n"
                + "  query: typeof import('./dom') | undefined;\n"
                + "  ext = extns;\n"
                + "  run(): void {\n"
                + "    const made = new dom.Foo();\n"
                + "    const local = dom;\n"
                + "    const bar = new mod.Bar();\n"
                + "    dom.helper();\n"
                + "  }\n"
                + "}\n");
        files.put("/src/app/dom.ts", "export class Foo { }\nexport function helper(): void { }\n");
        files.put("/src/app/reexport.ts", "export * as ns from './dom';\n");
        files.put("/src/pkg/mod.ts", "export class Bar { }\n");
        files.put("/src/lib/aliased.ts", "export class Baz { }\n");
        files.put("/node_modules/extlib/index.d.ts", "export declare class Ext { }\n");
        files.put("/node_modules/extlib/package.json", "{ \"name\": \"extlib\", \"types\": \"index.d.ts\" }");
        return files;
    }

    /** One way of compiling the fixture. */
    private enum Mode {
        FULL_FOCUS, FULL_ALL_FILES, ONE_LEVEL
    }

    @BeforeClass
    public static void requireNode() {
        assumeTrue(NodeRuntime.isNodeAvailable());
    }

    /** Compiles the fixture from a fresh ProjectFiles, returning the model and the directory it used. */
    private static Compiled compile(final Mode mode) throws Exception {
        try (ProjectFiles files = new ProjectFiles()) {
            repository().forEach((path, content) -> files.insertFile(new ProjectFile(path, content)));
            final String directory = Paths.get(files.projectDir()).toAbsolutePath().normalize().toString();
            final CompileResult result = switch (mode) {
                case FULL_FOCUS -> new ClarpseProject(files, Lang.TYPESCRIPT, List.of(A)).result();
                case FULL_ALL_FILES -> new ClarpseProject(files, Lang.TYPESCRIPT).result();
                case ONE_LEVEL -> new ClarpseProject(files, Lang.TYPESCRIPT, List.of(A),
                        AnalysisOptions.oneLevel()).result();
            };
            return new Compiled(result.model(), directory);
        }
    }

    private record Compiled(OOPSourceCodeModel model, String directory) {
    }

    private static List<String> everyName(final OOPSourceCodeModel model) {
        final List<String> names = new ArrayList<>();
        model.components().forEach(component -> {
            names.add(component.uniqueName());
            component.references().forEach(reference -> names.add(reference.invokedComponent()));
            if (component.imports() != null) {
                names.addAll(component.imports());
            }
        });
        return names;
    }

    private static String json(final OOPSourceCodeModel model) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, String> sorted = new TreeMap<>();
        for (final Component component : model.components().collect(Collectors.toList())) {
            sorted.put(component.uniqueName(), mapper.writeValueAsString(component));
        }
        return sorted.toString();
    }

    private static Component component(final OOPSourceCodeModel model, final String uniqueName) {
        return model.component(uniqueName).orElseThrow(() -> new AssertionError("no component " + uniqueName));
    }

    private static Set<String> targets(final Iterable<ComponentReference> references) {
        final Set<String> targets = new TreeSet<>();
        references.forEach(reference -> targets.add(reference.invokedComponent()));
        return targets;
    }

    @Test
    public void noNameCarriesTheDirectoryTheSourcesWereWrittenTo() throws Exception {
        final String tmp = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize().toString();
        for (final Mode mode : Mode.values()) {
            final Compiled compiled = compile(mode);
            for (final String name : everyName(compiled.model())) {
                assertFalse(mode + ": " + name, name.contains(compiled.directory()));
                assertFalse(mode + ": " + name, name.contains(tmp));
                assertFalse(mode + ": " + name, name.contains("clarpse-src-"));
            }
            final String json = json(compiled.model());
            assertFalse(mode + " serialised model", json.contains(compiled.directory()));
            assertFalse(mode + " serialised model", json.contains("clarpse-src-"));
        }
    }

    @Test
    public void compilingTheSameSourcesTwiceGivesTheSameModel() throws Exception {
        for (final Mode mode : Mode.values()) {
            final Compiled first = compile(mode);
            final Compiled second = compile(mode);
            assertFalse("two compiles must use two directories", first.directory().equals(second.directory()));
            assertEquals(mode.toString(), json(first.model()), json(second.model()));
        }
    }

    @Test
    public void aWholeModuleIsReferencedByItsPlaceInTheRepository() throws Exception {
        for (final Mode mode : Mode.values()) {
            final OOPSourceCodeModel model = compile(mode).model();
            assertTrue(mode + " whole", targets(component(model, "src.app.a.A.whole").references())
                    .contains("src.app.dom"));
            assertTrue(mode + " typeof import", targets(component(model, "src.app.a.A.query").references())
                    .contains("src.app.dom"));
            assertTrue(mode + " local", targets(component(model, "src.app.a.A.run().local").references())
                    .contains("src.app.dom"));
        }
    }

    @Test
    public void aNamespaceImportNamesItsModule() throws Exception {
        for (final Mode mode : Mode.values()) {
            final Set<String> imports = new TreeSet<>(component(compile(mode).model(), "src.app.a.A").imports());
            assertTrue(mode + " " + imports, imports.containsAll(Set.of("src.app.dom", "src.pkg.mod",
                    "src.lib.aliased")));
            assertFalse(mode + " " + imports, imports.contains("src.app.dom.dom"));
        }
    }

    @Test
    public void membersReachedThroughANamespaceAreInternalWhenModelled() throws Exception {
        for (final Mode mode : new Mode[] {Mode.FULL_ALL_FILES, Mode.ONE_LEVEL}) {
            final OOPSourceCodeModel model = compile(mode).model();
            assertEquals(mode + " dom.Foo", Set.of("src.app.dom.Foo"),
                    targets(component(model, "src.app.a.A.typed").internalDependencies()));
            assertEquals(mode + " ns.Foo", Set.of("src.app.dom.Foo"),
                    targets(component(model, "src.app.a.A.viaNamespaceExport").internalDependencies()));
            assertEquals(mode + " @lib alias", Set.of("src.lib.aliased.Baz"),
                    targets(component(model, "src.app.a.A.viaAlias").internalDependencies()));
            assertEquals(mode + " ../pkg", Set.of("src.pkg.mod.Bar"),
                    targets(component(model, "src.app.a.A.other").internalDependencies()));
        }
    }

    @Test
    public void typeTextOutsideTheRepositorysSourcesIsRelativeToItsRoot() throws Exception {
        final OOPSourceCodeModel model = compile(Mode.FULL_FOCUS).model();
        final Set<String> ext = targets(component(model, "src.app.a.A.ext").references());
        assertEquals(Set.of("typeof import(\"node_modules/extlib/index\")"), ext);
    }
}
