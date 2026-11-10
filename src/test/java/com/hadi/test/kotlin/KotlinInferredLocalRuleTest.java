package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/** A Kotlin local without a written type records what the same written type would, literals included. */
public class KotlinInferredLocalRuleTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(new ProjectFile("/app/U.kt", String.join("\n",
                "package app",
                "class Box",
                "class U {",
                "    fun inferred() {",
                "        val i = 5",
                "        val l = 5L",
                "        val d = 1.5",
                "        val f = 1.5f",
                "        val s = \"x\"",
                "        val c = 'c'",
                "        val b = true",
                "        val box = Box()",
                "    }",
                "    fun written() {",
                "        val i: Int = 5",
                "        val l: Long = 5L",
                "        val d: Double = 1.5",
                "        val f: Float = 1.5f",
                "        val s: String = \"x\"",
                "        val c: Char = 'c'",
                "        val b: Boolean = true",
                "        val box: Box = Box()",
                "    }",
                "}"))).model();
    }

    private static Set<String> localReferences(final String function, final String local) {
        return model.components()
                .filter(c -> c.componentType() == ComponentType.LOCAL && c.name().equals(local)
                        && c.uniqueName().startsWith("app.U." + function + "("))
                .flatMap(c -> c.references().stream()).map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void everyInferredLocalRecordsWhatItsWrittenTypeRecords() {
        for (final String local : new String[] {"i", "l", "d", "f", "s", "c", "b", "box"}) {
            final Set<String> written = localReferences("written", local);
            assertEquals(local + " written is empty", false, written.isEmpty());
            assertEquals(local, written, localReferences("inferred", local));
        }
    }

    @Test
    public void aNumberLiteralIsAKotlinInt() {
        assertEquals(Set.of("kotlin.Int"), localReferences("inferred", "i"));
    }
}
