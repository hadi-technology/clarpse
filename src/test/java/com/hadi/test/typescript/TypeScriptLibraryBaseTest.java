package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** A class that extends a type of the language's own library records that type as its base. */
public class TypeScriptLibraryBaseTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Set<String> bases(final String source, final String name) throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts", source);
        final Component component = TypeScriptReferenceGapsTest.component(result, name);
        return component.references().stream().filter(r -> r instanceof TypeExtensionReference)
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    @Test
    public void errorIsTheBaseOfAnErrorClass() throws Exception {
        final Set<String> bases = bases("export class Failure extends Error {}\n", "src.a.Failure");
        assertTrue(bases.toString(), bases.contains("Error"));
        assertFalse(bases.toString(), bases.contains("any"));
    }

    @Test
    public void otherLibraryClassesAreRecordedByName() throws Exception {
        final String source = "export class Lookup extends Map<string, number> {}\n"
                + "export class Items extends Array<string> {}\n"
                + "export class Target extends EventTarget {}\n"
                + "export class Slow extends Promise<void> {}\n"
                + "export class Typed extends Uint8Array {}\n";
        assertTrue(bases(source, "src.a.Lookup").toString(), bases(source, "src.a.Lookup").contains("Map"));
        assertTrue(bases(source, "src.a.Items").contains("Array"));
        assertTrue(bases(source, "src.a.Target").contains("EventTarget"));
        assertTrue(bases(source, "src.a.Slow").contains("Promise"));
        assertTrue(bases(source, "src.a.Typed").contains("Uint8Array"));
    }

    @Test
    public void aBaseFromThisRepositoryIsStillThatBase() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/b.ts", "export class Base {}\n",
                "/src/a.ts", "import { Base } from './b';\nexport class Child extends Base {}\n");
        final Set<String> bases = TypeScriptReferenceGapsTest.component(result, "src.a.Child").references().stream()
                .filter(r -> r instanceof TypeExtensionReference).map(ComponentReference::invokedComponent)
                .collect(Collectors.toSet());
        assertTrue(bases.toString(), bases.contains("src.b.Base"));
    }

    @Test
    public void aBaseImportedFromAnUnreadPackageIsStillThatPackagesType() throws Exception {
        final Set<String> bases = bases("import { Widget } from 'extlib';\nexport class Mine extends Widget {}\n",
                "src.a.Mine");
        assertTrue(bases.toString(), bases.contains("extlib:Widget"));
    }
}
