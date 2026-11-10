package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A local declared without a type depends on the type of its initializer when a declaration the
 * repository holds writes that type: the return type of a function called on a receiver of known
 * type or at the top level, the type of a property. A receiver is known when its type is written or
 * it is constructed plainly, and nothing is inferred beyond what is written.
 */
public class KotlinInferredLocalTypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Types.kt", String.join("\n",
                        "package app",
                        "class Bar",
                        "class Baz",
                        "class Qux",
                        "class Item",
                        "class Wrap<T>",
                        "class Elem",
                        "open class Foo {",
                        "  fun bar(): Bar = Bar()",
                        "  fun nul(): Bar? = null",
                        "  fun gen(): Wrap<Elem>? = null",
                        "  fun arr(): Array<Elem> = arrayOf()",
                        "  fun over(): Bar = Bar()",
                        "  fun over(x: Int): Baz = Baz()",
                        "  fun bare() = Elem()",
                        "  fun <T> param(): T = throw IllegalStateException()",
                        "}",
                        "class Sub : Foo()",
                        "class Odd : Missing() { fun bar(): Bar = Bar() }",
                        "object Registry { val current: Baz = Baz() }",
                        "class Holder { val item: Item = Item() }",
                        "class Library { fun count(): Int = 1; fun id(): java.util.UUID = java.util.UUID.randomUUID() }",
                        "fun makeQux(): Qux = Qux()",
                        "fun twice(): Bar = Bar()",
                        "fun twice(x: Int): Baz = Baz()")),
                new ProjectFile("/app/Use.kt", String.join("\n",
                        "package app",
                        "class Use {",
                        "  fun fromCall(f: Foo) { val b = f.bar() }",
                        "  fun fromObjectProp() { val z = Registry.current }",
                        "  fun fromTopFun() { val q = makeQux() }",
                        "  fun fromParamProp(h: Holder) { val i = h.item }",
                        "  fun fromConstructed() { val f = Foo(); val b = f.bar() }",
                        "  fun fromInherited(s: Sub) { val b = s.bar() }",
                        "  fun nullable(f: Foo) { val b = f.nul() }",
                        "  fun generic(f: Foo) { val w = f.gen() }",
                        "  fun array(f: Foo) { val a = f.arr() }",
                        "  fun overloaded(f: Foo) { val o = f.over() }",
                        "  fun twiceDeclared() { val t = twice() }",
                        "  fun unwritten(f: Foo) { val u = f.bare() }",
                        "  fun typeParameter(f: Foo) { val t = f.param<Int>() }",
                        "  fun unreadableAncestry(o: Odd) { val b = o.bar() }",
                        "  fun unknownReceiver(x: Unknown) { val b = x.bar() }",
                        "  fun redeclared(f: Foo) { val g = { f: Holder -> f.item }; val b = f.bar() }",
                        "  fun narrowed(f: Foo) { if (f is Sub) { }; val b = f.bar() }",
                        "  fun assigned(f: Foo) { var g: Foo = f; g = Sub(); val b = g.bar() }",
                        "  fun chained(h: Holder) { val i = h.item.toString() }",
                        "  fun library(l: Library) { val n = l.count(); val u = l.id() }",
                        "  fun writtenInt(l: Library) { val n: Int = 1 }",
                        "}")))
                .model();
    }

    private static Set<String> targetsOf(final String member) {
        return targets(component(model, "app.Use." + member));
    }

    @Test
    public void aCallOnAReceiverOfAWrittenTypeIsTheFunctionsDeclaredType() {
        assertTrue(targetsOf("fromCall(Foo)").contains("app.Bar"));
        assertTrue(targetsOf("nullable(Foo)").contains("app.Bar"));
    }

    @Test
    public void aPropertyOfAnObjectOrAParameterIsItsDeclaredType() {
        assertTrue(targetsOf("fromObjectProp()").contains("app.Baz"));
        assertTrue(targetsOf("fromParamProp(Holder)").contains("app.Item"));
    }

    @Test
    public void aTopLevelFunctionCallIsItsDeclaredReturnType() {
        assertTrue(targetsOf("fromTopFun()").contains("app.Qux"));
    }

    @Test
    public void aReceiverConstructedPlainlyOrInheritedFromIsKnown() {
        assertTrue(targetsOf("fromConstructed()").contains("app.Bar"));
        assertTrue(targetsOf("fromInherited(Sub)").contains("app.Bar"));
    }

    @Test
    public void genericsArraysTypeParametersAndUnwrittenTypesAreNotInferred() {
        assertFalse(targetsOf("generic(Foo)").contains("app.Elem"));
        assertFalse(targetsOf("generic(Foo)").contains("app.Wrap"));
        assertFalse(targetsOf("array(Foo)").contains("app.Elem"));
        assertFalse(targetsOf("unwritten(Foo)").contains("app.Elem"));
        assertEquals(Set.of("app.Foo", "kotlin.Int"), targetsOf("typeParameter(Foo)"));
    }

    @Test
    public void overloadsThatDisagreeAddNothing() {
        assertFalse(targetsOf("overloaded(Foo)").contains("app.Bar"));
        assertFalse(targetsOf("overloaded(Foo)").contains("app.Baz"));
        assertFalse(targetsOf("twiceDeclared()").contains("app.Bar"));
        assertFalse(targetsOf("twiceDeclared()").contains("app.Baz"));
    }

    @Test
    public void aReceiverWhoseTypeOrAncestryIsUnknownAddsNothing() {
        assertFalse(targetsOf("unreadableAncestry(Odd)").contains("app.Bar"));
        assertFalse(targetsOf("unknownReceiver(Unknown)").contains("app.Bar"));
    }

    @Test
    public void aNameDeclaredAgainOrNarrowedOrAssignedIsNotKnown() {
        assertFalse(targetsOf("redeclared(Foo)").contains("app.Bar"));
        assertFalse(targetsOf("narrowed(Foo)").contains("app.Bar"));
        assertFalse(targetsOf("assigned(Foo)").contains("app.Bar"));
    }

    @Test
    public void aChainThroughAnUnknownMemberAddsNothing() {
        assertFalse(targetsOf("chained(Holder)").contains("app.Item"));
    }

    @Test
    public void aLibraryTypeIsRecordedAsAWrittenTypeIs() {
        assertTrue(targetsOf("library(Library)").contains("kotlin.Int"));
        assertTrue(targetsOf("writtenInt(Library)").contains("kotlin.Int"));
        assertTrue(targetsOf("library(Library)").contains("java.util.UUID"));
    }
}
