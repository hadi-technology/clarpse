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
import com.hadi.clarpse.sourcemodel.Component;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * What a TypeScript file's code says about the rest of the program is in the model: the modules it
 * imports, what its function bodies name, and what the types it writes resolve to. Each test
 * compiles a few small files and reads the model back.
 */
public class TypeScriptReferenceGapsTest {

    private static final String CONFIG = "{ \"compilerOptions\": { \"target\": \"es2020\", \"strict\": true,"
            + " \"module\": \"commonjs\", \"jsx\": \"react\", \"lib\": [\"es2022\"] },"
            + " \"include\": [\"src/**/*.ts\", \"src/**/*.tsx\"] }";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    /** Compiles the given path and source pairs beside a tsconfig that includes {@code src}. */
    static CompileResult compile(final String... pathsAndSources) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json", CONFIG));
        for (int i = 0; i < pathsAndSources.length; i += 2) {
            files.insertFile(new ProjectFile(pathsAndSources[i], pathsAndSources[i + 1]));
        }
        return new ClarpseProject(files, Lang.TYPESCRIPT).result();
    }

    static Set<String> targets(final Component component) {
        return component.references().stream().map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static Component component(final CompileResult result, final String name) {
        return result.model().component(name)
                .orElseThrow(() -> new AssertionError(name + " is not in the model: "
                        + result.model().components().map(Component::uniqueName).toList()));
    }

    private static final String UTIL = "export const LIMIT = 5;\n"
            + "export function helper(): number { return 1; }\n";

    @Test
    public void aModuleLoadedByDynamicImportInsideAFunctionIsAnImportOfTheFile() throws Exception {
        final CompileResult result = compile("/src/util.ts", UTIL,
                "/src/app.ts", "export function lazy() { return import('./util'); }\n"
                        + "export function remote() { return import('some-package-nobody-installed'); }\n");
        final Set<String> imports = Set.copyOf(component(result, "src.app.lazy").imports());
        assertTrue("the module the import() names should be an import: " + imports,
                imports.contains("src.util"));
        assertTrue("so should a module that cannot be resolved, by its specifier: " + imports,
                imports.contains("some-package-nobody-installed"));
    }

    @Test
    public void aRequireCallAndAnImportEqualsAreImportsOfTheFile() throws Exception {
        final CompileResult result = compile("/src/util.ts", UTIL,
                "/src/cjs.ts", "import fs = require('node:fs');\n"
                        + "declare function require(id: string): any;\n"
                        + "declare namespace require { function resolve(id: string): string; }\n"
                        + "export function load() { return require('./util') && require.resolve('pkg-x') && fs; }\n");
        final Set<String> imports = Set.copyOf(component(result, "src.cjs.load").imports());
        assertTrue(imports.toString(), imports.contains("src.util"));
        assertTrue(imports.toString(), imports.contains("pkg-x"));
        assertTrue(imports.toString(), imports.contains("node:fs"));
    }

    @Test
    public void aFunctionInAFileThatAlsoDeclaresATypeHoldsTheFilesImports() throws Exception {
        final CompileResult result = compile("/src/util.ts", UTIL,
                "/src/mixed.ts", "import { helper } from './util';\n"
                        + "export class Holder { run() { return helper(); } }\n"
                        + "export function standalone() { return helper(); }\n"
                        + "export const answer = helper();\n");
        assertTrue(component(result, "src.mixed.Holder").imports().contains("src.util.helper"));
        assertTrue("a function of the file imports what the file does: "
                        + component(result, "src.mixed.standalone").imports(),
                component(result, "src.mixed.standalone").imports().contains("src.util.helper"));
        assertTrue(component(result, "src.mixed.answer").imports().contains("src.util.helper"));
        assertTrue("a member does not carry them",
                component(result, "src.mixed.Holder.run()").imports().isEmpty());
        assertFalse(component(result, "src.mixed.standalone").imports().isEmpty());
        assertEquals(0, result.failures().size());
    }

    private static final String CONSTS = "export const TOKEN = Symbol.for('token');\n"
            + "export const LIMIT = 5;\n"
            + "export function handler(): void { }\n"
            + "export const Greeting = (name: string): string => 'hi ' + name;\n";

    @Test
    public void aConstantReadInsideAFunctionBodyIsAReference() throws Exception {
        final CompileResult result = compile("/src/consts.ts", CONSTS,
                "/src/use.ts", "import { TOKEN, LIMIT } from './consts';\n"
                        + "export function use() { return [TOKEN, LIMIT]; }\n"
                        + "export class Holder { read() { return LIMIT; } }\n");
        assertTrue(targets(component(result, "src.use.use")).toString(),
                targets(component(result, "src.use.use")).containsAll(Set.of("src.consts.TOKEN", "src.consts.LIMIT")));
        assertTrue(targets(component(result, "src.use.Holder.read()")).contains("src.consts.LIMIT"));
    }

    @Test
    public void aFunctionPassedAsAValueIsAReference() throws Exception {
        final CompileResult result = compile("/src/consts.ts", CONSTS,
                "/src/use.ts", "import { handler } from './consts';\n"
                        + "export function use(items: number[]) { return items.map(handler); }\n");
        assertTrue(targets(component(result, "src.use.use")).toString(),
                targets(component(result, "src.use.use")).contains("src.consts.handler"));
    }

    @Test
    public void aJsxTagNamesTheComponentItRenders() throws Exception {
        final CompileResult result = compile("/src/widget.tsx",
                "export function Widget(props: { title: string }) { return props.title; }\n",
                "/src/page.tsx", "import { Widget } from './widget';\n"
                        + "declare namespace JSX { interface IntrinsicElements { [tag: string]: unknown } }\n"
                        + "export function Page() { return <section><Widget title=\"t\" /></section>; }\n");
        assertTrue(targets(component(result, "src.page.Page")).toString(),
                targets(component(result, "src.page.Page")).contains("src.widget.Widget"));
    }

    @Test
    public void aCallToAConstArrowFunctionIsAReference() throws Exception {
        final CompileResult result = compile("/src/consts.ts", CONSTS,
                "/src/use.ts", "import { Greeting } from './consts';\n"
                        + "export function use() { return Greeting('x'); }\n"
                        + "const local = (n: number) => n + 1;\n"
                        + "export function twice() { return local(local(1)); }\n");
        assertTrue(targets(component(result, "src.use.use")).toString(),
                targets(component(result, "src.use.use")).contains("src.consts.Greeting"));
        assertTrue(targets(component(result, "src.use.twice")).toString(),
                targets(component(result, "src.use.twice")).contains("src.use.local"));
    }

    @Test
    public void aLocalOrParameterShadowingAModelledNameIsNotAReference() throws Exception {
        final CompileResult result = compile("/src/consts.ts", CONSTS,
                "/src/use.ts", "import { LIMIT } from './consts';\n"
                        + "export function use(handler: () => void) { const LIMIT2 = LIMIT; { const LIMIT = 1; "
                        + "return [handler, LIMIT, LIMIT2]; } }\n");
        final Set<String> targets = targets(component(result, "src.use.use"));
        assertTrue(targets.toString(), targets.contains("src.consts.LIMIT"));
        assertFalse("the parameter named handler is not the exported function: " + targets,
                targets.contains("src.consts.handler"));
    }
}
