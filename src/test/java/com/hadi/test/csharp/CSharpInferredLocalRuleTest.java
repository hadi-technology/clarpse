package com.hadi.test.csharp;

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
import static org.junit.Assert.assertFalse;

/** A C# {@code var} local records what the same written type would, literals included. */
public class CSharpInferredLocalRuleTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(new ProjectFile("/U.cs", """
                namespace App {
                  public class Box<T> { }
                  public class Item { }
                  public class U {
                    public void Inferred() {
                      var i = 5; var l = 5L; var d = 1.5; var f = 1.5f; var m = 2m;
                      var s = "x"; var c = 'c'; var b = true; var box = new Box<Item>();
                    }
                    public void Written() {
                      int i = 5; long l = 5L; double d = 1.5; float f = 1.5f; decimal m = 2m;
                      string s = "x"; char c = 'c'; bool b = true; Box<Item> box = new Box<Item>();
                    }
                  }
                }
                """)).model();
    }

    private static Set<String> localReferences(final String method, final String local) {
        return model.components()
                .filter(c -> c.componentType() == ComponentType.LOCAL && c.name().equals(local)
                        && c.uniqueName().startsWith("App.U." + method + "("))
                .flatMap(c -> c.references().stream()).map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void everyInferredLocalRecordsWhatItsWrittenTypeRecords() {
        for (final String local : new String[] {"i", "l", "d", "f", "m", "s", "c", "b", "box"}) {
            final Set<String> written = localReferences("Written", local);
            assertFalse(local + " written is empty", written.isEmpty());
            assertEquals(local, written, localReferences("Inferred", local));
        }
    }

    @Test
    public void aNumberLiteralIsAnInt32() {
        assertEquals(Set.of("System.Int32"), localReferences("Inferred", "i"));
    }
}
