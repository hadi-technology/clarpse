package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * A TypeScript reference reports how its target was arrived at: {@code EXACT} for a declaration the
 * checker settled, in the repository or in an imported package, and {@code UNRESOLVED} for a name
 * that is only kept as written.
 */
public class TypeScriptResolutionKindTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static ResolutionKind kindOf(final Component component, final String target) {
        for (final ComponentReference reference : component.references()) {
            if (reference.invokedComponent().equals(target)) {
                return reference.resolutionKind();
            }
        }
        throw new AssertionError(component.uniqueName() + " has no reference to " + target + ": "
                + component.references());
    }

    private static CompileResult compile() throws Exception {
        return TypeScriptReferenceGapsTest.compile(
                "/src/b.ts", "export class Base {}\n",
                "/src/a.ts", "import { Widget } from 'extlib';\nimport { Base } from './b';\n"
                        + "@Mystery()\nexport class A extends Base { w!: Widget; }\n");
    }

    @Test
    public void aRepositoryDeclarationIsExact() throws Exception {
        final CompileResult result = compile();
        assertEquals(ResolutionKind.EXACT,
                kindOf(TypeScriptReferenceGapsTest.component(result, "src.a.A"), "src.b.Base"));
    }

    @Test
    public void aTypeImportedFromAPackageIsExact() throws Exception {
        final CompileResult result = compile();
        assertEquals(ResolutionKind.EXACT,
                kindOf(TypeScriptReferenceGapsTest.component(result, "src.a.A.w"), "extlib:Widget"));
    }

    @Test
    public void aDecoratorNothingDeclaresIsKeptAsWrittenAndUnresolved() throws Exception {
        final CompileResult result = compile();
        assertEquals(ResolutionKind.UNRESOLVED,
                kindOf(TypeScriptReferenceGapsTest.component(result, "src.a.A"), "Mystery"));
    }
}
