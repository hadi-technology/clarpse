package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Java reports how each type reference was resolved: {@code EXACT} when the scoping rules pick one
 * declaration, {@code AMBIGUOUS} when several are visible, {@code UNRESOLVED} when none is.
 */
public class JavaResolutionKindTest {

    private static OOPSourceCodeModel model() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/a/Lim.java", "package a;\npublic class Lim { }\n"));
        files.insertFile(new ProjectFile("/src/p1/Dup.java", "package p1;\npublic class Dup { }\n"));
        files.insertFile(new ProjectFile("/src/p2/Dup.java", "package p2;\npublic class Dup { }\n"));
        files.insertFile(new ProjectFile("/src/q/Same.java", "package q;\npublic class Same { }\n"));
        files.insertFile(new ProjectFile("/src/q/User.java", String.join("\n",
                "package q;",
                "import a.Lim;",
                "import java.util.List;",
                "import p1.*;",
                "import p2.*;",
                "import org.lib.*;",
                "public class User {",
                "  Lim imported;",
                "  List<String> jdk;",
                "  Same samePackage;",
                "  Widget unknown;",
                "  Dup both;",
                "  x.y.Qualified written;",
                "  String lang;",
                "  @Marker void m() { }",
                "}")));
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static ResolutionKind kindOf(final String owner, final String target) throws Exception {
        final OOPSourceCodeModel model = model();
        for (final ComponentReference ref : model.copyOfComponent(owner).orElseThrow().references()) {
            if (ref.invokedComponent().equals(target)) {
                return ref.resolutionKind();
            }
        }
        throw new AssertionError("no reference to " + target + " from " + owner + ": "
                + model.copyOfComponent(owner).orElseThrow().references());
    }

    @Test
    public void importedTypeIsExact() throws Exception {
        assertEquals(ResolutionKind.EXACT, kindOf("q.User.imported", "a.Lim"));
    }

    @Test
    public void jdkImportAndJavaLangAreExact() throws Exception {
        assertEquals(ResolutionKind.EXACT, kindOf("q.User.jdk", "java.util.List"));
        assertEquals(ResolutionKind.EXACT, kindOf("q.User.lang", "java.lang.String"));
    }

    @Test
    public void typeOfTheSamePackageIsExact() throws Exception {
        assertEquals(ResolutionKind.EXACT, kindOf("q.User.samePackage", "q.Same"));
    }

    @Test
    public void fullyQualifiedNameIsExact() throws Exception {
        assertEquals(ResolutionKind.EXACT, kindOf("q.User.written", "x.y.Qualified"));
    }

    @Test
    public void nameNothingInScopeSuppliesIsUnresolvedAndKeptAsWritten() throws Exception {
        assertEquals(ResolutionKind.UNRESOLVED, kindOf("q.User.unknown", "Widget"));
    }

    @Test
    public void annotationNothingInScopeSuppliesIsUnresolved() throws Exception {
        assertEquals(ResolutionKind.UNRESOLVED, kindOf("q.User.m()", "Marker"));
    }

    @Test
    public void nameTwoOnDemandImportsSupplyIsAmbiguousAndBindsToNeitherType() throws Exception {
        final OOPSourceCodeModel model = model();
        assertEquals(ResolutionKind.AMBIGUOUS, kindOf("q.User.both", "Dup"));
        for (final ComponentReference ref : model.copyOfComponent("q.User.both").orElseThrow().references()) {
            assertTrue(ref.invokedComponent(), !ref.invokedComponent().equals("p1.Dup")
                    && !ref.invokedComponent().equals("p2.Dup"));
        }
    }
}
