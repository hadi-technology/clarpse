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

/**
 * A name that several star imports supply is ambiguous: it is kept as written, marked
 * {@code AMBIGUOUS}, and bound to none of the candidates.
 */
public class KotlinAmbiguousStarImportTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/one/Dup.kt", "package one\nclass Dup\nclass Solo\n"),
                new ProjectFile("/two/Dup.kt", "package two\nclass Dup\n"),
                new ProjectFile("/app/Use.kt", String.join("\n",
                        "package app",
                        "import one.*",
                        "import two.*",
                        "import java.util.*",
                        "import javax.swing.*",
                        "class Use(val dup: Dup, val solo: Solo, val timer: Timer, val gone: Missing)"))).model();
    }

    @Test
    public void aNameTwoRepositoryStarImportsSupplyIsAmbiguous() {
        assertEquals(ResolutionKind.AMBIGUOUS, reference(component(model, "app.Use.dup"), "Dup").resolutionKind());
        assertFalse(targets(component(model, "app.Use.dup")).contains("one.Dup"));
        assertFalse(targets(component(model, "app.Use.dup")).contains("two.Dup"));
    }

    @Test
    public void aNameTwoPlatformStarImportsSupplyIsAmbiguous() {
        assertEquals(ResolutionKind.AMBIGUOUS, reference(component(model, "app.Use.timer"), "Timer").resolutionKind());
    }

    @Test
    public void aNameOneStarImportSuppliesIsExact() {
        assertEquals(ResolutionKind.EXACT, reference(component(model, "app.Use.solo"), "one.Solo").resolutionKind());
    }

    @Test
    public void aNameNothingSuppliesIsUnresolved() {
        assertEquals(ResolutionKind.UNRESOLVED,
                reference(component(model, "app.Use.gone"), "Missing").resolutionKind());
    }
}
