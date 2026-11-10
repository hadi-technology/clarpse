package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Java model does not depend on how a tree reaches the parser, and a name is bound to a type
 * only when the project, or an import, provides one.
 */
public class JavaCrossLanguageParityTest {

    private static Map<String, String> tree() {
        final Map<String, String> tree = new TreeMap<>();
        tree.put("src/main/java/app/Error.java", "package app;\npublic class Error { }\n");
        tree.put("src/main/java/app/Anno.java", "package app;\npublic @interface Anno { }\n");
        tree.put("src/main/java/app/Qux.java", "package app;\npublic class Qux { public static final int LIMIT = 3; }\n");
        tree.put("src/main/java/app/Sib.java", "package app;\npublic class Sib { }\n");
        tree.put("src/main/java/app/A.java", String.join("\n",
                "package app;",
                "@Anno",
                "public class A {",
                "  Error e;",
                "  Sib s;",
                "  int f() { return Qux.LIMIT; }",
                "}"));
        tree.put("src/main/java/app/sub/B.java", String.join("\n",
                "package app.sub;",
                "import app.Qux;",
                "public class B {",
                "  int f() { return Qux.LIMIT; }",
                "}"));
        tree.put("src/main/java/app/Marker.java", "package app;\npublic interface Marker { }\n");
        tree.put("src/main/java/app/sub/C.java", String.join("\n",
                "package app.sub;",
                "import app.*;",
                "public class C implements Marker {",
                "  Sib s;",
                "  int f() { return Qux.LIMIT + new Sib().hashCode(); }",
                "}"));
        return tree;
    }

