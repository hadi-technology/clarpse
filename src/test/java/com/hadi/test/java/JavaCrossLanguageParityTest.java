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
        return tree;
    }

    private static ProjectFiles fromDirectory(final Map<String, String> tree) throws Exception {
        final Path dir = Files.createTempDirectory(
                Path.of(System.getProperty("java.io.tmpdir")), "parity");
        for (final Map.Entry<String, String> file : tree.entrySet()) {
            final Path target = dir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        return new ProjectFiles(dir.toString());
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

    private static Set<String> describe(final OOPSourceCodeModel model) {
        final Set<String> lines = new TreeSet<>();
        for (final Component c : model.components().toList()) {
            lines.add("C|" + c.uniqueName() + "|" + c.componentType() + "|" + c.sourceFile());
            for (final ComponentReference ref : c.references()) {
                lines.add("R|" + c.uniqueName() + "|" + ref.getClass().getSimpleName() + "|"
                        + ref.invokedComponent() + "|" + ref.isExternal() + "|" + ref.resolutionKind());
            }
        }
        return lines;
    }

    private static OOPSourceCodeModel model(final ProjectFiles files) throws Exception {
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @Test
    public void aDirectoryGivesTheModelItsArchiveGives() throws Exception {
        final Set<String> fromDir = describe(model(fromDirectory(tree())));
        final Set<String> fromZip = describe(model(fromZip(tree())));
        assertEquals(fromZip, fromDir);
    }

    @Test
    public void aDirectoryBindsSamePackageTypesAnnotationsAndConstants() throws Exception {
        final Set<String> lines = describe(model(fromDirectory(tree())));
        final String all = lines.toString();
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A.e|") && l.contains("|app.Error|false|")));
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A|AnnotationReference|app.Anno|false|")));
        assertTrue(all, lines.stream().anyMatch(l -> l.startsWith("R|app.A.f()|") && l.contains("|app.Qux|false|")));
        assertFalse(all, all.contains("java.lang.Error"));
    }

    @Test
    public void aDirectoryHoldsFilesUnderTheirPathInsideIt() throws Exception {
        final ProjectFiles files = fromDirectory(tree());
        final Set<String> paths = new TreeSet<>();
        for (final ProjectFile f : files.files(Lang.JAVA)) {
            paths.add(f.path());
        }
        assertTrue(paths.toString(), paths.contains("/src/main/java/app/A.java"));
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
}
