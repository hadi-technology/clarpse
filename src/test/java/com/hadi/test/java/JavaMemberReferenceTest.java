package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A call on a receiver whose type the solver gives exactly is a reference to the member called, on
 * the owning type: the declaring type when a parsed base declares it, the receiver's type otherwise.
 */
public class JavaMemberReferenceTest {

    private static OOPSourceCodeModel model(final String callerBody, final String... extraImports) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/v/Base.java", "package v; public class Base { public void run() { } }"));
        files.insertFile(new ProjectFile("/v/Sub.java", "package v; public class Sub extends Base { public void own() { } }"));
        files.insertFile(new ProjectFile("/v/Helper.java",
                "package v; public class Helper { public static void go() { } }"));
        files.insertFile(new ProjectFile("/v/C.java", "package v; " + String.join("", extraImports)
                + " public class C { void m(Sub s, Helper h, Unknown u, StringBuilder sb) { " + callerBody + " } }"));
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static List<MemberReference> members(final OOPSourceCodeModel model) {
        return model.components().filter(cmp -> cmp.uniqueName().startsWith("v.C.m("))
                .flatMap(cmp -> cmp.references().stream())
                .filter(MemberReference.class::isInstance).map(MemberReference.class::cast)
                .collect(Collectors.toList());
    }

    private static MemberReference only(final OOPSourceCodeModel model, final String member) {
        final List<MemberReference> found = members(model).stream()
                .filter(ref -> ref.member().equals(member)).collect(Collectors.toList());
        assertEquals(found.toString(), 1, found.size());
        return found.get(0);
    }

    @Test
    public void aMethodInheritedFromAParsedBaseIsOwnedByTheBase() throws Exception {
        final MemberReference ref = only(model("s.run(); s.own();"), "run");
        assertEquals("v.Base", ref.ownerType());
        assertEquals(ref.ownerType(), ref.invokedComponent());
        assertEquals(MemberKind.CALL, ref.memberKind());
        assertNull(ref.externalUnit());
        assertTrue(!ref.isExternal());
    }

    @Test
    public void aMethodTheReceiversTypeDeclaresIsOwnedByIt() throws Exception {
        assertEquals("v.Sub", only(model("s.run(); s.own();"), "own").ownerType());
    }

    @Test
    public void aLibraryReceiverIsOwnedByItsTypeAndNamesItsPackage() throws Exception {
        final MemberReference ref = only(model("sb.append(\"x\");"), "append");
        assertEquals("java.lang.StringBuilder", ref.ownerType());
        assertEquals(ref.ownerType(), ref.invokedComponent());
        assertEquals("java.lang", ref.externalUnit());
        assertTrue(ref.isExternal());
        assertEquals(ResolutionKind.EXACT, ref.resolutionKind());
    }

    @Test
    public void aNestedLibraryTypeKeepsItsPackageAsTheUnit() throws Exception {
        final OOPSourceCodeModel model = model("java.util.Map.Entry<String, String> e = null; e.getKey();");
        final MemberReference ref = only(model, "getKey");
        assertEquals("java.util.Map.Entry", ref.ownerType());
        assertEquals("java.util", ref.externalUnit());
    }

    @Test
    public void aStaticCallThroughATypeNameIsOwnedByTheType() throws Exception {
        final OOPSourceCodeModel model = model("Helper.go(); java.util.Collections.emptyList();");
        assertEquals("v.Helper", only(model, "go").ownerType());
        final MemberReference library = only(model, "emptyList");
        assertEquals("java.util.Collections", library.ownerType());
        assertEquals("java.util", library.externalUnit());
    }

    @Test
    public void aReceiverOfUnknownTypeAddsNothing() throws Exception {
        final OOPSourceCodeModel model = model("u.foo(); Missing.bar();");
        assertTrue(members(model).toString(), members(model).isEmpty());
    }

    @Test
    public void aLibraryTypeTheSolverCannotReadAddsNoMemberReference() throws Exception {
        final OOPSourceCodeModel model = model("org.example.Foo f = null; f.bar(); other.Thing.stat();",
                "import org.example.Foo;");
        assertTrue(members(model).toString(), members(model).isEmpty());
    }
}
