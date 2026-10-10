package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin's visibilities reach the model so a consumer can tell public from not public: what is
 * written is kept, {@code internal} included, and a declaration with none written is
 * {@code public}, Kotlin's default.
 */
public class KotlinModifiersTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(new ProjectFile("/app/Svc.kt", String.join("\n",
                "package app",
                "",
                "class Svc(private val repo: Repo, var mode: Int) {",
                "    val size: Int = 0",
                "    internal fun reset() { }",
                "    protected open fun hook() { }",
                "    private lateinit var cache: String",
                "    suspend fun load() { }",
                "}",
                "internal class Repo",
                "private class Secret",
                "open class Base",
                "abstract class Shape",
                "sealed interface Event",
                "data class Point(val x: Int)",
                "value class Id(val raw: String)",
                "object Single",
                "private fun helper() { }",
                "internal val counter = 0"))).model();
    }

    @Test
    public void aDeclarationWithNoVisibilityWrittenIsPublic() {
        assertEquals(List.of("public"), List.copyOf(component(model, "app.Svc").modifiers()));
        assertTrue(component(model, "app.Svc.size").modifiers().contains("public"));
        assertTrue(component(model, "app.Svc.load()").modifiers().contains("public"));
        assertTrue(component(model, "app.Single").modifiers().contains("public"));
    }

    @Test
    public void writtenVisibilitiesAreKept() {
        assertEquals(List.of("internal"), List.copyOf(component(model, "app.Repo").modifiers()));
        assertEquals(List.of("private"), List.copyOf(component(model, "app.Secret").modifiers()));
        assertEquals(List.of("internal"), List.copyOf(component(model, "app.Svc.reset()").modifiers()));
        assertTrue(component(model, "app.Svc.hook()").modifiers().contains("protected"));
        assertTrue(component(model, "app.Svc.cache").modifiers().contains("private"));
        assertFalse(component(model, "app.Svc.hook()").modifiers().contains("public"));
    }

    @Test
    public void constructorPropertiesCarryTheirOwnVisibility() {
        assertTrue(component(model, "app.Svc.repo").modifiers().contains("private"));
        assertTrue(component(model, "app.Svc.mode").modifiers().contains("public"));
    }

    @Test
    public void topLevelVisibilitiesReachTheFileClassMembers() {
        assertTrue(component(model, "app.SvcKt.helper()").modifiers().contains("private"));
        assertTrue(component(model, "app.SvcKt.counter").modifiers().contains("internal"));
        assertTrue(component(model, "app.SvcKt").modifiers().contains("public"));
    }

    @Test
    public void aValIsFinalAndAVarIsNot() {
        assertTrue(component(model, "app.Svc.size").modifiers().contains("final"));
        assertTrue(component(model, "app.Svc.repo").modifiers().contains("final"));
        assertFalse(component(model, "app.Svc.mode").modifiers().contains("final"));
        assertFalse(component(model, "app.Svc.cache").modifiers().contains("final"));
    }

    @Test
    public void kotlinDeclarationModifiersAreKept() {
        assertTrue(component(model, "app.Base").modifiers().contains("open"));
        assertTrue(component(model, "app.Shape").modifiers().contains("abstract"));
        assertTrue(component(model, "app.Event").modifiers().contains("sealed"));
        assertTrue(component(model, "app.Point").modifiers().contains("data"));
        assertTrue(component(model, "app.Id").modifiers().contains("value"));
        assertTrue(component(model, "app.Svc.hook()").modifiers().contains("open"));
        assertTrue(component(model, "app.Svc.cache").modifiers().contains("lateinit"));
        assertTrue(component(model, "app.Svc.load()").modifiers().contains("suspend"));
    }

    @Test
    public void everyModifierIsOneTheModelCanResolve() {
        model.components().forEach(component -> component.modifiers().forEach(modifier ->
                assertNotNull(component.uniqueName() + " carries " + modifier,
                        OOPSourceModelConstants.accessModifier(modifier))));
    }
}
