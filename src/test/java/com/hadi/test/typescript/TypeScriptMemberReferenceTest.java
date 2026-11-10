package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * A call on a receiver of exactly known type references the member it calls, on the owning type. A
 * class of the repository owns it unless a base of the repository declares it, a package's type owns
 * it with the package as its unit, and the deprecated {@code package:Type.member} string is still
 * recorded beside it.
 */
public class TypeScriptMemberReferenceTest {

    private static final String LIB_PACKAGE = "{ \"name\": \"@acme/lib\", \"version\": \"1.0.0\","
            + " \"types\": \"index.d.ts\" }";
    private static final String LIB_TYPES = "export declare class Client { send(message: string): Promise<void>; }\n";
    private static final String BASES = "export class Base { run(): void {} static make(): Base { return new Base(); } }\n"
            + "export class Sub extends Base { own(): void {} }\n";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Component component(final CompileResult result, final String name) {
        return TypeScriptReferenceGapsTest.component(result, name);
    }

    private static List<MemberReference> members(final Component component) {
        return component.references().stream().filter(MemberReference.class::isInstance)
                .map(MemberReference.class::cast).collect(Collectors.toList());
    }

    private static MemberReference member(final Component component, final String name) {
        final List<MemberReference> found = members(component).stream()
                .filter(ref -> ref.member().equals(name)).collect(Collectors.toList());
        assertEquals(members(component).toString(), 1, found.size());
        assertEquals(found.get(0).ownerType(), found.get(0).invokedComponent());
        assertEquals(MemberKind.CALL, found.get(0).memberKind());
        return found.get(0);
    }

    @Test
    public void aMethodInheritedFromABaseOfTheRepositoryIsOwnedByTheBase() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/base.ts", BASES,
                "/src/use.ts", "import { Sub } from './base';\n"
                        + "export function go(s: Sub) { s.run(); s.own(); }\n");
        final Component go = component(result, "src.use.go");
        assertEquals("src.base.Base", member(go, "run").ownerType());
        assertNull(member(go, "run").externalUnit());
        assertEquals("src.base.Sub", member(go, "own").ownerType());
    }

    @Test
    public void aStaticCallThroughAClassNameIsOwnedByTheClass() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/base.ts", BASES,
                "/src/use.ts", "import { Sub } from './base';\n"
                        + "export function go() { return Sub.make(); }\n");
        assertEquals("src.base.Base", member(component(result, "src.use.go"), "make").ownerType());
    }

    @Test
    public void aLibraryReceiverIsOwnedByItsTypeAndNamesItsPackageBesideTheDeprecatedString() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/node_modules/@acme/lib/package.json", LIB_PACKAGE,
                "/node_modules/@acme/lib/index.d.ts", LIB_TYPES,
                "/src/use.ts", "import { Client } from '@acme/lib';\n"
                        + "export function send(client: Client) { return client.send('x'); }\n");
        final Component send = component(result, "src.use.send");
        final MemberReference ref = member(send, "send");
        assertEquals("@acme/lib:Client", ref.ownerType());
        assertEquals("@acme/lib", ref.externalUnit());
        assertTrue(ref.isExternal());
        assertEquals(ResolutionKind.EXACT, ref.resolutionKind());
        final Set<String> targets = TypeScriptReferenceGapsTest.targets(send);
        assertTrue("the string form stays until 12.0: " + targets, targets.contains("@acme/lib:Client.send"));
    }

    @Test
    public void aTypeOfAnUninstalledPackageIsOwnedByItsImportedName() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/use.ts",
                "import { Editor } from 'extlib';\n"
                        + "export function go(e: Editor) { return e.getBounds(); }\n");
        final Component go = component(result, "src.use.go");
        assertEquals("extlib:Editor", member(go, "getBounds").ownerType());
        assertEquals("extlib", member(go, "getBounds").externalUnit());
        assertTrue(TypeScriptReferenceGapsTest.targets(go).contains("extlib:Editor.getBounds"));
    }

    @Test
    public void aNodeBuiltInHasTheSameUnitWithOrWithoutThePrefix() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/use.ts",
                "import { Stats } from 'node:fs';\nimport { Stats as Plain } from 'fs';\n"
                        + "export function prefixed(s: Stats) { return s.isFile(); }\n"
                        + "export function bare(s: Plain) { return s.isDirectory(); }\n");
        assertEquals("node:fs", member(component(result, "src.use.prefixed"), "isFile").externalUnit());
        assertEquals("node:fs", member(component(result, "src.use.bare"), "isDirectory").externalUnit());
    }

    @Test
    public void aReceiverOfUnknownTypeAddsNothing() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/use.ts",
                "export function go(a: any, b: string, c: string[]) { a.one(); b.two(); c.three(); }\n");
        assertTrue(members(component(result, "src.use.go")).toString(),
                members(component(result, "src.use.go")).isEmpty());
    }
}