    private static Path writeTree(final Map<String, String> tree) throws Exception {
        final Path dir = Files.createTempDirectory(
                Path.of(System.getProperty("java.io.tmpdir")), "parity");
        for (final Map.Entry<String, String> file : tree.entrySet()) {
            final Path target = dir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        return dir;
    }

    private static ProjectFiles fromZip(final Map<String, String> tree) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (final Map.Entry<String, String> file : tree.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new ProjectFiles(new ByteArrayInputStream(bytes.toByteArray()));
    }

    /** The directory prefix is stripped from a directory load's paths, leaving the path inside the tree. */
    private static String inTree(final String path, final String directory) {
        if (!directory.isEmpty() && path.startsWith(directory + "/")) {
            return path.substring(directory.length());
        }
        return path;
    }

    private static Set<String> describe(final Component[] components, final String directory) {
        final Set<String> lines = new TreeSet<>();
        for (final Component c : components) {
            final Set<String> imported = new TreeSet<>();
            c.importedFiles().forEach(f -> imported.add(inTree(f, directory)));
            lines.add("C|" + c.uniqueName() + "|" + c.componentType() + "|"
                    + inTree(c.sourceFile(), directory) + "|" + imported);
            for (final ComponentReference ref : c.references()) {
                lines.add("R|" + c.uniqueName() + "|" + ref.getClass().getSimpleName() + "|"
                        + ref.invokedComponent() + "|" + ref.isExternal() + "|" + ref.resolutionKind());
            }
        }
        return lines;
    }

    private static Component[] components(final ProjectFiles files) throws Exception {
        return new ClarpseProject(files, Lang.JAVA).result().model().components().toList()
                .toArray(new Component[0]);
    }

    private static Set<String> describeDirectory(final Path dir) throws Exception {
        return describe(components(new ProjectFiles(dir.toString())), dir.toString());
    }

    private static OOPSourceCodeModel model(final ProjectFiles files) throws Exception {
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @Test
    public void aDirectoryGivesTheModelItsArchiveGivesOnceThePathsAreNormalised() throws Exception {
        final Set<String> fromDir = describeDirectory(writeTree(tree()));
        final Set<String> fromZip = describe(components(fromZip(tree())), "");
        assertEquals(fromZip, fromDir);
    }

    @Test
    public void aDirectoryBindsSamePackageTypesAnnotationsAndConstants() throws Exception {
        final Set<String> lines = describeDirectory(writeTree(tree()));
        final String all = lines.toString();
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A.e|") && l.contains("|app.Error|false|")));
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A.s|") && l.contains("|app.Sib|false|")));
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A|AnnotationReference|app.Anno|false|")));
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A.f()|") && l.contains("|app.Qux|false|")));
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.sub.C|") && l.contains("|app.Marker|false|")));
        assertFalse(all, all.contains("java.lang.Error"));
    }

    @Test
    public void aDirectoryKeepsTheAbsoluteOnDiskPaths() throws Exception {
        final Path dir = writeTree(tree());
        final String root = dir.toFile().getAbsolutePath();
        final ProjectFiles files = new ProjectFiles(dir.toString());
        final Set<String> paths = new TreeSet<>();
        for (final ProjectFile f : files.files(Lang.JAVA)) {
            paths.add(f.path());
        }
        assertTrue(paths.toString(), paths.contains(root + "/src/main/java/app/A.java"));
        final Component a = new ClarpseProject(new ProjectFiles(dir.toString()), Lang.JAVA).result().model()
                .copyOfComponent("app.A").orElseThrow();
        assertEquals(root + "/src/main/java/app/A.java", a.sourceFile());
        final Component b = new ClarpseProject(new ProjectFiles(dir.toString()), Lang.JAVA).result().model()
                .copyOfComponent("app.sub.B").orElseThrow();
        assertTrue(b.importedFiles().toString(), b.importedFiles().contains(root + "/src/main/java/app/Qux.java"));
    }

    private static OOPSourceCodeModel modelOf(final String... pathAndSource) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        for (int i = 0; i < pathAndSource.length; i += 2) {
            files.insertFile(new ProjectFile(pathAndSource[i], pathAndSource[i + 1]));
        }
        return model(files);
    }

    private static Set<String> referencedBy(final OOPSourceCodeModel model, final String component) {
        final Component found = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component));
        final Set<String> names = new TreeSet<>();
        for (final ComponentReference ref : found.references()) {
            names.add(ref.invokedComponent());
        }
        return names;
    }

    // A call receiver that is not a type in scope names no type.

    @Test
    public void anUnknownUpperCaseReceiverIsNotMadeATypeOfThePackage() throws Exception {
        final OOPSourceCodeModel model = modelOf("/src/app/A.java", String.join("\n",
                "package app;",
                "public class A {",
                "  void f() { LOGGER.info(\"x\"); }",
                "}"));
        final Set<String> refs = referencedBy(model, "app.A.f()");
        assertFalse(refs.toString(), refs.stream().anyMatch(r -> r.endsWith("LOGGER")));
    }

    @Test
    public void anUpperCaseReceiverThatMayBeInheritedFromAnUnseenBaseNamesNoType() throws Exception {
        final OOPSourceCodeModel model = modelOf("/src/app/A.java", String.join("\n",
                "package app;",
                "import lib.Base;",
                "public class A extends Base {",
                "  void f() { LOG.warn(\"x\"); }",
                "}"));
        final Set<String> refs = referencedBy(model, "app.A.f()");
        assertFalse(refs.toString(), refs.stream().anyMatch(r -> r.endsWith("LOG")));
    }

    @Test
    public void aReceiverThatIsATypeInScopeStillNamesIt() throws Exception {
        final OOPSourceCodeModel model = modelOf(
                "/src/app/Util.java", "package app;\npublic class Util { public static int make() { return 1; } }\n",
                "/src/app/A.java", String.join("\n",
                        "package app;",
                        "import ext.Tool;",
                        "public class A {",
                        "  int f() { return Util.make(); }",
                        "  void g() { Tool.run(); }",
                        "}"));
        assertTrue(referencedBy(model, "app.A.f()").contains("app.Util"));
        assertTrue(referencedBy(model, "app.A.g()").contains("ext.Tool"));
    }

    // A bare name is a type of the current package only when the project has that type.

    @Test
    public void aNameFromALibraryWildcardImportIsNotMadeATypeOfThePackage() throws Exception {
        final OOPSourceCodeModel model = modelOf("/src/app/web/C.java", String.join("\n",
                "package app.web;",
                "import org.springframework.http.*;",
                "public class C {",
                "  ResponseEntity<String> r;",
                "  HttpHeaders h() { return null; }",
                "}"));
        final Set<String> refs = referencedBy(model, "app.web.C");
        assertFalse(refs.toString(), refs.stream().anyMatch(r -> r.startsWith("app.web.")));
        assertTrue(refs.toString(), refs.contains("ResponseEntity"));
        assertTrue(refs.toString(), refs.contains("HttpHeaders"));
    }

    @Test
    public void aBareNameWithNoSuchTypeStaysBareAndNeverBindsAnUnrelatedPackage() throws Exception {
        final OOPSourceCodeModel model = modelOf(
                "/src/com/other/Widget.java", "package com.other;\npublic class Widget { }\n",
                "/src/app/b/D.java", String.join("\n",
                        "package app.b;",
                        "import lib.ext.*;",
                        "public class D {",
                        "  Gadget g;",
                        "  Widget w;",
                        "}"));
        final Set<String> refs = referencedBy(model, "app.b.D");
        assertEquals("[Gadget, Widget]", refs.toString());
    }

    @Test
    public void aSamePackageTypeAndAnImportedTypeStillBind() throws Exception {
        final OOPSourceCodeModel model = modelOf(
                "/src/app/Sib.java", "package app;\npublic class Sib { }\n",
                "/src/other/Imp.java", "package other;\npublic class Imp { }\n",
                "/src/app/E.java", String.join("\n",
                        "package app;",
                        "import other.Imp;",
                        "import lib.ext.*;",
                        "public class E {",
                        "  Sib s;",
                        "  Imp i;",
                        "}"));
        assertEquals("[app.Sib, other.Imp]", referencedBy(model, "app.E").toString());
    }

    // Only java.lang is imported implicitly, and after everything the file names.

    @Test
    public void aRepositoryTypeSharingANameWithAJavaUtilTypeIsTheRepositoryType() throws Exception {
        final OOPSourceCodeModel model = modelOf(
                "/src/com/domain/Provider.java", "package com.domain;\npublic class Provider { }\n",
                "/src/com/domain/Stack.java", "package com.domain;\npublic class Stack { }\n",
                "/src/com/domain/Properties.java", "package com.domain;\npublic class Properties { }\n",
                "/src/app/F.java", String.join("\n",
                        "package app;",
                        "import com.domain.*;",
                        "public class F {",
                        "  Provider p;",
                        "  Stack s;",
                        "  Properties props;",
                        "  String text;",
                        "}"));
        assertEquals("[com.domain.Properties, com.domain.Provider, com.domain.Stack, java.lang.String]",
                referencedBy(model, "app.F").toString());
    }

    @Test
    public void aTypeOutsideJavaLangIsNotImportedImplicitly() throws Exception {
        final OOPSourceCodeModel model = modelOf("/src/app/G.java", String.join("\n",
                "package app;",
                "public class G {",
                "  Stack s;",
                "  Provider p;",
                "  Attributes a;",
                "  Collection c;",
                "}"));
        final Set<String> refs = referencedBy(model, "app.G");
        assertFalse(refs.toString(), refs.stream().anyMatch(r -> r.startsWith("java.") || r.startsWith("app.")));
    }

    @Test
    public void javaUtilStillResolvesWhereTheFileImportsIt() throws Exception {
        final OOPSourceCodeModel model = modelOf("/src/app/H.java", String.join("\n",
                "package app;",
                "import java.util.*;",
                "import java.io.File;",
                "public class H {",
                "  List<String> l;",
                "  Stack<String> s;",
                "  File f;",
                "}"));
        assertEquals("[java.io.File, java.lang.String, java.util.List, java.util.Stack]",
                referencedBy(model, "app.H").toString());
    }

    @Test
    public void aSamePackageTypeBeatsJavaLangAndANameBothOnDemandSourcesDeclareBindsToNeither() throws Exception {
        final OOPSourceCodeModel model = modelOf(
                "/src/com/domain/Number.java", "package com.domain;\npublic class Number { }\n",
                "/src/app/Error.java", "package app;\npublic class Error { }\n",
                "/src/app/J.java", String.join("\n",
                        "package app;",
                        "import com.domain.*;",
                        "public class J {",
                        "  Error e;",
                        "  Number n;",
                        "  Object o;",
                        "}"));
        // `Number` is kept as written, ambiguous: it binds to neither com.domain.Number nor java.lang.Number.
        assertEquals("[Number, app.Error, java.lang.Object]", referencedBy(model, "app.J").toString());
    }
}
