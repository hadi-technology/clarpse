package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A call on a receiver of exactly known type references the member it calls, on the owning type. A
 * type of the repository owns it unless a readable ancestor declares it, a JDK type owns it when the
 * platform has a member of that name, and an extension function is owned by its file class.
 */
public class KotlinMemberReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/app/Types.kt", String.join("\n",
                        "package app",
                        "open class Base { fun run() {} }",
                        "class Sub : Base() { fun own() {} }",
                        "object Registry { fun lookup() {} }",
                        "class Plain",
                        "fun Plain.shout(): Int = 1")),
                new ProjectFile("/app/Use.kt", String.join("\n",
                        "package app",
                        "import java.io.File",
                        "class Use {",
                        "    fun go(s: Sub, f: File, p: Plain, u: Unknown) {",
                        "        s.run()",
                        "        s.own()",
                        "        f.exists()",
                        "        p.shout()",
                        "        Registry.lookup()",
                        "        u.nothing()",
                        "    }",
                        "}"))).model();
    }

    private static List<MemberReference> members() {
        final Component go = model.components().filter(c -> c.uniqueName().startsWith("app.Use.go("))
                .filter(c -> c.componentType().isMethodComponent()).findFirst().orElseThrow();
        return go.references().stream().filter(MemberReference.class::isInstance)
                .map(MemberReference.class::cast).collect(Collectors.toList());
    }

    private static MemberReference member(final String name) {
        final List<MemberReference> found = members().stream().filter(ref -> ref.member().equals(name))
                .collect(Collectors.toList());
        assertEquals(members().toString(), 1, found.size());
        assertEquals(found.get(0).ownerType(), found.get(0).invokedComponent());
        assertEquals(MemberKind.CALL, found.get(0).memberKind());
        return found.get(0);
    }

    @Test
    public void aMethodInheritedFromAReadableBaseIsOwnedByTheBase() {
        final MemberReference run = member("run");
        assertEquals("app.Base", run.ownerType());
        assertNull(run.externalUnit());
        assertEquals("app.Sub", member("own").ownerType());
    }

    @Test
    public void aJdkReceiverIsOwnedByItsTypeAndNamesItsPackage() {
        final MemberReference exists = member("exists");
        assertEquals("java.io.File", exists.ownerType());
        assertEquals("java.io", exists.externalUnit());
        assertTrue(exists.isExternal());
    }

    @Test
    public void aCallThroughAnObjectNameIsOwnedByTheObject() {
        assertEquals("app.Registry", member("lookup").ownerType());
    }

    @Test
    public void aReceiverOfUnknownTypeAddsNothing() {
        assertTrue(members().toString(), members().stream().noneMatch(ref -> ref.member().equals("nothing")));
    }

    @Test
    public void anExtensionCallIsAMemberOfTheFileClassBesideTheTypeReference() {
        final MemberReference shout = member("shout");
        assertEquals("app.TypesKt", shout.ownerType());
        final Component go = model.components().filter(c -> c.uniqueName().startsWith("app.Use.go("))
                .filter(c -> c.componentType().isMethodComponent()).findFirst().orElseThrow();
        assertTrue(targets(go).toString(), targets(go).contains("app.TypesKt"));
        assertTrue(go.references().stream().anyMatch(ref -> !(ref instanceof MemberReference)
                && ref.invokedComponent().equals("app.TypesKt")));
        assertEquals("app.Use", component(model, "app.Use").uniqueName());
    }
}
