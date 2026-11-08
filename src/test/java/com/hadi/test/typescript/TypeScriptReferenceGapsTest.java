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

    /** Compiles the given path and source pairs, with a tsconfig of the test's own at the root. */
    static CompileResult compileWithConfig(final String config, final String... pathsAndSources) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json", config));
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

    private static final String LIB_PACKAGE = "{ \"name\": \"@acme/lib\", \"version\": \"1.0.0\", \"types\": \"index.d.ts\" }";
    private static final String LIB_TYPES = "export declare class Client { send(message: string): Promise<void>; }\n"
            + "export declare function useThing(options: { a?: number }): { data: string };\n"
            + "export declare const VERSION: string;\n"
            + "export declare function Widget(props: { title: string }): unknown;\n";

    @Test
    public void aSymbolOfAPackageIsAReferenceNamedByItsPackage() throws Exception {
        final CompileResult result = compile("/node_modules/@acme/lib/package.json", LIB_PACKAGE,
                "/node_modules/@acme/lib/index.d.ts", LIB_TYPES,
                "/src/use.tsx", "import { useThing, VERSION, Widget } from '@acme/lib';\n"
                        + "declare namespace JSX { interface IntrinsicElements { [tag: string]: unknown } }\n"
                        + "export function use() { return [VERSION, useThing({})]; }\n"
                        + "export function view() { return <Widget title=\"t\" />; }\n");
        final Set<String> used = targets(component(result, "src.use.use"));
        assertTrue("a constant of the package: " + used, used.contains("@acme/lib:VERSION"));
        assertTrue("a function of the package that is called: " + used, used.contains("@acme/lib:useThing"));
        assertTrue("a component of the package named as a tag: " + targets(component(result, "src.use.view")),
                targets(component(result, "src.use.view")).contains("@acme/lib:Widget"));
    }

    @Test
    public void aMethodCalledOnALibraryObjectIsTheReceiverTypeAndTheMethod() throws Exception {
        final CompileResult result = compile("/node_modules/@acme/lib/package.json", LIB_PACKAGE,
                "/node_modules/@acme/lib/index.d.ts", LIB_TYPES,
                "/src/use.ts", "import { Client } from '@acme/lib';\n"
                        + "export function send(client: Client) { return client.send('x'); }\n");
        assertTrue(targets(component(result, "src.use.send")).toString(),
                targets(component(result, "src.use.send")).contains("@acme/lib:Client.send"));
    }

    @Test
    public void aMemberReadFromANamespaceImportOfAPackageIsNamedByItsPackage() throws Exception {
        final CompileResult result = compile("/node_modules/@acme/lib/package.json", LIB_PACKAGE,
                "/node_modules/@acme/lib/index.d.ts", LIB_TYPES,
                "/src/use.ts", "import * as lib from '@acme/lib';\n"
                        + "export function version() { return lib.VERSION; }\n");
        assertTrue(targets(component(result, "src.use.version")).toString(),
                targets(component(result, "src.use.version")).contains("@acme/lib:VERSION"));
    }

    @Test
    public void aPackageWithNoDeclarationsIsStillNamed() throws Exception {
        final CompileResult result = compile("/src/use.ts",
                "import { useMutation } from '@tanstack/react-query';\n"
                        + "export function save() { return useMutation({}); }\n");
        assertTrue(targets(component(result, "src.use.save")).toString(),
                targets(component(result, "src.use.save")).contains("@tanstack/react-query:useMutation"));
    }

    @Test
    public void aNameBoundByRequireIsNamedByItsPackage() throws Exception {
        final CompileResult result = compile("/src/main.ts",
                "declare function require(id: string): any;\n"
                        + "const { app } = require('electron');\n"
                        + "export function quit() { return app.quit(); }\n");
        assertTrue(targets(component(result, "src.main.quit")).toString(),
                targets(component(result, "src.main.quit")).contains("electron:app"));
    }

    @Test
    public void aNameOfTheLanguagesOwnLibraryOrOfTheRepositoryIsNotAPackageReference() throws Exception {
        final CompileResult result = compile("/src/own.ts", "export const LIMIT = 1;\n",
                "/src/use.ts", "import { LIMIT } from './own';\n"
                        + "export function use(items: number[]) { return [Math.max(...items), new Map(), LIMIT]; }\n");
        for (final String target : targets(component(result, "src.use.use"))) {
            assertFalse("only a package's symbols are named package:Symbol: " + target,
                    target.contains(":") && !target.startsWith("src."));
        }
    }

    private static final String WORKSPACE_CONFIG = "{ \"compilerOptions\": { \"target\": \"es2020\", \"strict\": true,"
            + " \"module\": \"commonjs\", \"moduleResolution\": \"node\" }, \"include\": [\"packages/**/*.ts\"] }";
    private static final String PACKAGE_B_SOURCE = "export interface Shape { sides: number }\n"
            + "export function area(shape: Shape): number { return shape.sides; }\n";

    @Test
    public void anImportOfAWorkspacePackageResolvesToThePackagesSource() throws Exception {
        final CompileResult result = compileWithConfig(WORKSPACE_CONFIG,
                "/package.json", "{ \"name\": \"root\", \"private\": true, \"workspaces\": [\"packages/*\"] }",
                "/packages/b/package.json", "{ \"name\": \"@x/b\", \"exports\": { \".\": "
                        + "{ \"types\": \"./dist/index.d.ts\", \"default\": \"./dist/index.js\" } } }",
                "/packages/b/src/index.ts", PACKAGE_B_SOURCE,
                "/packages/a/package.json", "{ \"name\": \"@x/a\", \"dependencies\": { \"@x/b\": \"workspace:*\" } }",
                "/packages/a/src/use.ts", "import { area, Shape } from '@x/b';\n"
                        + "export function measure(shape: Shape) { return area(shape); }\n");
        final Set<String> measured = targets(component(result, "packages.a.src.use.measure"));
        assertTrue("an edge into the package's source: " + measured,
                measured.contains("packages.b.src.index.area") || measured.contains("packages.b.src.index.Shape"));
        assertTrue(component(result, "packages.a.src.use.measure").imports().toString(),
                component(result, "packages.a.src.use.measure").imports().stream()
                        .anyMatch(imported -> imported.startsWith("packages.b.src.index")));
    }

    @Test
    public void aSubpathExportAndAnAliasOfAWorkspacePackageResolveToItsSource() throws Exception {
        final CompileResult result = compileWithConfig(WORKSPACE_CONFIG,
                "/package.json", "{ \"private\": true, \"workspaces\": { \"packages\": [\"packages/*\"] } }",
                "/packages/b/package.json", "{ \"name\": \"@x/b\", \"version\": \"1.2.0\", \"exports\": { \"./shapes\": "
                        + "{ \"types\": \"./dist/shapes.d.ts\" } } }",
                "/packages/b/src/shapes.ts", PACKAGE_B_SOURCE,
                "/packages/a/package.json", "{ \"name\": \"@x/a\", \"dependencies\": "
                        + "{ \"bee\": \"npm:@x/b@^1\" } }",
                "/packages/a/src/use.ts", "import { area } from '@x/b/shapes';\n"
                        + "export function viaSubpath() { return area({ sides: 1 }); }\n",
                "/packages/a/src/alias.ts", "import { Shape } from 'bee/shapes';\n"
                        + "export function viaAlias(shape: Shape) { return shape; }\n");
        assertTrue(targets(component(result, "packages.a.src.use.viaSubpath")).toString(),
                targets(component(result, "packages.a.src.use.viaSubpath")).contains("packages.b.src.shapes.area"));
        assertTrue(targets(component(result, "packages.a.src.alias.viaAlias")).toString(),
                targets(component(result, "packages.a.src.alias.viaAlias")).contains("packages.b.src.shapes.Shape"));
    }

    @Test
    public void aPackageNoWorkspaceListsIsNotMappedToASourceFolder() throws Exception {
        final CompileResult result = compileWithConfig(WORKSPACE_CONFIG,
                "/packages/b/package.json", "{ \"name\": \"@x/b\", \"types\": \"./dist/index.d.ts\" }",
                "/packages/b/src/index.ts", PACKAGE_B_SOURCE,
                "/packages/a/src/use.ts", "import { area } from '@x/b';\n"
                        + "export function measure() { return area({ sides: 1 }); }\n");
        assertFalse(targets(component(result, "packages.a.src.use.measure")).toString(),
                targets(component(result, "packages.a.src.use.measure")).contains("packages.b.src.index.area"));
    }

    private static final String ALIASES = "export interface Shape<K extends string> { kind: K }\n"
            + "export interface Circle { r: number }\n"
            + "export interface Square { s: number }\n"
            + "export type Card = Shape<\"card\">;\n"
            + "export type Round = Circle | Square;\n"
            + "export type Ctx = { id: string };\n"
            + "export type Tracked = Ctx & { extra: number };\n";

    @Test
    public void anAliasOfAGenericInstantiationReferencesTheGenericItInstantiates() throws Exception {
        final CompileResult result = compile("/src/types.ts", ALIASES);
        assertTrue(targets(component(result, "src.types.Card")).toString(),
                targets(component(result, "src.types.Card")).contains("src.types.Shape"));
    }

    @Test
    public void anAliasWrittenAsAReturnTypeIsReferencedByName() throws Exception {
        final CompileResult result = compile("/src/types.ts", ALIASES,
                "/src/use.ts", "import { Tracked, Round } from './types';\n"
                        + "export function tracked(): Tracked { return null as unknown as Tracked; }\n"
                        + "export function pick(round: Round): Round[] { return [round]; }\n");
        assertTrue("the intersection alias, not only the type it stands for: "
                        + targets(component(result, "src.use.tracked")),
                targets(component(result, "src.use.tracked")).containsAll(Set.of("src.types.Tracked", "src.types.Ctx")));
        assertTrue(targets(component(result, "src.use.pick")).toString(),
                targets(component(result, "src.use.pick")).contains("src.types.Round"));
        assertTrue("and as a parameter's type: " + targets(component(result, "src.use.pick.round")),
                targets(component(result, "src.use.pick.round")).contains("src.types.Round"));
    }

    @Test
    public void anAliasWrittenAsAFieldOrVariableTypeIsReferencedByName() throws Exception {
        final CompileResult result = compile("/src/types.ts", ALIASES,
                "/src/use.ts", "import { Round, Tracked } from './types';\n"
                        + "export class Holder { shape: Round | undefined; }\n"
                        + "export const current: Tracked | null = null;\n");
        assertTrue(targets(component(result, "src.use.Holder.shape")).toString(),
                targets(component(result, "src.use.Holder.shape")).contains("src.types.Round"));
        assertTrue(targets(component(result, "src.use.current")).toString(),
                targets(component(result, "src.use.current")).contains("src.types.Tracked"));
    }

    @Test
    public void declarationsNamedInAnExportListAreExported() throws Exception {
        final CompileResult result = compile("/src/listed.ts",
                "const a = 1;\nfunction b(): void { }\nclass C { }\ninterface D { x: number }\n"
                        + "type E = string;\nenum F { A }\nconst hidden = 2;\nconst g = 3;\n"
                        + "export { a, b, C, D, E, F, g as renamed };\nexport default C;\n");
        for (final String name : new String[] {"a", "b", "C", "D", "E", "F", "g"}) {
            assertTrue(name + " is on the module's surface: " + component(result, "src.listed." + name).modifiers(),
                    component(result, "src.listed." + name).modifiers().contains("export"));
            assertTrue(name, component(result, "src.listed." + name).modifiers().contains("public"));
        }
        assertTrue(component(result, "src.listed.C").modifiers().contains("default"));
        assertFalse("a declaration the list leaves out is not exported",
                component(result, "src.listed.hidden").modifiers().contains("export"));
    }

    @Test
    public void aReExportFromAnotherModuleDoesNotExportALocalOfTheSameName() throws Exception {
        final CompileResult result = compile("/src/other.ts", "export const a = 1;\n",
                "/src/reexport.ts", "const a = 2;\nexport { a as other } from './other';\n");
        assertFalse(component(result, "src.reexport.a").modifiers().contains("export"));
    }

    @Test
    public void aComputedOrQuotedMemberIsNamedWithoutItsBracketsAndQuotes() throws Exception {
        final CompileResult result = compile("/src/names.ts",
                "export class Q {\n"
                        + "  get ['~standard'](): number { return 1; }\n"
                        + "  ['quoted-method'](): void { }\n"
                        + "  'plain-quoted': string = '';\n"
                        + "  async [Symbol.asyncIterator](): Promise<void> { }\n"
                        + "}\n"
                        + "export interface Disposable2 { [Symbol.iterator](): Iterator<number>; }\n");
        final java.util.List<String> names = result.model().components().map(Component::uniqueName).toList();
        for (final String expected : new String[] {"src.names.Q.~standard()", "src.names.Q.quoted-method()",
            "src.names.Q.plain-quoted", "src.names.Q.Symbol.asyncIterator()",
            "src.names.Disposable2.Symbol.iterator()"}) {
            assertTrue(expected + " in " + names, names.contains(expected));
        }
        assertFalse(names.toString(), names.stream().anyMatch(name -> name.contains("[") || name.contains("'")));
    }

    @Test
    public void aTypeParameterConstraintIsAReference() throws Exception {
        final CompileResult result = compile("/src/generic.ts",
                "export interface Config { name: string }\n"
                        + "export type Base = { id: number };\n"
                        + "export function parse<T extends Config>(value: T): T { return value; }\n"
                        + "export class Repo<E extends Base> { find<U extends Config>(u: U): U { return u; } }\n"
                        + "export interface Store<S extends Config> { held: S }\n"
                        + "export type Wrap<W extends Base> = W[];\n");
        assertTrue(targets(component(result, "src.generic.parse")).toString(),
                targets(component(result, "src.generic.parse")).contains("src.generic.Config"));
        assertTrue(targets(component(result, "src.generic.Repo")).toString(),
                targets(component(result, "src.generic.Repo")).contains("src.generic.Base"));
        assertTrue(targets(component(result, "src.generic.Repo.find(U)")).toString(),
                targets(component(result, "src.generic.Repo.find(U)")).contains("src.generic.Config"));
        assertTrue(targets(component(result, "src.generic.Store")).contains("src.generic.Config"));
        assertTrue(targets(component(result, "src.generic.Wrap")).contains("src.generic.Base"));
    }

    @Test
    public void aConstHoldingAFunctionReferencesTheTypesOfItsSignature() throws Exception {
        final CompileResult result = compile("/src/arrow.ts",
                "export interface Opts { n: number }\n"
                        + "export interface Result { ok: boolean }\n"
                        + "export type Handler = (o: Opts) => Result;\n"
                        + "export const connect = (c: Opts = { n: 1 }) => c;\n"
                        + "export const run = ((o: Opts): Result => ({ ok: true })) as Handler;\n"
                        + "export const cast = (() => ({ ok: true })) as () => Result;\n"
                        + "export const plain = 3;\n");
        assertTrue(targets(component(result, "src.arrow.connect")).toString(),
                targets(component(result, "src.arrow.connect")).contains("src.arrow.Opts"));
        assertTrue(targets(component(result, "src.arrow.run")).toString(),
                targets(component(result, "src.arrow.run")).containsAll(Set.of("src.arrow.Handler", "src.arrow.Opts",
                        "src.arrow.Result")));
        assertTrue(targets(component(result, "src.arrow.cast")).toString(),
                targets(component(result, "src.arrow.cast")).contains("src.arrow.Result"));
        assertFalse(targets(component(result, "src.arrow.plain")).stream().anyMatch(target -> target.startsWith("src.")));
    }

    @Test
    public void aConstructorParameterPropertyIsAField() throws Exception {
        final CompileResult result = compile("/src/errors.ts",
                "export class Detail { }\n"
                        + "export class TimeoutError {\n"
                        + "  declared: string = '';\n"
                        + "  constructor(public readonly timeout: number, private detail: Detail, plain: string) { }\n"
                        + "}\n");
        final Component timeout = component(result, "src.errors.TimeoutError.timeout");
        assertEquals(com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.FIELD,
                timeout.componentType());
        assertTrue(timeout.modifiers().toString(), timeout.modifiers().contains("readonly"));
        assertTrue(targets(component(result, "src.errors.TimeoutError.detail")).contains("src.errors.Detail"));
        assertTrue(component(result, "src.errors.TimeoutError.detail").modifiers().contains("private"));
        assertFalse("a parameter with no modifier declares no field",
                result.model().containsComponent("src.errors.TimeoutError.plain"));
        assertEquals("the constructor's own parameters are unchanged",
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                component(result, "src.errors.TimeoutError.constructor(number, Detail, string).timeout").componentType());
    }

    @Test
    public void aTypePredicateReferencesTheTypeItTestsFor() throws Exception {
        final CompileResult result = compile("/src/guards.ts",
                "export class ApiError { status = 1; }\n"
                        + "export function isApiError(value: unknown): value is ApiError { return true; }\n"
                        + "export function assertApi(value: unknown): asserts value is ApiError { }\n"
                        + "export const isApi = (value: unknown): value is ApiError => true;\n"
                        + "export class Guard { check(value: unknown): value is ApiError { return true; } }\n");
        for (final String name : new String[] {"isApiError", "assertApi", "isApi", "Guard.check(unknown)"}) {
            assertTrue(name + ": " + targets(component(result, "src.guards." + name)),
                    targets(component(result, "src.guards." + name)).contains("src.guards.ApiError"));
        }
    }

    @Test
    public void theMembersOfAnObjectTypeInAnAnnotationAreReferences() throws Exception {
        final CompileResult result = compile("/src/cards.ts",
                "export type AssetType = 'a' | 'b';\n"
                        + "export interface Label { text: string }\n"
                        + "export const CARDS: { type: AssetType; label: Label }[] = [];\n"
                        + "export class Holder { items: Array<{ kind: AssetType }> = []; }\n"
                        + "export function pick(card: { inner: { t: AssetType } }) { return card; }\n");
        assertTrue(targets(component(result, "src.cards.CARDS")).toString(),
                targets(component(result, "src.cards.CARDS")).containsAll(Set.of("src.cards.AssetType", "src.cards.Label")));
        assertTrue(targets(component(result, "src.cards.Holder.items")).contains("src.cards.AssetType"));
        assertTrue(targets(component(result, "src.cards.pick.card")).contains("src.cards.AssetType"));
    }

    @Test
    public void aRequireMadeByCreateRequireIsAnImportAndNamesWhatItBinds() throws Exception {
        final CompileResult result = compile("/src/util.ts", UTIL,
                "/src/cjs.ts", "import { createRequire } from 'node:module';\n"
                        + "const require = createRequire(import.meta.url);\n"
                        + "export function locale() {\n"
                        + "  const { app } = require('electron') as typeof import('electron');\n"
                        + "  return app.getLocale() + require.resolve('@scope/mcp') + require('./util');\n"
                        + "}\n");
        final Component locale = component(result, "src.cjs.locale");
        assertTrue(locale.imports().toString(), locale.imports().containsAll(Set.of("electron", "@scope/mcp",
                "src.util")));
        assertTrue(targets(locale).toString(), targets(locale).contains("electron:app"));
    }

    @Test
    public void aClassExtendingABuiltInClassHasTheMembersTheBuiltInDeclares() throws Exception {
        final CompileResult result = compile("/src/errors.ts",
                "export class WebError extends Error {\n"
                        + "  constructor(message: string, public code: number) { super(message); }\n"
                        + "  describe(): string { return this.message; }\n"
                        + "  override name = 'WebError';\n"
                        + "}\n"
                        + "export class NetworkError extends WebError { }\n"
                        + "export class Plain { }\n");
        for (final String inherited : new String[] {"message", "stack", "cause"}) {
            assertTrue(inherited + " comes from Error",
                    result.model().containsComponent("src.errors.WebError." + inherited));
        }
        assertEquals("a member the class declares is not copied over it", 1, result.model().components()
                .filter(component -> component.uniqueName().equals("src.errors.WebError.name")).count());
        for (final String inherited : new String[] {"message", "stack", "cause"}) {
            assertTrue(inherited + " is marked external, as inherited from a base the repository does not hold, so it is "
                            + "not mistaken for a member the class declares",
                    component(result, "src.errors.WebError." + inherited).modifiers().contains("external"));
        }
        assertFalse("a member the class declares is not marked",
                component(result, "src.errors.WebError.describe()").modifiers().contains("external"));
        assertFalse(component(result, "src.errors.WebError.name").modifiers().contains("external"));
        assertFalse("a class that extends one of the repository's classes is left alone",
                result.model().containsComponent("src.errors.NetworkError.message"));
        assertFalse(result.model().containsComponent("src.errors.Plain.message"));
    }

    @Test
    public void aPackageTypeWrittenInAnAliasOrABaseListIsNamedByItsPackage() throws Exception {
        final CompileResult result = compile("/src/shapes.ts",
                "import { TLShape, BaseUtil } from 'canvas-lib';\n"
                        + "export type CardShape = TLShape<'card'>;\n"
                        + "export class CardUtil extends BaseUtil<CardShape> { }\n"
                        + "export function describe(shape: CardShape, other: TLShape<'x'>) { return shape; }\n");
        assertTrue(targets(component(result, "src.shapes.CardShape")).toString(),
                targets(component(result, "src.shapes.CardShape")).contains("canvas-lib:TLShape"));
        assertTrue(targets(component(result, "src.shapes.CardUtil")).toString(),
                targets(component(result, "src.shapes.CardUtil")).contains("canvas-lib:BaseUtil"));
        assertTrue(targets(component(result, "src.shapes.describe")).toString(),
                targets(component(result, "src.shapes.describe")).contains("canvas-lib:TLShape"));
    }

    private static final String ROOT_WORKSPACES = "{ \"private\": true, \"workspaces\": [\"packages/*\"] }";

    private static boolean reachesPackageSource(final CompileResult result) {
        return targets(component(result, "packages.a.src.use.measure")).contains("packages.b.src.index.area");
    }

    @Test
    public void twoWorkspacePackagesOfOneNameAreNotMappedToEither() throws Exception {
        final CompileResult result = compileWithConfig(WORKSPACE_CONFIG,
                "/package.json", ROOT_WORKSPACES,
                "/packages/b/package.json", "{ \"name\": \"@x/b\", \"types\": \"./dist/index.d.ts\" }",
                "/packages/b/src/index.ts", PACKAGE_B_SOURCE,
                "/packages/c/package.json", "{ \"name\": \"@x/b\", \"types\": \"./dist/index.d.ts\" }",
                "/packages/c/src/index.ts", PACKAGE_B_SOURCE,
                "/packages/a/src/use.ts", "import { area } from '@x/b';\n"
                        + "export function measure() { return area({ sides: 1 }); }\n");
        final Set<String> measured = targets(component(result, "packages.a.src.use.measure"));
        assertFalse(measured.toString(), measured.contains("packages.b.src.index.area")
                || measured.contains("packages.c.src.index.area"));
    }

    @Test
    public void aPackageNestedInsideAWorkspacePackageIsNotAWorkspaceUnlessListed() throws Exception {
        final CompileResult result = compileWithConfig(WORKSPACE_CONFIG,
                "/package.json", ROOT_WORKSPACES,
                "/packages/b/package.json", "{ \"name\": \"@x/b\", \"types\": \"./dist/index.d.ts\" }",
                "/packages/b/src/index.ts", PACKAGE_B_SOURCE,
                "/packages/b/packages/inner/package.json", "{ \"name\": \"@x/inner\", \"types\": \"./dist/index.d.ts\" }",
                "/packages/b/packages/inner/src/index.ts", PACKAGE_B_SOURCE,
                "/packages/a/src/use.ts", "import { area } from '@x/inner';\n"
                        + "export function measure() { return area({ sides: 1 }); }\n");
        assertFalse(targets(component(result, "packages.a.src.use.measure")).toString(),
                targets(component(result, "packages.a.src.use.measure"))
                        .contains("packages.b.packages.inner.src.index.area"));
    }

    @Test
    public void anAliasPinningAnotherVersionThanTheWorkspacePackageIsNotMapped() throws Exception {
        final String[] b = {"/packages/b/package.json", "{ \"name\": \"@x/b\", \"version\": \"2.0.0\", "
                + "\"types\": \"./dist/index.d.ts\" }", "/packages/b/src/index.ts", PACKAGE_B_SOURCE};
        for (final String spec : new String[] {"npm:@x/b@^1", "npm:@x/b@1.2.3", "npm:@x/b@>=1 <2"}) {
            assertFalse(spec + " names a published release", reachesPackageSource(
                    withAlias(spec, b)));
        }
        assertTrue("a range the workspace version satisfies is the workspace package",
                reachesPackageSource(withAlias("npm:@x/b@^2", b)));
        assertTrue("workspace: is the workspace package whatever the version",
                reachesPackageSource(withAlias("workspace:@x/b@*", b)));
        assertFalse("a version that cannot be read cannot be checked", reachesPackageSource(withAlias("npm:@x/b@^2",
                "/packages/b/package.json", "{ \"name\": \"@x/b\", \"types\": \"./dist/index.d.ts\" }",
                "/packages/b/src/index.ts", PACKAGE_B_SOURCE)));
    }

    private CompileResult withAlias(final String spec, final String... packageFiles) {
        try {
            final CompileResult result = compileWithConfig(WORKSPACE_CONFIG,
                    concat(new String[] {"/package.json", ROOT_WORKSPACES,
                        "/packages/a/package.json", "{ \"name\": \"@x/a\", \"dependencies\": { \"bee\": \"" + spec + "\" } }",
                        "/packages/a/src/use.ts", "import { area } from 'bee';\n"
                                + "export function measure() { return area({ sides: 1 }); }\n"}, packageFiles));
            return result;
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String[] concat(final String[] first, final String[] second) {
        final String[] all = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }
}
