package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.ImportDirective;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The names a literal {@code await import("m")} binds are bound like the names of a static import:
 * the directive carries them, and what is used through them references the loaded declarations.
 */
public class TypeScriptDynamicImportBindingTest {

    private static final String CONFIG = "{\"compilerOptions\":{\"target\":\"es2020\",\"module\":\"esnext\","
            + "\"moduleResolution\":\"bundler\",\"strict\":true},\"include\":[\"src\"]}";
    private static final String KEYS = "export class Keys { sign(): string { return \"\"; } }\n"
            + "export function hash(s: string): string { return s; }\nexport const LIMIT = 5;\n";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static CompileResult compile(final boolean withConfig, final String dyn) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        if (withConfig) {
            files.insertFile(new ProjectFile("/tsconfig.json", CONFIG));
        }
        files.insertFile(new ProjectFile("/src/core/keys.ts", KEYS));
        files.insertFile(new ProjectFile("/src/ui/dyn.ts", dyn));
        return new ClarpseProject(files, Lang.TYPESCRIPT).result();
    }

    private static Component dyn(final CompileResult result) {
        return result.model().components().filter(c -> c.uniqueName().matches(".*dyn\\.Dyn\\.run\\([^)]*\\)")).findFirst().orElseThrow();
    }

    private static List<String> refs(final Component component) {
        return component.references().stream().map(r -> r.getClass().getSimpleName() + " "
                + r.invokedComponent() + (r instanceof MemberReference ? " " + ((MemberReference) r).member() : ""))
                .sorted().collect(Collectors.toList());
    }

    private static final String DESTRUCTURED = "export class Dyn {\n  async run() {\n"
            + "    const { Keys, hash } = await import('../core/keys');\n    hash('x');\n"
            + "    return new Keys().sign();\n  }\n}\n";

    private void assertBound(final CompileResult result) {
        final List<String> found = refs(dyn(result));
        Assert.assertTrue(found.toString(), found.contains("SimpleTypeReference src.core.keys.hash"));
        Assert.assertTrue(found.toString(), found.contains("SimpleTypeReference src.core.keys.Keys"));
        Assert.assertTrue(found.toString(), found.contains("MemberReference src.core.keys.Keys sign"));
        Assert.assertTrue(found.toString(), found.contains("ConstructionReference src.core.keys.Keys"));
    }

    @Test
    public void destructuredNamesWithoutAConfig() throws Exception {
        assertBound(compile(false, DESTRUCTURED));
    }

    @Test
    public void destructuredNamesWithAConfig() throws Exception {
        assertBound(compile(true, DESTRUCTURED));
    }

    @Test
    public void theDirectiveCarriesTheNames() throws Exception {
        final CompileResult result = compile(false, DESTRUCTURED);
        final Component owner = result.model().components().filter(c -> c.uniqueName().endsWith("dyn.Dyn"))
                .findFirst().orElseThrow();
        final ImportDirective directive = owner.importDirectives().get(0);
        Assert.assertEquals(List.of("Keys", "hash"), directive.names());
        Assert.assertEquals(List.of("Keys", "hash"), directive.binds());
        Assert.assertFalse(directive.importsModule());
        Assert.assertTrue(directive.isDynamic());
    }

    @Test
    public void aNamespaceBindingHasAnAlias() throws Exception {
        for (final boolean withConfig : new boolean[] {true, false}) {
            final CompileResult result = compile(withConfig, "export class Dyn {\n  async run() {\n"
                    + "    const ns = await import('../core/keys');\n    return ns.hash('x') + ns.LIMIT;\n  }\n}\n");
            final Component owner = result.model().components().filter(c -> c.uniqueName().endsWith("dyn.Dyn"))
                    .findFirst().orElseThrow();
            final ImportDirective directive = owner.importDirectives().get(0);
            Assert.assertEquals("ns", directive.alias());
            Assert.assertTrue(directive.importsModule());
            Assert.assertTrue(withConfig + " " + refs(dyn(result)),
                    refs(dyn(result)).contains("SimpleTypeReference src.core.keys.hash"));
        }
    }

    @Test
    public void aParenthesisedAwaitIsRead() throws Exception {
        final CompileResult result = compile(false, "export class Dyn {\n  async run() {\n"
                + "    return (await import('../core/keys')).hash('x');\n  }\n}\n");
        Assert.assertTrue(refs(dyn(result)).toString(), refs(dyn(result)).contains("SimpleTypeReference src.core.keys.hash"));
    }

    @Test
    public void aNonLiteralArgumentAddsNothing() throws Exception {
        final CompileResult result = compile(false, "export class Dyn {\n  async run(p: string) {\n"
                + "    const { hash } = await import(p);\n    return hash('x');\n  }\n}\n");
        Assert.assertTrue(refs(dyn(result)).toString(),
                refs(dyn(result)).stream().noneMatch(r -> r.contains("src.core.keys")));
    }

    @Test
    public void aLocalThatShadowsTheNameAddsNothing() throws Exception {
        final CompileResult result = compile(false, "export class Dyn {\n  async run() {\n"
                + "    await import('../core/keys');\n    { const hash = (s: string) => s; hash('x'); }\n"
                + "    return 1;\n  }\n}\n");
        Assert.assertTrue(refs(dyn(result)).toString(),
                !refs(dyn(result)).contains("SimpleTypeReference src.core.keys.hash"));
    }
}
