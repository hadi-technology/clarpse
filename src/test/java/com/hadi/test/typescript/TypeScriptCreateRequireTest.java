package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** A `require` function made by `createRequire` loads modules as `require` itself does. */
public class TypeScriptCreateRequireTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static final String SOURCE = "import { createRequire } from 'module';\n"
            + "const load = createRequire(import.meta.url);\n"
            + "export function loc() { const { app } = load('electron'); return app; }\n"
            + "export function whole() { return load('left-pad'); }\n"
            + "export function unrelated(load2: (id: string) => any) { return load2('not-a-module'); }\n";

    @Test
    public void aModuleLoadedThroughTheMadeRequireIsAnImport() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts", SOURCE);
        final Set<String> imports = Set.copyOf(TypeScriptReferenceGapsTest.component(result, "src.a.loc").imports());
        assertTrue(imports.toString(), imports.contains("electron"));
        assertTrue(imports.toString(), imports.contains("left-pad"));
    }

    @Test
    public void aBindingFromTheLoadedModuleIsAReferenceToThatPackage() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts", SOURCE);
        final Set<String> refs = TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, "src.a.loc"));
        assertTrue(refs.toString(), refs.contains("electron:app"));
    }

    @Test
    public void anyOtherFunctionCalledWithAStringIsNotAModuleLoad() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts", SOURCE);
        final Set<String> imports = Set.copyOf(TypeScriptReferenceGapsTest.component(result, "src.a.unrelated").imports());
        assertFalse(imports.toString(), imports.contains("not-a-module"));
    }
}
