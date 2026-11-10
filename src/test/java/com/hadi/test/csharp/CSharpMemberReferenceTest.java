package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A call on a receiver of exactly known type references the member it calls, on the owning type: the
 * nearest parsed type that declares it, the extension's static class for an extension method, and
 * the {@code System} type for a built-in type in a file whose usings add no extension methods.
 */
public class CSharpMemberReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        namespace App;
                        public class Base { public void Run() { } }
                        public class Sub : Base { public void Own() { } }
                        public static class Util { public static void Go() { } }
                        public class Plain { }
                        public static class PlainExt { public static int Shout(this Plain p) => 1; }
                        """),
                new ProjectFile("/Use.cs", """
                        namespace App;
                        public class Use
                        {
                            public void M(Sub s, Plain p, string str, Unknown u)
                            {
                                s.Run(); s.Own();
                                Util.Go();
                                p.Shout();
                                str.Trim();
                                u.Nothing();
                            }
                        }
                        """),
                new ProjectFile("/Linq.cs", """
                        using System.Linq;
                        namespace App;
                        public class WithLinq { public void M(string str) { str.Select(1); } }
                        """)).model();
    }

    private static List<MemberReference> members(final String methodPrefix) {
        final Component method = model.components().filter(c -> c.uniqueName().startsWith(methodPrefix))
                .filter(c -> c.componentType().isMethodComponent()).findFirst().orElseThrow();
        return method.references().stream().filter(MemberReference.class::isInstance)
                .map(MemberReference.class::cast).collect(Collectors.toList());
    }

    private static MemberReference member(final String name) {
        final List<MemberReference> found = members("App.Use.M(").stream()
                .filter(ref -> ref.member().equals(name)).collect(Collectors.toList());
        assertEquals(members("App.Use.M(").toString(), 1, found.size());
        assertEquals(found.get(0).ownerType(), found.get(0).invokedComponent());
        assertEquals(MemberKind.CALL, found.get(0).memberKind());
        return found.get(0);
    }

    @Test
    public void aMethodInheritedFromAParsedBaseIsOwnedByTheBase() {
        assertEquals("App.Base", member("Run").ownerType());
        assertNull(member("Run").externalUnit());
        assertEquals("App.Sub", member("Own").ownerType());
    }

    @Test
    public void aBuiltInTypeIsOwnedBySystemTypeAndNamesTheNamespace() {
        final MemberReference trim = member("Trim");
        assertEquals("System.String", trim.ownerType());
        assertEquals("System", trim.externalUnit());
        assertTrue(trim.isExternal());
    }

    @Test
    public void aStaticCallThroughATypeNameIsOwnedByTheType() {
        assertEquals("App.Util", member("Go").ownerType());
    }

    @Test
    public void aReceiverOfUnknownTypeAddsNothing() {
        assertTrue(members("App.Use.M(").stream().noneMatch(ref -> ref.member().equals("Nothing")));
    }

    @Test
    public void anExtensionCallIsAMemberOfTheStaticClassBesideTheTypeReference() {
        assertEquals("App.PlainExt", member("Shout").ownerType());
        final Component method = model.components().filter(c -> c.uniqueName().startsWith("App.Use.M("))
                .filter(c -> c.componentType().isMethodComponent()).findFirst().orElseThrow();
        assertTrue(method.references().stream().anyMatch(ref -> !(ref instanceof MemberReference)
                && ref.invokedComponent().equals("App.PlainExt")));
    }

    @Test
    public void aUsingThatMayBringInAnExtensionMethodKeepsABuiltInTypeUnowned() {
        assertTrue(members("App.WithLinq.M(").toString(), members("App.WithLinq.M(").isEmpty());
    }
}
