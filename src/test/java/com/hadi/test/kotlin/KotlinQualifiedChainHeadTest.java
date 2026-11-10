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
 * A dotted chain in an expression is read as a package-qualified type only when no value in scope
 * could carry its first segment: not a parameter, a local, a lambda parameter, a member, a top-level
 * property, nor a name an unparsed supertype may declare.
 */
public class KotlinQualifiedChainHeadTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Cfg.kt", String.join("\n",
                        "package app",
                        "class Mode { val Value: String = \"\" }",
                        "class Cfg { val mode = Mode() }",
                        "val settings = Cfg()")),
                new ProjectFile("/lib/Util.kt", String.join("\n",
                        "package app.lib",
                        "object Util { fun run() {} }")),
                new ProjectFile("/web/Use.kt", String.join("\n",
                        "package web",
                        "import app.settings",
                        "open class Base",
                        "class Use {",
                        "  val member = 1",
                        "  fun repo() { app.lib.Util.run() }",
                        "  fun ext() { vendor.b.StmtCall.run() }",
                        "  fun param(vendor: Any) { vendor.x.Name.run() }",
                        "  fun prop(cfg: app.Cfg) { cfg.mode.Value.length }",
                        "  fun local() { val vendor = 1; vendor.l.Name.run() }",
                        "  fun lambda() { listOf(1).forEach { vendor -> vendor.p.Name.run() } }",
                        "  fun loop() { for (vendor in listOf(1)) { vendor.q.Name.run() } }",
                        "  fun field() { member.f.Name.run() }",
                        "  fun imported() { settings.i.Name.run() }",
                        "}",
                        "class Inheriting : Base() {",
                        "  fun unknown() { vendor.u.Name.run() }",
                        "  fun rooted() { org.known.Name.run() }",
                        "}")))
                .model();
    }

    private static boolean reads(final String member, final String chain) {
        return targets(component(model, "web.Use." + member)).contains(chain);
    }

    @Test
    public void aPackageQualifiedChainStillResolves() {
        assertTrue(reads("repo()", "app.lib.Util"));
        assertTrue(reads("ext()", "vendor.b.StmtCall"));
    }

    @Test
    public void aParameterIsNotAPackage() {
        assertFalse(reads("param(Any)", "vendor.x.Name"));
        assertFalse(reads("prop(app.Cfg)", "cfg.mode.Value"));
    }

    @Test
    public void aLocalALambdaParameterAndALoopVariableAreNotPackages() {
        assertFalse(reads("local()", "vendor.l.Name"));
        assertFalse(reads("lambda()", "vendor.p.Name"));
        assertFalse(reads("loop()", "vendor.q.Name"));
    }

    @Test
    public void aMemberOrAnImportedTopLevelPropertyIsNotAPackage() {
        assertFalse(reads("field()", "member.f.Name"));
        assertFalse(reads("imported()", "settings.i.Name"));
    }

    @Test
    public void anUnparsedSupertypeMayDeclareTheFirstSegment() {
        final java.util.Set<String> unknown = targets(component(model, "web.Inheriting.unknown()"));
        assertFalse(unknown.toString(), unknown.contains("vendor.u.Name"));
        assertTrue(targets(component(model, "web.Inheriting.rooted()")).contains("org.known.Name"));
    }
}
