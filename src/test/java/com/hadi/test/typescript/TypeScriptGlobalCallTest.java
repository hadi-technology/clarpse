package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A call of a global function names the function: exact where the checker resolves it to a
 * default-library or {@code @types} declaration, unresolved where no declaration answers to it. A callee
 * the project declares, a parameter, a local and an import add nothing new, and a call through an object
 * names no global.
 */
public class TypeScriptGlobalCallTest {

    private static final String WITH_LIB = "{ \"compilerOptions\": { \"target\": \"es2020\","
            + " \"lib\": [\"es2020\", \"dom\"] }, \"include\": [\".\"] }";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static CompileResult withLib(final String source) throws Exception {
        return TypeScriptReferenceGapsTest.compileWithConfig(WITH_LIB, "/a.ts", source);
    }

    private static CompileResult noConfig(final String source) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/a.ts", source));
        return new ClarpseProject(files, Lang.TYPESCRIPT).result();
    }

    private static Component go(final CompileResult result) {
        return result.model().components().filter(c -> c.uniqueName().startsWith("a.A.go"))
                .findFirst().orElseThrow(() -> new AssertionError("no go()"));
    }

    private static List<String> references(final CompileResult result) {
        return go(result).references().stream()
                .map(r -> r.invokedComponent() + " " + r.resolutionKind())
                .sorted().collect(Collectors.toList());
    }

    @Test
    public void aLibraryGlobalIsExactWhenTheProgramHasALibrary() throws Exception {
        final List<String> found = references(withLib("""
                export class A {
                  go() { fetch("/x"); setTimeout(() => {}, 1); GM_setValue("k", 1); }
                }
                declare function GM_setValue(k: string, v: unknown): void;
                """));
        Assert.assertTrue(found.toString(), found.contains("fetch EXACT"));
        Assert.assertTrue(found.toString(), found.contains("setTimeout EXACT"));
        Assert.assertTrue(found.toString(), found.contains("a.GM_setValue EXACT"));
        Assert.assertFalse(found.toString(), found.contains("GM_setValue UNRESOLVED"));
    }

    @Test
    public void aGlobalNothingDeclaresIsUnresolvedAsWritten() throws Exception {
        final List<String> found = references(noConfig("""
                export class A { go() { fetch("/x"); GM_setValue("k", 1); } }
                """));
        Assert.assertTrue(found.toString(), found.contains("fetch UNRESOLVED"));
        Assert.assertTrue(found.toString(), found.contains("GM_setValue UNRESOLVED"));
    }

    @Test
    public void aDeclaredAmbientFunctionOfTheProjectIsTheDeclaration() throws Exception {
        final List<String> found = references(withLib("""
                export class A { go() { GM_setValue("k", 1); } }
                declare function GM_setValue(k: string, v: unknown): void;
                """));
        Assert.assertTrue(found.toString(), found.contains("a.GM_setValue EXACT"));
        Assert.assertFalse(found.toString(), found.stream().anyMatch(r -> r.startsWith("GM_setValue ")));
    }

    @Test
    public void aLocalNamedLikeTheGlobalAddsNothing() throws Exception {
        final List<String> found = references(withLib("""
                export class A { go() { const fetch = (u: string) => u; fetch("a"); } }
                """));
        Assert.assertFalse(found.toString(), found.stream().anyMatch(r -> r.startsWith("fetch ")));
    }

    @Test
    public void aParameterNamedLikeTheGlobalAddsNothing() throws Exception {
        final List<String> found = references(withLib("""
                export class A { go(fetch: (u: string) => string) { fetch("a"); } }
                """));
        Assert.assertFalse(found.toString(), found.stream().anyMatch(r -> r.startsWith("fetch ")));
    }

    @Test
    public void anImportedFunctionIsNamedByItsPackageOnce() throws Exception {
        final List<String> found = references(withLib("""
                import fetch from "node-fetch";
                export class A { go() { fetch("a"); } }
                """));
        Assert.assertEquals(1L, found.stream().filter(r -> r.contains("fetch")).count());
        Assert.assertTrue(found.toString(), found.contains("node-fetch:fetch EXACT"));
    }

    @Test
    public void aCallThroughAnObjectKeepsTodaysOutput() throws Exception {
        final List<String> found = references(withLib("""
                export class A { go() { window.fetch("a"); globalThis.fetch("a"); console.log("a"); } }
                """));
        Assert.assertFalse(found.toString(), found.stream().anyMatch(r -> r.equals("fetch EXACT")
                || r.equals("fetch UNRESOLVED") || r.equals("log EXACT")));
    }

    @Test
    public void aConstructionIsNotChanged() throws Exception {
        final List<String> found = references(withLib("""
                export class A { go() { new Date(); new Map(); } }
                """));
        Assert.assertFalse(found.toString(), found.stream().anyMatch(r -> r.equals("Date UNRESOLVED")));
        Assert.assertTrue(found.toString(), found.stream().noneMatch(r -> r.startsWith("Date ")
                && !r.endsWith("EXACT")));
    }

    @Test
    public void aRequireCallIsNotAGlobalFunction() throws Exception {
        final List<String> found = references(noConfig("""
                export class A { go() { const x = require("pkg"); return x; } }
                """));
        Assert.assertFalse(found.toString(), found.stream().anyMatch(r -> r.startsWith("require ")));
    }

    @Test
    public void theReferenceIsASimpleTypeReference() throws Exception {
        final ComponentReference reference = go(withLib("export class A { go() { fetch('/x'); } }\n"))
                .references().stream().filter(r -> r.invokedComponent().equals("fetch"))
                .findFirst().orElseThrow();
        Assert.assertTrue(reference instanceof SimpleTypeReference);
        Assert.assertEquals(ResolutionKind.EXACT, reference.resolutionKind());
    }
}
