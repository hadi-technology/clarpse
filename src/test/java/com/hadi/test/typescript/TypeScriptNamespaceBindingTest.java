package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * A reference to a namespace itself binds to a block only when exactly one file declares the
 * namespace. A namespace several files declare is ambiguous: the name is kept as written and bound
 * to none of the files' blocks, whichever file sorts first.
 */
public class TypeScriptNamespaceBindingTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static ComponentReference referenceNamed(final Component component, final String target) {
        return component.references().stream().filter(r -> r.invokedComponent().equals(target)).findFirst()
                .orElseThrow(() -> new AssertionError(component.uniqueName() + " has no reference to " + target
                        + ": " + component.references()));
    }

    @Test
    public void aNamespaceSeveralFilesDeclareIsAmbiguousAndBoundToNoBlock() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/a.ts", "namespace Geometry { export const a = 1; }\n",
                "/src/b.ts", "namespace Geometry { export const b = 2; }\n",
                "/src/use.ts", "export const whole = Geometry;\n");
        final Component whole = TypeScriptReferenceGapsTest.component(result, "src.use.whole");
        final Set<String> targets = TypeScriptReferenceGapsTest.targets(whole);
        assertFalse(targets.toString(), targets.contains("src.a.Geometry"));
        assertFalse(targets.toString(), targets.contains("src.b.Geometry"));
        final ComponentReference named = referenceNamed(whole, "Geometry");
        assertEquals(ResolutionKind.AMBIGUOUS, named.resolutionKind());
        assertTrue(named.isExternal());
    }

    @Test
    public void aNamespaceOneFileDeclaresInSeveralBlocksBindsToIt() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/a.ts", "namespace Geometry { export const a = 1; }\nnamespace Geometry { export const c = 3; }\n",
                "/src/use.ts", "export const whole = Geometry;\n");
        final Component whole = TypeScriptReferenceGapsTest.component(result, "src.use.whole");
        assertEquals(ResolutionKind.EXACT, referenceNamed(whole, "src.a.Geometry").resolutionKind());
    }

    @Test
    public void aMemberReadFromTheNamespaceIsNotBoundToAnotherFilesBlock() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/a.ts", "namespace Geometry { export const a = 1; }\n",
                "/src/b.ts", "namespace Geometry { export const b = 2; }\n",
                "/src/use.ts", "export const member = Geometry.b;\n");
        final Set<String> targets = TypeScriptReferenceGapsTest.targets(
                TypeScriptReferenceGapsTest.component(result, "src.use.member"));
        assertFalse(targets.toString(), targets.stream().anyMatch(t -> t.startsWith("src.a.Geometry")));
        assertEquals(ResolutionKind.AMBIGUOUS, referenceNamed(
                TypeScriptReferenceGapsTest.component(result, "src.use.member"), "Geometry").resolutionKind());
    }
}
