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

/**
 * What a `declare module "specifier" { ... }` block declares is a declaration of the file that
 * holds the block, named as that file's own declarations are, so an augmentation is not invisible.
 */
public class TypeScriptAmbientModuleAugmentationTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    @Test
    public void anInterfaceOfAnAugmentationIsAComponentOfTheFile() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts",
                "declare module '@ext/schema' { interface GlobalProps { mine: string; other: Marker } }\n"
                        + "export interface Marker { id: number }\n");
        final Component props = TypeScriptReferenceGapsTest.component(result, "src.a.GlobalProps");
        assertTrue(props.componentType() == ComponentType.INTERFACE);
        assertTrue(props.modifiers().contains("export"));
        assertTrue(props.children().contains("src.a.GlobalProps.mine"));
        assertTrue(TypeScriptReferenceGapsTest.targets(props).contains("src.a.Marker"));
    }

    @Test
    public void classesAliasesAndConstantsOfTheBlockAreModelled() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts",
                "declare module 'ambient-pkg' {\n"
                        + "  export class Client { run(): void }\n"
                        + "  export function connect(): Client { return new Client(); }\n"
                        + "  export type Mode = 'a' | 'b';\n"
                        + "  export const VERSION: string;\n"
                        + "}\n");
        for (final String name : new String[] {"Client", "connect", "Mode", "VERSION"}) {
            assertTrue(name, result.model().component("src.a." + name).isPresent());
        }
    }

    @Test
    public void aNameTheFileAlreadyDeclaresIsKeptOnce() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts",
                "export interface Props { own: number }\n"
                        + "declare module 'x' { interface Props { extra: string } }\n"
                        + "declare module 'y' { interface Shared { a: string } }\n"
                        + "declare module 'z' { interface Shared { b: string } }\n");
        final Component props = TypeScriptReferenceGapsTest.component(result, "src.a.Props");
        assertTrue(props.children().contains("src.a.Props.own"));
        assertFalse(props.children().contains("src.a.Props.extra"));
        assertTrue(result.model().component("src.a.Shared").isPresent());
    }

    @Test
    public void aNamespaceIsNotReadAsAnAmbientModule() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts",
                "namespace Inner { export interface Hidden { a: string } }\nexport const SEEN = 1;\n");
        assertFalse(result.model().component("src.a.Hidden").isPresent());
        assertTrue(result.model().component("src.a.SEEN").isPresent());
    }
}
