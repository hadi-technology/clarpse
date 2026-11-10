package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A package-qualified receiver is read as a type whatever its first segment is, as long as nothing in
 * scope could be a value of that name: a type of the parse path is found by the type solver, and one
 * outside it is found by the shape of the name when no visible supertype could hide a field of that name.
 */
public class JavaUnlistedPackageRootReceiverTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/app/Plain.java", String.join("\n",
                "package app;",
                "public class Plain {",
                "  Object f = vendor.a.FieldInit.CONST;",
                "  void stmt() { vendor.b.StmtCall.run(); }",
                "  Object decl() { Object o = vendor.c.DeclInit.make(); return o; }",
                "  void chain() { vendor.g.ChainRecv.get().toString(); }",
                "  void shadowed(Object vendor) { vendor.x.Name.run(); }",
                "  void enumLike(Object q) { q.a.B.run(); }",
                "}")));
        files.insertFile(new ProjectFile("/src/app/Derived.java", String.join("\n",
                "package app;",
                "public class Derived extends other.base.Unseen {",
                "  void stmt() { vendor.b.StmtCall.run(); }",
                "}")));
        files.insertFile(new ProjectFile("/src/app/Wild.java", String.join("\n",
                "package app;",
                "import static other.base.Statics.*;",
                "public class Wild {",
                "  void stmt() { vendor.b.StmtCall.run(); }",
                "}")));
        files.insertFile(new ProjectFile("/src/app/Local.java", String.join("\n",
                "package app;",
                "public class Local {",
                "  void stmt() { vendor.b.StmtCall.run(); }",
                "}")));
        files.insertFile(new ProjectFile("/src/vendor/known/Known.java",
                "package vendor.known;\npublic class Known { public static Known make() { return null; } }\n"));
        files.insertFile(new ProjectFile("/src/app/UsesKnown.java", String.join("\n",
                "package app;",
                "public class UsesKnown {",
                "  Object m() { return vendor.known.Known.make(); }",
                "}")));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void unlistedRootIsReadAsAPackageWhenNoValueCanHaveThatName() {
        assertTrue(refs("app.Plain.stmt()").contains("vendor.b.StmtCall"));
        assertTrue(refs("app.Plain.decl()").contains("vendor.c.DeclInit"));
        assertTrue(refs("app.Plain.chain()").contains("vendor.g.ChainRecv"));
        assertTrue(refs("app.Plain").contains("vendor.a.FieldInit"));
        assertTrue(refs("app.Local.stmt()").contains("vendor.b.StmtCall"));
    }

    @Test
    public void aLocalValueOfThatNameIsNotAPackage() {
        assertFalse(refs("app.Plain.shadowed(Object)").stream().anyMatch(r -> r.startsWith("vendor")));
        assertFalse(refs("app.Plain.enumLike(Object)").stream().anyMatch(r -> r.startsWith("q.")));
    }

    @Test
    public void anUnseenSupertypeCouldDeclareAFieldOfThatName() {
        assertFalse(refs("app.Derived.stmt()").contains("vendor.b.StmtCall"));
    }

    @Test
    public void aStaticOnDemandImportCouldBringAFieldOfThatName() {
        assertFalse(refs("app.Wild.stmt()").contains("vendor.b.StmtCall"));
    }

    @Test
    public void aTypeOfTheParsedSetIsFoundWhateverItsRoot() {
        assertTrue(refs("app.UsesKnown.m()").contains("vendor.known.Known"));
    }
}
