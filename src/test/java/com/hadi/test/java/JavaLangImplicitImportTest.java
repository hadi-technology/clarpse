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
 * Every public top-level type of {@code java.lang} is imported implicitly, whichever JDK runs the
 * analysis, and a name is read case-sensitively. A type of the current package, or one a file
 * imports by name, wins over the implicit import.
 */
public class JavaLangImplicitImportTest {

    private static OOPSourceCodeModel model;

    private static void add(final ProjectFiles files, final String path, final String... lines) {
        files.insertFile(new ProjectFile(path, String.join("\n", lines)));
    }

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        final String root = "/src/main/java/";
        add(files, root + "u/Uses.java", "package u;",
                "public class Uses {",
                "  Void v; Record r; Module m; ProcessHandle h; StackWalker w; MatchException e;",
                "}");
        add(files, root + "u/Lower.java", "package u;", "public class Lower { void f() { void_ v; record r; } }");
        // a type of the same package beats java.lang
        add(files, root + "p/Module.java", "package p;", "public class Module { }");
        add(files, root + "p/SamePackage.java", "package p;", "public class SamePackage { Module m; }");
        // a single-type import beats java.lang
        add(files, root + "lib/Record.java", "package lib;", "public class Record { }");
        add(files, root + "q/Imported.java", "package q;", "import lib.Record;",
                "public class Imported { Record r; }");
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final String component) {
        return model.copyOfComponent(component).orElseThrow(() -> new AssertionError("no " + component))
                .references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void typesAddedAfterTheOriginalListResolveToJavaLang() {
        final Set<String> refs = refs("u.Uses");
        for (final String type : new String[] {"Void", "Record", "ProcessHandle", "StackWalker",
                "MatchException"}) {
            assertTrue(type + " in " + refs, refs.contains("java.lang." + type));
        }
    }

    @Test
    public void moduleResolvesToJavaLangWhenNothingCloserDeclaresOne() {
        assertTrue(refs("u.Uses").toString(), refs("u.Uses").contains("java.lang.Module"));
    }

    @Test
    public void aNameIsCaseSensitive() {
        assertFalse(refs("u.Lower").toString(), refs("u.Lower").contains("java.lang.Void"));
        assertFalse(refs("u.Lower").contains("java.lang.Record"));
    }

    @Test
    public void aTypeOfTheSamePackageBeatsJavaLang() {
        assertTrue(refs("p.SamePackage").contains("p.Module"));
        assertFalse(refs("p.SamePackage").contains("java.lang.Module"));
    }

    @Test
    public void aSingleTypeImportBeatsJavaLang() {
        assertTrue(refs("q.Imported").contains("lib.Record"));
        assertFalse(refs("q.Imported").contains("java.lang.Record"));
    }
}
