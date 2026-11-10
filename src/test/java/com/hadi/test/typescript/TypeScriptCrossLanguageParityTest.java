package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * References the TypeScript parser records beside those other languages record for the same shapes:
 * a type parameter, a namespace member and an inferred type's text are never recorded as types,
 * while decorator arguments, typed lambda parameters, {@code typeof} operands of a type alias and
 * type-parameter defaults are.
 */
public class TypeScriptCrossLanguageParityTest {

    private static final String TYPES = "export class Foo { x = 1; }\n"
            + "export class Bar { y = 1; }\n"
            + "export class Cfg { a = 1; }\n"
            + "export const TOKEN = 'tok';\n"
            + "export const config = { a: 1 };\n"
            + "export function factory(): number { return 1; }\n";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Set<String> targets(final String name, final String... sources) throws Exception {
        final String[] all = new String[sources.length + 2];
        all[0] = "/src/types.ts";
        all[1] = TYPES;
        System.arraycopy(sources, 0, all, 2, sources.length);
        final CompileResult result = TypeScriptReferenceGapsTest.compile(all);
        return TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, name));
    }

    @Test
    public void aTypeParameterIsNotRecordedAsAType() throws Exception {
        final Set<String> targets = targets("src.box.Box",
                "/src/box.ts", "export class Box<T> { v!: T; get(x: T): T { return x; } }\n");
        assertFalse(targets.toString(), targets.contains("src.box.T"));
        final Set<String> member = targets("src.box.Box.v",
                "/src/box.ts", "export class Box<T> { v!: T; }\n");
        assertFalse(member.toString(), member.contains("src.box.T"));
    }

    @Test
    public void aTypeParameterKeepsItsConstraint() throws Exception {
        final Set<String> targets = targets("src.use.run",
                "/src/use.ts", "import { Cfg } from './types';\n"
                        + "export function run<U extends Cfg>(u: U): number { return 1; }\n");
        assertTrue(targets.toString(), targets.contains("src.types.Cfg"));
        assertFalse(targets.toString(), targets.contains("src.use.U"));
    }

    @Test
    public void anInferredTypesTextIsNotAReference() throws Exception {
        final Set<String> targets = targets("src.a.A.l",
                "/src/a.ts", "import { Bar } from './types';\nexport class A { l = [Bar]; }\n");
        assertTrue(targets.toString(), targets.contains("src.types.Bar"));
        assertFalse(targets.toString(), targets.contains("(typeof Bar)[]"));
    }

    @Test
    public void decoratorArgumentsAndTheDecoratorAreReferenced() throws Exception {
        final String deco = "export function Component(o: object) { return (t: object) => {}; }\n"
                + "export function ManyToOne(f: () => unknown) { return (t: object, k: string) => {}; }\n"
                + "export function Inject(t: unknown) { return (a: object, k: string | undefined, i: number) => {}; }\n";
        final String use = "import { Component, ManyToOne, Inject } from './deco';\n"
                + "import { Foo, Bar, TOKEN } from './types';\n"
                + "@Component({ providers: [Foo] })\n"
                + "export class Use {\n"
                + "  @ManyToOne(() => Bar) owner: unknown;\n"
                + "  run(@Inject(TOKEN) p: string) {}\n"
                + "}\n";
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/types.ts", TYPES, "/src/deco.ts", deco, "/src/use.ts", use);
        final Set<String> cls = TypeScriptReferenceGapsTest.targets(
                TypeScriptReferenceGapsTest.component(result, "src.use.Use"));
        assertTrue(cls.toString(), cls.contains("src.types.Foo"));
        assertTrue(cls.toString(), cls.contains("src.deco.Component"));
        assertFalse(cls.toString(), cls.contains("Component"));
        final Set<String> field = TypeScriptReferenceGapsTest.targets(
                TypeScriptReferenceGapsTest.component(result, "src.use.Use.owner"));
        assertTrue(field.toString(), field.contains("src.types.Bar"));
        assertTrue(field.toString(), field.contains("src.deco.ManyToOne"));
        final Set<String> parameter = TypeScriptReferenceGapsTest.targets(
                TypeScriptReferenceGapsTest.component(result, "src.use.Use.run(string).p"));
        assertTrue(parameter.toString(), parameter.contains("src.types.TOKEN"));
        assertTrue(parameter.toString(), parameter.contains("src.deco.Inject"));
        assertFalse(parameter.stream().anyMatch(t -> t.equals("Inject")));
        final Set<String> annotations = TypeScriptReferenceGapsTest.component(result, "src.use.Use")
                .references(TypeReferences.ANNOTATION).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
        assertTrue(annotations.toString(), annotations.contains("src.deco.Component"));
    }

    @Test
    public void anExplicitlyTypedLambdaParameterIsReferenced() throws Exception {
        final Set<String> targets = targets("src.use.body",
                "/src/use.ts", "import { Foo, Bar } from './types';\n"
                        + "export function body(items: any[]) {\n"
                        + "  const f = (a: Foo) => 1;\n"
                        + "  const h = function (b: Bar) { return 2; };\n"
                        + "  return items.map((x: Foo) => 1);\n}\n");
        assertTrue(targets.toString(), targets.contains("src.types.Foo"));
        assertTrue(targets.toString(), targets.contains("src.types.Bar"));
    }

    @Test
    public void aCallbackParameterInAMethodIsReferenced() throws Exception {
        final Set<String> targets = targets("src.use.K.run(any[])",
                "/src/use.ts", "import { Bar } from './types';\n"
                        + "export class K { run(items: any[]) { items.forEach(function (b: Bar) { }); } }\n");
        assertTrue(targets.toString(), targets.contains("src.types.Bar"));
    }

    @Test
    public void typeofInATypeAliasReferencesTheValue() throws Exception {
        final String use = "import { config, factory } from './types';\n"
                + "export type C = typeof config;\n"
                + "export type R = ReturnType<typeof factory>;\n";
        assertTrue(targets("src.use.C", "/src/use.ts", use).contains("src.types.config"));
        assertTrue(targets("src.use.R", "/src/use.ts", use).contains("src.types.factory"));
    }

    @Test
    public void aTypeParameterDefaultIsReferenced() throws Exception {
        final Set<String> targets = targets("src.use.WithDefault",
                "/src/use.ts", "import { Cfg } from './types';\nexport class WithDefault<T = Cfg> { }\n");
        assertTrue(targets.toString(), targets.contains("src.types.Cfg"));
    }

    @Test
    public void typeofIsReadInEveryTypePosition() throws Exception {
        final String use = "import { config, factory } from './types';\n"
                + "export function param(p: ReturnType<typeof factory>): number { return 1; }\n"
                + "export class Holder {\n"
                + "  x: typeof config = config;\n"
                + "  get(): typeof config { return config; }\n"
                + "}\n"
                + "export function ret(): ReturnType<typeof factory> { return 1; }\n"
                + "export function union(u: string | typeof config): number { return 1; }\n"
                + "export function inter(i: { a: number } & typeof config): number { return 1; }\n"
                + "export function local(): number { const l: typeof config = config; return 1; }\n";
        for (final String owner : new String[] {"param", "ret", "union", "inter"}) {
            final Set<String> targets = targets("src.use." + owner, "/src/use.ts", use);
            assertTrue(owner + " " + targets, targets.stream().anyMatch(
                    t -> t.equals("src.types.factory") || t.equals("src.types.config")));
            assertFalse(owner + " " + targets, targets.stream().anyMatch(t -> t.contains("typeof")));
        }
        assertTrue(targets("src.use.param", "/src/use.ts", use).contains("src.types.factory"));
        assertTrue(targets("src.use.ret", "/src/use.ts", use).contains("src.types.factory"));
        assertTrue(targets("src.use.union", "/src/use.ts", use).contains("src.types.config"));
        assertTrue(targets("src.use.inter", "/src/use.ts", use).contains("src.types.config"));
        assertTrue(targets("src.use.Holder.x", "/src/use.ts", use).contains("src.types.config"));
        assertTrue(targets("src.use.Holder.get()", "/src/use.ts", use).contains("src.types.config"));
        assertTrue(targets("src.use.local", "/src/use.ts", use).contains("src.types.config"));
    }

    @Test
    public void aQualifiedTypeofReferencesTheDeclarationItResolvesTo() throws Exception {
        final String use = "import { cfg } from './objects';\n"
                + "import * as ns from './objects';\n"
                + "export function viaObject(p: typeof cfg.inner): number { return 1; }\n"
                + "export function viaNamespace(p: typeof ns.other): number { return 1; }\n";
        final String objects = "export const cfg = { inner: 1 };\nexport const other = { o: 1 };\n";
        final Set<String> viaObject = targets("src.use.viaObject",
                "/src/objects.ts", objects, "/src/use.ts", use);
        assertTrue(viaObject.toString(), viaObject.contains("src.objects.cfg"));
        final Set<String> viaNamespace = targets("src.use.viaNamespace",
                "/src/objects.ts", objects, "/src/use.ts", use);
        assertTrue(viaNamespace.toString(), viaNamespace.contains("src.objects.other"));
    }

    private static CompileResult compile(final String... pathsAndSources) throws Exception {
        return TypeScriptReferenceGapsTest.compile(pathsAndSources);
    }

    private static Set<String> referencesOf(final CompileResult result, final String name) {
        return TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, name));
    }

    /** Every reference that names a component of the repository names one the model holds. */
    private static void assertNoMadeUpNames(final CompileResult result) {
        final Set<String> held = result.model().components()
                .map(c -> c.uniqueName()).collect(Collectors.toSet());
        result.model().components().forEach(c -> c.references().forEach(r -> {
            final String target = r.invokedComponent();
            if (target.startsWith("src.")) {
                assertTrue(c.uniqueName() + " references " + target + " which is not in " + held,
                        held.contains(target));
            }
        }));
    }

    @Test
    public void aNamespaceClassUsedFromAnotherFileIsARealComponent() throws Exception {
        final CompileResult result = compile(
                "/src/ns.ts", "export namespace NS { export class Baz { static make(): number { return 1; } } }\n",
                "/src/use.ts", "import { NS } from './ns';\n"
                        + "export function viaNamespace() { return NS.Baz.make(); }\n"
                        + "export function made() { return new NS.Baz(); }\n");
        assertTrue(result.model().component("src.ns.NS").get().componentType() == ComponentType.NAMESPACE);
        assertTrue(result.model().component("src.ns.NS.Baz").get().componentType() == ComponentType.CLASS);
        assertTrue(result.model().component("src.ns.NS").get().children().contains("src.ns.NS.Baz"));
        assertTrue(referencesOf(result, "src.use.viaNamespace").contains("src.ns.NS.Baz"));
        assertTrue(referencesOf(result, "src.use.made").contains("src.ns.NS.Baz"));
        assertFalse(referencesOf(result, "src.use.viaNamespace").contains("src.ns.Baz"));
        assertNoMadeUpNames(result);
    }

    @Test
    public void aNestedNamespaceIsOneComponentPerSegment() throws Exception {
        final CompileResult result = compile(
                "/src/ns.ts", "export namespace A.B { export class C { c = 1; } }\n"
                        + "export namespace A { export namespace D { export const E = 1; } }\n",
                "/src/use.ts", "import { A } from './ns';\n"
                        + "export function use(): A.B.C { return new A.B.C(); }\n");
        assertTrue(result.model().component("src.ns.A").get().componentType() == ComponentType.NAMESPACE);
        assertTrue(result.model().component("src.ns.A.B").get().componentType() == ComponentType.NAMESPACE);
        assertTrue(result.model().component("src.ns.A.D").isPresent());
        assertTrue(result.model().component("src.ns.A.B.C").isPresent());
        assertTrue(result.model().component("src.ns.A.D.E").isPresent());
        assertTrue(referencesOf(result, "src.use.use").contains("src.ns.A.B.C"));
        assertNoMadeUpNames(result);
    }

    @Test
    public void aNamespaceMergedWithAClassOrFunctionAddsItsMembersToIt() throws Exception {
        final CompileResult result = compile(
                "/src/holder.ts", "export function make(): Holder.Inner { return new Holder.Inner(); }\n"
                        + "export class Holder { h = 1; }\n"
                        + "export namespace Holder { export class Inner { i = 1; } }\n"
                        + "export function fn(): number { return 1; }\n"
                        + "export namespace fn { export const LIMIT = 2; }\n"
                        + "export function other(): number { return fn.LIMIT; }\n");
        assertTrue(result.model().component("src.holder.Holder.Inner").get().componentType() == ComponentType.CLASS);
        assertTrue(result.model().component("src.holder.Holder").get().componentType() == ComponentType.CLASS);
        assertTrue(result.model().component("src.holder.fn.LIMIT").isPresent());
        assertTrue(result.model().components().filter(c -> c.uniqueName().equals("src.holder.Holder")).count() == 1);
        final Set<String> make = referencesOf(result, "src.holder.make");
        assertTrue(make.toString(), make.contains("src.holder.Holder.Inner"));
        assertFalse(make.toString(), make.contains("src.holder.Inner"));
        assertNoMadeUpNames(result);
    }

    @Test
    public void aNamespaceOfTwoBlocksIsOneComponentAndOneOfTwoFilesIsOnePerFile() throws Exception {
        final CompileResult result = compile(
                "/src/one.ts", "export namespace Shared { export class One { o = 1; } }\n"
                        + "export namespace Shared { export class Again { a = 1; } }\n",
                "/src/two.ts", "export namespace Shared { export class Two { t = 1; } }\n",
                "/src/use.ts", "import { Shared as S1 } from './one';\nimport { Shared as S2 } from './two';\n"
                        + "export function use(a: S1.One, b: S2.Two, c: S1.Again) { return 1; }\n");
        assertTrue(result.model().components().filter(c -> c.uniqueName().equals("src.one.Shared")).count() == 1);
        assertTrue(result.model().component("src.one.Shared").get().children().contains("src.one.Shared.One"));
        assertTrue(result.model().component("src.one.Shared").get().children().contains("src.one.Shared.Again"));
        assertTrue(result.model().component("src.two.Shared.Two").isPresent());
        final Set<String> use = referencesOf(result, "src.use.use");
        assertTrue(use.toString(), use.contains("src.one.Shared.One"));
        assertTrue(use.toString(), use.contains("src.two.Shared.Two"));
        assertTrue(use.toString(), use.contains("src.one.Shared.Again"));
        assertNoMadeUpNames(result);
    }

    @Test
    public void aNamespaceMemberThatIsNotExportedAndDeclareGlobalAreNotRecorded() throws Exception {
        final CompileResult result = compile(
                "/src/ns.ts", "export namespace NS { class Hidden { h = 1; } export function open(): Hidden { return new Hidden(); } }\n"
                        + "declare global { interface Window { w: number } }\n"
                        + "export function g(w: Window) { return 1; }\n");
        assertFalse(result.model().component("src.ns.NS.Hidden").isPresent());
        assertFalse(result.model().component("src.ns.Window").isPresent());
        assertFalse(referencesOf(result, "src.ns.g").stream().anyMatch(t -> t.startsWith("src.ns.Window")));
        assertNoMadeUpNames(result);
    }
}
