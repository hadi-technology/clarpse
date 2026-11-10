package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.reference;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A name a star import of a {@code java} or {@code javax} package brings in resolves against the
 * JDK, as it does in Java. A library package outside the JDK cannot be asked, so a name only it
 * could declare stays unresolved and invents nothing.
 */
public class KotlinPlatformStarImportTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Local.kt", String.join("\n",
                        "package app",
                        "class Instant")),
                new ProjectFile("/app/B.kt", String.join("\n",
                        "package app",
                        "import java.util.*",
                        "import java.util.concurrent.*",
                        "import java.time.*",
                        "import ext.http.*",
                        "class B {",
                        "  val d: Date? = null",
                        "  val x: Executor? = null",
                        "  val i: Instant? = null",
                        "  val l: List<String> = listOf()",
                        "  val w: Widget? = null",
                        "  fun f() { val c = Client(); val u = UUID.randomUUID() }",
                        "}")),
                new ProjectFile("/other/C.kt", String.join("\n",
                        "package other",
                        "import java.util.*",
                        "import java.sql.*",
                        "class C { val d: Date? = null }")))
                .model();
    }

    @Test
    public void aNameTheJdkPackageDeclaresResolvesExactly() {
        assertEquals(ResolutionKind.EXACT, reference(component(model, "app.B.d"), "java.util.Date").resolutionKind());
        assertTrue(targets(component(model, "app.B.x")).contains("java.util.concurrent.Executor"));
        assertTrue(targets(component(model, "app.B.f()")).contains("java.util.UUID"));
    }

    @Test
    public void aTypeOfTheFilesPackageAndTheDefaultImportsComeFirst() {
        assertTrue(targets(component(model, "app.B.i")).contains("app.Instant"));
        assertFalse(targets(component(model, "app.B.i")).contains("java.time.Instant"));
        assertTrue(targets(component(model, "app.B.l")).contains("kotlin.collections.List"));
        assertFalse(targets(component(model, "app.B.l")).contains("java.util.List"));
    }

    @Test
    public void aNameOnlyALibraryPackageCouldDeclareStaysUnresolved() {
        assertEquals(ResolutionKind.UNRESOLVED, reference(component(model, "app.B.w"), "Widget").resolutionKind());
        assertFalse(targets(component(model, "app.B.f()")).stream().anyMatch(target -> target.endsWith("Client")));
    }

    @Test
    public void aNameTwoJdkPackagesDeclareIsAmbiguousAndBindsToNeither() {
        assertEquals(ResolutionKind.AMBIGUOUS, reference(component(model, "other.C.d"), "Date").resolutionKind());
    }
}
