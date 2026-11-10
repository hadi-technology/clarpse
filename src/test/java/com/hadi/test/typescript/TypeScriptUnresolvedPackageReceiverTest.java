package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * A method called on an object whose declared type is imported from a package that is not
 * installed is a reference to that package's type and the method, as it is when the package's
 * declarations can be read.
 */
public class TypeScriptUnresolvedPackageReceiverTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Set<String> targets(final String name, final String source) throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts", source);
        return TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, name));
    }

    @Test
    public void methodOnAParameterTypedFromAnUnreadPackage() throws Exception {
        final Set<String> refs = targets("src.a.go", "import { Editor } from 'extlib';\n"
                + "export function go(editor: Editor) { return editor.getBounds(); }\n");
        assertTrue(refs.toString(), refs.contains("extlib:Editor.getBounds"));
    }

    @Test
    public void methodOnALocalAndOnAThisPropertyAreRead() throws Exception {
        final Set<String> local = targets("src.a.go", "import { Editor } from 'extlib';\n"
                + "export function go() { const e: Editor = make(); return e.getBounds(); }\n"
                + "declare function make(): any;\n");
        assertTrue(local.toString(), local.contains("extlib:Editor.getBounds"));
        final Set<String> property = targets("src.a.Host.run()", "import { Editor } from 'extlib';\n"
                + "export class Host { constructor(private editor: Editor) {}\n"
                + "  run() { return this.editor.getBounds(); } }\n");
        assertTrue(property.toString(), property.contains("extlib:Editor.getBounds"));
    }

    @Test
    public void nullableAnnotationAndRenamedImportNameTheExportedType() throws Exception {
        final Set<String> refs = targets("src.a.go", "import { Editor as Ed } from 'extlib';\n"
                + "export function go(editor: Ed | null) { return editor?.getBounds(); }\n");
        assertTrue(refs.toString(), refs.contains("extlib:Editor.getBounds"));
    }

    @Test
    public void receiverWithoutAnImportedAnnotationNamesNothing() throws Exception {
        final Set<String> refs = targets("src.a.go", "import { Editor } from 'extlib';\n"
                + "export function go(a: any, b: Editor | Date, c: Local) { a.one(); b.two(); c.three(); }\n"
                + "interface Local { three(): void }\n");
        assertFalse(refs.toString(), refs.stream().anyMatch(r -> r.endsWith(".one") || r.endsWith(".two")
                || r.endsWith(".three")));
    }

    @Test
    public void typeOfThisRepositoryFromARelativeImportIsNotAPackageType() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/b.ts", "export interface Panel { open(): void }\n",
                "/src/a.ts", "import { Panel } from './b';\nexport function go(p: Panel) { p.open(); }\n");
        final Set<String> refs = TypeScriptReferenceGapsTest.targets(
                TypeScriptReferenceGapsTest.component(result, "src.a.go"));
        assertFalse(refs.toString(), refs.stream().anyMatch(r -> r.contains(":Panel")));
    }
}
