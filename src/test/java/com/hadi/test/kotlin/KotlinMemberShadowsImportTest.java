package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin looks a name up in the closest scope first: the locals, then the members of the class
 * including the ones it inherits, then the imports. A call whose name a member of the class, or of
 * a supertype the repository declares, carries binds there and adds no reference to the import. A
 * supertype the repository does not declare changes nothing: the import still binds the name.
 */
public class KotlinMemberShadowsImportTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/lib/Util.kt", String.join("\n",
                        "package lib",
                        "fun log(msg: String) {}",
                        "class Defaults",
                        "class Config")),
                new ProjectFile("/app/A.kt", String.join("\n",
                        "package app",
                        "import lib.log",
                        "import lib.Defaults",
                        "import lib.Config",
                        "open class Parent { fun log(msg: String) {} }",
                        "open class Quiet",
                        "class Own { fun log(msg: String) {}; fun f() { log(\"x\") } }",
                        "class Free { fun f() { log(\"x\") } }",
                        "class Child : Parent() { fun f() { log(\"x\") } }",
                        "class Grandchild : Quiet() { fun f() { log(\"x\") } }",
                        "class Unreadable : Missing() { fun f() { log(\"x\") } }",
                        "class Holder { fun Defaults(): Int = 1; val d = Defaults() }",
                        "class Plain { val d = Defaults() }",
                        "class Reading : Quiet() { val d = Defaults() }",
                        "class Hidden : Missing() { val d = Defaults() }",
                        "class Nested { class Config; fun f() = Config() }")))
                .model();
    }

    private static boolean references(final String name, final String target) {
        return targets(component(model, name)).contains(target);
    }

    @Test
    public void aMemberOfTheClassBindsTheCallBeforeAnImport() {
        assertFalse(references("app.Own.f()", "lib.UtilKt"));
        assertFalse(references("app.Holder.d", "lib.Defaults"));
    }

    @Test
    public void aMemberOfAReadableSupertypeBindsTheCallToo() {
        assertFalse(references("app.Child.f()", "lib.UtilKt"));
    }

    @Test
    public void anImportIsCreditedWhenNothingCloserCanDeclareTheName() {
        assertTrue(references("app.Free.f()", "lib.UtilKt"));
        assertTrue(references("app.Grandchild.f()", "lib.UtilKt"));
        assertTrue(references("app.Plain.d", "lib.Defaults"));
        assertTrue(references("app.Reading.d", "lib.Defaults"));
    }

    @Test
    public void aSupertypeTheRepositoryDoesNotDeclareDoesNotUnbindAnImport() {
        assertTrue(references("app.Unreadable.f()", "lib.UtilKt"));
    }

    @Test
    public void anImportedClassIsStillConstructedWhenASupertypeCannotBeRead() {
        assertTrue(references("app.Hidden.d", "lib.Defaults"));
    }

    @Test
    public void aNestedTypeStillBeatsTheImport() {
        assertTrue(references("app.Nested.f()", "app.Nested.Config"));
        assertFalse(references("app.Nested.f()", "lib.Config"));
    }
}
