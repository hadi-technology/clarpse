package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** A TypeScript local without a written type records what the same written type would. */
public class TypeScriptInferredLocalRuleTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Set<String> localReferences(final CompileResult result, final String function, final String local) {
        return result.model().components()
                .filter(c -> c.componentType() == ComponentType.LOCAL && c.name().equals(local)
                        && c.uniqueName().startsWith("src.a." + function + "."))
                .flatMap(c -> c.references().stream()).map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void anInferredLocalRecordsWhatItsWrittenTypeRecords() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile(
                "/src/b.ts", "export class Item {}\nexport interface Pair<T> { first: T }\n",
                "/src/a.ts", "import { Item, Pair } from './b';\n"
                        + "export function inferred(): void { const item = new Item(); const items = [new Item()];"
                        + " const pair: Pair<Item> | undefined = undefined; }\n"
                        + "export function written(): void { const item: Item = new Item(); const items: Item[] = [new Item()]; }\n");
        for (final String local : new String[] {"item", "items"}) {
            final Set<String> written = localReferences(result, "written", local);
            assertFalse(local + " written is empty", written.isEmpty());
            assertEquals(local, written, localReferences(result, "inferred", local));
        }
    }
}
