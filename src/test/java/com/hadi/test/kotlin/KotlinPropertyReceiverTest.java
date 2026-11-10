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
 * Properties of the enclosing class, {@code this}, and the class itself are receivers whose type is
 * known: a property with a written type, own or of a readable supertype, a constructor
 * {@code val}, {@code this.name}, a bare name that names a property, and a call with no receiver.
 * A local or parameter of the same name hides the property, and a member, an unreadable ancestry,
 * an extension or a lambda still defeat the edge.
 */
public class KotlinPropertyReceiverTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Types.kt", String.join("\n",
                        "package app",
                        "class Order",
                        "class Line",
                        "class Bar",
                        "open class Base { val inherited: Order = Order(); fun make(): Bar = Bar() }")),
                new ProjectFile("/lib/Ext.kt", String.join("\n",
                        "package lib",
                        "import app.Order",
                        "import app.Line",
                        "fun Order.total(): Int = 1",
                        "fun Line.weight(): Int = 2")),
                new ProjectFile("/app/Host.kt", String.join("\n",
                        "package app",
                        "import lib.total",
                        "import lib.weight",
                        "class Host(val ctorProp: Order, plain: Order) : Base() {",
                        "  val prop: Order = Order()",
                        "  var untyped = Order()",
                        "  fun property() = prop.total()",
                        "  fun constructorProperty() = ctorProp.total()",
                        "  fun plainParameter() = plain.total()",
                        "  fun throughThis() = this.prop.total()",
                        "  fun inherited() = inherited.total()",
                        "  fun unwritten() = untyped.total()",
                        "  fun shadowedByLocal() { val prop: Line = Line(); prop.total() }",
                        "  fun shadowedByParameter(prop: Line) = prop.total()",
                        "  fun lambda() = with(prop) { prop.total() }",
                        "  fun Line.extension() = prop.total()",
                        "  fun inferredFromMember() { val b = make() }",
                        "  fun inferredThroughThis() { val b = this.make() }",
                        "  fun inferredFromProperty() { val i = inherited }",
                        "  fun companionCall() { val o = Maker.create(); o.total() }",
                        "}",
                        "class Odd : Missing() {",
                        "  val prop: Order = Order()",
                        "  fun property() = prop.total()",
                        "  fun make(): Bar = Bar()",
                        "  fun inferred() { val b = make() }",
                        "}")),
                new ProjectFile("/app/Self.kt", String.join("\n",
                        "package app",
                        "class Heavy : Base() { fun probe() = weight() }",
                        "fun Heavy.weight() = 1")),
                new ProjectFile("/app/Own.kt", String.join("\n",
                        "package app",
                        "import lib.weight",
                        "class Own { fun probe() = weight(); fun weight(x: Int = 0) = 1 }",
                        "class Maker { companion object { fun create(): Order = Order() } }")))
                .model();
    }

    private static boolean reaches(final String member) {
        return targets(component(model, member)).contains("lib.ExtKt");
    }

    @Test
    public void aPropertyWithAWrittenTypeIsAReceiver() {
        assertTrue(reaches("app.Host.property()"));
        assertTrue(reaches("app.Host.constructorProperty()"));
        assertTrue(reaches("app.Host.throughThis()"));
        assertTrue(reaches("app.Host.inherited()"));
    }

    @Test
    public void aPropertyWithoutAWrittenTypeOrAPlainParameterIsNot() {
        assertFalse(reaches("app.Host.unwritten()"));
        assertFalse(reaches("app.Host.plainParameter()"));
    }

    @Test
    public void aLocalOrParameterOfTheSameNameHidesTheProperty() {
        assertFalse(reaches("app.Host.shadowedByLocal()"));
        assertFalse(reaches("app.Host.shadowedByParameter(Line)"));
    }

    @Test
    public void aLambdaOrAnExtensionMayGiveTheNameAnotherReceiver() {
        assertFalse(reaches("app.Host.lambda()"));
        assertFalse(reaches("app.Host.extension(Line)"));
    }

    @Test
    public void anUnreadableAncestryMayDeclareTheMember() {
        assertFalse(reaches("app.Odd.property()"));
        assertFalse(targets(component(model, "app.Odd.inferred()")).contains("app.Bar"));
    }

    @Test
    public void aLocalInferredFromAMemberOfTheClassIsItsDeclaredType() {
        assertTrue(targets(component(model, "app.Host.inferredFromMember()")).contains("app.Bar"));
        assertTrue(targets(component(model, "app.Host.inferredThroughThis()")).contains("app.Bar"));
        assertTrue(targets(component(model, "app.Host.inferredFromProperty()")).contains("app.Order"));
    }

    @Test
    public void aCallThroughALocalTypedByACompanionMemberIsResolved() {
        assertTrue(reaches("app.Host.companionCall()"));
    }

    @Test
    public void aCallWithNoReceiverReachesAnExtensionOfTheEnclosingClass() {
        assertTrue(targets(component(model, "app.Heavy.probe()")).contains("app.SelfKt"));
    }

    @Test
    public void aMemberOfTheEnclosingClassWinsOverTheExtension() {
        assertFalse(targets(component(model, "app.Own.probe()")).contains("lib.ExtKt"));
    }
}
