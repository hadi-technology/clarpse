package com.hadi.test.java;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@code Component.importedFiles()} for Java: the repository files a file's import directives
 * resolve to, as project file paths (the form of {@code ProjectFile.path()} and
 * {@code Component.sourceFile()}).
 */
public class JavaImportedFilesTest {

    private static final String MODEL = "/src/model/";
    private static final String USER = "/src/app/User.java";

    private static ProjectFiles project(final String... pathsAndContents) {
        final ProjectFiles files = new ProjectFiles();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            files.insertFile(new ProjectFile(pathsAndContents[i], pathsAndContents[i + 1]));
        }
        return files;
    }

    private static OOPSourceCodeModel compile(final ProjectFiles files) throws Exception {
        final CompileResult result = new ClarpseProject(files, Lang.JAVA).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model();
    }

    private static List<String> importedBy(final OOPSourceCodeModel model, final String name) {
        return List.copyOf(model.component(name).orElseThrow(() -> new AssertionError("no component " + name))
                .importedFiles());
    }

    private static String user(final String imports) {
        return "package app;\n" + imports + "\npublic class User { }\n";
    }

    private static final String[] MODEL_FILES = {
        MODEL + "Order.java", "package model;\npublic class Order { public static final int MAX = 1;"
                + " public static int count() { return 0; } public static class Line { } }\n",
        MODEL + "Customer.java", "package model;\npublic class Customer { }\n",
        MODEL + "Invoice.java", "package model;\npublic interface Invoice { }\n",
        "/src/model/sub/Deep.java", "package model.sub;\npublic class Deep { }\n",
        "/src/other/Other.java", "package other;\npublic class Other { }\n",
    };

    private static ProjectFiles withModel(final String userImports) {
        final String[] all = new String[MODEL_FILES.length + 2];
        System.arraycopy(MODEL_FILES, 0, all, 0, MODEL_FILES.length);
        all[MODEL_FILES.length] = USER;
        all[MODEL_FILES.length + 1] = user(userImports);
        return project(all);
    }

    @Test
    public void aSingleTypeImportGivesTheFileDeclaringIt() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.Customer;"));

        assertEquals(List.of(MODEL + "Customer.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aNestedTypeImportGivesTheFileOfItsTopLevelType() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.Order.Line;"));

        assertEquals(List.of(MODEL + "Order.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aWildcardImportOfAPackageWithProjectFilesAddsNothing() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.*;\nimport model.sub.*;"));

        assertTrue(importedBy(model, "app.User").isEmpty());
        assertEquals(Set.of("model", "model.sub"), model.component("app.User").orElseThrow().imports());
    }

    @Test
    public void aWildcardAddsNothingBesideTheNamedImportsOfTheSamePackage() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.*;\nimport model.Customer;"));

        assertEquals(List.of(MODEL + "Customer.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aWildcardOverATypeGivesTheFileDeclaringTheType() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.Order.*;"));

        assertEquals(List.of(MODEL + "Order.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aStaticMemberImportGivesTheFileDeclaringTheType() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import static model.Order.count;"));

        assertEquals(List.of(MODEL + "Order.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aStaticConstantImportGivesTheFileDeclaringTheType() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import static model.Order.MAX;"));

        assertEquals(List.of(MODEL + "Order.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aStaticMemberOfANestedTypeGivesTheFileOfTheTopLevelType() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import static model.Order.Line.make;"));

        assertEquals(List.of(MODEL + "Order.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aStaticWildcardGivesTheFileDeclaringTheType() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import static model.Customer.*;"));

        assertEquals(List.of(MODEL + "Customer.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aStaticWildcardNeverReadsAPackage() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import static model.*;"));

        assertTrue(importedBy(model, "app.User").isEmpty());
    }

    @Test
    public void libraryImportsAddNothing() throws Exception {
        final OOPSourceCodeModel model = compile(withModel(String.join("\n",
                "import java.util.List;",
                "import java.util.*;",
                "import java.util.Map.Entry;",
                "import static java.util.Collections.emptyList;",
                "import static java.lang.Math.*;",
                "import javax.inject.Inject;",
                "import org.springframework.stereotype.Service;")));

        assertTrue(importedBy(model, "app.User").isEmpty());
        assertEquals(7, model.component("app.User").orElseThrow().imports().size());
    }

    @Test
    public void anImportNamingNothingInTheProjectAddsNothing() throws Exception {
        final OOPSourceCodeModel model = compile(withModel(String.join("\n",
                "import model.Ghost;",
                "import model.ghost.Phantom;",
                "import ghost.*;",
                "import static model.Ghost.make;")));

        assertTrue(importedBy(model, "app.User").isEmpty());
    }

    @Test
    public void aTypeOfTheFilesOwnPackageNeedsNoImportAndAddsNothing() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USER, "package app;\npublic class User { Helper helper; String name; }\n",
                "/src/app/Helper.java", "package app;\npublic class Helper { }\n"));

        assertTrue(importedBy(model, "app.User").isEmpty());
    }

    @Test
    public void aRedundantImportOfASamePackageTypeIsStillADirective() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USER, user("import app.Helper;"),
                "/src/app/Helper.java", "package app;\npublic class Helper { }\n"));

        assertEquals(List.of("/src/app/Helper.java"), importedBy(model, "app.User"));
    }

    @Test
    public void aFileDoesNotImportItself() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                "/src/app/Outer.java", "package app;\nimport app.Outer.Inner;\nimport app.Peer;\n"
                        + "public class Outer { public static class Inner { } }\n",
                "/src/app/Peer.java", "package app;\npublic class Peer { }\n"));

        assertEquals(List.of("/src/app/Peer.java"), importedBy(model, "app.Outer"));
    }

    @Test
    public void packageInfoAndModuleInfoAddNothing() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USER, user("import model.Customer;\nimport model.*;"),
                MODEL + "Customer.java", "package model;\npublic class Customer { }\n",
                MODEL + "package-info.java", "/** The model. */\npackage model;\n",
                "/src/module-info.java", "module demo.app {\n  requires java.base;\n  requires demo.model;\n"
                        + "  exports app;\n}\n"));

        assertEquals(List.of(MODEL + "Customer.java"), importedBy(model, "app.User"));
    }

    @Test
    public void entriesFollowTheDirectivesThenPathOrderWithoutRepeats() throws Exception {
        final OOPSourceCodeModel model = compile(withModel(String.join("\n",
                "import other.Other;",
                "import model.*;",
                "import model.Order;",
                "import model.Customer;",
                "import static other.Other.run;",
                "import static model.Order.count;")));

        assertEquals(List.of("/src/other/Other.java", MODEL + "Order.java", MODEL + "Customer.java"),
                importedBy(model, "app.User"));
    }

    @Test
    public void everyEntryIsTheSourceFileOfAComponentInThatFile() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.Order;\nimport model.Customer;"));

        assertTrue(importedBy(model, "app.User").contains(
                model.component("model.Order").orElseThrow().sourceFile()));
        assertTrue(importedBy(model, "app.User").contains(
                model.component("model.Customer").orElseThrow().sourceFile()));
    }

    @Test
    public void everyComponentCarryingImportsCarriesTheImportedFiles() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USER, "package app;\nimport model.Customer;\n"
                        + "public class User {\n"
                        + "  Customer field;\n"
                        + "  Customer find(Customer c) { Customer local = c; return local; }\n"
                        + "  public static class Inner { }\n"
                        + "  enum Mode { A }\n"
                        + "  interface Port { }\n"
                        + "  record Pair(int a) { }\n"
                        + "  @interface Marker { }\n"
                        + "}\n"
                        + "enum Level { LOW }\n"
                        + "interface Api { }\n"
                        + "record Point(int x) { }\n"
                        + "@interface Tag { }\n",
                MODEL + "Customer.java", "package model;\npublic class Customer { }\n"));
        final List<String> expected = List.of(MODEL + "Customer.java");
        int types = 0;

        for (final Component component : (Iterable<Component>) model.components()::iterator) {
            if (!"/src/app/User.java".equals(component.sourceFile())) {
                continue;
            }
            if (component.imports().isEmpty()) {
                assertTrue(component.uniqueName(), component.importedFiles().isEmpty());
            } else {
                types++;
                assertEquals(component.uniqueName(), expected, List.copyOf(component.importedFiles()));
            }
        }
        assertTrue("member types, enums, interfaces, records and annotations are all covered: " + types,
                types >= 10);
    }

    @Test
    public void importsAreUnchanged() throws Exception {
        final OOPSourceCodeModel model = compile(withModel(String.join("\n",
                "import model.Order;",
                "import model.Order.Line;",
                "import model.*;",
                "import static model.Order.count;",
                "import java.util.List;")));

        assertEquals(Set.of("model.Order", "model.Order.Line", "model", "model.Order.count", "java.util.List"),
                model.component("app.User").orElseThrow().imports());
    }

    @Test
    public void theSetIsUnmodifiableAndNeverNull() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.Customer;"));

        assertThrows(UnsupportedOperationException.class,
                () -> model.component("app.User").orElseThrow().importedFiles().add("/x.java"));
        assertTrue(model.component("model.Customer").orElseThrow().importedFiles().isEmpty());
    }

    @Test
    public void copiesKeepTheField() throws Exception {
        final OOPSourceCodeModel model = compile(withModel("import model.Customer;"));
        final List<String> expected = List.of(MODEL + "Customer.java");

        assertEquals(expected, List.copyOf(model.copyOfComponent("app.User").orElseThrow().importedFiles()));
        assertEquals(expected, List.copyOf(model.copy().component("app.User").orElseThrow().importedFiles()));
    }

    @Test
    public void aTypeOfAnotherLanguageIsNotAJavaFile() throws Exception {
        final ProjectFiles files = withModel("import kt.Widget;");
        files.insertFile(new ProjectFile("/src/kt/Widget.kt", "package kt\nclass Widget\n"));
        final OOPSourceCodeModel model = compile(files);

        assertTrue(importedBy(model, "app.User").isEmpty());
    }

    @Test
    public void aOneLevelAnalysisRecordsTheFieldOnAnalysedAndBoundaryFiles() throws Exception {
        final ProjectFiles files = project(
                USER, "package app;\nimport model.Customer;\npublic class User { Customer c; }\n",
                MODEL + "Customer.java", "package model;\nimport other.Other;\npublic class Customer { Other o; }\n",
                "/src/other/Other.java", "package other;\npublic class Other { }\n");
        final CompileResult result = new ClarpseProject(files, Lang.JAVA, List.of(USER),
                AnalysisOptions.oneLevel()).result();
        final Component customer = result.model().component("model.Customer").orElseThrow();

        assertEquals(List.of(MODEL + "Customer.java"), importedBy(result.model(), "app.User"));
        assertTrue(customer.isBoundary());
        assertEquals(List.of("/src/other/Other.java"), List.copyOf(customer.importedFiles()));
    }

    @Test
    public void aFileOutsideTheAnalysedSetStillResolvesAgainstTheWholeProject() throws Exception {
        final CompileResult result = new ClarpseProject(withModel("import model.Order;\nimport model.Customer;"),
                Lang.JAVA, List.of(USER)).result();

        assertEquals(List.of(MODEL + "Order.java", MODEL + "Customer.java"),
                importedBy(result.model(), "app.User"));
        assertFalse(result.model().containsComponent("model.Order"));
    }
}
