package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The scope a simple name is read in, in the order of JLS 6.4.1 and 7.5: member types of enclosing
 * types, declared or inherited; single-type imports; top-level types of the current package; and
 * last the on-demand imports, java.lang among them.
 */
public class JavaScopeOrderTest {

    private static OOPSourceCodeModel model;

    private static void add(final ProjectFiles files, final String path, final String... lines) {
        files.insertFile(new ProjectFile(path, String.join("\n", lines)));
    }

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        final String root = "/src/main/java/";
        add(files, root + "lib/Holder.java", "package lib;", "public class Holder {",
                "  public static class URL { public static int K = 1; }",
                "  public static final int LIMIT = 2;", "}");
        add(files, root + "lib/Guard.java", "package lib;", "public class Guard { }");
        add(files, root + "p/Guard.java", "package p;", "public class Guard { }");
        add(files, root + "p/Process.java", "package p;", "public class Process { }");
        add(files, root + "p/Base.java", "package p;", "public class Base { public static class Inner { } }");
        // a static import of an all-caps nested type is an edge to that type, not to its holder
        add(files, root + "q/StaticNested.java", "package q;", "import static lib.Holder.URL;",
                "public class StaticNested { Object f() { return URL.K; } }");
        add(files, root + "q/StaticConstant.java", "package q;", "import static lib.Holder.LIMIT;",
                "public class StaticConstant { int f() { return LIMIT; } }");
        // a single-type import beats a type of the same package in another file
        add(files, root + "p/ImportWins.java", "package p;", "import lib.Guard;",
                "public class ImportWins { Guard g; }");
        // an enclosing member type beats a single-type import
        add(files, root + "p/MemberWins.java", "package p;", "import lib.Guard;",
                "public class MemberWins { static class Guard { } Guard g; }");
        // an inherited member type beats a single-type import
        add(files, root + "p/InheritedWins.java", "package p;", "import lib.Guard;",
                "public class InheritedWins extends Base { Inner i; }");
        add(files, root + "p/InheritedBeatsImport.java", "package p;", "import lib.Inner;",
                "public class InheritedBeatsImport extends Base { Inner i; }");
        add(files, root + "lib/Inner.java", "package lib;", "public class Inner { }");
        // a type of the current package beats java.lang
        add(files, root + "p/PackageBeatsLang.java", "package p;",
                "public class PackageBeatsLang { Process proc; }");
        // a field chain the solver cannot place is not a package-qualified type
        add(files, root + "p/PhantomPackage.java", "package p;",
                "public class PhantomPackage extends Missing { Object f() { return config.limits.MAX; } }");
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final String component) {
        return model.copyOfComponent(component).orElseThrow(() -> new AssertionError("no " + component))
                .references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void staticImportOfAnAllCapsNestedTypeIsAnEdgeToThatType() {
        assertTrue(refs("q.StaticNested").toString(), refs("q.StaticNested").contains("lib.Holder.URL"));
    }

    @Test
    public void staticImportOfAConstantIsAnEdgeToItsHolder() {
        assertTrue(refs("q.StaticConstant").contains("lib.Holder"));
        assertFalse(refs("q.StaticConstant").contains("lib.Holder.LIMIT"));
    }

    @Test
    public void singleTypeImportBeatsASamePackageType() {
        assertTrue(refs("p.ImportWins").toString(), refs("p.ImportWins").contains("lib.Guard"));
        assertFalse(refs("p.ImportWins").contains("p.Guard"));
    }

    @Test
    public void enclosingMemberTypeBeatsASingleTypeImport() {
        assertTrue(refs("p.MemberWins").toString(), refs("p.MemberWins").contains("p.MemberWins.Guard"));
        assertFalse(refs("p.MemberWins").contains("lib.Guard"));
    }

    @Test
    public void inheritedMemberTypeBeatsASingleTypeImport() {
        assertTrue(refs("p.InheritedBeatsImport").toString(),
                refs("p.InheritedBeatsImport").contains("p.Base.Inner"));
        assertFalse(refs("p.InheritedBeatsImport").contains("lib.Inner"));
    }

    @Test
    public void sameNamedPackageTypeBeatsJavaLang() {
        assertTrue(refs("p.PackageBeatsLang").toString(), refs("p.PackageBeatsLang").contains("p.Process"));
        assertFalse(refs("p.PackageBeatsLang").contains("java.lang.Process"));
    }

    @Test
    public void unplaceableFieldChainNamesNoPhantomPackage() {
        assertTrue(refs("p.PhantomPackage").toString(),
                refs("p.PhantomPackage").stream().noneMatch(r -> r.startsWith("config")));
    }
}
