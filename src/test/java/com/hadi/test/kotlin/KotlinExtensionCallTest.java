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
 * A call {@code receiver.name(...)} references the file class of the extension function it reaches
 * when the receiver is a single name of known type, exactly one file class holds the extensions of
 * that name the file sees, one takes the arguments and extends the receiver's type, a type it inherits
 * from, or any type, and the receiver's type has no member of that name. A member always wins, and a
 * type whose ancestry cannot be read may have one.
 */
public class KotlinExtensionCallTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Types.kt", String.join("\n",
                        "package app",
                        "class Order { fun own(): Int = 1 }",
                        "open class Line",
                        "class Sub : Line()",
                        "class Odd : Line(), Missing",
                        "class Plain",
                        "fun Line.heavy(): Int = 4")),
                new ProjectFile("/lib/Ext.kt", String.join("\n",
                        "package lib",
                        "import app.Order",
                        "import app.Line",
                        "fun Order.total(): Int = 1",
                        "fun Line.weight(): Int = 2",
                        "fun Order.own(): Int = 3",
                        "fun String.shout(): String = this",
                        "fun <T> T.tap(): T = this",
                        "fun <T : Line> T.bounded(): T = this",
                        "fun Order.pair(a: Int, b: Int = 0): Int = 1",
                        "fun Order.pick(): Int = 1")),
                new ProjectFile("/lib2/Pick.kt", String.join("\n",
                        "package lib2",
                        "import app.Order",
                        "fun Order.pick(): Int = 2")),
                new ProjectFile("/app/ext/Send.kt", String.join("\n",
                        "package app.ext",
                        "import app.Order",
                        "fun Order.send(): Int = 1")),
                new ProjectFile("/app/Mail.kt", String.join("\n",
                        "package app",
                        "import app.ext.*",
                        "import io.lib.*",
                        "class Opaque { fun f(o: Order) = o.send() }")),
                new ProjectFile("/app/MailByName.kt", String.join("\n",
                        "package app",
                        "import app.ext.send",
                        "import io.lib.*",
                        "class Named { fun f(o: Order) = o.send() }")),
                new ProjectFile("/app/MailProject.kt", String.join("\n",
                        "package app",
                        "import app.ext.*",
                        "import java.util.*",
                        "class Plain2 { fun f(o: Order) = o.send() }")),
                new ProjectFile("/app/Use.kt", String.join("\n",
                        "package app",
                        "import lib.total",
                        "import lib.weight",
                        "import lib.own",
                        "import lib.shout",
                        "import lib.tap",
                        "import lib.bounded",
                        "import lib.pair",
                        "import lib.*",
                        "import lib2.*",
                        "class Use {",
                        "  fun onParam(o: Order) = o.total()",
                        "  fun onString(s: String) = s.shout()",
                        "  fun memberWins(o: Order) = o.own()",
                        "  fun onLocal(): Int { val l = Line(); return l.weight() }",
                        "  fun onSubtype(s: Sub) = s.weight()",
                        "  fun onSamePackage(l: Line) = l.heavy()",
                        "  fun generic(o: Order) = o.tap()",
                        "  fun bounded(o: Order, l: Line) = l.bounded()",
                        "  fun optionalArgument(o: Order) = o.pair(1)",
                        "  fun tooManyArguments(o: Order) = o.pair(1, 2, 3)",
                        "  fun ambiguous(o: Order) = o.pick()",
                        "  fun notFitting(p: Plain) = p.weight()",
                        "  fun unreadableAncestry(d: Odd) = d.weight()",
                        "  fun chained(o: Order) = o.also { }.total()",
                        "  fun redeclared(o: Order) { val f = { o: Line -> o.weight() } }",
                        "  fun narrowed(l: Line) { if (l is Sub) { }; l.weight() }",
                        "}",
                        "class Shadowing {",
                        "  fun Order.total(): Int = 5",
                        "  fun f(o: Order) = o.total()",
                        "}")))
                .model();
    }

    private static boolean reaches(final String member, final String fileClass) {
        return targets(component(model, member)).contains(fileClass);
    }

    @Test
    public void aCallOnATypedReceiverReferencesTheExtensionsFileClass() {
        assertTrue(reaches("app.Use.onParam(Order)", "lib.ExtKt"));
        assertTrue(reaches("app.Use.onString(String)", "lib.ExtKt"));
        assertTrue(reaches("app.Use.onLocal()", "lib.ExtKt"));
        assertTrue(reaches("app.Use.onSamePackage(Line)", "app.TypesKt"));
    }

    @Test
    public void anExtensionOfASupertypeOrOfAnyTypeAppliesToo() {
        assertTrue(reaches("app.Use.onSubtype(Sub)", "lib.ExtKt"));
        assertTrue(reaches("app.Use.generic(Order)", "lib.ExtKt"));
    }

    @Test
    public void aMemberOfTheReceiversTypeWins() {
        assertFalse(reaches("app.Use.memberWins(Order)", "lib.ExtKt"));
    }

    @Test
    public void aReceiverWhoseAncestryCannotBeReadMayHaveTheMember() {
        assertFalse(reaches("app.Use.unreadableAncestry(Odd)", "lib.ExtKt"));
    }

    @Test
    public void anExtensionTheReceiverDoesNotFitIsNotReached() {
        assertFalse(reaches("app.Use.notFitting(Plain)", "lib.ExtKt"));
        assertFalse(reaches("app.Use.bounded(Order, Line)", "lib.ExtKt"));
        assertFalse(reaches("app.Use.tooManyArguments(Order)", "lib.ExtKt"));
        assertTrue(reaches("app.Use.optionalArgument(Order)", "lib.ExtKt"));
    }

    @Test
    public void severalFileClassesHoldingTheNameAreAmbiguous() {
        assertFalse(reaches("app.Use.ambiguous(Order)", "lib.ExtKt"));
        assertFalse(reaches("app.Use.ambiguous(Order)", "lib2.PickKt"));
    }

    @Test
    public void onlyASingleNameOfKnownTypeIsAReceiver() {
        assertFalse(reaches("app.Use.chained(Order)", "lib.ExtKt"));
        assertFalse(reaches("app.Use.redeclared(Order)", "lib.ExtKt"));
        assertFalse(reaches("app.Use.narrowed(Line)", "lib.ExtKt"));
    }

    @Test
    public void anExtensionTheClassDeclaresItselfBindsFirst() {
        assertFalse(reaches("app.Shadowing.f(Order)", "lib.ExtKt"));
    }

    @Test
    public void aStarImportOfAPackageOutsideTheRepositoryMayHoldTheExtensionThatIsCalled() {
        assertFalse(reaches("app.Opaque.f(Order)", "app.ext.SendKt"));
    }

    @Test
    public void anExplicitImportByNameOrOnlyProjectAndJdkStarImportsStillCredit() {
        assertTrue(reaches("app.Named.f(Order)", "app.ext.SendKt"));
        assertTrue(reaches("app.Plain2.f(Order)", "app.ext.SendKt"));
    }
}
